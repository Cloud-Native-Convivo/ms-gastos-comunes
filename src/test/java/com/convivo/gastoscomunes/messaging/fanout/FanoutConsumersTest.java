package com.convivo.gastoscomunes.messaging.fanout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.convivo.gastoscomunes.config.MailProperties;
import com.convivo.gastoscomunes.domain.EstadoGasto;
import com.convivo.gastoscomunes.domain.GastoComun;
import com.convivo.gastoscomunes.domain.OrigenGasto;
import com.convivo.gastoscomunes.domain.SolicitudJob;
import com.convivo.gastoscomunes.domain.SolicitudJobRepository;
import com.convivo.gastoscomunes.repository.GastoComunRepository;
import com.convivo.gastoscomunes.service.PdfGastosGeneradorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.data.domain.PageImpl;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class FanoutConsumersTest {

    private SolicitudJobRepository jobRepository;
    private GastoComunRepository gastoRepository;
    private PdfGastosGeneradorService pdfGeneradorService;
    private ObjectMapper objectMapper;

    private PdfGastosConsumer pdfConsumer;
    private NotificacionGastosConsumer notificacionConsumer;
    private EmailEnviadoRepository emailEnviadoRepository;

    @BeforeEach
    void setUp() {
        jobRepository = mock(SolicitudJobRepository.class);
        gastoRepository = mock(GastoComunRepository.class);
        pdfGeneradorService = mock(PdfGastosGeneradorService.class);
        objectMapper = new ObjectMapper();
        emailEnviadoRepository = mock(EmailEnviadoRepository.class);

        pdfConsumer = new PdfGastosConsumer(jobRepository, gastoRepository, pdfGeneradorService, objectMapper);
        notificacionConsumer = new NotificacionGastosConsumer(
                objectMapper, emailEnviadoRepository, mock(JavaMailSender.class), new MailProperties(false, "no-reply@convivo.cl"));
    }

    @Test
    void debeProcesarPdfYCompletarJobConGastosExistentes() throws Exception {
        when(jobRepository.findById("ticket-100")).thenReturn(Optional.empty());
        when(jobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));

        GastoComun g1 = GastoComun.crear("cond-1", "204-B", "Gasto Común Ordinario", new BigDecimal("75000"), OrigenGasto.MANUAL, null, LocalDate.now());
        GastoComun g2 = GastoComun.crear("cond-1", "204-B", "Fondo Extraordinario", new BigDecimal("25000"), OrigenGasto.MANUAL, null, LocalDate.now());
        enMes(g1, "2026-09-15T12:00:00Z");
        enMes(g2, "2026-09-20T12:00:00Z");
        when(gastoRepository.findByUnidadId(eq("204-B"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(g1, g2)));

        JsonNode payload = objectMapper.readTree("""
                {
                    "ticket_id": "ticket-100",
                    "unidad_id": "204-B",
                    "mes": "Septiembre 2026",
                    "usuario_id": "usr-test"
                }
                """);

        pdfConsumer.procesarSolicitudPdf(payload);

        verify(pdfGeneradorService).generarComprobante(
                eq("ticket-100"), eq("Unidad 204-B"), eq("Septiembre 2026"),
                eq(List.of(
                        Map.of("concepto", "Gasto Común Ordinario", "monto", "75.000", "pct", "75"),
                        Map.of("concepto", "Fondo Extraordinario", "monto", "25.000", "pct", "25"))),
                eq("100.000"), eq("Pendiente"));
        verify(jobRepository, org.mockito.Mockito.atLeastOnce()).save(org.mockito.ArgumentMatchers.argThat(job ->
                "ticket-100".equals(job.getTicketId()) && "COMPLETADO".equals(job.getEstado())));
    }

    @Test
    void sinGastosElPdfNoInventaUnDesglose() throws Exception {
        when(jobRepository.findById("ticket-empty")).thenReturn(Optional.empty());
        when(jobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gastoRepository.findByUnidadId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        JsonNode payload = objectMapper.readTree("""
                {
                    "ticket_id": "ticket-empty",
                    "unidad_id": "101-A",
                    "mes": "Agosto 2026"
                }
                """);

        pdfConsumer.procesarSolicitudPdf(payload);

        verify(pdfGeneradorService).generarComprobante(
                eq("ticket-empty"), eq("Unidad 101-A"), eq("Agosto 2026"),
                eq(List.of()), eq("0"), eq("Sin gastos registrados"));
        verify(jobRepository, org.mockito.Mockito.atLeastOnce()).save(org.mockito.ArgumentMatchers.argThat(job ->
                "ticket-empty".equals(job.getTicketId()) && "COMPLETADO".equals(job.getEstado())));
    }

    @Test
    void elPdfSoloIncluyeLosGastosVigentesDelMesPedido() throws Exception {
        when(jobRepository.findById("ticket-mes")).thenReturn(Optional.empty());
        when(jobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));

        GastoComun delMes = GastoComun.crear("cond-1", "204-B", "Octubre vigente", new BigDecimal("40000"), OrigenGasto.MANUAL, null, LocalDate.now());
        GastoComun otroMes = GastoComun.crear("cond-1", "204-B", "Septiembre", new BigDecimal("90000"), OrigenGasto.MANUAL, null, LocalDate.now());
        GastoComun eliminado = GastoComun.crear("cond-1", "204-B", "Octubre eliminado", new BigDecimal("7000"), OrigenGasto.MANUAL, null, LocalDate.now());
        enMes(delMes, "2026-10-05T12:00:00Z");
        enMes(otroMes, "2026-09-05T12:00:00Z");
        enMes(eliminado, "2026-10-06T12:00:00Z");
        ReflectionTestUtils.setField(eliminado, "estado", EstadoGasto.ELIMINADO);
        when(gastoRepository.findByUnidadId(eq("204-B"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(delMes, otroMes, eliminado)));

        pdfConsumer.procesarSolicitudPdf(objectMapper.readTree("""
                { "ticket_id": "ticket-mes", "unidad_id": "204-B", "mes": "Octubre 2026" }
                """));

        verify(pdfGeneradorService).generarComprobante(
                eq("ticket-mes"), eq("Unidad 204-B"), eq("Octubre 2026"),
                eq(List.of(Map.of("concepto", "Octubre vigente", "monto", "40.000", "pct", "100"))),
                eq("40.000"), eq("Pendiente"));
    }

    @Test
    void incluyeLosGastosDeReservaDelUsuarioYMuestraLaUnidadDelPerfil() throws Exception {
        when(jobRepository.findById("ticket-reservas")).thenReturn(Optional.empty());
        when(jobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));

        GastoComun reserva = GastoComun.crear("cond-1", "unidad-usr-7", "Reserva Quincho el 10/12/2026 a las 10:00",
                new BigDecimal("15000"), OrigenGasto.RESERVA_ESPACIO, "45", LocalDate.now());
        GastoComun ordinario = GastoComun.crear("cond-1", "Casa M202", "Gasto común ordinario",
                new BigDecimal("30000"), OrigenGasto.MANUAL, null, LocalDate.now());
        enMes(reserva, "2026-11-20T12:00:00Z");
        enMes(ordinario, "2026-11-02T12:00:00Z");
        when(gastoRepository.findByUnidadId(eq("Casa M202"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(ordinario)));
        when(gastoRepository.findByUnidadId(eq("unidad-usr-7"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(reserva)));

        pdfConsumer.procesarSolicitudPdf(objectMapper.readTree("""
                { "ticket_id": "ticket-reservas", "unidad_id": "Casa M202", "usuario_id": "usr-7", "mes": "Noviembre 2026" }
                """));

        verify(pdfGeneradorService).generarComprobante(
                eq("ticket-reservas"), eq("Unidad Casa M202"), eq("Noviembre 2026"),
                eq(List.of(
                        Map.of("concepto", "Gasto común ordinario", "monto", "30.000", "pct", "67"),
                        Map.of("concepto", "Reserva Quincho el 10/12/2026 a las 10:00", "monto", "15.000", "pct", "33"))),
                eq("45.000"), eq("Pendiente"));
    }

    @Test
    void siLaUnidadDelPerfilEsLaClaveDeReservaNoSeLeenLosGastosDosVeces() throws Exception {
        when(jobRepository.findById("ticket-dup")).thenReturn(Optional.empty());
        when(jobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));
        GastoComun reserva = GastoComun.crear("cond-1", "unidad-usr-7", "Reserva", new BigDecimal("5000"), OrigenGasto.RESERVA_ESPACIO, "1", LocalDate.now());
        enMes(reserva, "2026-11-20T12:00:00Z");
        when(gastoRepository.findByUnidadId(eq("unidad-usr-7"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(reserva)));

        pdfConsumer.procesarSolicitudPdf(objectMapper.readTree("""
                { "ticket_id": "ticket-dup", "unidad_id": "unidad-usr-7", "usuario_id": "usr-7", "mes": "Noviembre 2026" }
                """));

        verify(pdfGeneradorService).generarComprobante(
                eq("ticket-dup"), eq("Unidad unidad-usr-7"), eq("Noviembre 2026"), any(), eq("5.000"), eq("Pendiente"));
        verify(gastoRepository, org.mockito.Mockito.times(1)).findByUnidadId(eq("unidad-usr-7"), any(Pageable.class));
    }

    @Test
    void siTodosLosGastosDelMesEstanSaldadosElEstadoEsAlDia() throws Exception {
        when(jobRepository.findById("ticket-pagado")).thenReturn(Optional.empty());
        when(jobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));

        GastoComun pagado = GastoComun.crear("cond-1", "204-B", "Gasto común", new BigDecimal("60000"), OrigenGasto.MANUAL, null, LocalDate.now());
        enMes(pagado, "2026-10-05T12:00:00Z");
        ReflectionTestUtils.setField(pagado, "saldoPendiente", BigDecimal.ZERO);
        ReflectionTestUtils.setField(pagado, "estado", EstadoGasto.PAGADO);
        when(gastoRepository.findByUnidadId(eq("204-B"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(pagado)));

        pdfConsumer.procesarSolicitudPdf(objectMapper.readTree("""
                { "ticket_id": "ticket-pagado", "unidad_id": "204-B", "mes": "Octubre 2026" }
                """));

        verify(pdfGeneradorService).generarComprobante(
                eq("ticket-pagado"), eq("Unidad 204-B"), eq("Octubre 2026"), any(), eq("60.000"), eq("Al día"));
    }

    @Test
    void sinUnidadLaSolicitudFallaEnVezDeUsarUnaPorDefecto() {
        when(jobRepository.findById("ticket-sin-unidad")).thenReturn(Optional.empty());
        when(jobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));

        JsonNode payload = objectMapper.createObjectNode().put("ticket_id", "ticket-sin-unidad");

        assertThatThrownBy(() -> pdfConsumer.procesarSolicitudPdf(payload))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unidad_id");
        verify(jobRepository, org.mockito.Mockito.atLeastOnce()).save(org.mockito.ArgumentMatchers.argThat(job ->
                "ticket-sin-unidad".equals(job.getTicketId()) && "FALLIDO".equals(job.getEstado())));
        org.mockito.Mockito.verifyNoInteractions(pdfGeneradorService);
    }

    @Test
    void elPeriodoSeInterpretaDelTextoYSinTextoValidoEsElMesActual() {
        assertThat(PdfGastosConsumer.resolverPeriodo("Noviembre 2026")).isEqualTo(YearMonth.of(2026, 11));
        assertThat(PdfGastosConsumer.resolverPeriodo("  agosto   2025 ")).isEqualTo(YearMonth.of(2025, 8));
        assertThat(PdfGastosConsumer.etiquetaPeriodo(YearMonth.of(2026, 3))).isEqualTo("Marzo 2026");
        YearMonth actual = YearMonth.now(java.time.ZoneId.of("America/Santiago"));
        assertThat(PdfGastosConsumer.resolverPeriodo(null)).isEqualTo(actual);
        assertThat(PdfGastosConsumer.resolverPeriodo("Período actual")).isEqualTo(actual);
        assertThat(PdfGastosConsumer.resolverPeriodo("Noviembre dosmil")).isEqualTo(actual);
    }

    /** Los gastos de prueba no pasan por JPA, así que la fecha de creación se fija a mano. */
    private static void enMes(GastoComun gasto, String instanteIso) {
        ReflectionTestUtils.setField(gasto, "fechaCreacion", Instant.parse(instanteIso));
    }

    @Test
    void debeMarcarJobComoFallidoYRelanzarSiFallaGeneracionPdf() {
        when(jobRepository.findById("ticket-err")).thenReturn(Optional.empty());
        when(jobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gastoRepository.findByUnidadId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        doThrow(new RuntimeException("Error en motor iText/PDF")).when(pdfGeneradorService)
                .generarComprobante(any(), any(), any(), any(), any(), any());

        JsonNode payload = objectMapper.createObjectNode()
                .put("ticket_id", "ticket-err")
                .put("unidad_id", "101-A");

        assertThatThrownBy(() -> pdfConsumer.procesarSolicitudPdf(payload))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error en worker de PDF");

        verify(jobRepository, org.mockito.Mockito.atLeastOnce()).save(org.mockito.ArgumentMatchers.argThat(job ->
                "ticket-err".equals(job.getTicketId()) && "FALLIDO".equals(job.getEstado())));
    }

    @Test
    void debeConsumirMensajeAMQPPdfExitosoConAck() throws Exception {
        String json = """
                {
                    "ticket_id": "ticket-ack",
                    "unidad_id": "101-A"
                }
                """;
        Message message = mock(Message.class);
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(77L);
        when(message.getMessageProperties()).thenReturn(props);
        when(message.getBody()).thenReturn(json.getBytes());

        when(jobRepository.findById("ticket-ack")).thenReturn(Optional.empty());
        when(jobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gastoRepository.findByUnidadId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        Channel channel = mock(Channel.class);

        pdfConsumer.recibir(message, channel);

        verify(channel).basicAck(77L, false);
    }

    @Test
    void debeRechazarMensajeAMQPPdfCorrupto() throws IOException {
        Message message = mock(Message.class);
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(88L);
        when(message.getMessageProperties()).thenReturn(props);
        when(message.getBody()).thenReturn("invalido".getBytes());

        Channel channel = mock(Channel.class);

        pdfConsumer.recibir(message, channel);

        verify(channel).basicReject(88L, false);
    }

    @Test
    void debeRechazarMensajeAMQPPdfSiProcesamientoFalla() throws IOException {
        String json = """
                {
                    "ticket_id": "ticket-fail",
                    "unidad_id": "101-A"
                }
                """;
        Message message = mock(Message.class);
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(99L);
        when(message.getMessageProperties()).thenReturn(props);
        when(message.getBody()).thenReturn(json.getBytes());

        doThrow(new RuntimeException("Crash")).when(jobRepository).findById("ticket-fail");

        Channel channel = mock(Channel.class);

        pdfConsumer.recibir(message, channel);

        verify(channel).basicReject(99L, false);
    }

    @Test
    void debeProcesarNotificacionSinErroresConValoresPorDefecto() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                    "ticket_id": "ticket-notif"
                }
                """);

        notificacionConsumer.procesarNotificacion(payload);
        assertThat(payload.get("ticket_id").asText()).isEqualTo("ticket-notif");
    }

    @Test
    void debeConsumirMensajeAMQPNotificacionExitosoConAck() throws Exception {
        String json = """
                {
                    "ticket_id": "ticket-notif-ack",
                    "email": "test@convivo.cl",
                    "unidad_id": "501"
                }
                """;
        Message message = mock(Message.class);
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(101L);
        when(message.getMessageProperties()).thenReturn(props);
        when(message.getBody()).thenReturn(json.getBytes());

        Channel channel = mock(Channel.class);

        notificacionConsumer.recibir(message, channel);

        verify(channel).basicAck(101L, false);
    }

    @Test
    void debeRechazarMensajeAMQPNotificacionCorrupto() throws IOException {
        Message message = mock(Message.class);
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(102L);
        when(message.getMessageProperties()).thenReturn(props);
        when(message.getBody()).thenReturn("invalido".getBytes());

        Channel channel = mock(Channel.class);

        notificacionConsumer.recibir(message, channel);

        verify(channel).basicReject(102L, false);
    }
}

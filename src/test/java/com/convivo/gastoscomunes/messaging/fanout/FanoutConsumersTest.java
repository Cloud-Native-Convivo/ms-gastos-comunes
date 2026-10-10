package com.convivo.gastoscomunes.messaging.fanout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class FanoutConsumersTest {

    private SolicitudJobRepository jobRepository;
    private GastoComunRepository gastoRepository;
    private PdfGastosGeneradorService pdfGeneradorService;
    private ObjectMapper objectMapper;

    private PdfGastosConsumer pdfConsumer;
    private NotificacionGastosConsumer notificacionConsumer;

    @BeforeEach
    void setUp() {
        jobRepository = mock(SolicitudJobRepository.class);
        gastoRepository = mock(GastoComunRepository.class);
        pdfGeneradorService = mock(PdfGastosGeneradorService.class);
        objectMapper = new ObjectMapper();

        pdfConsumer = new PdfGastosConsumer(jobRepository, gastoRepository, pdfGeneradorService, objectMapper);
        notificacionConsumer = new NotificacionGastosConsumer(objectMapper);
    }

    @Test
    void debeProcesarPdfYCompletarJobConGastosExistentes() throws Exception {
        when(jobRepository.findById("ticket-100")).thenReturn(Optional.empty());
        when(jobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));

        GastoComun g1 = GastoComun.crear("204-B", "Gasto Común Ordinario", new BigDecimal("75000"), OrigenGasto.MANUAL, null, LocalDate.now());
        GastoComun g2 = GastoComun.crear("204-B", "Fondo Extraordinario", new BigDecimal("25000"), OrigenGasto.MANUAL, null, LocalDate.now());
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

        verify(pdfGeneradorService).generarComprobante(eq("ticket-100"), eq("Unidad 204-B"), eq("Septiembre 2026"), any(), any(), eq("Al día"));
        verify(jobRepository, org.mockito.Mockito.atLeastOnce()).save(org.mockito.ArgumentMatchers.argThat(job ->
                "ticket-100".equals(job.getTicketId()) && "COMPLETADO".equals(job.getEstado())));
    }

    @Test
    void debeProcesarPdfFallbackSiNoHayGastos() throws Exception {
        when(jobRepository.findById("ticket-empty")).thenReturn(Optional.empty());
        when(jobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gastoRepository.findByUnidadId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        JsonNode payload = objectMapper.readTree("""
                {
                    "ticket_id": "ticket-empty",
                    "unidad_id": "101-A"
                }
                """);

        pdfConsumer.procesarSolicitudPdf(payload);

        verify(pdfGeneradorService).generarComprobante(eq("ticket-empty"), eq("Unidad 101-A"), eq("Agosto 2026"), any(), any(), eq("Al día"));
        verify(jobRepository, org.mockito.Mockito.atLeastOnce()).save(org.mockito.ArgumentMatchers.argThat(job ->
                "ticket-empty".equals(job.getTicketId()) && "COMPLETADO".equals(job.getEstado())));
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

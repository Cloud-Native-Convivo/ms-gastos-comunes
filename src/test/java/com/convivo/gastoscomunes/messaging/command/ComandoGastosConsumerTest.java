package com.convivo.gastoscomunes.messaging.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.convivo.gastoscomunes.domain.GastoComun;
import com.convivo.gastoscomunes.domain.Pago;
import com.convivo.gastoscomunes.domain.SolicitudJob;
import com.convivo.gastoscomunes.domain.SolicitudJobRepository;
import com.convivo.gastoscomunes.dto.GastoComunRequest;
import com.convivo.gastoscomunes.dto.PagoRequest;
import com.convivo.gastoscomunes.security.UsuarioContexto;
import com.convivo.gastoscomunes.service.GastoComunService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class ComandoGastosConsumerTest {

    private SolicitudJobRepository solicitudJobRepository;
    private GastoComunService gastoComunService;
    private ObjectMapper objectMapper;
    private ComandoGastosConsumer consumer;

    @BeforeEach
    void setUp() {
        solicitudJobRepository = mock(SolicitudJobRepository.class);
        gastoComunService = mock(GastoComunService.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new ComandoGastosConsumer(solicitudJobRepository, gastoComunService, objectMapper);
    }

    @Test
    void debeProcesarComandoRegistrarGastoExitoso() throws Exception {
        String json = """
        {
            "ticket_id": "ticket-101",
            "modulo": "GASTOS",
            "accion": "REGISTRAR_GASTO",
            "usuario_id": "admin-1",
            "rol": "administrador",
            "payload": {
                "unidad_id": "101-A",
                "concepto": "Gasto Octubre",
                "monto": 45000,
                "fecha_vencimiento": "2026-10-31"
            }
        }
        """;
        JsonNode root = objectMapper.readTree(json);

        GastoComun gastoMock = mock(GastoComun.class);
        when(gastoMock.getId()).thenReturn(10L);
        when(gastoMock.getUnidadId()).thenReturn("101-A");
        when(gastoMock.getConcepto()).thenReturn("Gasto Octubre");
        when(gastoMock.getMonto()).thenReturn(BigDecimal.valueOf(45000));
        when(gastoComunService.crear(any(GastoComunRequest.class))).thenReturn(gastoMock);

        when(solicitudJobRepository.findById("ticket-101")).thenReturn(Optional.empty());
        when(solicitudJobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.procesarComando(root);

        verify(solicitudJobRepository, org.mockito.Mockito.atLeastOnce()).save(org.mockito.ArgumentMatchers.argThat(job ->
                "ticket-101".equals(job.getTicketId()) && "COMPLETADO".equals(job.getEstado())));
    }

    @Test
    void debeProcesarComandoActualizarGastoExitoso() throws Exception {
        String json = """
        {
            "ticket_id": "ticket-102",
            "modulo": "GASTOS",
            "accion": "ACTUALIZAR_GASTO",
            "usuario_id": "admin-1",
            "rol": "administrador",
            "payload": {
                "id": 15,
                "unidadId": "102-B",
                "concepto": "Gasto Actualizado",
                "monto": 55000,
                "fechaVencimiento": "2026-11-15"
            }
        }
        """;
        JsonNode root = objectMapper.readTree(json);

        GastoComun gastoActualizado = mock(GastoComun.class);
        when(gastoActualizado.getId()).thenReturn(15L);
        when(gastoActualizado.getConcepto()).thenReturn("Gasto Actualizado");
        when(gastoComunService.actualizar(eq(15L), any(GastoComunRequest.class))).thenReturn(gastoActualizado);

        SolicitudJob jobExistente = new SolicitudJob(
                "ticket-102", "GASTOS", "ACTUALIZAR_GASTO", "admin-1", "PENDIENTE", null, null, Instant.now(), Instant.now());
        when(solicitudJobRepository.findById("ticket-102")).thenReturn(Optional.of(jobExistente));
        when(solicitudJobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.procesarComando(root);

        verify(gastoComunService).actualizar(eq(15L), any(GastoComunRequest.class));
        verify(solicitudJobRepository, org.mockito.Mockito.atLeastOnce()).save(org.mockito.ArgumentMatchers.argThat(job ->
                "ticket-102".equals(job.getTicketId()) && "COMPLETADO".equals(job.getEstado())));
    }

    @Test
    void debeProcesarComandoEliminarGastoExitoso() throws Exception {
        String json = """
        {
            "ticket_id": "ticket-103",
            "modulo": "GASTOS",
            "accion": "ELIMINAR_GASTO",
            "usuario_id": "admin-1",
            "payload": {
                "gasto_id": 20
            }
        }
        """;
        JsonNode root = objectMapper.readTree(json);

        when(solicitudJobRepository.findById("ticket-103")).thenReturn(Optional.empty());
        when(solicitudJobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.procesarComando(root);

        verify(gastoComunService).eliminar(20L);
        verify(solicitudJobRepository, org.mockito.Mockito.atLeastOnce()).save(org.mockito.ArgumentMatchers.argThat(job ->
                "ticket-103".equals(job.getTicketId()) && "COMPLETADO".equals(job.getEstado())));
    }

    @Test
    void debeProcesarComandoRegistrarPagoExitoso() throws Exception {
        String json = """
        {
            "ticket_id": "ticket-104",
            "modulo": "GASTOS",
            "accion": "REGISTRAR_PAGO",
            "usuario_id": "admin-1",
            "payload": {
                "gasto_id": 25,
                "monto": 30000,
                "metodo": "TRANSFERENCIA",
                "comprobante": "cmp-1234"
            }
        }
        """;
        JsonNode root = objectMapper.readTree(json);

        Pago pagoMock = mock(Pago.class);
        when(pagoMock.getId()).thenReturn(101L);
        when(pagoMock.getMonto()).thenReturn(BigDecimal.valueOf(30000));
        when(gastoComunService.registrarPago(eq(25L), any(PagoRequest.class), any(UsuarioContexto.class))).thenReturn(pagoMock);

        when(solicitudJobRepository.findById("ticket-104")).thenReturn(Optional.empty());
        when(solicitudJobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.procesarComando(root);

        verify(gastoComunService).registrarPago(eq(25L), any(PagoRequest.class), any(UsuarioContexto.class));
        verify(solicitudJobRepository, org.mockito.Mockito.atLeastOnce()).save(org.mockito.ArgumentMatchers.argThat(job ->
                "ticket-104".equals(job.getTicketId()) && "COMPLETADO".equals(job.getEstado())));
    }

    @Test
    void debeMarcarJobComoFallidoAnteAccionDesconocida() throws Exception {
        String json = """
        {
            "ticket_id": "ticket-999",
            "modulo": "GASTOS",
            "accion": "ACCION_INEXISTENTE",
            "usuario_id": "admin-1",
            "payload": {}
        }
        """;
        JsonNode root = objectMapper.readTree(json);
        when(solicitudJobRepository.findById("ticket-999")).thenReturn(Optional.empty());
        when(solicitudJobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.procesarComando(root);

        verify(solicitudJobRepository, org.mockito.Mockito.atLeastOnce()).save(org.mockito.ArgumentMatchers.argThat(job ->
                "ticket-999".equals(job.getTicketId()) && "FALLIDO".equals(job.getEstado())));
    }

    @Test
    void debeMarcarJobComoFallidoSiServicioLanzaExcepcion() throws Exception {
        String json = """
        {
            "ticket_id": "ticket-err",
            "modulo": "GASTOS",
            "accion": "ELIMINAR_GASTO",
            "usuario_id": "admin-1",
            "payload": {
                "id": 999
            }
        }
        """;
        JsonNode root = objectMapper.readTree(json);
        org.mockito.Mockito.doThrow(new RuntimeException("Gasto no encontrado")).when(gastoComunService).eliminar(999L);
        when(solicitudJobRepository.findById("ticket-err")).thenReturn(Optional.empty());
        when(solicitudJobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.procesarComando(root);

        verify(solicitudJobRepository, org.mockito.Mockito.atLeastOnce()).save(org.mockito.ArgumentMatchers.argThat(job ->
                "ticket-err".equals(job.getTicketId()) && "FALLIDO".equals(job.getEstado())));
    }

    @Test
    void debeProcesarMensajeAMQPValidoConAck() throws IOException {
        String json = """
        {
            "ticket_id": "ticket-ack",
            "modulo": "GASTOS",
            "accion": "ELIMINAR_GASTO",
            "usuario_id": "admin-1",
            "payload": {
                "id": 12
            }
        }
        """;
        Message message = mock(Message.class);
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(55L);
        when(message.getMessageProperties()).thenReturn(props);
        when(message.getBody()).thenReturn(json.getBytes());

        when(solicitudJobRepository.findById("ticket-ack")).thenReturn(Optional.empty());
        when(solicitudJobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));

        Channel channel = mock(Channel.class);

        consumer.recibir(message, channel);

        verify(channel).basicAck(55L, false);
    }

    @Test
    void debeRechazarMensajeAMQPCorrupto() throws IOException {
        Message message = mock(Message.class);
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(42L);
        when(message.getMessageProperties()).thenReturn(props);
        when(message.getBody()).thenReturn("invalido".getBytes());

        Channel channel = mock(Channel.class);

        consumer.recibir(message, channel);

        verify(channel).basicReject(42L, false);
    }
}

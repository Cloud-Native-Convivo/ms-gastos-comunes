package com.convivo.gastoscomunes.messaging.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.convivo.gastoscomunes.domain.GastoComun;
import com.convivo.gastoscomunes.domain.SolicitudJob;
import com.convivo.gastoscomunes.domain.SolicitudJobRepository;
import com.convivo.gastoscomunes.dto.GastoComunRequest;
import com.convivo.gastoscomunes.service.GastoComunService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
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

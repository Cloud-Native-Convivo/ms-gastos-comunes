package com.convivo.gastoscomunes.messaging.fanout;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class NotificacionGastosConsumerTest {

    private static final long TAG = 3L;
    private static final String JSON = """
            {"ticket_id":"t-1","email":"a@b.cl","unidad_id":"101-A"}""";

    @Mock
    private EmailEnviadoRepository repository;

    @Mock
    private Channel canal;

    private NotificacionGastosConsumer consumer;

    @BeforeEach
    void crear() {
        consumer = new NotificacionGastosConsumer(new ObjectMapper(), repository);
    }

    private static Message mensaje(String cuerpo) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setDeliveryTag(TAG);
        return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), propiedades);
    }

    @Test
    void reservaElTicketConSaveAndFlushAntesDeDespacharYHaceAck() throws Exception {
        when(repository.saveAndFlush(any(EmailEnviado.class))).thenReturn(new EmailEnviado("t-1"));

        consumer.recibir(mensaje(JSON), canal);

        verify(repository, times(1)).saveAndFlush(any(EmailEnviado.class));
        verify(canal).basicAck(TAG, false);
    }

    @Test
    void dosCopiasCasiJuntasNoDespachanDosVeces() throws Exception {
        // La primera reserva el ticket_id (INSERT real via saveAndFlush); la
        // segunda, casi junta, choca con la llave primaria ANTES de que
        // ninguna de las dos haya llegado a "enviar" — esa es la carrera que
        // el prototipo documentó (idempotencia.py): reservar con un INSERT
        // antes de tocar SMTP, no marcar después de enviar.
        when(repository.saveAndFlush(any(EmailEnviado.class)))
                .thenReturn(new EmailEnviado("t-1"))
                .thenThrow(new DataIntegrityViolationException("llave primaria duplicada"));

        consumer.recibir(mensaje(JSON), canal);
        consumer.recibir(mensaje(JSON), canal);

        verify(repository, times(2)).saveAndFlush(any(EmailEnviado.class));
        verify(canal, times(2)).basicAck(TAG, false);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void siFallaElEnvioSeLiberaLaReservaParaPermitirReintentoReal() throws Exception {
        when(repository.saveAndFlush(any(EmailEnviado.class))).thenReturn(new EmailEnviado("t-1"));
        NotificacionGastosConsumer consumerSpia = spy(consumer);
        doThrow(new RuntimeException("smtp caido")).when(consumerSpia).procesarNotificacion(any(JsonNode.class));

        consumerSpia.recibir(mensaje(JSON), canal);

        verify(repository).deleteById("t-1");
        verify(canal).basicReject(TAG, false);
        verify(canal, never()).basicAck(TAG, false);
    }

    @Test
    void ticketVacioSeDescartaSinReservar() throws Exception {
        consumer.recibir(mensaje("""
                {"email":"a@b.cl"}"""), canal);

        verify(repository, never()).saveAndFlush(any());
        verify(canal).basicAck(TAG, false);
    }

    @Test
    void cuerpoIlegibleSeRechazaSinRequeueHaciaLaDlq() throws Exception {
        consumer.recibir(mensaje("no es json"), canal);

        verify(canal).basicReject(TAG, false);
        verify(canal, never()).basicAck(TAG, false);
    }
}

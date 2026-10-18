package com.convivo.gastoscomunes.messaging.fanout;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void reservaElTicketAntesDeDespacharYHaceAck() throws Exception {
        when(repository.existsById("t-1")).thenReturn(false);

        consumer.recibir(mensaje(JSON), canal);

        verify(repository, times(1)).save(org.mockito.ArgumentMatchers.any(EmailEnviado.class));
        verify(canal).basicAck(TAG, false);
    }

    @Test
    void ticketDuplicadoNoDespachaDeNuevo() throws Exception {
        when(repository.existsById("t-1")).thenReturn(true);

        consumer.recibir(mensaje(JSON), canal);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(canal).basicAck(TAG, false);
    }
}

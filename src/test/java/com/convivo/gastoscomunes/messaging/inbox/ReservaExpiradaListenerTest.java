package com.convivo.gastoscomunes.messaging.inbox;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.convivo.gastoscomunes.messaging.inbox.dto.ReservaExpiradaEvent;
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
class ReservaExpiradaListenerTest {

    private static final long TAG = 7L;
    private static final String JSON_VALIDO = """
            {"event_id":"evt-exp-1","reserva_id":"res-1"}""";

    @Mock
    private ReservaExpiradaInboxService inboxService;

    @Mock
    private Channel canal;

    private ReservaExpiradaListener listener;

    @BeforeEach
    void crear() {
        listener = new ReservaExpiradaListener(inboxService, new ObjectMapper());
    }

    private static Message mensaje(String cuerpo) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setDeliveryTag(TAG);
        return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), propiedades);
    }

    @Test
    void procesadoHaceAck() throws Exception {
        when(inboxService.procesar(eq(new ReservaExpiradaEvent("evt-exp-1", "res-1"))))
                .thenReturn(ResultadoProcesamiento.PROCESADO);

        listener.recibir(mensaje(JSON_VALIDO), canal);

        verify(canal).basicAck(TAG, false);
    }

    @Test
    void duplicadoHaceAckSinReprocesar() throws Exception {
        when(inboxService.procesar(eq(new ReservaExpiradaEvent("evt-exp-1", "res-1"))))
                .thenReturn(ResultadoProcesamiento.DUPLICADO);

        listener.recibir(mensaje(JSON_VALIDO), canal);

        verify(canal).basicAck(TAG, false);
    }

    @Test
    void cuerpoIlegibleSeRechazaSinRequeueHaciaLaDlq() throws Exception {
        listener.recibir(mensaje("{no es json"), canal);

        verify(canal).basicReject(TAG, false);
        verify(canal, never()).basicAck(TAG, false);
    }
}

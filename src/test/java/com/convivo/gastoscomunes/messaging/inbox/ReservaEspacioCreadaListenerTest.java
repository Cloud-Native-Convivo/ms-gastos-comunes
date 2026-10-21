package com.convivo.gastoscomunes.messaging.inbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.convivo.gastoscomunes.config.MensajeriaProperties;
import com.convivo.gastoscomunes.messaging.inbox.dto.GastoFallidoPayload;
import com.convivo.gastoscomunes.messaging.outbox.OutboxPublisherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/** ACK manual del consumidor: duplicado / inválido (compensa) / procesado / ilegible (DLQ). */
@ExtendWith(MockitoExtension.class)
class ReservaEspacioCreadaListenerTest {

    private static final long TAG = 42L;
    private static final String JSON_VALIDO = """
            {"event_id":"evt-1","reserva_id":"res-1","unidad_id":"A-1","usuario_sub":"u-1",
             "monto":15000,"timestamp":"2026-10-07T10:00:00"}""";

    @Mock
    private ReservaCreadaInboxService inbox;

    @Mock
    private OutboxPublisherService outbox;

    @Mock
    private Channel canal;

    private final MensajeriaProperties props = new MensajeriaProperties(
            "espacios_events", "cola", "reserva_espacio_creada", "gasto_fallido", "dlx", "dlq", 50, 5, 3);

    private ReservaEspacioCreadaListener listener;

    @BeforeEach
    void crear() {
        listener = new ReservaEspacioCreadaListener(inbox, outbox, props, new ObjectMapper());
    }

    private static Message mensaje(String cuerpo) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setDeliveryTag(TAG);
        return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), propiedades);
    }

    @ParameterizedTest
    @EnumSource(ResultadoProcesamiento.class)
    void confirmaElMensajeEnCadaResultado(ResultadoProcesamiento resultado) throws Exception {
        when(inbox.procesar(any())).thenReturn(resultado);

        listener.recibir(mensaje(JSON_VALIDO), canal);

        verify(canal).basicAck(TAG, false);
        if (resultado == ResultadoProcesamiento.INVALIDO) {
            verify(outbox).encolar(eq("espacios_events"), eq("gasto_fallido"), eq("gasto_fallido"), any(GastoFallidoPayload.class));
        } else {
            verifyNoInteractions(outbox);
        }
    }

    @Test
    void cuerpoIlegibleSeRechazaSinRequeueHaciaLaDlq() throws Exception {
        listener.recibir(mensaje("{no es json"), canal);

        verify(canal).basicReject(TAG, false);
        verify(canal, never()).basicAck(TAG, false);
        verifyNoInteractions(inbox, outbox);
    }
}

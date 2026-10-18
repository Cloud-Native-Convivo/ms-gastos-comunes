package com.convivo.gastoscomunes.messaging.inbox;

import com.convivo.gastoscomunes.messaging.inbox.dto.ReservaExpiradaEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code reserva_expirada} desde {@code gastos_reserva_expirada_queue}
 * con ACK manual. La idempotencia (Inbox) y la anulación del cobro viven en
 * {@link ReservaExpiradaInboxService}, en una sola transacción — este
 * listener solo traduce el resultado a ACK/reject.
 */
@Component
public class ReservaExpiradaListener {

    private static final Logger log = LoggerFactory.getLogger(ReservaExpiradaListener.class);

    private final ReservaExpiradaInboxService inboxService;
    private final ObjectMapper objectMapper;

    public ReservaExpiradaListener(ReservaExpiradaInboxService inboxService, ObjectMapper objectMapper) {
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "#{@gastosReservaExpiradaQueue.name}")
    public void recibir(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        ReservaExpiradaEvent evento;
        try {
            JsonNode root = objectMapper.readTree(message.getBody());
            evento = ReservaExpiradaEvent.desde(root);
        } catch (Exception ex) {
            log.error("Fallo irrecuperable al deserializar reserva_expirada. Rechazando a DLQ: deliveryTag={}", deliveryTag, ex);
            channel.basicReject(deliveryTag, false);
            return;
        }

        // Fallos técnicos (ej. DB caída) propagan la excepción sin ACK: el
        // RetryInterceptor de RabbitMqConfig reintenta y, al agotar
        // intentos, rechaza sin requeue -> DLQ (mismo mecanismo que
        // ReservaEspacioCreadaListener).
        ResultadoProcesamiento resultado = inboxService.procesar(evento);

        switch (resultado) {
            case DUPLICADO -> {
                log.info("Evento reserva_expirada duplicado descartado: eventId={}", evento.eventId());
                channel.basicAck(deliveryTag, false);
            }
            case INVALIDO -> {
                log.warn("Evento reserva_expirada sin eventId/reservaId, se descarta: {}", evento);
                channel.basicAck(deliveryTag, false);
            }
            case PROCESADO -> {
                log.info("Cobro anulado por reserva expirada: reservaId={}", evento.reservaId());
                channel.basicAck(deliveryTag, false);
            }
        }
    }
}

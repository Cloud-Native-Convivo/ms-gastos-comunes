package com.convivo.gastoscomunes.messaging.inbox;

import com.convivo.gastoscomunes.config.MensajeriaProperties;
import com.convivo.gastoscomunes.messaging.inbox.dto.GastoFallidoPayload;
import com.convivo.gastoscomunes.messaging.inbox.dto.ReservaEspacioCreadaEvent;
import com.convivo.gastoscomunes.messaging.outbox.OutboxPublisherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Fase 3 del diagrama de mensajería asíncrona: consume
 * {@code reserva_espacio_creada} desde {@code gastos_reserva_creada_queue}.
 *
 * <ul>
 *   <li><b>Duplicado</b> (event_id ya en Inbox): ACK y descarta.</li>
 *   <li><b>Inválido</b> (datos inconsistentes): publica {@code gasto_fallido}
 *       vía Outbox (para que la compensación misma sea confiable) y hace
 *       ACK del mensaje original — evita reintentos infinitos de un evento
 *       que nunca podrá procesarse.</li>
 *   <li><b>Éxito</b>: ACK tras crear el gasto común.</li>
 *   <li><b>Fallo técnico</b> (ej. base de datos caída): la excepción se
 *       propaga sin ACK; el {@code RetryInterceptor} configurado en
 *       {@code RabbitMqConfig} reintenta con backoff y, al agotar los
 *       intentos, rechaza sin requeue -> la cola enruta a la Dead Letter
 *       Queue por su política {@code x-dead-letter-*}.</li>
 * </ul>
 */
@Component
public class ReservaEspacioCreadaListener {

    private static final Logger log = LoggerFactory.getLogger(ReservaEspacioCreadaListener.class);

    private final ReservaCreadaInboxService inboxService;
    private final OutboxPublisherService outboxPublisherService;
    private final MensajeriaProperties props;
    private final ObjectMapper objectMapper;

    public ReservaEspacioCreadaListener(
            ReservaCreadaInboxService inboxService,
            OutboxPublisherService outboxPublisherService,
            MensajeriaProperties props,
            ObjectMapper objectMapper) {
        this.inboxService = inboxService;
        this.outboxPublisherService = outboxPublisherService;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "#{@gastosReservaCreadaQueue.name}")
    public void recibir(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        ReservaEspacioCreadaEvent evento = deserializar(message);

        ResultadoProcesamiento resultado = inboxService.procesar(evento);

        switch (resultado) {
            case DUPLICADO -> {
                log.info("Evento duplicado descartado: eventId={}", evento.eventId());
                channel.basicAck(deliveryTag, false);
            }
            case INVALIDO -> {
                log.warn(
                        "Evento reserva_espacio_creada inválido (reservaId={}): se publica compensación",
                        evento.reservaId());
                outboxPublisherService.encolar(
                        props.exchangeEventos(),
                        props.routingKeyGastoFallido(),
                        "gasto_fallido",
                        GastoFallidoPayload.de(evento.reservaId(), "datos_inconsistentes"));
                channel.basicAck(deliveryTag, false);
            }
            case PROCESADO -> {
                log.info(
                        "Gasto común creado desde reserva: reservaId={}, unidadId={}",
                        evento.reservaId(),
                        evento.unidadId());
                channel.basicAck(deliveryTag, false);
            }
        }
    }

    private ReservaEspacioCreadaEvent deserializar(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), ReservaEspacioCreadaEvent.class);
        } catch (IOException ex) {
            // Fallo técnico de formato: se deja que el RetryInterceptor lo
            // maneje (reintentos -> DLQ), no se hace ACK aquí.
            throw new IllegalStateException("No se pudo deserializar el evento entrante", ex);
        }
    }
}

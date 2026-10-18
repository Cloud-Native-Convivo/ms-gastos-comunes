package com.convivo.gastoscomunes.messaging.inbox;

import com.convivo.gastoscomunes.messaging.inbox.dto.ReservaExpiradaEvent;
import com.convivo.gastoscomunes.service.GastoComunService;
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
 * y anula (borrado lógico) el cobro asociado, salvo que ya esté pagado.
 * Idempotente por {@code eventId}, reutilizando la misma tabla Inbox que
 * {@link ReservaCreadaInboxService} (los event_id son globalmente únicos
 * entre tipos de evento).
 */
@Component
public class ReservaExpiradaListener {

    private static final Logger log = LoggerFactory.getLogger(ReservaExpiradaListener.class);

    private final InboxEventoRepository inboxRepository;
    private final GastoComunService gastoComunService;
    private final ObjectMapper objectMapper;

    public ReservaExpiradaListener(
            InboxEventoRepository inboxRepository,
            GastoComunService gastoComunService,
            ObjectMapper objectMapper) {
        this.inboxRepository = inboxRepository;
        this.gastoComunService = gastoComunService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "#{@gastosReservaExpiradaQueue.name}")
    public void recibir(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        ReservaExpiradaEvent evento;
        try {
            evento = objectMapper.readValue(message.getBody(), ReservaExpiradaEvent.class);
        } catch (Exception ex) {
            log.error("Fallo irrecuperable al deserializar reserva_expirada. Rechazando a DLQ: deliveryTag={}", deliveryTag, ex);
            channel.basicReject(deliveryTag, false);
            return;
        }

        if (!evento.esValido() || inboxRepository.existsById(evento.eventId())) {
            if (evento.esValido()) {
                log.info("Evento reserva_expirada duplicado descartado: eventId={}", evento.eventId());
            } else {
                log.warn("Evento reserva_expirada sin eventId/reservaId, se descarta: {}", evento);
            }
            channel.basicAck(deliveryTag, false);
            return;
        }

        inboxRepository.save(new InboxEvento(evento.eventId(), "reserva_expirada"));
        gastoComunService.anularPorReservaExpirada(evento.reservaId());
        channel.basicAck(deliveryTag, false);
    }
}

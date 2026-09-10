package com.convivo.gastoscomunes.messaging.outbox;

import com.convivo.gastoscomunes.config.MensajeriaProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Relay Worker del patrón Outbox: cada {@code convivo.mensajeria.outbox-intervalo-ms}
 * (5s por defecto, igual que el diagrama de secuencia) publica en RabbitMQ
 * los eventos pendientes y marca el resultado, en lotes acotados para no
 * bloquear la tabla.
 */
@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxEventoRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final MensajeriaProperties props;

    public OutboxRelayScheduler(
            OutboxEventoRepository repository, RabbitTemplate rabbitTemplate, MensajeriaProperties props) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${convivo.mensajeria.outbox-intervalo-ms:5000}")
    public void publicarPendientes() {
        Pageable lote = PageRequest.of(0, props.outboxLoteMaximo());
        List<OutboxEvento> pendientes = repository.findByEstadoOrderByFechaCreacionAsc(EstadoOutbox.PENDIENTE, lote);

        for (OutboxEvento evento : pendientes) {
            publicarUno(evento);
        }
    }

    /**
     * Publica un evento y guarda su nuevo estado. No lleva {@code @Transactional}
     * propio a propósito: se invoca por auto-referencia desde
     * {@link #publicarPendientes()} (un proxy Spring no interceptaría esa
     * llamada), y cada {@code repository.save(...)} ya es atómico por sí
     * mismo (Spring Data JPA anota {@code @Transactional} en
     * {@code SimpleJpaRepository.save}), que es la única escritura que hace
     * este método.
     */
    void publicarUno(OutboxEvento evento) {
        try {
            MessageProperties propiedades = new MessageProperties();
            propiedades.setContentType("application/json");
            propiedades.setHeader("eventId", evento.getEventId());
            propiedades.setHeader("tipo", evento.getTipo());

            rabbitTemplate.convertAndSend(
                    evento.getExchange(),
                    evento.getRoutingKey(),
                    new Message(evento.getPayload().getBytes(StandardCharsets.UTF_8), propiedades));

            evento.marcarPublicado();
            log.debug("Outbox publicado: eventId={}, tipo={}", evento.getEventId(), evento.getTipo());
        } catch (Exception ex) {
            evento.registrarIntentoFallido(props.outboxMaxIntentos());
            log.warn(
                    "Fallo publicando evento outbox eventId={} (intento {}): {}",
                    evento.getEventId(),
                    evento.getIntentos(),
                    ex.getMessage());
        } finally {
            repository.save(evento);
        }
    }
}

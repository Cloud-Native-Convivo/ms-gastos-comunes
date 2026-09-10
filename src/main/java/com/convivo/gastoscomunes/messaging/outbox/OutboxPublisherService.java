package com.convivo.gastoscomunes.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Punto de entrada del patrón Outbox: los servicios de dominio llaman a
 * {@link #encolar} <b>dentro de su propia transacción</b> (por eso este
 * método es {@code Propagation.REQUIRED}, el default) para que el evento
 * quede garantizado en base de datos junto con el cambio de negocio que lo
 * origina, incluso si RabbitMQ está caído en ese instante.
 */
@Service
public class OutboxPublisherService {

    private final OutboxEventoRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxPublisherService(OutboxEventoRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void encolar(String exchange, String routingKey, String tipo, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            repository.save(OutboxEvento.crear(exchange, routingKey, tipo, json));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el evento outbox '" + tipo + "'", ex);
        }
    }
}

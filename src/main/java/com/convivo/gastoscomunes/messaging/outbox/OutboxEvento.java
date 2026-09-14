package com.convivo.gastoscomunes.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Fila de la tabla Outbox: se inserta en la <b>misma transacción local</b>
 * que el cambio de negocio que la origina (ej. la publicación de
 * {@code gasto_fallido} al detectar un error al procesar una reserva), y un
 * proceso en background ({@link OutboxRelayScheduler}) la publica en
 * RabbitMQ de forma confiable, evitando el problema de dual-write.
 */
@Entity
@Table(name = "outbox_eventos")
public class OutboxEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String exchange;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Column(nullable = false, length = 80)
    private String tipo;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoOutbox estado;

    @Column(nullable = false)
    private int intentos;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "fecha_publicacion")
    private Instant fechaPublicacion;

    protected OutboxEvento() {
        // JPA
    }

    public static OutboxEvento crear(String exchange, String routingKey, String tipo, String payloadJson) {
        OutboxEvento evento = new OutboxEvento();
        evento.eventId = UUID.randomUUID().toString();
        evento.exchange = exchange;
        evento.routingKey = routingKey;
        evento.tipo = tipo;
        evento.payload = payloadJson;
        evento.estado = EstadoOutbox.PENDIENTE;
        evento.intentos = 0;
        return evento;
    }

    public void marcarPublicado() {
        this.estado = EstadoOutbox.PUBLICADO;
        this.fechaPublicacion = Instant.now();
    }

    public void registrarIntentoFallido(int maxIntentos) {
        this.intentos++;
        if (this.intentos >= maxIntentos) {
            this.estado = EstadoOutbox.FALLIDO;
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getExchange() {
        return exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getTipo() {
        return tipo;
    }

    public String getPayload() {
        return payload;
    }

    public EstadoOutbox getEstado() {
        return estado;
    }

    public int getIntentos() {
        return intentos;
    }
}

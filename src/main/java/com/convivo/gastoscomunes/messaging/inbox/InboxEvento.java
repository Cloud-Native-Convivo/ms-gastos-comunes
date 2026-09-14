package com.convivo.gastoscomunes.messaging.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registro de idempotencia (patrón Inbox): antes de procesar un evento
 * entrante se verifica si su {@code eventId} ya existe aquí; si existe, el
 * mensaje se reconoce (ack) y se descarta sin reprocesar, protegiendo
 * contra reentregas at-least-once de RabbitMQ.
 */
@Entity
@Table(name = "inbox_eventos")
public class InboxEvento {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(nullable = false, length = 80)
    private String tipo;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "fecha_procesado", nullable = false, updatable = false)
    private Instant fechaProcesado;

    protected InboxEvento() {
        // JPA
    }

    public InboxEvento(String eventId, String tipo) {
        this.eventId = eventId;
        this.tipo = tipo;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTipo() {
        return tipo;
    }

    public Instant getFechaProcesado() {
        return fechaProcesado;
    }
}

package com.convivo.gastoscomunes.messaging.fanout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registro de idempotencia de {@code gastos_notificaciones_email_queue}: el
 * ticket_id se reserva ANTES de despachar el correo, igual que el patrón
 * Inbox de {@code messaging.inbox}.
 */
@Entity
@Table(name = "notificaciones_email_enviadas")
public class EmailEnviado {

    @Id
    @Column(name = "ticket_id", length = 64)
    private String ticketId;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "fecha_enviado", nullable = false, updatable = false)
    private Instant fechaEnviado;

    protected EmailEnviado() {
        // JPA
    }

    public EmailEnviado(String ticketId) {
        this.ticketId = ticketId;
    }

    public String getTicketId() {
        return ticketId;
    }
}

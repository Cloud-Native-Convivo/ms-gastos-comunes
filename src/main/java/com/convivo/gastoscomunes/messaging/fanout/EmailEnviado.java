package com.convivo.gastoscomunes.messaging.fanout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registro de idempotencia de {@code gastos_notificaciones_email_queue}: el
 * ticket_id se reserva ANTES de despachar el correo, igual que el patrón
 * Inbox de {@code messaging.inbox}. La fila nunca se borra: queda EN_CURSO
 * mientras se despacha y pasa a COMPLETADO al terminar, para que una
 * reentrega real de RabbitMQ (mensaje marcado {@code redelivered}, p. ej. si
 * el proceso murió antes de completar) pueda retomarla en vez de perder el
 * correo para siempre.
 */
@Entity
@Table(name = "notificaciones_email_enviadas")
public class EmailEnviado {

    @Id
    @Column(name = "ticket_id", length = 64)
    private String ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoNotificacion estado;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "fecha_enviado", nullable = false, updatable = false)
    private Instant fechaEnviado;

    protected EmailEnviado() {
        // JPA
    }

    public EmailEnviado(String ticketId) {
        this.ticketId = ticketId;
        this.estado = EstadoNotificacion.EN_CURSO;
    }

    public String getTicketId() {
        return ticketId;
    }

    public EstadoNotificacion getEstado() {
        return estado;
    }

    public void completar() {
        this.estado = EstadoNotificacion.COMPLETADO;
    }
}

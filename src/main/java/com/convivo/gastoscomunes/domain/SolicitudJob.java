package com.convivo.gastoscomunes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "solicitudes_jobs")
public class SolicitudJob {

    @Id
    @Column(name = "ticket_id", length = 64)
    private String ticketId;

    @Column(nullable = false, length = 30)
    private String modulo;

    @Column(nullable = false, length = 50)
    private String accion;

    @Column(name = "usuario_id", nullable = false, length = 100)
    private String usuarioId;

    @Column(nullable = false, length = 20)
    private String estado;

    @Lob
    @Column
    private String resultado;

    @Lob
    @Column
    private String error;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    public SolicitudJob() {
    }

    public SolicitudJob(
            String ticketId,
            String modulo,
            String accion,
            String usuarioId,
            String estado,
            String resultado,
            String error,
            Instant creadoEn,
            Instant actualizadoEn) {
        this.ticketId = ticketId;
        this.modulo = modulo;
        this.accion = accion;
        this.usuarioId = usuarioId;
        this.estado = estado;
        this.resultado = resultado;
        this.error = error;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public String getModulo() {
        return modulo;
    }

    public void setModulo(String modulo) {
        this.modulo = modulo;
    }

    public String getAccion() {
        return accion;
    }

    public void setAccion(String accion) {
        this.accion = accion;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getResultado() {
        return resultado;
    }

    public void setResultado(String resultado) {
        this.resultado = resultado;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(Instant actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }
}

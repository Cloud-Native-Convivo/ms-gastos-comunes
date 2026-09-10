package com.convivo.gastoscomunes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Un gasto común (cobro/cuota) asociado a una unidad del condominio.
 *
 * <p>Puede originarse manualmente (administración/comité) o
 * automáticamente al consumir el evento {@code reserva_espacio_creada}
 * publicado por ms-espacios-comunes (ver {@code messaging.inbox}).</p>
 */
@Entity
@Table(name = "gastos_comunes")
public class GastoComun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unidad_id", nullable = false, length = 64)
    private String unidadId;

    @Column(nullable = false, length = 200)
    private String concepto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "saldo_pendiente", nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoPendiente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoGasto estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenGasto origen;

    /** Ej. id de la reserva que originó el cobro, cuando origen = RESERVA_ESPACIO. */
    @Column(name = "referencia_externa", length = 100)
    private String referenciaExterna;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion")
    private Instant fechaActualizacion;

    @Version
    @Column(name = "version_optimista")
    private Long versionOptimista;

    protected GastoComun() {
        // JPA
    }

    public static GastoComun crear(
            String unidadId,
            String concepto,
            BigDecimal monto,
            OrigenGasto origen,
            String referenciaExterna,
            LocalDate fechaVencimiento) {
        GastoComun gasto = new GastoComun();
        gasto.unidadId = unidadId;
        gasto.concepto = concepto;
        gasto.monto = monto;
        gasto.saldoPendiente = monto;
        gasto.estado = EstadoGasto.PENDIENTE;
        gasto.origen = origen;
        gasto.referenciaExterna = referenciaExterna;
        gasto.fechaVencimiento = fechaVencimiento;
        return gasto;
    }

    /** Aplica un abono/pago al saldo pendiente y recalcula el estado. */
    public void aplicarPago(BigDecimal montoPago) {
        if (estado == EstadoGasto.ANULADO) {
            throw new IllegalStateException("No se puede pagar un gasto anulado");
        }
        this.saldoPendiente = this.saldoPendiente.subtract(montoPago);
        if (this.saldoPendiente.compareTo(BigDecimal.ZERO) <= 0) {
            this.saldoPendiente = BigDecimal.ZERO;
            this.estado = EstadoGasto.PAGADO;
        } else {
            this.estado = EstadoGasto.PARCIAL;
        }
    }

    public Long getId() {
        return id;
    }

    public String getUnidadId() {
        return unidadId;
    }

    public String getConcepto() {
        return concepto;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public BigDecimal getSaldoPendiente() {
        return saldoPendiente;
    }

    public EstadoGasto getEstado() {
        return estado;
    }

    public OrigenGasto getOrigen() {
        return origen;
    }

    public String getReferenciaExterna() {
        return referenciaExterna;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }
}

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @UpdateTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
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
        if (estado == EstadoGasto.ELIMINADO) {
            throw new IllegalStateException("No se puede pagar un gasto eliminado");
        }
        this.saldoPendiente = this.saldoPendiente.subtract(montoPago);
        if (this.saldoPendiente.compareTo(BigDecimal.ZERO) <= 0) {
            this.saldoPendiente = BigDecimal.ZERO;
            this.estado = EstadoGasto.PAGADO;
        } else {
            this.estado = EstadoGasto.PARCIAL;
        }
    }

    /**
     * Edita concepto/monto/vencimiento. El saldo pendiente se recalcula
     * conservando lo ya pagado ({@code monto actual - saldoPendiente}), para
     * no perder abonos ya registrados al cambiar el monto total.
     */
    public void actualizar(String concepto, BigDecimal monto, LocalDate fechaVencimiento) {
        if (estado == EstadoGasto.ELIMINADO) {
            throw new IllegalStateException("No se puede modificar un gasto eliminado");
        }
        BigDecimal pagado = this.monto.subtract(this.saldoPendiente);
        this.concepto = concepto;
        this.monto = monto;
        this.fechaVencimiento = fechaVencimiento;

        BigDecimal nuevoSaldo = monto.subtract(pagado);
        this.saldoPendiente = nuevoSaldo.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : nuevoSaldo;
        if (this.saldoPendiente.compareTo(BigDecimal.ZERO) <= 0) {
            this.estado = EstadoGasto.PAGADO;
        } else if (pagado.compareTo(BigDecimal.ZERO) > 0) {
            this.estado = EstadoGasto.PARCIAL;
        } else {
            this.estado = EstadoGasto.PENDIENTE;
        }
    }

    /** Borrado lógico: deja de listarse/consultarse (ver GastoComunService.buscarOLanzar). */
    public void eliminar() {
        if (estado == EstadoGasto.PAGADO) {
            throw new IllegalStateException("No se puede eliminar un gasto ya pagado");
        }
        if (estado == EstadoGasto.ELIMINADO) {
            throw new IllegalStateException("El gasto ya está eliminado");
        }
        this.estado = EstadoGasto.ELIMINADO;
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

    /**
     * Estado efectivo para mostrar: un gasto PENDIENTE/PARCIAL cuya fecha
     * de vencimiento ya pasó se muestra como VENCIDO. Se deriva en cada
     * lectura (no se persiste) para no depender de un job programado que
     * mantenga el campo {@code estado} al día.
     */
    public EstadoGasto getEstadoEfectivo() {
        boolean puedeVencer = estado == EstadoGasto.PENDIENTE || estado == EstadoGasto.PARCIAL;
        if (puedeVencer && fechaVencimiento != null && fechaVencimiento.isBefore(LocalDate.now())) {
            return EstadoGasto.VENCIDO;
        }
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

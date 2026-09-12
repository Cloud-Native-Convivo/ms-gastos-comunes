package com.convivo.gastoscomunes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/** Un abono/pago (total o parcial) sobre un {@link GastoComun}. */
@Entity
@Table(name = "pagos")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gasto_comun_id", nullable = false)
    private GastoComun gastoComun;

    /**
     * Copia de solo-lectura de la FK, mapeada directamente (sin join) para
     * poder construir DTOs fuera de la transacción sin tocar el proxy lazy
     * de {@link #gastoComun} (evita LazyInitializationException con
     * spring.jpa.open-in-view=false).
     */
    @Column(name = "gasto_comun_id", insertable = false, updatable = false)
    private Long gastoComunId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MetodoPago metodo;

    /** Claim {@code oid} de quien registró el pago (residente/propietario/gestor). */
    @Column(name = "usuario_sub", nullable = false, length = 100)
    private String usuarioSub;

    @Column(length = 200)
    private String comprobante;

    @CreationTimestamp
    @Column(name = "fecha_pago", nullable = false, updatable = false)
    private Instant fechaPago;

    protected Pago() {
        // JPA
    }

    public static Pago registrar(
            GastoComun gastoComun, BigDecimal monto, MetodoPago metodo, String usuarioSub, String comprobante) {
        Pago pago = new Pago();
        pago.gastoComun = gastoComun;
        // gastoComun ya viene persistido (con id) desde el servicio; se
        // copia también en la columna de solo-lectura para que
        // getGastoComunId() funcione sin depender del proxy lazy.
        pago.gastoComunId = gastoComun.getId();
        pago.monto = monto;
        pago.metodo = metodo;
        pago.usuarioSub = usuarioSub;
        pago.comprobante = comprobante;
        return pago;
    }

    public Long getId() {
        return id;
    }

    public Long getGastoComunId() {
        return gastoComunId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public MetodoPago getMetodo() {
        return metodo;
    }

    public String getUsuarioSub() {
        return usuarioSub;
    }

    public String getComprobante() {
        return comprobante;
    }

    public Instant getFechaPago() {
        return fechaPago;
    }
}

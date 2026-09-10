package com.convivo.gastoscomunes.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Roles de negocio de Convivo relevantes para Gastos Comunes.
 *
 * <p>Coincide con la tabla "Roles Convivo -> claims del token" de las
 * directrices del proyecto. {@code administrador} y {@code comite}
 * gestionan cobros/cuotas de todo el condominio; {@code propietario} y
 * {@code residente} solo consultan/pagan los gastos de su(s) unidad(es);
 * {@code conserje} no tiene acceso a este dominio.</p>
 */
public enum RolConvivo {
    ADMINISTRADOR,
    COMITE,
    PROPIETARIO,
    RESIDENTE,
    CONSERJE;

    public static Optional<RolConvivo> desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(RolConvivo.valueOf(valor.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** Roles con visión y administración total de gastos comunes. */
    public boolean esGestorCondominio() {
        return this == ADMINISTRADOR || this == COMITE;
    }
}

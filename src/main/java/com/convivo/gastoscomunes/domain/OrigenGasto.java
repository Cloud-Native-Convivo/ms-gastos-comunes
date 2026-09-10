package com.convivo.gastoscomunes.domain;

/** Origen que dio lugar al gasto común. */
public enum OrigenGasto {
    /** Creado manualmente por administración/comité (cuota mensual, multa, etc.). */
    MANUAL,
    /** Creado automáticamente al consumir el evento {@code reserva_espacio_creada}. */
    RESERVA_ESPACIO,
    OTRO,
}

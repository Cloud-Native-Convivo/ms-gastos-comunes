package com.convivo.gastoscomunes.domain;

/** Estado de un gasto común (cobro/cuota) respecto de su saldo pendiente. */
public enum EstadoGasto {
    PENDIENTE,
    PARCIAL,
    PAGADO,
    VENCIDO,
    ELIMINADO,
}

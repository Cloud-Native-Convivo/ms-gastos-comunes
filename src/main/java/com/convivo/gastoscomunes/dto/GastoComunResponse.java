package com.convivo.gastoscomunes.dto;

import com.convivo.gastoscomunes.domain.EstadoGasto;
import com.convivo.gastoscomunes.domain.GastoComun;
import com.convivo.gastoscomunes.domain.OrigenGasto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record GastoComunResponse(
        Long id,
        String unidadId,
        String concepto,
        BigDecimal monto,
        BigDecimal saldoPendiente,
        EstadoGasto estado,
        OrigenGasto origen,
        String referenciaExterna,
        LocalDate fechaVencimiento,
        Instant fechaCreacion) {

    public static GastoComunResponse desde(GastoComun gasto) {
        return new GastoComunResponse(
                gasto.getId(),
                gasto.getUnidadId(),
                gasto.getConcepto(),
                gasto.getMonto(),
                gasto.getSaldoPendiente(),
                gasto.getEstadoEfectivo(),
                gasto.getOrigen(),
                gasto.getReferenciaExterna(),
                gasto.getFechaVencimiento(),
                gasto.getFechaCreacion());
    }
}

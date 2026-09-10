package com.convivo.gastoscomunes.dto;

import com.convivo.gastoscomunes.domain.MetodoPago;
import com.convivo.gastoscomunes.domain.Pago;
import java.math.BigDecimal;
import java.time.Instant;

public record PagoResponse(
        Long id,
        Long gastoComunId,
        BigDecimal monto,
        MetodoPago metodo,
        String usuarioSub,
        String comprobante,
        Instant fechaPago) {

    public static PagoResponse desde(Pago pago) {
        return new PagoResponse(
                pago.getId(),
                pago.getGastoComunId(),
                pago.getMonto(),
                pago.getMetodo(),
                pago.getUsuarioSub(),
                pago.getComprobante(),
                pago.getFechaPago());
    }
}

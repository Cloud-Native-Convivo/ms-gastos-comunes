package com.convivo.gastoscomunes.dto;

import com.convivo.gastoscomunes.domain.MetodoPago;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Límites espejo de pagos.monto NUMBER(12,2) y pagos.comprobante VARCHAR2(200) — ver AGENTS.md §5.1. */
public record PagoRequest(
        @NotNull(message = "monto es obligatorio") @DecimalMin(value = "0.01", message = "monto debe ser mayor a 0")
                @Digits(integer = 10, fraction = 2, message = "monto admite hasta 10 enteros y 2 decimales")
                BigDecimal monto,
        @NotNull(message = "metodo es obligatorio") MetodoPago metodo,
        @Size(max = 200, message = "comprobante admite hasta 200 caracteres") String comprobante) {}

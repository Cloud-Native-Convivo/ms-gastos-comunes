package com.convivo.gastoscomunes.dto;

import com.convivo.gastoscomunes.domain.MetodoPago;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PagoRequest(
        @NotNull(message = "monto es obligatorio") @DecimalMin(value = "0.01", message = "monto debe ser mayor a 0")
                BigDecimal monto,
        @NotNull(message = "metodo es obligatorio") MetodoPago metodo,
        String comprobante) {}

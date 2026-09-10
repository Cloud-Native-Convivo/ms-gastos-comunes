package com.convivo.gastoscomunes.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Alta manual de un gasto común (cuota, multa, etc.) por administración/comité. */
public record GastoComunRequest(
        @NotBlank(message = "unidadId es obligatorio") String unidadId,
        @NotBlank(message = "concepto es obligatorio") String concepto,
        @NotNull(message = "monto es obligatorio") @DecimalMin(value = "0.01", message = "monto debe ser mayor a 0")
                BigDecimal monto,
        LocalDate fechaVencimiento) {}

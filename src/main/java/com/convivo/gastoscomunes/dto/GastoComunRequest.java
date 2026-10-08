package com.convivo.gastoscomunes.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Alta manual de un gasto común (cuota, multa, etc.) por administración/comité.
 * Límites espejo de las columnas Oracle (V1__init.sql): unidad_id VARCHAR2(64),
 * concepto VARCHAR2(200), monto NUMBER(12,2) — ver AGENTS.md §5.1.
 */
public record GastoComunRequest(
        @NotBlank(message = "unidadId es obligatorio") @Size(max = 64, message = "unidadId admite hasta 64 caracteres")
                String unidadId,
        @NotBlank(message = "concepto es obligatorio") @Size(max = 200, message = "concepto admite hasta 200 caracteres")
                String concepto,
        @NotNull(message = "monto es obligatorio") @DecimalMin(value = "0.01", message = "monto debe ser mayor a 0")
                @Digits(integer = 10, fraction = 2, message = "monto admite hasta 10 enteros y 2 decimales")
                BigDecimal monto,
        LocalDate fechaVencimiento) {}

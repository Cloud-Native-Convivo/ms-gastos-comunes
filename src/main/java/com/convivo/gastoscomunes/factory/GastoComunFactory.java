package com.convivo.gastoscomunes.factory;

import com.convivo.gastoscomunes.domain.GastoComun;
import com.convivo.gastoscomunes.domain.OrigenGasto;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Patrón creacional Factory Method (GoF) — Requisito explícito de la rúbrica DSY1106.
 *
 * <p>Define la interfaz para la creación de instancias de {@link GastoComun}
 * desacoplando la lógica de negocio de la instanciación concreta según su origen
 * ({@link OrigenGasto#MANUAL} vs. {@link OrigenGasto#RESERVA_ESPACIO}).</p>
 */
public interface GastoComunFactory {

    OrigenGasto getOrigen();

    GastoComun crearGasto(
            String unidadId,
            String concepto,
            BigDecimal monto,
            String referenciaExterna,
            LocalDate fechaVencimiento);
}

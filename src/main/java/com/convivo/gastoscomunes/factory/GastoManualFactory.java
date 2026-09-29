package com.convivo.gastoscomunes.factory;

import com.convivo.gastoscomunes.domain.GastoComun;
import com.convivo.gastoscomunes.domain.OrigenGasto;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Fábrica concreta para gastos comunes emitidos manualmente (administración/comité).
 */
@Component
public class GastoManualFactory implements GastoComunFactory {

    @Override
    public OrigenGasto getOrigen() {
        return OrigenGasto.MANUAL;
    }

    @Override
    public GastoComun crearGasto(
            String unidadId,
            String concepto,
            BigDecimal monto,
            String referenciaExterna,
            LocalDate fechaVencimiento) {
        return GastoComun.crear(unidadId, concepto, monto, OrigenGasto.MANUAL, null, fechaVencimiento);
    }
}

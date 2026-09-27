package com.convivo.gastoscomunes.factory;

import com.convivo.gastoscomunes.domain.GastoComun;
import com.convivo.gastoscomunes.domain.OrigenGasto;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Fábrica concreta para gastos originados por reservas de espacios comunes (consumo de eventos RabbitMQ).
 */
@Component
public class GastoReservaFactory implements GastoComunFactory {

    @Override
    public OrigenGasto getOrigen() {
        return OrigenGasto.RESERVA_ESPACIO;
    }

    @Override
    public GastoComun crearGasto(
            String unidadId,
            String concepto,
            BigDecimal monto,
            String referenciaExterna,
            LocalDate fechaVencimiento) {
        return GastoComun.crear(
                unidadId, concepto, monto, OrigenGasto.RESERVA_ESPACIO, referenciaExterna, fechaVencimiento);
    }
}

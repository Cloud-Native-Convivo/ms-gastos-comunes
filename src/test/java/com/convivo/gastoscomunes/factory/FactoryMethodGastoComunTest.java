package com.convivo.gastoscomunes.factory;

import com.convivo.gastoscomunes.domain.GastoComun;
import com.convivo.gastoscomunes.domain.OrigenGasto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Patrón Factory Method (GoF) - Creación polimórfica de GastoComun")
class FactoryMethodGastoComunTest {

    private final GastoManualFactory manualFactory = new GastoManualFactory();
    private final GastoReservaFactory reservaFactory = new GastoReservaFactory();

    @Test
    @DisplayName("GastoManualFactory crea instancias con origen MANUAL y sin referencia externa")
    void manualFactoryCreaInstanciaCorrecta() {
        assertThat(manualFactory.getOrigen()).isEqualTo(OrigenGasto.MANUAL);

        LocalDate vencimiento = LocalDate.now().plusDays(15);
        GastoComun gasto = manualFactory.crearGasto(
                "101",
                "Mantención ordinaria",
                new BigDecimal("50000"),
                null,
                vencimiento
        );

        assertThat(gasto.getOrigen()).isEqualTo(OrigenGasto.MANUAL);
        assertThat(gasto.getUnidadId()).isEqualTo("101");
        assertThat(gasto.getConcepto()).isEqualTo("Mantención ordinaria");
        assertThat(gasto.getMonto()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(gasto.getReferenciaExterna()).isNull();
        assertThat(gasto.getFechaVencimiento()).isEqualTo(vencimiento);
    }

    @Test
    @DisplayName("GastoReservaFactory crea instancias con origen RESERVA_ESPACIO y referencia a la reserva")
    void reservaFactoryCreaInstanciaCorrecta() {
        assertThat(reservaFactory.getOrigen()).isEqualTo(OrigenGasto.RESERVA_ESPACIO);

        GastoComun gasto = reservaFactory.crearGasto(
                "202",
                "Uso Quincho",
                new BigDecimal("15000"),
                "res-888",
                null
        );

        assertThat(gasto.getOrigen()).isEqualTo(OrigenGasto.RESERVA_ESPACIO);
        assertThat(gasto.getUnidadId()).isEqualTo("202");
        assertThat(gasto.getConcepto()).isEqualTo("Uso Quincho");
        assertThat(gasto.getMonto()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(gasto.getReferenciaExterna()).isEqualTo("res-888");
    }
}

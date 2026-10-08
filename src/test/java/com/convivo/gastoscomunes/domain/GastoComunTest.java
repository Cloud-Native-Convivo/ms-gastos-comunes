package com.convivo.gastoscomunes.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.convivo.gastoscomunes.exception.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Cubre getEstadoEfectivo(): PENDIENTE/PARCIAL vencidos se muestran como VENCIDO sin persistirlo. */
class GastoComunTest {

    @Test
    void marcaVencidoCuandoFechaVencimientoYaPaso() {
        GastoComun gasto = GastoComun.crear(
                "unidad-A302", "Cuota", new BigDecimal("10000"), OrigenGasto.MANUAL, null,
                LocalDate.now().minusDays(1));

        assertEquals(EstadoGasto.VENCIDO, gasto.getEstadoEfectivo());
        assertEquals(EstadoGasto.PENDIENTE, gasto.getEstado());
    }

    @Test
    void noMarcaVencidoSiTodaviaNoVenceOSinFecha() {
        GastoComun sinFecha = GastoComun.crear(
                "unidad-A302", "Cuota", new BigDecimal("10000"), OrigenGasto.MANUAL, null, null);
        assertEquals(EstadoGasto.PENDIENTE, sinFecha.getEstadoEfectivo());

        GastoComun futuro = GastoComun.crear(
                "unidad-A302", "Cuota", new BigDecimal("10000"), OrigenGasto.MANUAL, null,
                LocalDate.now().plusDays(5));
        assertEquals(EstadoGasto.PENDIENTE, futuro.getEstadoEfectivo());
    }

    @Test
    void gastoPagadoNoSeMuestraComoVencidoAunqueLaFechaHayaPasado() {
        GastoComun gasto = GastoComun.crear(
                "unidad-A302", "Cuota", new BigDecimal("10000"), OrigenGasto.MANUAL, null,
                LocalDate.now().minusDays(1));
        gasto.aplicarPago(new BigDecimal("10000"));

        assertEquals(EstadoGasto.PAGADO, gasto.getEstadoEfectivo());
    }

    @Test
    void actualizarConservaLoYaPagadoAlRecalcularSaldo() {
        GastoComun gasto = GastoComun.crear(
                "unidad-A302", "Cuota", new BigDecimal("10000"), OrigenGasto.MANUAL, null, null);
        gasto.aplicarPago(new BigDecimal("4000")); // saldoPendiente = 6000, PARCIAL

        gasto.actualizar("Cuota corregida", new BigDecimal("12000"), LocalDate.now().plusDays(10));

        assertEquals(new BigDecimal("8000"), gasto.getSaldoPendiente()); // 12000 - 4000 ya pagados
        assertEquals(EstadoGasto.PARCIAL, gasto.getEstado());
        assertEquals("Cuota corregida", gasto.getConcepto());
    }

    @Test
    void actualizarSobreGastoEliminadoLanzaExcepcion() {
        GastoComun gasto = GastoComun.crear(
                "unidad-A302", "Cuota", new BigDecimal("10000"), OrigenGasto.MANUAL, null, null);
        gasto.eliminar();

        assertThrows(ReglaNegocioException.class,
                () -> gasto.actualizar("Otro", new BigDecimal("5000"), null));
    }

    @Test
    void eliminarDejaElGastoComoEliminado() {
        GastoComun gasto = GastoComun.crear(
                "unidad-A302", "Cuota", new BigDecimal("10000"), OrigenGasto.MANUAL, null, null);

        gasto.eliminar();

        assertEquals(EstadoGasto.ELIMINADO, gasto.getEstado());
    }

    @Test
    void noSePuedeEliminarUnGastoYaPagado() {
        GastoComun gasto = GastoComun.crear(
                "unidad-A302", "Cuota", new BigDecimal("10000"), OrigenGasto.MANUAL, null, null);
        gasto.aplicarPago(new BigDecimal("10000"));

        assertThrows(ReglaNegocioException.class, gasto::eliminar);
    }

    // Valores límite sobre saldoPendiente = 10000: igual (ok), +0.01 (rechaza), gasto ya PAGADO (rechaza).
    @Test
    void pagoExactoAlSaldoDejaGastoPagado() {
        GastoComun gasto = GastoComun.crear(
                "unidad-A302", "Cuota", new BigDecimal("10000"), OrigenGasto.MANUAL, null, null);

        gasto.aplicarPago(new BigDecimal("10000.00"));

        assertEquals(EstadoGasto.PAGADO, gasto.getEstado());
        assertEquals(0, gasto.getSaldoPendiente().signum());
    }

    @Test
    void pagoQueExcedeElSaldoSeRechazaSinTocarElSaldo() {
        GastoComun gasto = GastoComun.crear(
                "unidad-A302", "Cuota", new BigDecimal("10000"), OrigenGasto.MANUAL, null, null);

        assertThrows(ReglaNegocioException.class, () -> gasto.aplicarPago(new BigDecimal("10000.01")));
        assertEquals(new BigDecimal("10000"), gasto.getSaldoPendiente());
        assertEquals(EstadoGasto.PENDIENTE, gasto.getEstado());
    }

    @Test
    void pagoSobreGastoYaPagadoSeRechaza() {
        GastoComun gasto = GastoComun.crear(
                "unidad-A302", "Cuota", new BigDecimal("10000"), OrigenGasto.MANUAL, null, null);
        gasto.aplicarPago(new BigDecimal("10000"));

        assertThrows(ReglaNegocioException.class, () -> gasto.aplicarPago(new BigDecimal("1")));
    }
}

package com.convivo.gastoscomunes.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        assertThrows(IllegalStateException.class,
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

        assertThrows(IllegalStateException.class, gasto::eliminar);
    }
}

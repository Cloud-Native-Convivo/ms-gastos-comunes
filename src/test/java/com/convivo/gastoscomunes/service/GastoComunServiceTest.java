package com.convivo.gastoscomunes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.convivo.gastoscomunes.domain.EstadoGasto;
import com.convivo.gastoscomunes.domain.GastoComun;
import com.convivo.gastoscomunes.domain.MetodoPago;
import com.convivo.gastoscomunes.domain.OrigenGasto;
import com.convivo.gastoscomunes.domain.Pago;
import com.convivo.gastoscomunes.dto.GastoComunRequest;
import com.convivo.gastoscomunes.dto.PagoRequest;
import com.convivo.gastoscomunes.exception.OperacionNoPermitidaException;
import com.convivo.gastoscomunes.exception.RecursoNoEncontradoException;
import com.convivo.gastoscomunes.factory.GastoManualFactory;
import com.convivo.gastoscomunes.factory.GastoReservaFactory;
import com.convivo.gastoscomunes.repository.GastoComunRepository;
import com.convivo.gastoscomunes.repository.PagoRepository;
import com.convivo.gastoscomunes.messaging.outbox.OutboxEventoRepository;
import com.convivo.gastoscomunes.security.UsuarioContexto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/** Reglas de negocio y ownership (anti-BOLA/IDOR) de {@link GastoComunService}, sin base de datos. */
@ExtendWith(MockitoExtension.class)
class GastoComunServiceTest {

    @Mock
    private GastoComunRepository gastoRepo;

    @Mock
    private PagoRepository pagoRepo;

    @Mock
    private OutboxEventoRepository outboxEventoRepository;

    private GastoComunService servicio;
    private final Pageable pagina = Pageable.ofSize(10);

    @BeforeEach
    void crearServicio() {
        servicio = new GastoComunService(gastoRepo, pagoRepo, new GastoManualFactory(), new GastoReservaFactory(), outboxEventoRepository);
    }

    private static UsuarioContexto usuario(String unidadId, String... roles) {
        UsuarioContexto u = new UsuarioContexto();
        u.setUsuarioSub("sub-" + unidadId);
        u.setUnidadId(unidadId);
        u.setRoles(Set.of(roles));
        return u;
    }

    private static GastoComun gasto(String unidadId) {
        return GastoComun.crear(unidadId, "Cuota", new BigDecimal("10000"), OrigenGasto.MANUAL, null, null);
    }

    private void existe(long id, GastoComun gasto) {
        when(gastoRepo.findById(id)).thenReturn(Optional.of(gasto));
    }

    @Test
    void crearActualizarYEliminarPersistenElGasto() {
        when(gastoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        GastoComunRequest req = new GastoComunRequest("A-1", "Cuota octubre", new BigDecimal("5000"), LocalDate.now());

        GastoComun creado = servicio.crear(req);
        assertThat(creado.getUnidadId()).isEqualTo("A-1");
        assertThat(creado.getOrigen()).isEqualTo(OrigenGasto.MANUAL);

        existe(1L, creado);
        GastoComun actualizado = servicio.actualizar(1L, new GastoComunRequest("OTRA", "Nuevo", new BigDecimal("7000"), null));
        assertThat(actualizado.getConcepto()).isEqualTo("Nuevo");
        assertThat(actualizado.getUnidadId()).isEqualTo("A-1");

        servicio.eliminar(1L);
        assertThat(creado.getEstado()).isEqualTo(EstadoGasto.ELIMINADO);
    }

    @Test
    void gastoInexistenteOEliminadoEsNoEncontrado() {
        when(gastoRepo.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.eliminar(9L)).isInstanceOf(RecursoNoEncontradoException.class);

        GastoComun eliminado = gasto("A-1");
        eliminado.eliminar();
        existe(8L, eliminado);
        assertThatThrownBy(() -> servicio.obtener(8L, usuario("A-1", "administrador")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void listarSegunRolYUnidad() {
        Page<GastoComun> todos = new PageImpl<>(List.of(gasto("A-1"), gasto("B-2")));
        Page<GastoComun> propios = new PageImpl<>(List.of(gasto("A-1")));
        when(gastoRepo.findAll(pagina)).thenReturn(todos);
        when(gastoRepo.findByUnidadId("A-1", pagina)).thenReturn(propios);

        assertThat(servicio.listar(usuario(null, "comite"), pagina)).isSameAs(todos);
        assertThat(servicio.listar(usuario("A-1", "residente"), pagina)).isSameAs(propios);
        assertThatThrownBy(() -> servicio.listar(usuario(null, "residente"), pagina))
                .isInstanceOf(OperacionNoPermitidaException.class)
                .hasMessageContaining("unidad_id");
        assertThatThrownBy(() -> servicio.listarPorUnidad("B-2", usuario("A-1", "propietario"), pagina))
                .isInstanceOf(OperacionNoPermitidaException.class);
    }

    @Test
    void obtenerValidaOwnership() {
        GastoComun g = gasto("A-1");
        existe(1L, g);
        assertThat(servicio.obtener(1L, usuario("A-1", "residente"))).isSameAs(g);
        assertThatThrownBy(() -> servicio.obtener(1L, usuario("B-2", "residente")))
                .isInstanceOf(OperacionNoPermitidaException.class);
    }

    @Test
    void registrarPagoDescuentaSaldoYGuardaAutor() {
        GastoComun g = gasto("A-1");
        existe(1L, g);
        when(pagoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Pago pago = servicio.registrarPago(
                1L, new PagoRequest(new BigDecimal("4000"), MetodoPago.TRANSFERENCIA, "comp-1"), usuario("A-1", "residente"));

        assertThat(g.getSaldoPendiente()).isEqualByComparingTo("6000");
        assertThat(g.getEstado()).isEqualTo(EstadoGasto.PARCIAL);
        assertThat(pago.getUsuarioSub()).isEqualTo("sub-A-1");
        assertThat(pago.getComprobante()).isEqualTo("comp-1");
        verify(gastoRepo).save(g);
    }

    @Test
    void registrarPagoEnUnidadAjenaSeRechazaSinTocarSaldo() {
        GastoComun g = gasto("A-1");
        existe(1L, g);
        PagoRequest req = new PagoRequest(BigDecimal.ONE, MetodoPago.EFECTIVO, null);
        assertThatThrownBy(() -> servicio.registrarPago(1L, req, usuario("B-2", "residente")))
                .isInstanceOf(OperacionNoPermitidaException.class);
        assertThat(g.getSaldoPendiente()).isEqualByComparingTo("10000");
        verify(pagoRepo, never()).save(any());
    }

    @Test
    void listarPagosValidaOwnership() {
        existe(1L, gasto("A-1"));
        Page<Pago> pagos = new PageImpl<>(List.of());
        when(pagoRepo.findByGastoComunId(1L, pagina)).thenReturn(pagos);
        assertThat(servicio.listarPagos(1L, usuario(null, "administrador"), pagina)).isSameAs(pagos);
        assertThatThrownBy(() -> servicio.listarPagos(1L, usuario("B-2", "residente"), pagina))
                .isInstanceOf(OperacionNoPermitidaException.class);
    }

    @Test
    void crearDesdeReservaEsIdempotentePorReferencia() {
        GastoComun existente = gasto("A-1");
        when(gastoRepo.findByOrigenAndReferenciaExterna(OrigenGasto.RESERVA_ESPACIO, "res-1"))
                .thenReturn(Optional.of(existente));
        assertThat(servicio.crearDesdeReserva("A-1", "Quincho", BigDecimal.TEN, "res-1")).isSameAs(existente);
        verify(gastoRepo, never()).save(any());

        when(gastoRepo.findByOrigenAndReferenciaExterna(OrigenGasto.RESERVA_ESPACIO, "res-2"))
                .thenReturn(Optional.empty());
        when(gastoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        GastoComun nuevo = servicio.crearDesdeReserva("A-1", "Quincho", BigDecimal.TEN, "res-2");
        assertThat(nuevo.getOrigen()).isEqualTo(OrigenGasto.RESERVA_ESPACIO);
        assertThat(nuevo.getReferenciaExterna()).isEqualTo("res-2");
    }
}




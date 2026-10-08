package com.convivo.gastoscomunes.service;

import com.convivo.gastoscomunes.domain.EstadoGasto;
import com.convivo.gastoscomunes.domain.GastoComun;
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
import com.convivo.gastoscomunes.security.UsuarioContexto;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio y de ownership (Nivel 3 — anti-BOLA/IDOR) de Gastos
 * Comunes: administrador/comité gestionan todo el condominio;
 * propietario/residente solo ven los gastos y pagos de su propia unidad.
 * Registrar pagos lo restringe a administrador/comité el controller.
 */
@Service
@Transactional
public class GastoComunService {

    private final GastoComunRepository gastoComunRepository;
    private final PagoRepository pagoRepository;
    private final GastoManualFactory gastoManualFactory;
    private final GastoReservaFactory gastoReservaFactory;

    public GastoComunService(
            GastoComunRepository gastoComunRepository,
            PagoRepository pagoRepository,
            GastoManualFactory gastoManualFactory,
            GastoReservaFactory gastoReservaFactory) {
        this.gastoComunRepository = gastoComunRepository;
        this.pagoRepository = pagoRepository;
        this.gastoManualFactory = gastoManualFactory;
        this.gastoReservaFactory = gastoReservaFactory;
    }

    /**
     * Emite un cobro manual (origen {@code MANUAL}) para la unidad indicada.
     *
     * @param request unidad, concepto, monto y vencimiento del cobro
     * @return el gasto creado, en estado {@code PENDIENTE} y con saldo igual al monto
     */
    public GastoComun crear(GastoComunRequest request) {
        GastoComun gasto = gastoManualFactory.crearGasto(
                request.unidadId(), request.concepto(), request.monto(), null,
                request.fechaVencimiento());
        return gastoComunRepository.save(gasto);
    }

    /** No permite reasignar la unidad (request.unidadId() se ignora): solo concepto/monto/vencimiento. */
    public GastoComun actualizar(Long id, GastoComunRequest request) {
        GastoComun gasto = buscarOLanzar(id);
        gasto.actualizar(request.concepto(), request.monto(), request.fechaVencimiento());
        return gastoComunRepository.save(gasto);
    }

    /** Borrado lógico: un gasto eliminado ya no aparece en listados/consultas. */
    public void eliminar(Long id) {
        GastoComun gasto = buscarOLanzar(id);
        gasto.eliminar();
        gastoComunRepository.save(gasto);
    }

    /**
     * Lista los gastos visibles para el usuario: todos si administra el
     * condominio, solo los de su unidad en otro caso.
     *
     * @param usuario identidad y roles del request actual
     * @param pageable paginación y orden
     * @return página de gastos no eliminados
     * @throws OperacionNoPermitidaException si no es gestor y su token no trae {@code unidad_id}
     */
    @Transactional(readOnly = true)
    public Page<GastoComun> listar(UsuarioContexto usuario, Pageable pageable) {
        if (usuario.esGestorCondominio()) {
            return gastoComunRepository.findAll(pageable);
        }
        return listarPorUnidad(requerirUnidadPropia(usuario), usuario, pageable);
    }

    /**
     * Lista los gastos de una unidad, validando ownership.
     *
     * @param unidadId unidad a consultar
     * @param usuario identidad y roles del request actual
     * @param pageable paginación y orden
     * @return página de gastos no eliminados de la unidad
     * @throws OperacionNoPermitidaException si el usuario no administra el condominio ni es dueño de la unidad
     */
    @Transactional(readOnly = true)
    public Page<GastoComun> listarPorUnidad(String unidadId, UsuarioContexto usuario, Pageable pageable) {
        if (!usuario.puedeOperarSobreUnidad(unidadId)) {
            throw new OperacionNoPermitidaException("No tiene acceso a los gastos comunes de esta unidad");
        }
        return gastoComunRepository.findByUnidadId(unidadId, pageable);
    }

    /**
     * Obtiene un gasto por id, validando ownership sobre su unidad.
     *
     * @param id identificador del gasto
     * @param usuario identidad y roles del request actual
     * @return el gasto encontrado
     * @throws RecursoNoEncontradoException si no existe o está eliminado
     * @throws OperacionNoPermitidaException si el usuario no puede operar sobre la unidad del gasto
     */
    @Transactional(readOnly = true)
    public GastoComun obtener(Long id, UsuarioContexto usuario) {
        GastoComun gasto = buscarOLanzar(id);
        if (!usuario.puedeOperarSobreUnidad(gasto.getUnidadId())) {
            throw new OperacionNoPermitidaException("No tiene acceso a este gasto común");
        }
        return gasto;
    }

    /**
     * Registra un abono sobre un gasto y descuenta su saldo pendiente; si el
     * saldo llega a cero, el gasto pasa a {@code PAGADO}.
     *
     * @param gastoId gasto al que se abona
     * @param request monto, método y comprobante del pago
     * @param usuario identidad del request actual, queda como autor del pago
     * @return el pago persistido
     * @throws RecursoNoEncontradoException si el gasto no existe o está eliminado
     * @throws OperacionNoPermitidaException si el usuario no puede operar sobre la unidad del gasto
     * @see GastoComun#aplicarPago(BigDecimal)
     */
    public Pago registrarPago(Long gastoId, PagoRequest request, UsuarioContexto usuario) {
        GastoComun gasto = buscarOLanzar(gastoId);
        if (!usuario.puedeOperarSobreUnidad(gasto.getUnidadId())) {
            throw new OperacionNoPermitidaException("No puede registrar pagos sobre esta unidad");
        }
        gasto.aplicarPago(request.monto());
        gastoComunRepository.save(gasto);

        Pago pago = Pago.registrar(
                gasto, request.monto(), request.metodo(), usuario.getUsuarioSub(), request.comprobante());
        return pagoRepository.save(pago);
    }

    /**
     * Lista el historial de pagos de un gasto, validando ownership.
     *
     * @param gastoId gasto a consultar
     * @param usuario identidad y roles del request actual
     * @param pageable paginación y orden
     * @return página de pagos del gasto
     * @throws RecursoNoEncontradoException si el gasto no existe o está eliminado
     * @throws OperacionNoPermitidaException si el usuario no puede operar sobre la unidad del gasto
     */
    @Transactional(readOnly = true)
    public Page<Pago> listarPagos(Long gastoId, UsuarioContexto usuario, Pageable pageable) {
        GastoComun gasto = buscarOLanzar(gastoId);
        if (!usuario.puedeOperarSobreUnidad(gasto.getUnidadId())) {
            throw new OperacionNoPermitidaException("No tiene acceso al historial de pagos de esta unidad");
        }
        return pagoRepository.findByGastoComunId(gastoId, pageable);
    }

    /**
     * Crea (idempotente por {@code referenciaExterna}) un gasto común a
     * partir de un evento de reserva de espacio común. Usado por el
     * consumidor Inbox (ver {@code messaging.inbox}).
     */
    public GastoComun crearDesdeReserva(
            String unidadId, String concepto, BigDecimal monto, String reservaId) {
        return gastoComunRepository
                .findByOrigenAndReferenciaExterna(OrigenGasto.RESERVA_ESPACIO, reservaId)
                .orElseGet(() -> gastoComunRepository.save(gastoReservaFactory.crearGasto(
                        unidadId, concepto, monto, reservaId, null)));
    }

    private GastoComun buscarOLanzar(Long id) {
        return gastoComunRepository
                .findById(id)
                .filter(gasto -> gasto.getEstado() != EstadoGasto.ELIMINADO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Gasto común " + id + " no encontrado"));
    }

    private String requerirUnidadPropia(UsuarioContexto usuario) {
        if (usuario.getUnidadId() == null) {
            throw new OperacionNoPermitidaException(
                    "Su token no tiene asociada una unidad (claim unidad_id); contacte a administración");
        }
        return usuario.getUnidadId();
    }
}

package com.convivo.gastoscomunes.service;

import com.convivo.gastoscomunes.domain.EstadoGasto;
import com.convivo.gastoscomunes.domain.GastoComun;
import com.convivo.gastoscomunes.domain.OrigenGasto;
import com.convivo.gastoscomunes.domain.Pago;
import com.convivo.gastoscomunes.dto.GastoComunRequest;
import com.convivo.gastoscomunes.dto.PagoRequest;
import com.convivo.gastoscomunes.exception.OperacionNoPermitidaException;
import com.convivo.gastoscomunes.exception.ReglaNegocioException;
import com.convivo.gastoscomunes.exception.RecursoNoEncontradoException;
import com.convivo.gastoscomunes.factory.GastoManualFactory;
import com.convivo.gastoscomunes.factory.GastoReservaFactory;
import com.convivo.gastoscomunes.repository.GastoComunRepository;
import com.convivo.gastoscomunes.repository.PagoRepository;
import com.convivo.gastoscomunes.security.UsuarioContexto;
import java.math.BigDecimal;
import com.convivo.gastoscomunes.messaging.outbox.OutboxEvento;
import com.convivo.gastoscomunes.messaging.outbox.OutboxEventoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(GastoComunService.class);

    private final GastoComunRepository gastoComunRepository;
    private final PagoRepository pagoRepository;
    private final GastoManualFactory gastoManualFactory;
    private final GastoReservaFactory gastoReservaFactory;
    private final OutboxEventoRepository outboxEventoRepository;

    public GastoComunService(
            GastoComunRepository gastoComunRepository,
            PagoRepository pagoRepository,
            GastoManualFactory gastoManualFactory,
            GastoReservaFactory gastoReservaFactory,
            OutboxEventoRepository outboxEventoRepository) {
        this.gastoComunRepository = gastoComunRepository;
        this.pagoRepository = pagoRepository;
        this.gastoManualFactory = gastoManualFactory;
        this.gastoReservaFactory = gastoReservaFactory;
        this.outboxEventoRepository = outboxEventoRepository;
    }

    /**
     * Emite un cobro manual (origen {@code MANUAL}) para la unidad indicada.
     *
     * @param request unidad, concepto, monto y vencimiento del cobro
     * @return el gasto creado, en estado {@code PENDIENTE} y con saldo igual al monto
     */
    public GastoComun crear(GastoComunRequest request) {
        GastoComun gasto = gastoManualFactory.crearGasto(
                request.condominioId(), request.unidadId(), request.concepto(), request.monto(), null,
                request.fechaVencimiento());
        return gastoComunRepository.save(gasto);
    }

    /**
     * No permite reasignar condominio ni unidad (esos campos del request se
     * ignoran): solo concepto/monto/vencimiento.
     *
     * @throws RecursoNoEncontradoException si el gasto no existe o es de otro condominio
     */
    public GastoComun actualizar(Long id, String condominioId, GastoComunRequest request) {
        GastoComun gasto = buscarOLanzar(id);
        verificarCondominio(gasto, condominioId);
        gasto.actualizar(request.concepto(), request.monto(), request.fechaVencimiento());
        return gastoComunRepository.save(gasto);
    }

    /**
     * Borrado lógico: un gasto eliminado ya no aparece en listados/consultas.
     *
     * @throws RecursoNoEncontradoException si el gasto no existe o es de otro condominio
     */
    public void eliminar(Long id, String condominioId) {
        GastoComun gasto = buscarOLanzar(id);
        verificarCondominio(gasto, condominioId);
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
    public Page<GastoComun> listar(UsuarioContexto usuario, String condominioId, Pageable pageable) {
        if (usuario.esGestorCondominio()) {
            return gastoComunRepository.findByCondominioId(condominioId, pageable);
        }
        return listarPorUnidad(requerirUnidadPropia(usuario), condominioId, usuario, pageable);
    }

    /**
     * Lista los gastos de una unidad dentro de un condominio, validando ownership.
     *
     * @param unidadId unidad a consultar
     * @param condominioId condominio a consultar (filtro, no reemplaza el chequeo de unidad)
     * @param usuario identidad y roles del request actual
     * @param pageable paginación y orden
     * @return página de gastos no eliminados de la unidad
     * @throws OperacionNoPermitidaException si el usuario no administra el condominio ni es dueño de la unidad
     */
    @Transactional(readOnly = true)
    public Page<GastoComun> listarPorUnidad(
            String unidadId, String condominioId, UsuarioContexto usuario, Pageable pageable) {
        if (!usuario.puedeOperarSobreUnidad(unidadId)) {
            throw new OperacionNoPermitidaException("No tiene acceso a los gastos comunes de esta unidad");
        }
        return gastoComunRepository.findByUnidadIdAndCondominioId(unidadId, condominioId, pageable);
    }

    /**
     * Obtiene un gasto por id, validando condominio y ownership sobre su unidad.
     *
     * @param id identificador del gasto
     * @param condominioId condominio esperado del gasto
     * @param usuario identidad y roles del request actual
     * @return el gasto encontrado
     * @throws RecursoNoEncontradoException si no existe, está eliminado o es de otro condominio
     * @throws OperacionNoPermitidaException si el usuario no puede operar sobre la unidad del gasto
     */
    @Transactional(readOnly = true)
    public GastoComun obtener(Long id, String condominioId, UsuarioContexto usuario) {
        GastoComun gasto = buscarOLanzar(id);
        verificarCondominio(gasto, condominioId);
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
    public Pago registrarPago(Long gastoId, String condominioId, PagoRequest request, UsuarioContexto usuario) {
        GastoComun gasto = buscarOLanzar(gastoId);
        verificarCondominio(gasto, condominioId);
        if (!usuario.puedeOperarSobreUnidad(gasto.getUnidadId())) {
            throw new OperacionNoPermitidaException("No puede registrar pagos sobre esta unidad");
        }
        gasto.aplicarPago(request.monto());
        gastoComunRepository.save(gasto);

        Pago pago = Pago.registrar(
                gasto, request.monto(), request.metodo(), usuario.getUsuarioSub(), request.comprobante());
        pago = pagoRepository.save(pago);

        if (gasto.getOrigen() == OrigenGasto.RESERVA_ESPACIO && gasto.getEstado() == EstadoGasto.PAGADO) {
            String payloadJson = String.format("{\"reserva_id\":%s}", gasto.getReferenciaExterna());
            OutboxEvento evento = OutboxEvento.crear("espacios_events", "reserva_pagada", "reserva_pagada", payloadJson);
            outboxEventoRepository.save(evento);
        }

        return pago;
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
    public Page<Pago> listarPagos(Long gastoId, String condominioId, UsuarioContexto usuario, Pageable pageable) {
        GastoComun gasto = buscarOLanzar(gastoId);
        verificarCondominio(gasto, condominioId);
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
        // El evento reserva_espacio_creada (ms-espacios-comunes) todavia no
        // incluye condominio_id en su payload — se asume el condominio
        // placeholder "1" hasta que el evento lo propague (mismo default
        // usado en la migracion V3, TD-19).
        String condominioIdPorDefecto = "1";
        return gastoComunRepository
                .findByOrigenAndReferenciaExterna(OrigenGasto.RESERVA_ESPACIO, reservaId)
                .orElseGet(() -> gastoComunRepository.save(gastoReservaFactory.crearGasto(
                        condominioIdPorDefecto, unidadId, concepto, monto, reservaId, null)));
    }

    /**
     * Anula (borrado lógico) el cobro asociado a una reserva que expiró sin
     * pago. Si el cobro ya está {@code PAGADO} (pagó justo cuando expiraba),
     * no se anula: se deja para revisión manual.
     *
     * @param reservaId reserva que expiró
     */
    public void anularPorReservaExpirada(String reservaId) {
        gastoComunRepository.findByOrigenAndReferenciaExterna(OrigenGasto.RESERVA_ESPACIO, reservaId)
                .ifPresentOrElse(gasto -> {
                    try {
                        gasto.eliminar();
                        gastoComunRepository.save(gasto);
                    } catch (ReglaNegocioException ex) {
                        log.error(
                                "PAGO SOBRE RESERVA EXPIRADA: no se anula el cobro de la reserva {} (ya pagado). Requiere revisión manual.",
                                reservaId);
                    }
                }, () -> log.warn("No se encontró cobro para la reserva expirada {}", reservaId));
    }

    private GastoComun buscarOLanzar(Long id) {
        return gastoComunRepository
                .findById(id)
                .filter(gasto -> gasto.getEstado() != EstadoGasto.ELIMINADO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Gasto común " + id + " no encontrado"));
    }

    /** Un gasto de otro condominio se trata como inexistente: nunca confirma su existencia a quien no debe verlo. */
    private void verificarCondominio(GastoComun gasto, String condominioId) {
        if (!gasto.getCondominioId().equals(condominioId)) {
            throw new RecursoNoEncontradoException("Gasto común " + gasto.getId() + " no encontrado");
        }
    }

    private String requerirUnidadPropia(UsuarioContexto usuario) {
        if (usuario.getUnidadId() == null) {
            throw new OperacionNoPermitidaException(
                    "Su token no tiene asociada una unidad (claim unidad_id); contacte a administración");
        }
        return usuario.getUnidadId();
    }
}






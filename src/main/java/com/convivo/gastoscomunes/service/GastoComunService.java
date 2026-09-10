package com.convivo.gastoscomunes.service;

import com.convivo.gastoscomunes.domain.EstadoGasto;
import com.convivo.gastoscomunes.domain.GastoComun;
import com.convivo.gastoscomunes.domain.OrigenGasto;
import com.convivo.gastoscomunes.domain.Pago;
import com.convivo.gastoscomunes.dto.GastoComunRequest;
import com.convivo.gastoscomunes.dto.PagoRequest;
import com.convivo.gastoscomunes.exception.OperacionNoPermitidaException;
import com.convivo.gastoscomunes.exception.RecursoNoEncontradoException;
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
 * propietario/residente solo ven y pagan los gastos de su propia unidad.
 */
@Service
@Transactional
public class GastoComunService {

    private final GastoComunRepository gastoComunRepository;
    private final PagoRepository pagoRepository;

    public GastoComunService(GastoComunRepository gastoComunRepository, PagoRepository pagoRepository) {
        this.gastoComunRepository = gastoComunRepository;
        this.pagoRepository = pagoRepository;
    }

    public GastoComun crear(GastoComunRequest request) {
        GastoComun gasto = GastoComun.crear(
                request.unidadId(), request.concepto(), request.monto(), OrigenGasto.MANUAL, null,
                request.fechaVencimiento());
        return gastoComunRepository.save(gasto);
    }

    @Transactional(readOnly = true)
    public Page<GastoComun> listar(UsuarioContexto usuario, Pageable pageable) {
        if (usuario.esGestorCondominio()) {
            return gastoComunRepository.findAll(pageable);
        }
        return listarPorUnidad(requerirUnidadPropia(usuario), usuario, pageable);
    }

    @Transactional(readOnly = true)
    public Page<GastoComun> listarPorUnidad(String unidadId, UsuarioContexto usuario, Pageable pageable) {
        if (!usuario.puedeOperarSobreUnidad(unidadId)) {
            throw new OperacionNoPermitidaException("No tiene acceso a los gastos comunes de esta unidad");
        }
        return gastoComunRepository.findByUnidadId(unidadId, pageable);
    }

    @Transactional(readOnly = true)
    public GastoComun obtener(Long id, UsuarioContexto usuario) {
        GastoComun gasto = buscarOLanzar(id);
        if (!usuario.puedeOperarSobreUnidad(gasto.getUnidadId())) {
            throw new OperacionNoPermitidaException("No tiene acceso a este gasto común");
        }
        return gasto;
    }

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
                .orElseGet(() -> gastoComunRepository.save(GastoComun.crear(
                        unidadId, concepto, monto, OrigenGasto.RESERVA_ESPACIO, reservaId, null)));
    }

    private GastoComun buscarOLanzar(Long id) {
        return gastoComunRepository
                .findById(id)
                .filter(gasto -> gasto.getEstado() != EstadoGasto.ANULADO)
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

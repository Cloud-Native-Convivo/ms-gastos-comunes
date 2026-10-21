package com.convivo.gastoscomunes.messaging.inbox;

import com.convivo.gastoscomunes.messaging.inbox.dto.ReservaExpiradaEvent;
import com.convivo.gastoscomunes.service.GastoComunService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotencia (Inbox) + anulación del cobro, en una única transacción
 * local ACID — mismo patrón que {@link ReservaCreadaInboxService}. Si
 * {@code anularPorReservaExpirada} falla, el registro en el Inbox también
 * se revierte: una reentrega del mismo evento no debe quedar marcada como
 * "ya procesada" si el cobro nunca se anuló.
 */
@Service
public class ReservaExpiradaInboxService {

    private final InboxEventoRepository inboxRepository;
    private final GastoComunService gastoComunService;

    public ReservaExpiradaInboxService(InboxEventoRepository inboxRepository, GastoComunService gastoComunService) {
        this.inboxRepository = inboxRepository;
        this.gastoComunService = gastoComunService;
    }

    @Transactional
    public ResultadoProcesamiento procesar(ReservaExpiradaEvent evento) {
        if (!evento.esValido()) {
            return ResultadoProcesamiento.INVALIDO;
        }
        if (inboxRepository.existsById(evento.eventId())) {
            return ResultadoProcesamiento.DUPLICADO;
        }

        inboxRepository.save(new InboxEvento(evento.eventId(), "reserva_expirada"));
        gastoComunService.anularPorReservaExpirada(evento.reservaId());
        return ResultadoProcesamiento.PROCESADO;
    }
}

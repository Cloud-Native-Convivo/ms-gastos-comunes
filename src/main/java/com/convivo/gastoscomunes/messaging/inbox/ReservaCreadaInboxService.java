package com.convivo.gastoscomunes.messaging.inbox;

import com.convivo.gastoscomunes.messaging.inbox.dto.ReservaEspacioCreadaEvent;
import com.convivo.gastoscomunes.service.GastoComunService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fase 3 del diagrama de saga: idempotencia (Inbox) + creación del gasto
 * común, en una única transacción local ACID sobre {@code gastos_db}.
 */
@Service
public class ReservaCreadaInboxService {

    private final InboxEventoRepository inboxRepository;
    private final GastoComunService gastoComunService;

    public ReservaCreadaInboxService(InboxEventoRepository inboxRepository, GastoComunService gastoComunService) {
        this.inboxRepository = inboxRepository;
        this.gastoComunService = gastoComunService;
    }

    @Transactional
    public ResultadoProcesamiento procesar(ReservaEspacioCreadaEvent evento) {
        if (inboxRepository.existsById(evento.eventId())) {
            return ResultadoProcesamiento.DUPLICADO;
        }
        if (!evento.esValido()) {
            return ResultadoProcesamiento.INVALIDO;
        }

        inboxRepository.save(new InboxEvento(evento.eventId(), evento.tipo()));
        gastoComunService.crearDesdeReserva(
                evento.unidadId(), evento.conceptoOPorDefecto(), evento.monto(), evento.reservaId());
        return ResultadoProcesamiento.PROCESADO;
    }
}

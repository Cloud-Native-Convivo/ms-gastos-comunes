package com.convivo.gastoscomunes.messaging.inbox.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload del evento {@code reserva_expirada}, publicado por el Outbox de
 * ms-espacios-comunes cuando una reserva no se pagó dentro de los 15
 * minutos. Solo se necesita el identificador para anular el cobro
 * asociado — el resto del payload (email, fechas) lo consume el worker de
 * notificaciones del propio ms-espacios-comunes, no este listener.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservaExpiradaEvent(
        @JsonAlias({"event_id", "eventId", "id"}) String eventId,
        @JsonAlias({"reserva_id", "reservaId"}) String reservaId) {

    public boolean esValido() {
        return eventId != null && !eventId.isBlank() && reservaId != null && !reservaId.isBlank();
    }
}

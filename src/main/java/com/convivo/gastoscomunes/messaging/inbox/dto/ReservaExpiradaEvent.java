package com.convivo.gastoscomunes.messaging.inbox.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Payload del evento {@code reserva_expirada}, publicado por el Outbox de
 * ms-espacios-comunes cuando una reserva no se pagó dentro de los 15
 * minutos. Solo se necesita el identificador para anular el cobro
 * asociado — el resto del payload (email, fechas) lo consume el worker de
 * notificaciones del propio ms-espacios-comunes, no este listener.
 *
 * <p>Se construye con {@link #desde(JsonNode)} en vez de dejar que Jackson
 * bindee el record directo vía {@code @JsonAlias}: el productor manda cada
 * campo en snake_case Y camelCase a la vez (p. ej. {@code reserva_id} y
 * {@code reservaId} ambos presentes), y Jackson no puede bindear un
 * segundo valor sobre un creator property de un record ya construido
 * ("No fallback setter/field defined for creator property") — falla
 * siempre con el payload real, no solo en casos de borde.</p>
 */
public record ReservaExpiradaEvent(String eventId, String reservaId) {

    public static ReservaExpiradaEvent desde(JsonNode root) {
        return new ReservaExpiradaEvent(
                primerValor(root, "event_id", "eventId", "id"),
                primerValor(root, "reserva_id", "reservaId"));
    }

    private static String primerValor(JsonNode root, String... claves) {
        for (String clave : claves) {
            if (root.hasNonNull(clave)) {
                return root.path(clave).asText();
            }
        }
        return null;
    }

    public boolean esValido() {
        return eventId != null && !eventId.isBlank() && reservaId != null && !reservaId.isBlank();
    }
}

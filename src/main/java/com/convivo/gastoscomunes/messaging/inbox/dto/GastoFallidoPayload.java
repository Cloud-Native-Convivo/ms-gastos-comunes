package com.convivo.gastoscomunes.messaging.inbox.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Payload de compensación publicado hacia {@code espacios_compensacion_queue}
 * cuando el evento de reserva no puede convertirse en un gasto común
 * válido (Caso 3C del diagrama de saga coreografiada).
 */
public record GastoFallidoPayload(
    @JsonProperty("reserva_id") String reservaId,
    @JsonProperty("motivo") String motivo,
    @JsonProperty("timestamp") Instant timestamp
) {

    /**
     * Crea el payload de compensación con la hora actual.
     *
     * @param reservaId reserva que no pudo convertirse en gasto
     * @param motivo causa del rechazo (ej. {@code datos_inconsistentes})
     * @return payload listo para encolar en el Outbox
     */
    public static GastoFallidoPayload de(String reservaId, String motivo) {
        return new GastoFallidoPayload(reservaId, motivo, Instant.now());
    }
}

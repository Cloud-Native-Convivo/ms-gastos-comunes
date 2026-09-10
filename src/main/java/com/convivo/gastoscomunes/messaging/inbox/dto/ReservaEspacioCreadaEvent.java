package com.convivo.gastoscomunes.messaging.inbox.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload del evento {@code reserva_espacio_creada}, publicado por el
 * Outbox de ms-espacios-comunes al exchange {@code espacios_events}.
 *
 * <p><b>Nota de diseño:</b> {@code unidadId} se asume denormalizado en el
 * propio evento por el productor (ms-espacios-comunes), porque
 * ms-gastos-comunes no tiene acceso directo a la relación usuario-unidad
 * (dominio de MS-USUARIOS) y hacer una llamada síncrona desde un consumidor
 * de eventos rompería el propósito de la arquitectura orientada a
 * eventos.</p>
 */
public record ReservaEspacioCreadaEvent(
        String eventId,
        String tipo,
        String reservaId,
        String espacioId,
        String unidadId,
        String usuarioSub,
        String concepto,
        BigDecimal monto,
        Instant timestamp) {

    /**
     * Validación de negocio mínima que este microservicio puede hacer sin
     * llamadas síncronas a otros dominios (unidad/residente existentes se
     * validarían idealmente en el productor o en una réplica local — fuera
     * de alcance de esta iteración, ver README).
     */
    public boolean esValido() {
        return eventId != null && !eventId.isBlank()
                && reservaId != null && !reservaId.isBlank()
                && unidadId != null && !unidadId.isBlank()
                && usuarioSub != null && !usuarioSub.isBlank()
                && monto != null && monto.signum() > 0;
    }

    public String conceptoOPorDefecto() {
        return (concepto == null || concepto.isBlank()) ? "Reserva Espacio" : concepto;
    }
}

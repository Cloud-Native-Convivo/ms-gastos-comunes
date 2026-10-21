package com.convivo.gastoscomunes.messaging.inbox.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservaEspacioCreadaEvent(
        @JsonAlias({"event_id", "eventId", "id"}) String eventId,
        @JsonAlias({"tipo", "tipo_evento"}) String tipo,
        @JsonAlias({"reserva_id", "reservaId"}) String reservaId,
        @JsonAlias({"espacio_id", "espacioId"}) String espacioId,
        @JsonAlias({"unidad_id", "unidadId"}) String unidadId,
        @JsonAlias({"usuario_sub", "usuarioSub"}) String usuarioSub,
        @JsonAlias({"concepto", "descripcion"}) String concepto,
        @JsonAlias({"monto", "monto_total", "montoTotal"}) BigDecimal monto,
        @JsonDeserialize(using = FlexibleInstantDeserializer.class) Instant timestamp) {

    // Largos y precisión espejo de V1__init.sql (ver AGENTS.md §5.1): este
    // evento no pasa por Bean Validation, así que se validan aquí para que un
    // valor que Oracle rechazaría sea INVALIDO (compensación) y no un error
    // de base de datos que agota reintentos y termina en la DLQ.
    private static final int MAX_EVENT_ID = 64;      // inbox_eventos.event_id
    private static final int MAX_TIPO = 80;          // inbox_eventos.tipo
    private static final int MAX_RESERVA_ID = 100;   // gastos_comunes.referencia_externa
    private static final int MAX_UNIDAD_ID = 64;     // gastos_comunes.unidad_id
    private static final int MAX_CONCEPTO = 200;     // gastos_comunes.concepto
    private static final int MONTO_ENTEROS = 10;     // NUMBER(12,2)
    private static final int MONTO_DECIMALES = 2;

    /**
     * Validación de negocio mínima que este microservicio puede hacer sin
     * llamadas síncronas a otros dominios (unidad/residente existentes se
     * validarían idealmente en el productor o en una réplica local — fuera
     * de alcance de esta iteración, ver README).
     */
    public boolean esValido() {
        return textoValido(eventId, MAX_EVENT_ID)
                && (tipo == null || tipo.length() <= MAX_TIPO)
                && textoValido(reservaId, MAX_RESERVA_ID)
                && textoValido(unidadId, MAX_UNIDAD_ID)
                && usuarioSub != null && !usuarioSub.isBlank()
                && montoValido();
    }

    /**
     * Concepto a persistir. Se recorta en vez de invalidar el evento: un
     * texto descriptivo largo no justifica cancelar una reserva válida.
     */
    public String conceptoOPorDefecto() {
        if (concepto == null || concepto.isBlank()) {
            return "Reserva Espacio";
        }
        return concepto.codePointCount(0, concepto.length()) <= MAX_CONCEPTO
                ? concepto
                : concepto.substring(0, concepto.offsetByCodePoints(0, MAX_CONCEPTO));
    }

    private static boolean textoValido(String valor, int largoMaximo) {
        return valor != null && !valor.isBlank() && valor.length() <= largoMaximo;
    }

    private boolean montoValido() {
        if (monto == null || monto.signum() < 0) {
            return false;
        }
        BigDecimal normalizado = monto.stripTrailingZeros();
        return normalizado.scale() <= MONTO_DECIMALES
                && normalizado.precision() - normalizado.scale() <= MONTO_ENTEROS;
    }
}


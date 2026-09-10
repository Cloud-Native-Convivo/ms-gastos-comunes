package com.convivo.gastoscomunes.exception;

import java.time.Instant;

/**
 * Formato de error uniforme, alineado con el filtro global de excepciones
 * del BFF ({@code statusCode}, {@code code}, {@code message}, {@code requestId})
 * para que el cliente reciba una forma consistente sin importar qué capa
 * de Convivo respondió el error.
 */
public record ErrorResponse(int statusCode, String code, String message, String requestId, Instant timestamp) {

    public static ErrorResponse of(int statusCode, String code, String message, String requestId) {
        return new ErrorResponse(statusCode, code, message, requestId, Instant.now());
    }
}

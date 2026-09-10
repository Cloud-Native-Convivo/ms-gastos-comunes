package com.convivo.gastoscomunes.config;

import com.convivo.gastoscomunes.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Respuestas 401/403 en el mismo formato JSON uniforme que usa el BFF, en
 * vez del HTML/texto plano que Spring Security devuelve por defecto.
 */
final class JsonAuthEntryPoints {

    private JsonAuthEntryPoints() {}

    static AuthenticationEntryPoint unauthorized(ObjectMapper mapper) {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) ->
                escribir(mapper, response, 401, "UNAUTHORIZED", "Token ausente, inválido o expirado", request);
    }

    static AccessDeniedHandler forbidden(ObjectMapper mapper) {
        return (HttpServletRequest request, HttpServletResponse response, org.springframework.security.access.AccessDeniedException ex) ->
                escribir(mapper, response, 403, "FORBIDDEN", "No tiene permisos para esta operación", request);
    }

    private static void escribir(
            ObjectMapper mapper, HttpServletResponse response, int status, String code, String message,
            HttpServletRequest request) throws java.io.IOException {
        String requestId = firstNonBlank(request.getHeader("X-Correlation-Id"), UUID.randomUUID().toString());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(mapper.writeValueAsString(ErrorResponse.of(status, code, message, requestId)));
    }

    private static String firstNonBlank(String a, String b) {
        return (a == null || a.isBlank()) ? b : a;
    }
}

package com.convivo.gastoscomunes.exception;

import com.convivo.gastoscomunes.security.UsuarioContexto;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de dominio a respuestas HTTP en el formato
 * uniforme {@link ErrorResponse}, igual que el filtro global del BFF.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final UsuarioContexto usuarioContexto;

    public GlobalExceptionHandler(UsuarioContexto usuarioContexto) {
        this.usuarioContexto = usuarioContexto;
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarNoEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(OperacionNoPermitidaException.class)
    public ResponseEntity<ErrorResponse> manejarNoPermitida(OperacionNoPermitidaException ex, HttpServletRequest req) {
        return responder(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), req);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> manejarEstadoInvalido(IllegalStateException ex, HttpServletRequest req) {
        return responder(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return responder(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detalle, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarGenerica(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {} {}", req.getMethod(), req.getRequestURI(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno del servidor", req);
    }

    private ResponseEntity<ErrorResponse> responder(HttpStatus status, String code, String message, HttpServletRequest req) {
        String requestId = usuarioContexto.getCorrelationId() != null
                ? usuarioContexto.getCorrelationId()
                : firstNonBlank(req.getHeader("X-Correlation-Id"), UUID.randomUUID().toString());
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), code, message, requestId));
    }

    private static String firstNonBlank(String a, String b) {
        return (a == null || a.isBlank()) ? b : a;
    }
}

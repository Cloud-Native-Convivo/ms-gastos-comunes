package com.convivo.gastoscomunes.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.convivo.gastoscomunes.security.UsuarioContexto;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private UsuarioContexto usuarioContexto;
    private HttpServletRequest request;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        usuarioContexto = mock(UsuarioContexto.class);
        request = mock(HttpServletRequest.class);
        handler = new GlobalExceptionHandler(usuarioContexto);
    }

    @Test
    void manejaRecursoNoEncontrado() {
        when(usuarioContexto.getCorrelationId()).thenReturn("corr-123");
        RecursoNoEncontradoException ex = new RecursoNoEncontradoException("Gasto no encontrado");

        ResponseEntity<ErrorResponse> resp = handler.manejarNoEncontrado(ex, request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(resp.getBody().message()).isEqualTo("Gasto no encontrado");
        assertThat(resp.getBody().requestId()).isEqualTo("corr-123");
    }

    @Test
    void manejaOperacionNoPermitida() {
        when(request.getHeader("X-Correlation-Id")).thenReturn("header-corr");
        OperacionNoPermitidaException ex = new OperacionNoPermitidaException("No permitido");

        ResponseEntity<ErrorResponse> resp = handler.manejarNoPermitida(ex, request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("FORBIDDEN");
        assertThat(resp.getBody().message()).isEqualTo("No permitido");
        assertThat(resp.getBody().requestId()).isEqualTo("header-corr");
    }

    @Test
    void manejaAutorizacionDenegada() {
        AccessDeniedException ex = new AccessDeniedException("denied");

        ResponseEntity<ErrorResponse> resp = handler.manejarAutorizacionDenegada(ex, request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("FORBIDDEN");
        assertThat(resp.getBody().message()).isEqualTo("No tiene permisos para esta operación");
    }

    @Test
    void manejaReglaNegocio() {
        ReglaNegocioException ex = new ReglaNegocioException("Saldo excedido");

        ResponseEntity<ErrorResponse> resp = handler.manejarReglaNegocio(ex, request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("CONFLICT");
        assertThat(resp.getBody().message()).isEqualTo("Saldo excedido");
    }

    @Test
    void manejaValidacionArgumentos() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("gasto", "monto", "no puede ser nulo"),
                new FieldError("gasto", "unidadId", "obligatorio")
        ));

        ResponseEntity<ErrorResponse> resp = handler.manejarValidacion(ex, request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(resp.getBody().message()).contains("monto: no puede ser nulo");
        assertThat(resp.getBody().message()).contains("unidadId: obligatorio");
    }

    @Test
    void manejaExcepcionGenerica() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/gastos");

        ResponseEntity<ErrorResponse> resp = handler.manejarGenerica(new RuntimeException("crash"), request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(resp.getBody().message()).isEqualTo("Error interno del servidor");
    }
}

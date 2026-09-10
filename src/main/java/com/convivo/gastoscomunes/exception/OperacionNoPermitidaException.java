package com.convivo.gastoscomunes.exception;

/** Ownership violado: el usuario autenticado no tiene relación con el recurso solicitado. */
public class OperacionNoPermitidaException extends RuntimeException {
    public OperacionNoPermitidaException(String message) {
        super(message);
    }
}

package com.convivo.gastoscomunes.exception;

/** Operación que viola una regla del dominio (ej. pagar un gasto ya pagado); se responde 409. */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String message) {
        super(message);
    }
}

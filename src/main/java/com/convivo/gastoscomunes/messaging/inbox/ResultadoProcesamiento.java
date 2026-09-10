package com.convivo.gastoscomunes.messaging.inbox;

/** Resultado de intentar procesar un evento entrante (ver {@link ReservaCreadaInboxService}). */
public enum ResultadoProcesamiento {
    /** Ya existía en la tabla Inbox: se descarta sin reprocesar. */
    DUPLICADO,
    /** Datos del evento inconsistentes: requiere compensación de saga. */
    INVALIDO,
    /** Gasto común creado correctamente. */
    PROCESADO,
}

package com.convivo.gastoscomunes.messaging.outbox;

/** Ciclo de vida de un evento en la tabla Outbox (ver {@link OutboxRelayScheduler}). */
public enum EstadoOutbox {
    /** Guardado, aún no publicado en RabbitMQ; el relay lo reintentará. */
    PENDIENTE,
    /** Publicado correctamente. */
    PUBLICADO,
    /** Agotó los reintentos; requiere revisión manual. */
    FALLIDO,
}

package com.convivo.gastoscomunes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de exchange/colas y parámetros del patrón Outbox/Inbox,
 * configurables por entorno (coinciden con los nombres del diagrama de
 * secuencia de mensajería asíncrona).
 */
@ConfigurationProperties(prefix = "convivo.mensajeria")
public record MensajeriaProperties(
        String exchangeEventos,
        String colaReservaCreada,
        String routingKeyReservaCreada,
        String routingKeyGastoFallido,
        String dlx,
        String colaDeadLetter,
        int outboxLoteMaximo,
        int outboxMaxIntentos,
        int listenerMaxReintentos) {

    public MensajeriaProperties {
        exchangeEventos = valorODefecto(exchangeEventos, "espacios_events");
        colaReservaCreada = valorODefecto(colaReservaCreada, "gastos_reserva_creada_queue");
        routingKeyReservaCreada = valorODefecto(routingKeyReservaCreada, "reserva_espacio_creada");
        routingKeyGastoFallido = valorODefecto(routingKeyGastoFallido, "gasto_fallido");
        dlx = valorODefecto(dlx, "gastos.dlx");
        colaDeadLetter = valorODefecto(colaDeadLetter, "gastos_reserva_creada_dlq");
        outboxLoteMaximo = outboxLoteMaximo <= 0 ? 50 : outboxLoteMaximo;
        outboxMaxIntentos = outboxMaxIntentos <= 0 ? 10 : outboxMaxIntentos;
        listenerMaxReintentos = listenerMaxReintentos <= 0 ? 3 : listenerMaxReintentos;
    }

    private static String valorODefecto(String valor, String defecto) {
        return (valor == null || valor.isBlank()) ? defecto : valor;
    }
}

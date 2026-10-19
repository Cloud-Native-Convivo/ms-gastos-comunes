package com.convivo.gastoscomunes.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class RabbitMqConfigReservaExpiradaTest {

    private final MensajeriaProperties props = new MensajeriaProperties(
            "espacios_events", "gastos_reserva_creada_queue", "reserva_espacio_creada",
            "gasto_fallido", "gastos_dlx", "gastos_reserva_creada_dlq", 50, 10, 3);
    private final RabbitMqConfig config = new RabbitMqConfig(props);

    @Test
    void declaraColaConDlqYBindingAReservaExpirada() {
        Queue cola = config.gastosReservaExpiradaQueue();
        assertThat(cola.getName()).isEqualTo("gastos_reserva_expirada_queue");
        assertThat(cola.isDurable()).isTrue();
        assertThat(cola.getArguments().get("x-dead-letter-exchange")).isEqualTo("gastos_dlx");
        assertThat(cola.getArguments().get("x-dead-letter-routing-key")).isEqualTo("gastos_reserva_expirada_dlq");

        Queue dlq = config.gastosReservaExpiradaDlq();
        assertThat(dlq.getName()).isEqualTo("gastos_reserva_expirada_dlq");

        TopicExchange exchange = config.espaciosEventsExchange();
        Binding binding = config.bindingReservaExpirada(cola, exchange);
        assertThat(binding.getRoutingKey()).isEqualTo("reserva_expirada");
    }
}

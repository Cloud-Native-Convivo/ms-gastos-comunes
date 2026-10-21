package com.convivo.gastoscomunes.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;

class RabbitMqConfigFanoutTest {

    @Test
    void debeConfigurarExchangeFanoutYColasVinculadas() {
        MensajeriaProperties props = new MensajeriaProperties(
                "espacios_events",
                "gastos_reserva_creada_queue",
                "reserva_espacio_creada",
                "gasto_fallido",
                "gastos_dlx",
                "gastos_dead_letter_queue",
                50,
                10,
                3);
        RabbitMqConfig config = new RabbitMqConfig(props);

        FanoutExchange exchange = config.gastosPdfFanout();
        assertThat(exchange.getName()).isEqualTo("gastos.pdf.fanout");
        assertThat(exchange.isDurable()).isTrue();

        Queue pdfQueue = config.gastosPdfGeneracionQueue();
        assertThat(pdfQueue.getName()).isEqualTo("gastos_pdf_generacion_queue");
        assertThat(pdfQueue.isDurable()).isTrue();
        assertThat(pdfQueue.getArguments().get("x-dead-letter-exchange")).isEqualTo("gastos_dlx");

        Queue emailQueue = config.gastosNotificacionesEmailQueue();
        assertThat(emailQueue.getName()).isEqualTo("gastos_notificaciones_email_queue");
        assertThat(emailQueue.isDurable()).isTrue();
        assertThat(emailQueue.getArguments().get("x-dead-letter-exchange")).isEqualTo("gastos_dlx");
    }
}

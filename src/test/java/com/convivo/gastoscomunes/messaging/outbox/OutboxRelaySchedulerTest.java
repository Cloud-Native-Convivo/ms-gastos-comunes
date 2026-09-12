package com.convivo.gastoscomunes.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.convivo.gastoscomunes.config.MensajeriaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    @Mock
    private OutboxEventoRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private final MensajeriaProperties props = new MensajeriaProperties(
            "espacios_events", "gastos_reserva_creada_queue", "reserva_espacio_creada", "gasto_fallido",
            "gastos.dlx", "gastos_reserva_creada_dlq", 50, 10, 3);

    @Test
    void marcaPublicadoCuandoRabbitConfirmaElEnvio() {
        OutboxRelayScheduler scheduler = new OutboxRelayScheduler(repository, rabbitTemplate, props);
        OutboxEvento evento = OutboxEvento.crear(
                "espacios_events", "gasto_fallido", "gasto_fallido", "{\"reservaId\":\"r-1\"}");

        scheduler.publicarUno(evento);

        assertThat(evento.getEstado()).isEqualTo(EstadoOutbox.PUBLICADO);
        assertThat(evento.getIntentos()).isZero();
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Message.class));
        verify(repository).save(evento);
    }

    @Test
    void incrementaIntentosYMantienePendienteCuandoFallaLaPublicacion() {
        doThrow(new AmqpException("broker no disponible"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Message.class));

        OutboxRelayScheduler scheduler = new OutboxRelayScheduler(repository, rabbitTemplate, props);
        OutboxEvento evento = OutboxEvento.crear(
                "espacios_events", "gasto_fallido", "gasto_fallido", "{\"reservaId\":\"r-1\"}");

        scheduler.publicarUno(evento);

        assertThat(evento.getEstado()).isEqualTo(EstadoOutbox.PENDIENTE);
        assertThat(evento.getIntentos()).isEqualTo(1);
        verify(repository).save(evento);
    }

    @Test
    void marcaFallidoTrasAgotarLosIntentosMaximos() {
        OutboxEvento evento = OutboxEvento.crear(
                "espacios_events", "gasto_fallido", "gasto_fallido", "{\"reservaId\":\"r-1\"}");
        for (int i = 0; i < props.outboxMaxIntentos(); i++) {
            evento.registrarIntentoFallido(props.outboxMaxIntentos());
        }

        assertThat(evento.getEstado()).isEqualTo(EstadoOutbox.FALLIDO);
        assertThat(evento.getIntentos()).isEqualTo(props.outboxMaxIntentos());
    }
}

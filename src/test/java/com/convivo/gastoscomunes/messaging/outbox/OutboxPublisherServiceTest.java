package com.convivo.gastoscomunes.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboxPublisherServiceTest {

    private OutboxEventoRepository repository;
    private ObjectMapper objectMapper;
    private OutboxPublisherService service;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventoRepository.class);
        objectMapper = mock(ObjectMapper.class);
        service = new OutboxPublisherService(repository, objectMapper);
    }

    @Test
    void encolarGuardaEventoCorrectamente() throws Exception {
        Object payload = new Object();
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"key\":\"val\"}");

        service.encolar("exchange.test", "routing.test", "TIPO_TEST", payload);

        ArgumentCaptor<OutboxEvento> captor = ArgumentCaptor.forClass(OutboxEvento.class);
        verify(repository).save(captor.capture());
        OutboxEvento evento = captor.getValue();
        assertThat(evento.getExchange()).isEqualTo("exchange.test");
        assertThat(evento.getRoutingKey()).isEqualTo("routing.test");
        assertThat(evento.getTipo()).isEqualTo("TIPO_TEST");
        assertThat(evento.getPayload()).isEqualTo("{\"key\":\"val\"}");
        assertThat(evento.getEstado()).isEqualTo(EstadoOutbox.PENDIENTE);
    }

    @Test
    void encolarLanzaIllegalStateExceptionSiFallaSerializacion() throws Exception {
        Object payload = new Object();
        when(objectMapper.writeValueAsString(payload)).thenThrow(new JsonProcessingException("error") {});

        assertThatThrownBy(() -> service.encolar("ex", "rk", "TIPO_FALLA", payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se pudo serializar el evento outbox 'TIPO_FALLA'");
    }
}

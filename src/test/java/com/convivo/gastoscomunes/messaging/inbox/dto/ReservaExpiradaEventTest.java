package com.convivo.gastoscomunes.messaging.inbox.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * El productor real (ms-espacios-comunes, payload_reserva()) manda cada
 * campo en snake_case Y camelCase a la vez, no uno u otro -- este test usa
 * el payload real capturado de la DLQ local, no uno simplificado.
 */
class ReservaExpiradaEventTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializaElPayloadRealConClavesSnakeYCamelDuplicadas() throws Exception {
        String json = "{\"event_id\": \"reserva-3-expirada\", \"eventId\": \"reserva-3-expirada\", "
                + "\"tipo\": \"reserva_expirada\", \"reserva_id\": \"3\", \"reservaId\": \"3\", "
                + "\"espacio_id\": \"21\", \"espacioId\": \"21\", \"espacio_nombre\": \"Quincho Demo Local\", "
                + "\"espacioNombre\": \"Quincho Demo Local\", \"unidad_id\": \"unidad-residente-demo-caso-b\", "
                + "\"unidadId\": \"unidad-residente-demo-caso-b\", \"usuario_sub\": \"residente-demo-caso-b\", "
                + "\"usuarioSub\": \"residente-demo-caso-b\", \"email\": \"caso.b@convivo.cl\", "
                + "\"concepto\": \"Reserva Espacio 21\", \"monto\": 12001.67, \"monto_total\": 12001.67, "
                + "\"fecha_inicio\": \"2026-10-19T16:43:38\", \"fecha_fin\": \"2026-10-19T18:43:39\", "
                + "\"expira_en\": \"2026-10-18T14:45:33\", \"timestamp\": \"2026-10-18T14:45:35.953025Z\"}";

        JsonNode root = mapper.readTree(json);
        ReservaExpiradaEvent evento = ReservaExpiradaEvent.desde(root);

        assertThat(evento.eventId()).isEqualTo("reserva-3-expirada");
        assertThat(evento.reservaId()).isEqualTo("3");
        assertThat(evento.esValido()).isTrue();
    }

    @Test
    void esInvalidoSinReservaIdNiEventId() throws Exception {
        JsonNode vacio = mapper.readTree("{}");
        ReservaExpiradaEvent evento = ReservaExpiradaEvent.desde(vacio);

        assertThat(evento.esValido()).isFalse();
    }

    @Test
    void aceptaSoloUnaConvencionDeNombres() throws Exception {
        JsonNode soloSnake = mapper.readTree("{\"event_id\":\"e1\",\"reserva_id\":\"5\"}");
        assertThat(ReservaExpiradaEvent.desde(soloSnake).reservaId()).isEqualTo("5");

        JsonNode soloCamel = mapper.readTree("{\"eventId\":\"e1\",\"reservaId\":\"5\"}");
        assertThat(ReservaExpiradaEvent.desde(soloCamel).reservaId()).isEqualTo("5");
    }
}

package com.convivo.gastoscomunes.messaging.inbox.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Validación de negocio del evento entrante y parseo flexible del timestamp. */
class ReservaEspacioCreadaEventTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static ReservaEspacioCreadaEvent evento(
            String eventId, String tipo, String reservaId, String unidadId, String usuarioSub, String concepto, BigDecimal monto) {
        return new ReservaEspacioCreadaEvent(eventId, tipo, reservaId, "esp-1", unidadId, usuarioSub, concepto, monto, null);
    }

    private static ReservaEspacioCreadaEvent valido() {
        return evento("evt-1", "reserva_espacio_creada", "res-1", "A-1", "u-1", null, new BigDecimal("15000.50"));
    }

    @Test
    void eventoCompletoEsValido() {
        assertThat(valido().esValido()).isTrue();
        assertThat(evento("evt-1", null, "res-1", "A-1", "u-1", null, BigDecimal.ONE).esValido()).isTrue();
    }

    @Test
    void camposObligatoriosAusentesOFueraDeLargoLoInvalidan() {
        assertThat(evento(null, null, "res-1", "A-1", "u-1", null, BigDecimal.ONE).esValido()).isFalse();
        assertThat(evento("evt-1", "x".repeat(81), "res-1", "A-1", "u-1", null, BigDecimal.ONE).esValido()).isFalse();
        assertThat(evento("evt-1", null, " ", "A-1", "u-1", null, BigDecimal.ONE).esValido()).isFalse();
        assertThat(evento("evt-1", null, "res-1", "A".repeat(65), "u-1", null, BigDecimal.ONE).esValido()).isFalse();
        assertThat(evento("evt-1", null, "res-1", "A-1", null, null, BigDecimal.ONE).esValido()).isFalse();
        assertThat(evento("evt-1", null, "res-1", "A-1", " ", null, BigDecimal.ONE).esValido()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"-1", "12345678901", "1.001"})
    void montoNegativoODeMasPrecisionEsInvalido(String monto) {
        assertThat(evento("evt-1", null, "res-1", "A-1", "u-1", null, new BigDecimal(monto)).esValido()).isFalse();
    }

    @Test
    void montoCeroEsValido() {
        // Solo se rechazan negativos: una reserva de espacio sin costo genera un gasto en 0.
        assertThat(evento("evt-1", null, "res-1", "A-1", "u-1", null, BigDecimal.ZERO).esValido()).isTrue();
    }

    @Test
    void montoAusenteEsInvalido() {
        assertThat(evento("evt-1", null, "res-1", "A-1", "u-1", null, null).esValido()).isFalse();
    }

    @Test
    void conceptoPorDefectoYRecortado() {
        assertThat(valido().conceptoOPorDefecto()).isEqualTo("Reserva Espacio");
        assertThat(evento("e", null, "r", "u", "s", "  ", BigDecimal.ONE).conceptoOPorDefecto()).isEqualTo("Reserva Espacio");
        assertThat(evento("e", null, "r", "u", "s", "Quincho", BigDecimal.ONE).conceptoOPorDefecto()).isEqualTo("Quincho");
        assertThat(evento("e", null, "r", "u", "s", "x".repeat(250), BigDecimal.ONE).conceptoOPorDefecto()).hasSize(200);
    }

    @ParameterizedTest
    @CsvSource({
        "2026-10-07T10:00:00Z, 2026-10-07T10:00:00Z",
        "2026-10-07T10:00:00, 2026-10-07T10:00:00Z",
        "2026-10-07T10:00:00.123456, 2026-10-07T10:00:00.123456Z"
    })
    void timestampAceptaIsoConYSinZona(String entrada, String esperado) throws Exception {
        String json = "{\"event_id\":\"e\",\"timestamp\":\"" + entrada + "\"}";
        assertThat(mapper.readValue(json, ReservaEspacioCreadaEvent.class).timestamp()).isEqualTo(Instant.parse(esperado));
    }

    @Test
    void timestampVacioEsNulo() throws Exception {
        assertThat(mapper.readValue("{\"timestamp\":\"\"}", ReservaEspacioCreadaEvent.class).timestamp()).isNull();
    }
}

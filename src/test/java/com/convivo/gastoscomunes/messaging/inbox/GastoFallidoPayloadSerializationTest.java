package com.convivo.gastoscomunes.messaging.inbox;

import com.convivo.gastoscomunes.messaging.inbox.dto.GastoFallidoPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GastoFallidoPayloadSerializationTest {
    @Test
    void serializaConClaveReservaIdSnakeCase() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        GastoFallidoPayload payload = GastoFallidoPayload.de("101", "error_unidad");
        String json = mapper.writeValueAsString(payload);
        assertThat(json).contains("\"reserva_id\":\"101\"");
    }
}

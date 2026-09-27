package com.convivo.gastoscomunes.messaging.inbox.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Deserializador tolerante para Instant que acepta formatos ISO-8601 estándar con o sin sufijo UTC.
 */
public class FlexibleInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(text).atZone(ZoneOffset.UTC).toInstant();
            } catch (Exception ex) {
                return Instant.parse(text + "Z");
            }
        }
    }
}

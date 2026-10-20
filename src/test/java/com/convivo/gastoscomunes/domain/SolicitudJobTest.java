package com.convivo.gastoscomunes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SolicitudJobTest {

    @Test
    @DisplayName("crear y acceder campos de SolicitudJob")
    void testCreacionYGetters() {
        Instant now = Instant.now();
        SolicitudJob job = new SolicitudJob(
                "ticket-1",
                "GASTOS",
                "DESCARGAR_PDF",
                "user-1",
                "PENDIENTE",
                null,
                null,
                now,
                now);

        assertThat(job.getTicketId()).isEqualTo("ticket-1");
        assertThat(job.getModulo()).isEqualTo("GASTOS");
        assertThat(job.getAccion()).isEqualTo("DESCARGAR_PDF");
        assertThat(job.getUsuarioId()).isEqualTo("user-1");
        assertThat(job.getEstado()).isEqualTo("PENDIENTE");
        assertThat(job.getResultado()).isNull();
        assertThat(job.getError()).isNull();
        assertThat(job.getCreadoEn()).isEqualTo(now);
        assertThat(job.getActualizadoEn()).isEqualTo(now);
    }

    @Test
    @DisplayName("campos Instant deben declarar explícitamente JdbcTypeCode TIMESTAMP para compatibilidad con Oracle")
    void testJdbcTypeCodeTimestampEnInstants() throws NoSuchFieldException {
        Field creadoEnField = SolicitudJob.class.getDeclaredField("creadoEn");
        JdbcTypeCode creadoEnAnnotation = creadoEnField.getAnnotation(JdbcTypeCode.class);
        assertThat(creadoEnAnnotation)
                .as("creadoEn debe tener @JdbcTypeCode")
                .isNotNull();
        assertThat(creadoEnAnnotation.value()).isEqualTo(SqlTypes.TIMESTAMP);

        Field actualizadoEnField = SolicitudJob.class.getDeclaredField("actualizadoEn");
        JdbcTypeCode actualizadoEnAnnotation = actualizadoEnField.getAnnotation(JdbcTypeCode.class);
        assertThat(actualizadoEnAnnotation)
                .as("actualizadoEn debe tener @JdbcTypeCode")
                .isNotNull();
        assertThat(actualizadoEnAnnotation.value()).isEqualTo(SqlTypes.TIMESTAMP);
    }
}

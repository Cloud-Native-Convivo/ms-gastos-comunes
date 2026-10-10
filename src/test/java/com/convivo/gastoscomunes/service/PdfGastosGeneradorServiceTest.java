package com.convivo.gastoscomunes.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class PdfGastosGeneradorServiceTest {

    private PdfGastosGeneradorService generador;
    private PdfStorageService storage;

    @BeforeEach
    void setUp() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);

        storage = new PdfStorageService();
        generador = new PdfGastosGeneradorService(engine, storage);
    }

    @Test
    void debeGenerarDocumentoPdfValidoDesdePlantillaThymeleaf() {
        byte[] pdfBytes = generador.generarComprobante(
                "ticket-test-1",
                "Unidad 301 — Torre A",
                "Agosto 2026",
                List.of(
                        Map.of("concepto", "Portería y Seguridad", "monto", "180.000"),
                        Map.of("concepto", "Aseo y Mantenimiento", "monto", "120.000"),
                        Map.of("concepto", "Fondo de Reserva", "monto", "50.000")
                ),
                "350.000",
                "Al día"
        );

        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 5)).isEqualTo("%PDF-");
        assertThat(storage.obtenerPdf("ticket-test-1")).isPresent();
        assertThat(storage.obtenerPdf("ticket-test-1").get()).isEqualTo(pdfBytes);
    }
}

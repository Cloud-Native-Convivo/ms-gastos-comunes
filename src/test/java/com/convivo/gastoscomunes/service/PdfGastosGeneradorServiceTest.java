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
    void debeGenerarDocumentoPdfValidoDesdePlantillaThymeleaf() throws Exception {
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
        assertThat(textoDe(pdfBytes)).contains("Portería y Seguridad", "$350.000", "Unidad 301", "Agosto 2026", "ticket-test-1");
        assertThat(new String(pdfBytes, 0, 5)).isEqualTo("%PDF-");
        assertThat(storage.obtenerPdf("ticket-test-1")).isPresent();
        assertThat(storage.obtenerPdf("ticket-test-1").get()).isEqualTo(pdfBytes);
    }

    @Test
    void elDesgloseMuestraElPorcentajeDeCadaConcepto() throws Exception {
        byte[] pdf = generador.generarComprobante(
                "t-pct", "Unidad Casa M202", "Noviembre 2026",
                List.of(
                        Map.of("concepto", "Gasto común ordinario", "monto", "78.000", "pct", "75"),
                        Map.of("concepto", "Fondo de reserva", "monto", "26.000", "pct", "25")),
                "104.000", "Pendiente");

        assertThat(textoDe(pdf)).contains("75% del total", "25% del total", "$104.000", "Pendiente");
    }

    @Test
    void sinGastosElComprobanteLoDiceYNoMuestraDesglose() throws Exception {
        byte[] pdf = generador.generarComprobante(
                "t-vacio", "Unidad Casa M202", "Noviembre 2026", List.of(), "0", "Sin gastos registrados");

        assertThat(textoDe(pdf))
                .contains("No hay gastos registrados para este período.", "$0", "Sin gastos registrados")
                .doesNotContain("% del total");
    }

    private static String textoDe(byte[] pdf) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument documento = org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(documento);
        }
    }
}

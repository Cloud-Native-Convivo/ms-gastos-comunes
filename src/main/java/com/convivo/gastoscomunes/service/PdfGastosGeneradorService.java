package com.convivo.gastoscomunes.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
public class PdfGastosGeneradorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGastosGeneradorService.class);

    private final SpringTemplateEngine templateEngine;
    private final PdfStorageService pdfStorageService;

    public PdfGastosGeneradorService(SpringTemplateEngine templateEngine, PdfStorageService pdfStorageService) {
        this.templateEngine = templateEngine;
        this.pdfStorageService = pdfStorageService;
    }

    public byte[] generarComprobante(
            String ticketId,
            String unidad,
            String mes,
            List<Map<String, String>> items,
            String total,
            String estado) {
        Context context = new Context();
        context.setVariable("ticketId", ticketId);
        context.setVariable("unidad", unidad != null ? unidad : "Sin unidad asignada");
        context.setVariable("mes", mes != null ? mes : "Período actual");
        context.setVariable("items", items != null ? items : List.of());
        context.setVariable("total", total != null ? total : "0");
        context.setVariable("estado", estado != null ? estado : "Pendiente");

        String html = templateEngine.process("comprobante-gastos", context);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();

            byte[] pdfBytes = os.toByteArray();
            pdfStorageService.guardarPdf(ticketId, pdfBytes);
            log.info("PDF generado exitosamente para ticketId={}, tamano={} bytes", ticketId, pdfBytes.length);
            return pdfBytes;
        } catch (Exception e) {
            log.error("Fallo al generar PDF con OpenHTMLtoPDF para ticketId={}", ticketId, e);
            throw new RuntimeException("Error al generar PDF de gastos comunes: " + e.getMessage(), e);
        }
    }
}

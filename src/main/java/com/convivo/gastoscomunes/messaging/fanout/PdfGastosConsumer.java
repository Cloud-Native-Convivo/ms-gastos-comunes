package com.convivo.gastoscomunes.messaging.fanout;

import com.convivo.gastoscomunes.domain.EstadoGasto;
import com.convivo.gastoscomunes.domain.GastoComun;
import com.convivo.gastoscomunes.domain.SolicitudJob;
import com.convivo.gastoscomunes.domain.SolicitudJobRepository;
import com.convivo.gastoscomunes.repository.GastoComunRepository;
import com.convivo.gastoscomunes.service.PdfGastosGeneradorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Genera el comprobante PDF de gastos comunes de una unidad para un período (mes).
 * Todo lo que el PDF muestra sale de los gastos registrados de esa unidad en ese mes:
 * sin gastos, el comprobante lo dice y no inventa un desglose.
 */
@Component
public class PdfGastosConsumer {

    private static final Logger log = LoggerFactory.getLogger(PdfGastosConsumer.class);

    private static final List<String> MESES = List.of(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre");

    /** Clave de unidad con que ms-espacios-comunes registra los gastos de una reserva: {@code unidad-<sub del usuario>}. */
    static final String PREFIJO_UNIDAD_RESERVA = "unidad-";

    /** Tope de gastos leídos por unidad; alto a propósito para no truncar el período en silencio. */
    private static final int MAX_GASTOS_LEIDOS = 500;

    /**
     * El período de un gasto es el AAAA-MM de su fecha de creación en UTC: es el mismo criterio con
     * que el frontend agrupa los gastos por mes, para que el PDF coincida con lo que el residente ve.
     */
    private static final ZoneId ZONA_PERIODO = ZoneOffset.UTC;

    private static final ZoneId ZONA_CHILE = ZoneId.of("America/Santiago");

    private final SolicitudJobRepository solicitudJobRepository;
    private final GastoComunRepository gastoComunRepository;
    private final PdfGastosGeneradorService pdfGastosGeneradorService;
    private final ObjectMapper objectMapper;

    public PdfGastosConsumer(
            SolicitudJobRepository solicitudJobRepository,
            GastoComunRepository gastoComunRepository,
            PdfGastosGeneradorService pdfGastosGeneradorService,
            ObjectMapper objectMapper) {
        this.solicitudJobRepository = solicitudJobRepository;
        this.gastoComunRepository = gastoComunRepository;
        this.pdfGastosGeneradorService = pdfGastosGeneradorService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "#{@gastosPdfGeneracionQueue.name}")
    public void recibir(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        JsonNode root;
        try {
            root = objectMapper.readTree(message.getBody());
        } catch (Exception ex) {
            log.error("Fallo al deserializar mensaje en gastos_pdf_generacion_queue. Desviando a DLQ: deliveryTag={}", deliveryTag, ex);
            channel.basicReject(deliveryTag, false);
            return;
        }

        try {
            procesarSolicitudPdf(root);
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("Error no recuperable procesando PDF para mensaje deliveryTag={}. Enviando a DLQ", deliveryTag, ex);
            channel.basicReject(deliveryTag, false);
        }
    }

    public void procesarSolicitudPdf(JsonNode root) {
        String ticketId = root.path("ticket_id").asText();
        String unidadId = root.path("unidad_id").asText("");
        String usuarioId = root.path("usuario_id").asText("residente-anon");

        log.info("Procesando generacion asincrona de PDF para ticketId={}, unidadId={}", ticketId, unidadId);

        SolicitudJob job = solicitudJobRepository.findById(ticketId)
                .orElseGet(() -> new SolicitudJob(
                        ticketId, "GASTOS", "GENERAR_PDF", usuarioId, "PROCESANDO", null, null, Instant.now(), Instant.now()));
        job.setEstado("PROCESANDO");
        job.setActualizadoEn(Instant.now());
        solicitudJobRepository.save(job);

        try {
            if (unidadId.isBlank()) {
                throw new IllegalArgumentException("La solicitud no indica la unidad (unidad_id)");
            }
            YearMonth periodo = resolverPeriodo(root.path("mes").asText(null));
            List<GastoComun> gastos = gastosDelPeriodo(unidadId, root.path("usuario_id").asText(""), periodo);

            NumberFormat clpFormat = NumberFormat.getIntegerInstance(Locale.of("es", "CL"));
            BigDecimal total = BigDecimal.ZERO;
            boolean conSaldoPendiente = false;
            for (GastoComun g : gastos) {
                total = total.add(montoDe(g));
                if (g.getSaldoPendiente() != null && g.getSaldoPendiente().signum() > 0) {
                    conSaldoPendiente = true;
                }
            }
            // El porcentaje alimenta la tira proporcional del comprobante; mismo redondeo que la pantalla de Gastos.
            List<Map<String, String>> items = new ArrayList<>();
            for (GastoComun g : gastos) {
                BigDecimal monto = montoDe(g);
                String pct = total.signum() > 0
                        ? monto.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).toPlainString()
                        : "0";
                items.add(Map.of("concepto", g.getConcepto(), "monto", clpFormat.format(monto), "pct", pct));
            }

            String estado = gastos.isEmpty() ? "Sin gastos registrados" : conSaldoPendiente ? "Pendiente" : "Al día";
            pdfGastosGeneradorService.generarComprobante(
                    ticketId, "Unidad " + unidadId, etiquetaPeriodo(periodo), items, clpFormat.format(total), estado);

            job.setEstado("COMPLETADO");
            job.setResultado(objectMapper.writeValueAsString(Map.of(
                    "ticket_id", ticketId,
                    "download_url", "/api/v1/gastos/pdf/" + ticketId + "/descargar",
                    "estado", "COMPLETADO")));
            job.setActualizadoEn(Instant.now());
            solicitudJobRepository.save(job);

            log.info("Job ticketId={} completado exitosamente y PDF listo para descarga", ticketId);
        } catch (Exception ex) {
            log.error("Fallo durante generacion del PDF para ticketId={}", ticketId, ex);
            job.setEstado("FALLIDO");
            job.setError("{\"error\":\"" + ex.getMessage() + "\"}");
            job.setActualizadoEn(Instant.now());
            solicitudJobRepository.save(job);
            throw new RuntimeException("Error en worker de PDF: " + ex.getMessage(), ex);
        }
    }

    private static BigDecimal montoDe(GastoComun g) {
        return g.getMonto() != null ? g.getMonto() : BigDecimal.ZERO;
    }

    /**
     * Gastos vigentes del residente cuya fecha de creación cae en el período; los eliminados no cuentan.
     * Se buscan por la unidad del perfil y también por la clave con que nacen los gastos de reserva
     * ({@value #PREFIJO_UNIDAD_RESERVA}{@code <sub>}, ver ms-espacios-comunes), si no, el comprobante
     * dejaría fuera lo que el residente generó reservando.
     */
    private List<GastoComun> gastosDelPeriodo(String unidadId, String usuarioSub, YearMonth periodo) {
        List<GastoComun> candidatos = new ArrayList<>(leerGastos(unidadId));
        String claveReservas = PREFIJO_UNIDAD_RESERVA + usuarioSub;
        if (!usuarioSub.isBlank() && !claveReservas.equals(unidadId)) {
            candidatos.addAll(leerGastos(claveReservas));
        }
        return candidatos.stream()
                .filter(g -> g.getEstado() != EstadoGasto.ELIMINADO)
                .filter(g -> g.getFechaCreacion() != null
                        && YearMonth.from(g.getFechaCreacion().atZone(ZONA_PERIODO)).equals(periodo))
                .sorted(Comparator.comparing(GastoComun::getFechaCreacion))
                .toList();
    }

    private List<GastoComun> leerGastos(String unidadId) {
        Page<GastoComun> pagina = gastoComunRepository.findByUnidadId(unidadId, PageRequest.of(0, MAX_GASTOS_LEIDOS));
        return pagina == null ? List.of() : pagina.getContent();
    }

    /** Interpreta "Noviembre 2026"; sin período válido usa el mes actual en Chile, nunca uno fijo. */
    static YearMonth resolverPeriodo(String mes) {
        if (mes != null) {
            String[] partes = mes.trim().toLowerCase(Locale.ROOT).split("\\s+");
            int indice = partes.length == 2 ? MESES.indexOf(partes[0]) : -1;
            if (indice >= 0) {
                try {
                    return YearMonth.of(Integer.parseInt(partes[1]), indice + 1);
                } catch (RuntimeException ignorado) {
                    // año no numérico o fuera de rango: se cae al mes actual
                }
            }
        }
        return YearMonth.now(ZONA_CHILE);
    }

    static String etiquetaPeriodo(YearMonth periodo) {
        String mes = MESES.get(periodo.getMonthValue() - 1);
        return Character.toUpperCase(mes.charAt(0)) + mes.substring(1) + " " + periodo.getYear();
    }
}

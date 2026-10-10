package com.convivo.gastoscomunes.messaging.command;

import com.convivo.gastoscomunes.domain.GastoComun;
import com.convivo.gastoscomunes.domain.Pago;
import com.convivo.gastoscomunes.domain.SolicitudJob;
import com.convivo.gastoscomunes.domain.SolicitudJobRepository;
import com.convivo.gastoscomunes.dto.GastoComunRequest;
import com.convivo.gastoscomunes.dto.PagoRequest;
import com.convivo.gastoscomunes.security.UsuarioContexto;
import com.convivo.gastoscomunes.service.GastoComunService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ComandoGastosConsumer {

    private static final Logger log = LoggerFactory.getLogger(ComandoGastosConsumer.class);

    private final SolicitudJobRepository solicitudJobRepository;
    private final GastoComunService gastoComunService;
    private final ObjectMapper objectMapper;

    public ComandoGastosConsumer(
            SolicitudJobRepository solicitudJobRepository,
            GastoComunService gastoComunService,
            ObjectMapper objectMapper) {
        this.solicitudJobRepository = solicitudJobRepository;
        this.gastoComunService = gastoComunService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "#{@gastosComandosQueue.name}")
    public void recibir(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        JsonNode root;
        try {
            root = objectMapper.readTree(message.getBody());
        } catch (Exception ex) {
            log.error("Fallo al deserializar comando en gastos_comandos_queue. Rechazando: deliveryTag={}", deliveryTag, ex);
            channel.basicReject(deliveryTag, false);
            return;
        }

        procesarComando(root);
        channel.basicAck(deliveryTag, false);
    }

    public void procesarComando(JsonNode root) {
        String ticketId = root.path("ticket_id").asText();
        String modulo = root.path("modulo").asText("GASTOS");
        String accion = root.path("accion").asText();
        String usuarioId = root.path("usuario_id").asText();
        String rol = root.path("rol").asText("administrador");
        JsonNode payload = root.path("payload");

        SolicitudJob job = solicitudJobRepository.findById(ticketId)
                .orElseGet(() -> new SolicitudJob(
                        ticketId, modulo, accion, usuarioId, "PROCESANDO", null, null, Instant.now(), Instant.now()));
        job.setEstado("PROCESANDO");
        job = solicitudJobRepository.save(job);

        try {
            switch (accion) {
                case "REGISTRAR_GASTO" -> {
                    String unidadId = payload.has("unidad_id") ? payload.get("unidad_id").asText() : payload.path("unidadId").asText(null);
                    String concepto = payload.path("concepto").asText(null);
                    BigDecimal monto = payload.has("monto") ? new BigDecimal(payload.get("monto").asText()) : BigDecimal.ZERO;
                    LocalDate fechaVencimiento = null;
                    String fechaStr = payload.has("fecha_vencimiento") ? payload.get("fecha_vencimiento").asText() : payload.path("fechaVencimiento").asText(null);
                    if (fechaStr != null && !fechaStr.isBlank()) {
                        fechaVencimiento = LocalDate.parse(fechaStr);
                    }
                    GastoComunRequest req = new GastoComunRequest(unidadId, concepto, monto, fechaVencimiento);
                    GastoComun creado = gastoComunService.crear(req);
                    job.setEstado("COMPLETADO");
                    job.setResultado(objectMapper.writeValueAsString(Map.of(
                            "id", creado.getId(),
                            "unidad_id", creado.getUnidadId(),
                            "concepto", creado.getConcepto(),
                            "monto", creado.getMonto())));
                }
                case "ACTUALIZAR_GASTO" -> {
                    Long id = payload.has("id") ? payload.get("id").asLong() : payload.path("gasto_id").asLong();
                    String unidadId = payload.has("unidad_id") ? payload.get("unidad_id").asText() : payload.path("unidadId").asText(null);
                    String concepto = payload.path("concepto").asText(null);
                    BigDecimal monto = payload.has("monto") ? new BigDecimal(payload.get("monto").asText()) : BigDecimal.ZERO;
                    LocalDate fechaVencimiento = null;
                    String fechaStr = payload.has("fecha_vencimiento") ? payload.get("fecha_vencimiento").asText() : payload.path("fechaVencimiento").asText(null);
                    if (fechaStr != null && !fechaStr.isBlank()) {
                        fechaVencimiento = LocalDate.parse(fechaStr);
                    }
                    GastoComunRequest req = new GastoComunRequest(unidadId, concepto, monto, fechaVencimiento);
                    GastoComun actualizado = gastoComunService.actualizar(id, req);
                    job.setEstado("COMPLETADO");
                    job.setResultado(objectMapper.writeValueAsString(Map.of(
                            "id", actualizado.getId(),
                            "concepto", actualizado.getConcepto())));
                }
                case "ELIMINAR_GASTO" -> {
                    Long id = payload.has("id") ? payload.get("id").asLong() : payload.path("gasto_id").asLong();
                    gastoComunService.eliminar(id);
                    job.setEstado("COMPLETADO");
                    job.setResultado(objectMapper.writeValueAsString(Map.of("id", id, "eliminado", true)));
                }
                case "REGISTRAR_PAGO" -> {
                    Long gastoId = payload.has("gasto_id") ? payload.get("gasto_id").asLong() : payload.path("gastoId").asLong();
                    BigDecimal monto = payload.has("monto") ? new BigDecimal(payload.get("monto").asText()) : BigDecimal.ZERO;
                    String metodoStr = payload.has("metodo") ? payload.get("metodo").asText() : payload.path("metodo_pago").asText("TRANSFERENCIA");
                    com.convivo.gastoscomunes.domain.MetodoPago metodo = com.convivo.gastoscomunes.domain.MetodoPago.valueOf(metodoStr.toUpperCase());
                    String comprobante = payload.path("comprobante").asText(null);
                    PagoRequest req = new PagoRequest(monto, metodo, comprobante);
                    UsuarioContexto uc = new UsuarioContexto();
                    uc.setUsuarioSub(usuarioId);
                    uc.setRoles(Set.of("administrador"));
                    Pago pago = gastoComunService.registrarPago(gastoId, req, uc);
                    job.setEstado("COMPLETADO");
                    job.setResultado(objectMapper.writeValueAsString(Map.of("id", pago.getId(), "monto", pago.getMonto())));
                }

                default -> throw new IllegalArgumentException("Acción no soportada: " + accion);
            }
        } catch (Exception ex) {
            log.error("Fallo al procesar comando de mutacion {}: {}", accion, ex.getMessage());
            job.setEstado("FALLIDO");
            try {
                job.setError(objectMapper.writeValueAsString(Map.of(
                        "codigo", "ERROR_PROCESAMIENTO",
                        "mensaje", ex.getMessage() != null ? ex.getMessage() : "Error desconocido")));
            } catch (Exception eJson) {
                job.setError("{\"codigo\":\"ERROR_PROCESAMIENTO\"}");
            }
        }
        solicitudJobRepository.save(job);
    }
}

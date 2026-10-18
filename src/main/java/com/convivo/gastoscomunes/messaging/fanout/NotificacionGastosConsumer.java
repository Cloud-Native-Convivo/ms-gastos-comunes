package com.convivo.gastoscomunes.messaging.fanout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class NotificacionGastosConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionGastosConsumer.class);

    private final ObjectMapper objectMapper;
    private final EmailEnviadoRepository emailEnviadoRepository;

    public NotificacionGastosConsumer(ObjectMapper objectMapper, EmailEnviadoRepository emailEnviadoRepository) {
        this.objectMapper = objectMapper;
        this.emailEnviadoRepository = emailEnviadoRepository;
    }

    @RabbitListener(queues = "#{@gastosNotificacionesEmailQueue.name}")
    public void recibir(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        JsonNode root;
        try {
            root = objectMapper.readTree(message.getBody());
        } catch (Exception ex) {
            log.error("Fallo al deserializar mensaje en gastos_notificaciones_email_queue. Rechazando a DLQ: deliveryTag={}", deliveryTag, ex);
            channel.basicReject(deliveryTag, false);
            return;
        }

        String ticketId = root.path("ticket_id").asText();
        if (ticketId.isBlank()) {
            log.info("Notificacion de email sin ticket_id, se descarta");
            channel.basicAck(deliveryTag, false);
            return;
        }

        // Reclamar: INSERT inmediato (saveAndFlush, no save) ANTES de enviar
        // el correo. Si dos copias llegan casi juntas, la segunda choca con
        // la llave primaria aquí mismo — nunca después de haber "enviado"
        // (esa es la carrera que el prototipo documentó en idempotencia.py).
        try {
            emailEnviadoRepository.saveAndFlush(new EmailEnviado(ticketId));
        } catch (DataIntegrityViolationException ex) {
            log.info("Notificacion de email duplicada, ya reservada: ticketId='{}'", ticketId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            procesarNotificacion(root);
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            // Liberar: borra la reserva para que una reentrega real (si
            // llega) pueda reintentar el envío en vez de descartarlo como
            // "ya enviado".
            emailEnviadoRepository.deleteById(ticketId);
            log.error("Fallo al despachar notificacion de email para ticketId='{}'. Enviando a DLQ", ticketId, ex);
            channel.basicReject(deliveryTag, false);
        }
    }

    public void procesarNotificacion(JsonNode root) {
        String ticketId = root.path("ticket_id").asText();
        String email = root.has("email") ? root.path("email").asText() : "residente@convivo.cl";
        String unidadId = root.path("unidad_id").asText("301-A");

        log.info("Despachando notificacion por correo a '{}' para ticketId='{}' (Unidad '{}'). Comprobante generado en proceso.",
                email, ticketId, unidadId);
    }
}

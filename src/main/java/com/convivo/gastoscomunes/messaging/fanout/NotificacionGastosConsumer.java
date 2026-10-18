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

        boolean redelivered = message.getMessageProperties().isRedelivered();
        EmailEnviado reserva = reclamar(ticketId, redelivered);
        if (reserva == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            procesarNotificacion(root);
            reserva.completar();
            emailEnviadoRepository.save(reserva);
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            // No se borra: la fila queda EN_CURSO para que una reentrega
            // real de RabbitMQ (redelivered=true) pueda retomarla en vez de
            // perder el correo para siempre. Sin job de reconciliacion: si
            // nunca llega esa reentrega, el envio se pierde (riesgo aceptado).
            log.error("Fallo al despachar notificacion de email para ticketId='{}'. Enviando a DLQ, queda EN_CURSO", ticketId, ex);
            channel.basicReject(deliveryTag, false);
        }
    }

    /**
     * Reclama el ticket para procesarlo. Devuelve la fila a completar, o
     * {@code null} si hay que descartar el mensaje sin despachar: ya está
     * COMPLETADO, está EN_CURSO por otra copia concurrente (sin reentrega),
     * o perdió la carrera de inserción contra otra copia casi simultánea.
     * Si está EN_CURSO y el mensaje llegó marcado {@code redelivered}, se
     * retoma (el proceso que la reservó pudo haber muerto antes de completar).
     */
    private EmailEnviado reclamar(String ticketId, boolean redelivered) {
        EmailEnviado existente = emailEnviadoRepository.findById(ticketId).orElse(null);
        if (existente != null) {
            if (existente.getEstado() == EstadoNotificacion.COMPLETADO) {
                log.info("Notificacion de email ya enviada, se descarta: ticketId='{}'", ticketId);
                return null;
            }
            if (!redelivered) {
                log.info("Notificacion de email duplicada, ya en curso: ticketId='{}'", ticketId);
                return null;
            }
            log.warn("Notificacion de email reentregada mientras estaba EN_CURSO, se retoma: ticketId='{}'", ticketId);
            return existente;
        }

        try {
            return emailEnviadoRepository.saveAndFlush(new EmailEnviado(ticketId));
        } catch (DataIntegrityViolationException ex) {
            log.info("Notificacion de email duplicada, perdio la carrera de insercion: ticketId='{}'", ticketId);
            return null;
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

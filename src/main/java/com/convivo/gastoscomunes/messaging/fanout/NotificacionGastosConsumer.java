package com.convivo.gastoscomunes.messaging.fanout;

import com.convivo.gastoscomunes.config.MailProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class NotificacionGastosConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionGastosConsumer.class);

    private final ObjectMapper objectMapper;
    private final EmailEnviadoRepository emailEnviadoRepository;
    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public NotificacionGastosConsumer(
            ObjectMapper objectMapper,
            EmailEnviadoRepository emailEnviadoRepository,
            JavaMailSender mailSender,
            MailProperties mailProperties) {
        this.objectMapper = objectMapper;
        this.emailEnviadoRepository = emailEnviadoRepository;
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
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

        // isRedelivered() devuelve Boolean (nullable): sin broker real detras,
        // un Message armado a mano puede no traer el flag seteado.
        boolean redelivered = Boolean.TRUE.equals(message.getMessageProperties().isRedelivered());
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
        String mes = root.has("mes") ? root.path("mes").asText() : null;

        if (mailProperties.habilitado()) {
            mailSender.send(construirCorreo(email, ticketId, unidadId, mes));
        }

        log.info("Notificacion de email despachada a '{}' para ticketId='{}' (Unidad '{}').", email, ticketId, unidadId);
    }

    private MimeMessage construirCorreo(String destinatario, String ticketId, String unidadId, String mes) {
        String periodo = mes == null || mes.isBlank() ? "" : mes;
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom(mailProperties.remitente(), "Convivo");
            helper.setTo(destinatario);
            helper.setSubject("Tu comprobante de gastos comunes está en proceso de generación");
            helper.setText(textoPlano(unidadId, periodo, ticketId), html(unidadId, periodo, ticketId));
            return mensaje;
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new MailPreparationException("No se pudo armar el correo de notificación", ex);
        }
    }

    private static String textoPlano(String unidadId, String periodo, String ticketId) {
        return """
                Hola,

                Tu solicitud de comprobante de gastos comunes de la unidad %s%s se encuentra en proceso de generación.

                Podrás descargarlo desde el portal Convivo en unos minutos.

                Ticket: %s

                Equipo Convivo
                """.formatted(unidadId, periodo.isEmpty() ? "" : " (" + periodo + ")", ticketId);
    }

    private static String html(String unidadId, String periodo, String ticketId) {
        String filas = fila("Unidad", unidadId)
                + (periodo.isEmpty() ? "" : fila("Período", periodo))
                + fila("Ticket", ticketId);
        return """
                <!DOCTYPE html>
                <html lang="es">
                <body style="margin:0;padding:0;background:#f1f5f9;font-family:Arial,Helvetica,sans-serif;color:#0f172a;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f1f5f9;padding:24px 12px;">
                <tr><td align="center">
                  <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%%;background:#ffffff;border:1px solid #e2e8f0;border-radius:12px;">
                    <tr><td style="background:#0f766e;padding:24px 32px;border-radius:12px 12px 0 0;">
                      <span style="font-family:Georgia,'Times New Roman',serif;font-size:26px;color:#ffffff;">Convivo</span>
                    </td></tr>
                    <tr><td style="padding:32px;">
                      <h1 style="margin:0 0 16px;font-family:Georgia,'Times New Roman',serif;font-size:22px;font-weight:normal;color:#0f172a;">Tu comprobante está en proceso</h1>
                      <p style="margin:0 0 12px;font-size:15px;line-height:1.6;color:#334155;">Hola,</p>
                      <p style="margin:0 0 24px;font-size:15px;line-height:1.6;color:#334155;">Tu solicitud de comprobante de gastos comunes se encuentra en proceso de generación. Podrás descargarlo desde el portal Convivo en unos minutos.</p>
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:8px 16px;">
                        %s
                      </table>
                    </td></tr>
                    <tr><td style="padding:16px 32px 24px;border-top:1px solid #e2e8f0;font-size:12px;line-height:1.5;color:#64748b;">
                      Equipo Convivo &middot; Este es un mensaje automático, por favor no respondas a este correo.
                    </td></tr>
                  </table>
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(filas);
    }

    private static String fila(String etiqueta, String valor) {
        return "<tr><td style=\"padding:8px 0;font-size:13px;color:#64748b;width:90px;\">" + etiqueta
                + "</td><td style=\"padding:8px 0;font-size:14px;color:#0f172a;font-weight:bold;\">"
                + HtmlUtils.htmlEscape(valor) + "</td></tr>";
    }
}

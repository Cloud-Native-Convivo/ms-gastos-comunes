package com.convivo.gastoscomunes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configura el envío real de la notificación por correo (ver
 * {@code NotificacionGastosConsumer}). {@code habilitado} queda en
 * {@code false} por defecto (perfil local no manda correo real, solo loguea)
 * y se activa explícitamente en el perfil {@code aws}.
 */
@ConfigurationProperties(prefix = "convivo.mail")
public record MailProperties(boolean habilitado, String remitente) {

    public MailProperties {
        remitente = (remitente == null || remitente.isBlank()) ? "no-reply@convivo.cl" : remitente;
    }
}

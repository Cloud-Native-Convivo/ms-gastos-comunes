-- La fila de notificaciones_email_enviadas ya no se borra al fallar el envio
-- (ver NotificacionGastosConsumer): queda EN_CURSO hasta completarse, para
-- que una reentrega real de RabbitMQ pueda retomarla en vez de perder el
-- correo para siempre.

ALTER TABLE notificaciones_email_enviadas
    ADD estado VARCHAR2(20) DEFAULT 'EN_CURSO' NOT NULL;

-- Idempotencia de gastos_notificaciones_email_queue: un mismo ticket_id no
-- debe disparar dos correos (reentrega at-least-once de RabbitMQ).

CREATE TABLE notificaciones_email_enviadas (
    ticket_id        VARCHAR2(64 CHAR)  NOT NULL,
    fecha_enviado    TIMESTAMP          NOT NULL,
    PRIMARY KEY (ticket_id)
);

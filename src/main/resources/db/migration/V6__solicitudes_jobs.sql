-- Jobs asincronos despachados por el BFF via RabbitMQ (ComandoGastosConsumer):
-- faltaba esta tabla, aunque la entidad SolicitudJob ya existia -- con
-- ddl-auto=validate en el perfil aws, el arranque fallaba por esquema
-- incompleto.

CREATE TABLE solicitudes_jobs (
    ticket_id       VARCHAR2(64)       PRIMARY KEY,
    modulo          VARCHAR2(30)       NOT NULL,
    accion          VARCHAR2(50)       NOT NULL,
    usuario_id      VARCHAR2(100 CHAR) NOT NULL,
    estado          VARCHAR2(20)       NOT NULL,
    resultado       CLOB,
    error           CLOB,
    creado_en       TIMESTAMP          NOT NULL,
    actualizado_en  TIMESTAMP          NOT NULL
);

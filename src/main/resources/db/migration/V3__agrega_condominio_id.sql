-- ms-gastos-comunes: agrega condominio_id para filtrar gastos por condominio
-- (multi-tenancy, TD-19).
--
-- String libre VARCHAR2(64 CHAR), mismo patron que unidad_id: sin FK a una
-- tabla de condominios local (ms-condominios es otro microservicio). DEFAULT
-- '1' solo para no romper filas ya existentes; el codigo nuevo siempre lo
-- envia explicito.

ALTER TABLE gastos_comunes ADD (
    condominio_id  VARCHAR2(64 CHAR)  DEFAULT '1'  NOT NULL
);

CREATE INDEX idx_gastos_comunes_condominio ON gastos_comunes (condominio_id);

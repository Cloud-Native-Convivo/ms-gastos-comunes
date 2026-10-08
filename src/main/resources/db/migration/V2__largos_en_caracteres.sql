-- ms-gastos-comunes: largos de texto medidos en caracteres, no en bytes.
--
-- V1 declaró VARCHAR2(n) con la semántica por defecto (BYTE). Con charset
-- AL32UTF8, 'á', 'ñ', '°' ocupan 2 bytes: un concepto de 200 caracteres con
-- tildes no cabe en VARCHAR2(200) y falla con ORA-12899, aunque pase las
-- validaciones Java (@Size en los DTOs, ReservaEspacioCreadaEvent), que
-- cuentan caracteres. Ver AGENTS.md §5.1.
--
-- Solo columnas de texto que llega desde afuera (usuarios, BFF, eventos de
-- ms-espacios-comunes). Los enums (estado, origen, metodo) y la
-- configuración interna del Outbox son ASCII y quedan igual.
--
-- Ampliar la semántica no reescribe filas ni pierde datos; MODIFY sin
-- NULL/NOT NULL conserva la restricción existente.

ALTER TABLE gastos_comunes MODIFY (
    unidad_id           VARCHAR2(64 CHAR),
    concepto            VARCHAR2(200 CHAR),
    referencia_externa  VARCHAR2(100 CHAR)
);

ALTER TABLE pagos MODIFY (
    usuario_sub  VARCHAR2(100 CHAR),
    comprobante  VARCHAR2(200 CHAR)
);

ALTER TABLE inbox_eventos MODIFY (
    event_id  VARCHAR2(64 CHAR),
    tipo      VARCHAR2(80 CHAR)
);

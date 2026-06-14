-- Tabla de trazabilidad SAGA coreografiada.
-- Sin columnas de outbox (siguiente_accion, intentos, maximo_intentos)
-- porque el flujo avanza por reacción a eventos Kafka, no por polling.

CREATE TABLE saga_registros (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    asignacion_id   UUID        REFERENCES asignaciones(id),
    estado          VARCHAR(60) NOT NULL,
    vehiculo_id     UUID,
    motivo_fallo    TEXT,
    creada_en       TIMESTAMP   NOT NULL DEFAULT NOW(),
    actualizada_en  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_saga_asignacion ON saga_registros (asignacion_id);

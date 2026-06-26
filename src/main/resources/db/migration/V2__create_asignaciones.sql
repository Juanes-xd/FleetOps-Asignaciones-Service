CREATE TABLE asignaciones (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conductor_id   UUID         NOT NULL REFERENCES conductores(id),
    vehiculo_id    UUID,
    tipo_vehiculo  VARCHAR(100) NOT NULL,
    fecha_inicio   DATE         NOT NULL,
    fecha_fin      DATE         NOT NULL,
    creada_en      TIMESTAMP    NOT NULL DEFAULT NOW()
);


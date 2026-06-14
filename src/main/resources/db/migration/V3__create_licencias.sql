CREATE TABLE licencias (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conductor_id       UUID        NOT NULL REFERENCES conductores(id),
    categoria          VARCHAR(50) NOT NULL,
    fecha_vencimiento  DATE        NOT NULL
);

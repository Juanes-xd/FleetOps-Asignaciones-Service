CREATE TABLE conductores (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre        VARCHAR(200) NOT NULL,
    email         VARCHAR(200) NOT NULL UNIQUE,
    tipo_vehiculo VARCHAR(100) NOT NULL,
    estado        VARCHAR(50)  NOT NULL
);

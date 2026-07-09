CREATE TABLE conductores (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre        VARCHAR(200) NOT NULL,
    tipo_vehiculo VARCHAR(100) NOT NULL,
    estado        VARCHAR(50)  NOT NULL
);


-- 2. CREAR LOS DATOS (INSERTS)
INSERT INTO conductores (id, nombre, tipo_vehiculo, estado) VALUES
  ('11111111-1111-1111-4111-811111111111', 'Juan Pérez', 'CAMION', 'DISPONIBLE'),
  ('22222222-2222-2222-4222-822222222222', 'María Gómez', 'Camioneta', 'DISPONIBLE'),
  ('33333333-3333-3333-4333-833333333333', 'Carlos Sánchez', 'Automovil', 'DISPONIBLE'),
  ('11511111-1111-1111-4111-811111111111', 'Juan Gomez', 'Furgoneta', 'DISPONIBLE'),
  ('22622222-2222-2222-4222-822222222222', 'Julio Gómez', 'Camioneta', 'DISPONIBLE'),
  ('33733333-3333-3333-4333-833333333333', 'Manuel Sánchez', 'Vehiculo_especializado', 'DISPONIBLE'),
  ('33333733-3333-3333-4333-833333333333', 'Cristiano Ronaldo', 'Vehiculo_especializado', 'DISPONIBLE'),
  ('11511911-1111-1111-4111-811111111111', 'Leo Messi', 'Vehiculo_especializado', 'DISPONIBLE'),
  ('22622522-2222-2222-4222-822222222222', 'James Rodriguez', 'Automovil', 'DISPONIBLE'),
  ('33733433-3333-3333-4333-833333333333', 'Luis Diaz', 'Moto', 'DISPONIBLE');
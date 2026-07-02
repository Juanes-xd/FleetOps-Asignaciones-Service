
----Agregamos la columna de kilometros--------
ALTER TABLE asignaciones
ADD COLUMN kilometros INTEGER;
-------Dejamos los KM en 0 para las filas existentes--------
UPDATE asignaciones
SET kilometros = 0
WHERE kilometros IS NULL;
-----------Hacemos que la columna no pueda ser nula----------
ALTER TABLE asignaciones
ALTER COLUMN kilometros SET NOT NULL;
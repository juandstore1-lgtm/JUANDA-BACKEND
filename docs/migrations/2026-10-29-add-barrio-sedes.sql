-- Migración para agregar la columna barrio a sedes
ALTER TABLE sedes ADD COLUMN IF NOT EXISTS barrio VARCHAR(255);

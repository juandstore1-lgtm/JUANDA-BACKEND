-- Reservas temporales de boletos (compra pública).
-- Incremental y compatible: solo añade una columna opcional y un índice. No modifica ni borra datos.
-- Con DDL_AUTO=update, Hibernate añade la columna al arrancar; el índice no. Con validate/none hay que ejecutar
-- este script ANTES de desplegar la nueva versión. No se ejecutó contra ninguna base real durante el desarrollo.

ALTER TABLE tickets ADD COLUMN IF NOT EXISTS reservation_expires_at TIMESTAMP WITH TIME ZONE;

-- Búsqueda de reservas vencidas (tarea programada cada minuto)
CREATE INDEX IF NOT EXISTS idx_tickets_reservation_expiry ON tickets (reservation_expires_at) WHERE status = 'RESERVED';

-- Reservas existentes (RESERVED antes de este cambio): quedan con reservation_expires_at NULL y NO vencen solas.
-- Si el negocio decide que también venzan, por ejemplo 24 h después de este despliegue (decisión pendiente):
--   UPDATE tickets SET reservation_expires_at = now() + interval '24 hours'
--   WHERE status = 'RESERVED' AND reservation_expires_at IS NULL;

-- Reversión (solo si se vuelve a la versión anterior y ya no se usa la columna):
--   DROP INDEX IF EXISTS idx_tickets_reservation_expiry;
--   ALTER TABLE tickets DROP COLUMN IF EXISTS reservation_expires_at;

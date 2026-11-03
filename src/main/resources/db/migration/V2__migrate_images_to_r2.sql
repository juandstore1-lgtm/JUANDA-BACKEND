-- ==============================================================================
-- JUANDSTORE: MIGRACIÓN DE ESQUEMA PARA ALMACENAMIENTO DE IMÁGENES EN CLOUDFLARE R2
-- ==============================================================================
-- 1. Añadir columnas image_url donde no existían previamente
ALTER TABLE categories ADD COLUMN IF NOT EXISTS image_url TEXT;
ALTER TABLE sedes ADD COLUMN IF NOT EXISTS image_url TEXT;

-- 2. Añadir columnas de respaldo legacy_base64 para garantizar CERO pérdida de datos
--    durante la migración por lotes antes de la compactación final
ALTER TABLE product_images ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;
ALTER TABLE sedes ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;
ALTER TABLE raffles ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;
ALTER TABLE hero_slides ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;
ALTER TABLE home_category_collections ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;
ALTER TABLE contests ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;
ALTER TABLE contest_participants ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;
ALTER TABLE outfit_settings ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;

-- ==============================================================================
-- CONSULTAS DE AUDITORÍA Y MONITOREO DE REGISTROS PENDIENTES
-- ==============================================================================
-- SELECT 'product_images' AS tabla, COUNT(*) AS pendientes FROM product_images WHERE (image_url LIKE 'data:image%' OR (image_url NOT LIKE 'http%' AND LENGTH(image_url) > 500))
-- UNION ALL
-- SELECT 'categories', COUNT(*) FROM categories WHERE (image LIKE 'data:image%' OR (image NOT LIKE 'http%' AND LENGTH(image) > 500)) AND (image_url IS NULL OR image_url NOT LIKE 'http%')
-- UNION ALL
-- SELECT 'sedes', COUNT(*) FROM sedes WHERE (image LIKE 'data:image%' OR (image NOT LIKE 'http%' AND LENGTH(image) > 500)) AND (image_url IS NULL OR image_url NOT LIKE 'http%')
-- UNION ALL
-- SELECT 'raffles', COUNT(*) FROM raffles WHERE (image_url LIKE 'data:image%' OR (image_url NOT LIKE 'http%' AND LENGTH(image_url) > 500))
-- UNION ALL
-- SELECT 'hero_slides', COUNT(*) FROM hero_slides WHERE (image_url LIKE 'data:image%' OR (image_url NOT LIKE 'http%' AND LENGTH(image_url) > 500))
-- UNION ALL
-- SELECT 'home_category_collections', COUNT(*) FROM home_category_collections WHERE (image_url LIKE 'data:image%' OR (image_url NOT LIKE 'http%' AND LENGTH(image_url) > 500))
-- UNION ALL
-- SELECT 'contests', COUNT(*) FROM contests WHERE (banner_url LIKE 'data:image%' OR (banner_url NOT LIKE 'http%' AND LENGTH(banner_url) > 500))
-- UNION ALL
-- SELECT 'contest_participants', COUNT(*) FROM contest_participants WHERE (outfit_image_url LIKE 'data:image%' OR (outfit_image_url NOT LIKE 'http%' AND LENGTH(outfit_image_url) > 500))
-- UNION ALL
-- SELECT 'outfit_settings', COUNT(*) FROM outfit_settings WHERE (image_url LIKE 'data:image%' OR (image_url NOT LIKE 'http%' AND LENGTH(image_url) > 500));

-- ==============================================================================
-- LIMPIEZA FINAL POST-MIGRACIÓN Y COMPACTACIÓN DE DISCO
-- (Ejecutar solo tras confirmar estabilidad total en producción)
-- ==============================================================================
-- UPDATE categories SET image = NULL WHERE image_url IS NOT NULL;
-- UPDATE sedes SET image = NULL WHERE image_url IS NOT NULL;
-- ALTER TABLE product_images DROP COLUMN IF EXISTS legacy_base64;
-- ALTER TABLE categories DROP COLUMN IF EXISTS legacy_base64;
-- ALTER TABLE sedes DROP COLUMN IF EXISTS legacy_base64;
-- ALTER TABLE raffles DROP COLUMN IF EXISTS legacy_base64;
-- ALTER TABLE hero_slides DROP COLUMN IF EXISTS legacy_base64;
-- ALTER TABLE home_category_collections DROP COLUMN IF EXISTS legacy_base64;
-- ALTER TABLE contests DROP COLUMN IF EXISTS legacy_base64;
-- ALTER TABLE contest_participants DROP COLUMN IF EXISTS legacy_base64;
-- ALTER TABLE outfit_settings DROP COLUMN IF EXISTS legacy_base64;
-- VACUUM FULL ANALYZE;

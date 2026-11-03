package com.jdqstore.backend.migration;

import com.jdqstore.backend.service.R2StorageService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class R2HistoricalMigrationService {

    private final JdbcTemplate jdbcTemplate;
    private final R2StorageService storageService;

    private static final String ERROR_LOG_FILE = "migration-errors.log";

    public MigrationReport runMigration(boolean dryRun, int batchSize) {
        long startTime = System.currentTimeMillis();
        log.info("=== INICIANDO MIGRACIÓN HISTÓRICA A CLOUDFLARE R2 (dryRun={}, batchSize={}) ===", dryRun, batchSize);

        MigrationReport report = new MigrationReport();
        report.setDryRun(dryRun);
        report.setStartTime(LocalDateTime.now().toString());

        // 1. Migrar product_images
        migrateTable(
                "product_images",
                "SELECT product_id AS id, image_url AS raw_data FROM product_images WHERE (image_url LIKE 'data:image%' OR (image_url NOT LIKE 'http%' AND LENGTH(image_url) > 500)) ORDER BY product_id ASC LIMIT ?",
                "UPDATE product_images SET image_url = ?, legacy_base64 = ? WHERE product_id = ? AND image_url = ?",
                "products",
                batchSize, dryRun, report, true
        );

        // 2. Migrar categories
        migrateTable(
                "categories",
                "SELECT id, image AS raw_data FROM categories WHERE (image LIKE 'data:image%' OR (image NOT LIKE 'http%' AND LENGTH(image) > 500)) AND (image_url IS NULL OR image_url NOT LIKE 'http%') ORDER BY id ASC LIMIT ?",
                "UPDATE categories SET image_url = ?, legacy_base64 = ? WHERE id = ?",
                "categories",
                batchSize, dryRun, report, false
        );

        // 3. Migrar sedes
        migrateTable(
                "sedes",
                "SELECT id, image AS raw_data FROM sedes WHERE (image LIKE 'data:image%' OR (image NOT LIKE 'http%' AND LENGTH(image) > 500)) AND (image_url IS NULL OR image_url NOT LIKE 'http%') ORDER BY id ASC LIMIT ?",
                "UPDATE sedes SET image_url = ?, legacy_base64 = ? WHERE id = ?",
                "sedes",
                batchSize, dryRun, report, false
        );

        // 4. Migrar raffles
        migrateTable(
                "raffles",
                "SELECT id, image_url AS raw_data FROM raffles WHERE (image_url LIKE 'data:image%' OR (image_url NOT LIKE 'http%' AND LENGTH(image_url) > 500)) ORDER BY id ASC LIMIT ?",
                "UPDATE raffles SET image_url = ?, legacy_base64 = ? WHERE id = ?",
                "raffles",
                batchSize, dryRun, report, false
        );

        // 5. Migrar hero_slides
        migrateTable(
                "hero_slides",
                "SELECT id, image_url AS raw_data FROM hero_slides WHERE (image_url LIKE 'data:image%' OR (image_url NOT LIKE 'http%' AND LENGTH(image_url) > 500)) ORDER BY id ASC LIMIT ?",
                "UPDATE hero_slides SET image_url = ?, legacy_base64 = ? WHERE id = ?",
                "hero-slides",
                batchSize, dryRun, report, false
        );

        // 6. Migrar home_category_collections
        migrateTable(
                "home_category_collections",
                "SELECT id, image_url AS raw_data FROM home_category_collections WHERE (image_url LIKE 'data:image%' OR (image_url NOT LIKE 'http%' AND LENGTH(image_url) > 500)) ORDER BY id ASC LIMIT ?",
                "UPDATE home_category_collections SET image_url = ?, legacy_base64 = ? WHERE id = ?",
                "collections",
                batchSize, dryRun, report, false
        );

        // 7. Migrar contests
        migrateTable(
                "contests",
                "SELECT id, banner_url AS raw_data FROM contests WHERE (banner_url LIKE 'data:image%' OR (banner_url NOT LIKE 'http%' AND LENGTH(banner_url) > 500)) ORDER BY id ASC LIMIT ?",
                "UPDATE contests SET banner_url = ?, legacy_base64 = ? WHERE id = ?",
                "contests",
                batchSize, dryRun, report, false
        );

        // 8. Migrar contest_participants
        migrateTable(
                "contest_participants",
                "SELECT id, outfit_image_url AS raw_data FROM contest_participants WHERE (outfit_image_url LIKE 'data:image%' OR (outfit_image_url NOT LIKE 'http%' AND LENGTH(outfit_image_url) > 500)) ORDER BY id ASC LIMIT ?",
                "UPDATE contest_participants SET outfit_image_url = ?, legacy_base64 = ? WHERE id = ?",
                "participants",
                batchSize, dryRun, report, false
        );

        // 9. Migrar outfit_settings
        migrateTable(
                "outfit_settings",
                "SELECT id, image_url AS raw_data FROM outfit_settings WHERE (image_url LIKE 'data:image%' OR (image_url NOT LIKE 'http%' AND LENGTH(image_url) > 500)) ORDER BY id ASC LIMIT ?",
                "UPDATE outfit_settings SET image_url = ?, legacy_base64 = ? WHERE id = ?",
                "outfits",
                batchSize, dryRun, report, false
        );

        long duration = (System.currentTimeMillis() - startTime) / 1000;
        report.setDurationSeconds(duration);
        report.setStatus("COMPLETED");

        log.info("=== FIN DE MIGRACIÓN HISTÓRICA === Duración: {}s | Total procesado: {} | Total migrado: {} | Total fallas: {}",
                duration, report.getTotalProcessed(), report.getTotalMigrated(), report.getTotalFailed());

        return report;
    }

    private void migrateTable(
            String tableName,
            String selectSql,
            String updateSql,
            String entityFolder,
            int batchSize,
            boolean dryRun,
            MigrationReport report,
            boolean isProductImageTable
    ) {
        log.info("Procesando tabla '{}'...", tableName);
        EntityStat stats = new EntityStat();
        report.getByEntity().put(tableName, stats);

        boolean hasMore = true;
        int totalBatches = 0;

        while (hasMore) {
            List<Map<String, Object>> rows;
            try {
                rows = jdbcTemplate.queryForList(selectSql, batchSize);
            } catch (Exception e) {
                log.warn("No se pudo consultar tabla '{}' (¿existe en el schema?): {}", tableName, e.getMessage());
                break;
            }

            if (rows.isEmpty()) {
                break;
            }

            totalBatches++;
            log.info("Lote #{} para tabla '{}': {} registros encontrados", totalBatches, tableName, rows.size());

            for (Map<String, Object> row : rows) {
                Object idObj = row.get("id");
                String id = idObj != null ? idObj.toString() : "unknown";
                String rawData = (String) row.get("raw_data");

                if (rawData == null || rawData.trim().isEmpty() || rawData.startsWith("http://") || rawData.startsWith("https://")) {
                    continue;
                }

                stats.incrementProcessed();
                report.incrementTotalProcessed();

                boolean success = processRecordWithRetry(
                        tableName,
                        id,
                        rawData,
                        updateSql,
                        entityFolder,
                        dryRun,
                        isProductImageTable
                );

                if (success) {
                    stats.incrementMigrated();
                    report.incrementTotalMigrated();
                } else {
                    stats.incrementFailed();
                    report.incrementTotalFailed();
                }
            }

            // In dry-run mode, break after 1 batch per table to avoid endless loop without DB writes
            if (dryRun) {
                log.info("Modo dry-run: procesado primer lote de prueba para '{}'. Pasando a siguiente tabla.", tableName);
                break;
            }

            // If rows returned is less than batchSize, we processed the last batch
            if (rows.size() < batchSize) {
                hasMore = false;
            }
        }
    }

    private boolean processRecordWithRetry(
            String tableName,
            String id,
            String rawData,
            String updateSql,
            String entityFolder,
            boolean dryRun,
            boolean isProductImageTable
    ) {
        int maxAttempts = 3;
        long backoffMs = 500;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                R2StorageService.DecodedImage decoded = storageService.decodeBase64Image(rawData);

                if (dryRun) {
                    log.info("[DRY-RUN] OK {} id={} ({} bytes, MIME: {}) -> Simulada subida a R2",
                            tableName, id, decoded.bytes().length, decoded.contentType());
                    return true;
                }

                // Subir a Cloudflare R2
                String extension = decoded.contentType().contains("png") ? "png" :
                                   decoded.contentType().contains("jpeg") || decoded.contentType().contains("jpg") ? "jpg" : "webp";
                String key = String.format("%s/%s_%s.%s", entityFolder, id, UUID.randomUUID().toString().substring(0, 8), extension);

                String publicUrl = storageService.uploadDirect(decoded.bytes(), key, decoded.contentType());

                // Actualizar base de datos
                if (isProductImageTable) {
                    // product_images has product_id and image_url
                    Long productId = Long.parseLong(id);
                    jdbcTemplate.update(updateSql, publicUrl, rawData, productId, rawData);
                } else {
                    Long recordId = Long.parseLong(id);
                    jdbcTemplate.update(updateSql, publicUrl, rawData, recordId);
                }

                log.info("Migrado exitoso: {} id={} -> {}", tableName, id, publicUrl);
                return true;

            } catch (Exception e) {
                log.warn("Falla en intento {}/{} para {} id={}: {}", attempt, maxAttempts, tableName, id, e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    backoffMs *= 2; // Exponential backoff: 500ms, 1000ms, 2000ms
                } else {
                    logErrorToFile(tableName, id, e.getMessage());
                }
            }
        }
        return false;
    }

    private synchronized void logErrorToFile(String tableName, String id, String errorMessage) {
        try {
            Path logPath = Paths.get(ERROR_LOG_FILE);
            if (!Files.exists(logPath)) {
                Files.createFile(logPath);
            }
            try (PrintWriter out = new PrintWriter(new FileWriter(ERROR_LOG_FILE, true))) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                out.printf("[%s] TABLE=%s ID=%s ERROR=%s%n", timestamp, tableName, id, errorMessage);
            }
        } catch (Exception ex) {
            log.error("No se pudo escribir en {}: {}", ERROR_LOG_FILE, ex.getMessage());
        }
    }

    public Map<String, Object> getPendingCounts() {
        Map<String, Object> counts = new LinkedHashMap<>();
        String[] tables = {
                "product_images", "categories", "sedes", "raffles",
                "hero_slides", "home_category_collections", "contests",
                "contest_participants", "outfit_settings"
        };

        for (String table : tables) {
            try {
                String col = table.equals("categories") || table.equals("sedes") ? "image" :
                             table.equals("contests") ? "banner_url" :
                             table.equals("contest_participants") ? "outfit_image_url" : "image_url";

                String sql = String.format("SELECT COUNT(*) FROM %s WHERE (%s LIKE 'data:image%%' OR (%s NOT LIKE 'http%%' AND LENGTH(%s) > 500))", table, col, col, col);
                Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
                counts.put(table, count != null ? count : 0);
            } catch (Exception e) {
                counts.put(table, "Error o no existe: " + e.getMessage());
            }
        }
        return counts;
    }

    @Data
    public static class MigrationReport {
        private String status = "PENDING";
        private boolean dryRun = false;
        private String startTime;
        private long durationSeconds = 0;
        private int totalProcessed = 0;
        private int totalMigrated = 0;
        private int totalFailed = 0;
        private Map<String, EntityStat> byEntity = new LinkedHashMap<>();

        public void incrementTotalProcessed() { totalProcessed++; }
        public void incrementTotalMigrated() { totalMigrated++; }
        public void incrementTotalFailed() { totalFailed++; }
    }

    @Data
    public static class EntityStat {
        private int processed = 0;
        private int migrated = 0;
        private int failed = 0;

        public void incrementProcessed() { processed++; }
        public void incrementMigrated() { migrated++; }
        public void incrementFailed() { failed++; }
    }
}

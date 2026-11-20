package com.jdqstore.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

    private static final Logger logger = LoggerFactory.getLogger(BackendApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    @Bean
    public CommandLineRunner databaseMigrationRunner(JdbcTemplate jdbcTemplate) {
        return args -> {
            logger.info("Verificando y aplicando migraciones de base de datos para Cloudflare R2...");

            String[] ddlStatements = {
                "ALTER TABLE product_images ALTER COLUMN image_url TYPE TEXT;",
                "ALTER TABLE product_images ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;",
                "ALTER TABLE categories ADD COLUMN IF NOT EXISTS image_url TEXT;",
                "ALTER TABLE categories ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;",
                "ALTER TABLE sedes ADD COLUMN IF NOT EXISTS image_url TEXT;",
                "ALTER TABLE sedes ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;",
                "ALTER TABLE raffles ALTER COLUMN image_url TYPE TEXT;",
                "ALTER TABLE raffles ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;",
                "ALTER TABLE hero_slides ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;",
                "ALTER TABLE home_category_collections ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;",
                "ALTER TABLE contests ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;",
                "ALTER TABLE contest_participants ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;",
                "ALTER TABLE outfit_settings ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;",
                "ALTER TABLE tiktok_accounts ADD COLUMN IF NOT EXISTS open_id TEXT;",
                "ALTER TABLE tiktok_accounts ADD COLUMN IF NOT EXISTS scope TEXT;",
                "ALTER TABLE job_vacancies ADD COLUMN IF NOT EXISTS saturday_hours VARCHAR(255);",
                "ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS emergency_contact_name VARCHAR(255);",
                "ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS emergency_contact_phone VARCHAR(255);",
                "ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS how_did_you_hear VARCHAR(255);",
                "ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS has_medical_condition BOOLEAN;",
                "ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS medical_condition_details TEXT;",
                "ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS in_other_selection_processes BOOLEAN;",
                "ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS image_authorization_accepted BOOLEAN;",
                "ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS document_file_url TEXT;",
                "ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS document_file_original_name VARCHAR(255);",
                "ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS store_id BIGINT;",
                "ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS store_name VARCHAR(100);",
                "CREATE TABLE IF NOT EXISTS audit_alerts (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "alert_type VARCHAR(50) NOT NULL, " +
                    "title VARCHAR(255) NOT NULL, " +
                    "description TEXT NOT NULL, " +
                    "sede_id BIGINT, " +
                    "sede_name VARCHAR(100), " +
                    "severity VARCHAR(30) DEFAULT 'INFO', " +
                    "status VARCHAR(30) DEFAULT 'PENDIENTE', " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "reviewed_at TIMESTAMP, " +
                    "reviewed_by VARCHAR(100)" +
                ");",
                "CREATE TABLE IF NOT EXISTS audit_alert_settings (" +
                    "id BIGINT PRIMARY KEY, " +
                    "daily_target INT DEFAULT 5, " +
                    "inactive_days_threshold INT DEFAULT 3, " +
                    "mass_edit_threshold INT DEFAULT 8, " +
                    "mass_delete_threshold INT DEFAULT 5" +
                ");",
                "CREATE TABLE IF NOT EXISTS tiktok_settings (" +
                    "id BIGINT PRIMARY KEY, " +
                    "client_key VARCHAR(255), " +
                    "client_secret VARCHAR(255), " +
                    "redirect_uri VARCHAR(500), " +
                    "updated_at TIMESTAMP" +
                ");",
                "ALTER TABLE tiktok_accounts ADD COLUMN IF NOT EXISTS refresh_token_expires_at TIMESTAMP;",
                "ALTER TABLE tiktok_accounts ADD COLUMN IF NOT EXISTS is_global BOOLEAN DEFAULT TRUE;",
                "ALTER TABLE tiktok_scheduled_videos ADD COLUMN IF NOT EXISTS description TEXT;",
                "ALTER TABLE tiktok_scheduled_videos ADD COLUMN IF NOT EXISTS hashtags VARCHAR(500);",
                "ALTER TABLE tiktok_scheduled_videos ADD COLUMN IF NOT EXISTS video_file_url TEXT;",
                "ALTER TABLE tiktok_scheduled_videos ADD COLUMN IF NOT EXISTS video_file_path TEXT;",
                "ALTER TABLE tiktok_scheduled_videos ADD COLUMN IF NOT EXISTS privacy_level VARCHAR(50) DEFAULT 'PUBLIC_TO_EVERYONE';",
                "ALTER TABLE tiktok_scheduled_videos ADD COLUMN IF NOT EXISTS allow_comments BOOLEAN DEFAULT TRUE;",
                "ALTER TABLE tiktok_scheduled_videos ADD COLUMN IF NOT EXISTS allow_duet BOOLEAN DEFAULT TRUE;",
                "ALTER TABLE tiktok_scheduled_videos ADD COLUMN IF NOT EXISTS allow_stitch BOOLEAN DEFAULT TRUE;",
                "ALTER TABLE tiktok_scheduled_videos ADD COLUMN IF NOT EXISTS is_draft BOOLEAN DEFAULT FALSE;",
                "ALTER TABLE tiktok_scheduled_videos ADD COLUMN IF NOT EXISTS tiktok_publish_id VARCHAR(255);",
                "ALTER TABLE tiktok_scheduled_videos ADD COLUMN IF NOT EXISTS error_message TEXT;",
                "ALTER TABLE tiktok_scheduled_videos ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;",
                "INSERT INTO tiktok_settings (id, client_key, client_secret, redirect_uri, updated_at) " +
                    "VALUES (1, 'sbaw8ymhocmewzu9lt', 'aSoOp8oM0mlaVF13PlMkDrJsdjEQWPGG', 'https://www.juandstore.com/admin/tiktok/callback', CURRENT_TIMESTAMP) " +
                    "ON CONFLICT (id) DO UPDATE SET client_key = EXCLUDED.client_key, client_secret = EXCLUDED.client_secret, redirect_uri = EXCLUDED.redirect_uri, updated_at = EXCLUDED.updated_at;"
            };

            for (String sql : ddlStatements) {
                try {
                    jdbcTemplate.execute(sql);
                } catch (Exception e) {
                    logger.debug("DDL ignorado o ya aplicado: {} ({})", sql, e.getMessage());
                }
            }
            logger.info("Migraciones de esquema completadas exitosamente.");
        };
    }
}

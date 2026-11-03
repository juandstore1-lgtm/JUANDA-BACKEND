package com.jdqstore.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
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
                "ALTER TABLE outfit_settings ADD COLUMN IF NOT EXISTS legacy_base64 TEXT;"
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

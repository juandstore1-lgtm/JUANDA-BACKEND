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
            try {
                logger.info("Running automatic database migrations for image_url columns...");
                jdbcTemplate.execute("ALTER TABLE product_images ALTER COLUMN image_url TYPE TEXT;");
                logger.info("Migrated product_images.image_url to TEXT");
            } catch (Exception e) {
                logger.warn("Could not migrate product_images: {}", e.getMessage());
            }

            try {
                jdbcTemplate.execute("ALTER TABLE galleries ALTER COLUMN image_url TYPE TEXT;");
                logger.info("Migrated galleries.image_url to TEXT");
            } catch (Exception e) {
                logger.warn("Could not migrate galleries: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute("ALTER TABLE raffles ALTER COLUMN image_url TYPE TEXT;");
                System.out.println("Migración exitosa: raffles.image_url a TEXT");
            } catch (Exception e) {
                System.out.println("No se pudo migrar raffles o ya es TEXT: " + e.getMessage());
            }

            try {
                jdbcTemplate.execute("ALTER TABLE sedes ALTER COLUMN image TYPE TEXT;");
                System.out.println("Migración exitosa: sedes.image a TEXT");
            } catch (Exception e) {
                System.out.println("No se pudo migrar sedes o ya es TEXT: " + e.getMessage());
            }
        };
    }
}

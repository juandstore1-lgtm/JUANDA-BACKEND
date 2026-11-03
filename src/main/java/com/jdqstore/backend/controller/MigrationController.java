package com.jdqstore.backend.controller;

import com.jdqstore.backend.migration.R2HistoricalMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/migration")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MigrationController {

    private final R2HistoricalMigrationService migrationService;

    @PostMapping("/images")
    public ResponseEntity<R2HistoricalMigrationService.MigrationReport> startMigration(
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "50") int batchSize
    ) {
        R2HistoricalMigrationService.MigrationReport report = migrationService.runMigration(dryRun, batchSize);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/images/pending")
    public ResponseEntity<Map<String, Object>> getPendingCounts() {
        return ResponseEntity.ok(migrationService.getPendingCounts());
    }

    @GetMapping("/images/errors")
    public ResponseEntity<List<String>> getErrorLogs() {
        try {
            Path path = Paths.get("migration-errors.log");
            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path);
                return ResponseEntity.ok(lines);
            }
            return ResponseEntity.ok(Collections.emptyList());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(List.of("Error al leer logs: " + e.getMessage()));
        }
    }
}

package com.jdqstore.backend.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class R2MigrationRunner implements ApplicationRunner {

    private final R2HistoricalMigrationService migrationService;

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("app.migrate-images") || args.containsOption("migrate-images")) {
            boolean dryRun = true;
            if (args.containsOption("dry-run")) {
                String dryRunVal = args.getOptionValues("dry-run").get(0);
                dryRun = Boolean.parseBoolean(dryRunVal);
            }

            int batchSize = 50;
            if (args.containsOption("batch-size")) {
                try {
                    batchSize = Integer.parseInt(args.getOptionValues("batch-size").get(0));
                } catch (NumberFormatException e) {
                    batchSize = 50;
                }
            }

            log.info("Detected --app.migrate-images flag. Executing historical R2 migration (dryRun={}, batchSize={})...",
                     dryRun, batchSize);

            R2HistoricalMigrationService.MigrationReport report = migrationService.runMigration(dryRun, batchSize);
            log.info("Migration runner completed: {}", report);
        }
    }
}

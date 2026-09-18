package cz.trixi.ruian.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic (by default monthly) import, see {@code importer.schedule} in application.yml.
 */
@Component
class ScheduledImport {

    private static final Logger log = LoggerFactory.getLogger(ScheduledImport.class);

    private final RuianImportService importService;

    ScheduledImport(RuianImportService importService) {
        this.importService = importService;
    }

    @Scheduled(cron = "${importer.schedule.cron}", zone = "${importer.schedule.zone}")
    void runScheduledImport() {
        try {
            importService.importFromDefaultSource(ImportTrigger.SCHEDULED);
        } catch (ImportAlreadyRunningException e) {
            log.warn("Scheduled import skipped: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.error("Scheduled import failed, it will be retried at the next scheduled time", e);
        }
    }
}

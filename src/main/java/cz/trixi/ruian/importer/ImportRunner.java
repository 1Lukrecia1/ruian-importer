package cz.trixi.ruian.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/**
 * Runs the import on application startup.
 * The URL can be passed as the first program argument, otherwise {@code importer.url} is used.
 * A failed import does not stop the application, the API keeps serving the data already in the database.
 */
@Component
@ConditionalOnProperty(prefix = "importer", name = "run-on-startup", havingValue = "true", matchIfMissing = true)
class ImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportRunner.class);

    private final RuianImportService importService;

    ImportRunner(RuianImportService importService) {
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> nonOptionArgs = args.getNonOptionArgs();
        try {
            if (nonOptionArgs.isEmpty()) {
                importService.importFromDefaultSource(ImportTrigger.STARTUP);
            } else {
                importService.importFrom(URI.create(nonOptionArgs.getFirst()), ImportTrigger.STARTUP);
            }
        } catch (RuntimeException e) {
            log.error("Import on startup failed", e);
        }
    }
}

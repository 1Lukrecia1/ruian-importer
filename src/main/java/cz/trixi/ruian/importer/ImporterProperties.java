package cz.trixi.ruian.importer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;

/**
 * @param sources        named sources the data can be imported from, keyed by id
 * @param defaultSource  id of the source used on startup, by the schedule and by {@code POST /api/import}
 * @param runOnStartup   whether to run the import when the application starts
 * @param connectTimeout HTTP connect timeout
 * @param requestTimeout HTTP timeout for receiving the response headers
 * @param schedule       periodic import
 */
@ConfigurationProperties("importer")
public record ImporterProperties(
        Map<String, Source> sources,
        @DefaultValue("cuzk") String defaultSource,
        @DefaultValue("true") boolean runOnStartup,
        @DefaultValue("30s") Duration connectTimeout,
        @DefaultValue("5m") Duration requestTimeout,
        @DefaultValue Schedule schedule) {

    public ImporterProperties {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("No importer.sources configured");
        }
        if (!sources.containsKey(defaultSource)) {
            throw new IllegalArgumentException(
                    "importer.default-source '" + defaultSource + "' is not one of " + sources.keySet());
        }
        sources = Map.copyOf(sources);
    }

    /**
     * @param name label shown in the UI
     * @param url  location of the file, may contain placeholders, see {@link ImportUrlResolver}
     */
    public record Source(String name, String url) {
    }

    /**
     * @param cron cron expression of the periodic import, {@code -} disables it
     * @param zone time zone of the cron expression and of the URL placeholders
     */
    public record Schedule(
            @DefaultValue("0 0 3 2 * *") String cron,
            @DefaultValue("Europe/Prague") ZoneId zone) {
    }
}

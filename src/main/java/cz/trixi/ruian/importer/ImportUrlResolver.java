package cz.trixi.ruian.importer;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Resolves placeholders in the configured import URL.
 * <p>
 * Supported placeholder: {@code {lastDayOfPreviousMonth}} – the last day of the previous month
 * as {@code yyyyMMdd} (e.g. {@code 20260831} during September 2026), evaluated in {@code importer.schedule.zone}.
 * ČÚZK publishes the RÚIAN files as of the end of each month under such a date.
 */
@Component
public class ImportUrlResolver {

    static final String LAST_DAY_OF_PREVIOUS_MONTH = "{lastDayOfPreviousMonth}";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^}]*}");

    private final ZoneId zone;

    public ImportUrlResolver(ImporterProperties properties) {
        this.zone = properties.schedule().zone();
    }

    public URI resolve(String urlTemplate) {
        return resolve(urlTemplate, LocalDate.now(zone));
    }

    URI resolve(String urlTemplate, LocalDate today) {
        LocalDate lastDayOfPreviousMonth = today.withDayOfMonth(1).minusDays(1);
        String url = urlTemplate.replace(LAST_DAY_OF_PREVIOUS_MONTH, lastDayOfPreviousMonth.format(DATE_FORMAT));
        if (PLACEHOLDER.matcher(url).find()) {
            throw new IllegalArgumentException("Unknown placeholder in import URL " + urlTemplate
                    + ", supported is only " + LAST_DAY_OF_PREVIOUS_MONTH);
        }
        return URI.create(url);
    }
}

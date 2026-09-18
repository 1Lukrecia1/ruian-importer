package cz.trixi.ruian.importer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportUrlResolverTest {

    private static final String TEMPLATE =
            "https://vdp.cuzk.cz/vymenny_format/soucasna/{lastDayOfPreviousMonth}_OB_573060_UKSH.xml.zip";

    private final ImportUrlResolver resolver = new ImportUrlResolver(new ImporterProperties(
            Map.of("cuzk", new ImporterProperties.Source("ČÚZK", TEMPLATE)), "cuzk", true,
            Duration.ofSeconds(30), Duration.ofMinutes(5),
            new ImporterProperties.Schedule("0 0 3 2 * *", ZoneId.of("Europe/Prague"))));

    @ParameterizedTest
    @CsvSource({
            "2026-09-16, 20260831",
            "2026-09-01, 20260831",
            "2026-09-30, 20260831",
            "2026-10-02, 20260930",
            "2026-03-15, 20260228",
            "2028-03-15, 20280229",
            "2027-01-02, 20261231"
    })
    void replacesLastDayOfPreviousMonth(LocalDate today, String expectedDate) {
        URI uri = resolver.resolve(TEMPLATE, today);

        assertThat(uri).hasToString(
                "https://vdp.cuzk.cz/vymenny_format/soucasna/" + expectedDate + "_OB_573060_UKSH.xml.zip");
    }

    @Test
    void keepsUrlWithoutPlaceholders() {
        String url = "https://www.smartform.cz/download/kopidlno.xml.zip";

        assertThat(resolver.resolve(url, LocalDate.of(2026, 9, 16))).hasToString(url);
    }

    @Test
    void rejectsUnknownPlaceholder() {
        assertThatThrownBy(() -> resolver.resolve("https://example.com/{today}.xml.zip", LocalDate.of(2026, 9, 16)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{today}");
    }
}

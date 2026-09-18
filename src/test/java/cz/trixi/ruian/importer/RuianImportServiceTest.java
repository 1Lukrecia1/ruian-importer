package cz.trixi.ruian.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.trixi.ruian.PostgisContainerConfig;
import cz.trixi.ruian.db.RuianRepository;
import cz.trixi.ruian.download.DownloadException;
import cz.trixi.ruian.model.CastObce;
import cz.trixi.ruian.model.Obec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgisContainerConfig.class)
class RuianImportServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private RuianImportService importService;

    @Autowired
    private RuianRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM cast_obce").update();
        jdbc.sql("DELETE FROM obec").update();
    }

    @Test
    void importsZippedXmlIntoDatabase() throws IOException {
        URI zip = zipSample();

        ImportRun run = importService.importFrom(zip, ImportTrigger.MANUAL);

        assertThat(obce()).containsExactly(Map.of("kod", 573060, "nazev", "Kopidlno"));
        assertThat(castiObci()).containsExactly(
                Map.of("kod", 69299, "nazev", "Kopidlno", "kod_obce", 573060),
                Map.of("kod", 69302, "nazev", "Ledkov", "kod_obce", 573060));
        assertThat(run.status()).isEqualTo(ImportRun.Status.SUCCESS);
        assertThat(run.obce()).isEqualTo(1);
        assertThat(run.castiObci()).isEqualTo(2);
        assertThat(importService.status()).isEqualTo(new ImportStatus(false, run));
    }

    @Test
    void repeatedImportUpdatesExistingRows() throws IOException {
        jdbc.sql("INSERT INTO obec (kod, nazev) VALUES (573060, 'Old name')").update();
        URI zip = zipSample();

        importService.importFrom(zip, ImportTrigger.MANUAL);
        importService.importFrom(zip, ImportTrigger.SCHEDULED);

        assertThat(obce()).containsExactly(Map.of("kod", 573060, "nazev", "Kopidlno"));
        assertThat(castiObci()).hasSize(2);
    }

    @Test
    void importsPlainXml() throws IOException {
        Path xmlFile = tempDir.resolve("kopidlno.xml");
        try (InputStream xml = getClass().getResourceAsStream("/kopidlno-sample.xml")) {
            Files.copy(xml, xmlFile);
        }

        importService.importFrom(xmlFile.toUri(), ImportTrigger.MANUAL);

        assertThat(obce()).containsExactly(Map.of("kod", 573060, "nazev", "Kopidlno"));
        assertThat(castiObci()).hasSize(2);
    }

    @Test
    void importsUploadedZip() throws IOException {
        Path zipFile = Path.of(zipSample());

        ImportRun run;
        try (InputStream in = Files.newInputStream(zipFile)) {
            run = importService.importFromFile("kopidlno.xml.zip", in, ImportTrigger.UPLOAD);
        }

        assertThat(run.source()).isEqualTo("upload:kopidlno.xml.zip");
        assertThat(run.trigger()).isEqualTo(ImportTrigger.UPLOAD);
        assertThat(obce()).containsExactly(Map.of("kod", 573060, "nazev", "Kopidlno"));
        assertThat(castiObci()).hasSize(2);
    }

    @Test
    void importsUploadedPlainXml() throws IOException {
        ImportRun run;
        try (InputStream in = getClass().getResourceAsStream("/kopidlno-sample.xml")) {
            run = importService.importFromFile("kopidlno.xml", in, ImportTrigger.UPLOAD);
        }

        assertThat(run.status()).isEqualTo(ImportRun.Status.SUCCESS);
        assertThat(repository.findObecDefinicniBodGeoJson(573060)).isPresent();
        assertThat(castiObci()).hasSize(2);
    }

    @Test
    void failedImportIsRecordedInStatus() {
        URI missing = tempDir.resolve("missing.xml").toUri();

        assertThatThrownBy(() -> importService.importFrom(missing, ImportTrigger.SCHEDULED))
                .isInstanceOf(DownloadException.class);

        ImportStatus status = importService.status();
        assertThat(status.running()).isFalse();
        assertThat(status.lastRun().status()).isEqualTo(ImportRun.Status.FAILED);
        assertThat(status.lastRun().trigger()).isEqualTo(ImportTrigger.SCHEDULED);
        assertThat(status.lastRun().error()).contains("missing.xml");
    }

    @Test
    void repositoryReadsImportedData() throws IOException {
        importService.importFrom(zipSample(), ImportTrigger.MANUAL);

        assertThat(repository.findObec(573060)).contains(new Obec(573060, "Kopidlno"));
        assertThat(repository.findObec(1)).isEmpty();
        assertThat(repository.findCastiObciByObec(573060)).containsExactly(
                new CastObce(69299, "Kopidlno", 573060),
                new CastObce(69302, "Ledkov", 573060));
        assertThat(repository.findCastObce(69302)).contains(new CastObce(69302, "Ledkov", 573060));
    }

    @Test
    void storesGeometryAndReadsItAsGeoJsonInWgs84() throws IOException {
        importService.importFrom(zipSample(), ImportTrigger.MANUAL);

        JsonNode point = json(repository.findObecDefinicniBodGeoJson(573060).orElseThrow());
        assertThat(point.get("type").asText()).isEqualTo("Point");
        assertThat(point.get("coordinates").get(0).asDouble()).isCloseTo(15.27, org.assertj.core.data.Offset.offset(0.1));
        assertThat(point.get("coordinates").get(1).asDouble()).isCloseTo(50.33, org.assertj.core.data.Offset.offset(0.1));

        JsonNode boundary = json(repository.findObecHraniceGeoJson(573060).orElseThrow());
        assertThat(boundary.get("type").asText()).isEqualTo("MultiPolygon");
        assertThat(boundary.get("coordinates").get(0).get(0)).hasSize(5);

        assertThat(repository.findCastObceDefinicniBodGeoJson(69299)).isPresent();
        // this cast obce has no geometry in the sample
        assertThat(repository.findCastObceDefinicniBodGeoJson(69302)).isEmpty();
    }

    @Test
    void returnsCastiObciAsGeoJsonFeatureCollection() throws IOException {
        importService.importFrom(zipSample(), ImportTrigger.MANUAL);

        JsonNode featureCollection = json(repository.findCastiObciGeoJson(573060));

        assertThat(featureCollection.get("type").asText()).isEqualTo("FeatureCollection");
        JsonNode features = featureCollection.get("features");
        // only the cast obce with geometry is a feature
        assertThat(features).hasSize(1);
        assertThat(features.get(0).get("properties").get("kod").asInt()).isEqualTo(69299);
        assertThat(features.get(0).get("properties").get("nazev").asText()).isEqualTo("Kopidlno");
        assertThat(features.get(0).get("geometry").get("type").asText()).isEqualTo("Point");
    }

    @Test
    void emptyFeatureCollectionForObecWithoutCastiObci() {
        jdbc.sql("INSERT INTO obec (kod, nazev) VALUES (1, 'Bez casti')").update();

        JsonNode featureCollection = json(repository.findCastiObciGeoJson(1));

        assertThat(featureCollection.get("type").asText()).isEqualTo("FeatureCollection");
        assertThat(featureCollection.get("features")).isEmpty();
    }

    private static JsonNode json(String value) {
        try {
            return JSON.readTree(value);
        } catch (IOException e) {
            throw new IllegalArgumentException("Not a JSON: " + value, e);
        }
    }

    private List<Map<String, Object>> obce() {
        return jdbc.sql("SELECT kod, nazev FROM obec ORDER BY kod").query().listOfRows();
    }

    private List<Map<String, Object>> castiObci() {
        return jdbc.sql("SELECT kod, nazev, kod_obce FROM cast_obce ORDER BY kod").query().listOfRows();
    }

    private URI zipSample() throws IOException {
        Path zipFile = tempDir.resolve("kopidlno.xml.zip");
        try (OutputStream out = Files.newOutputStream(zipFile);
             ZipOutputStream zip = new ZipOutputStream(out);
             InputStream xml = getClass().getResourceAsStream("/kopidlno-sample.xml")) {
            zip.putNextEntry(new ZipEntry("kopidlno.xml"));
            xml.transferTo(zip);
            zip.closeEntry();
        }
        return zipFile.toUri();
    }
}

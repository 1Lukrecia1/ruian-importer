package cz.trixi.ruian.api;

import cz.trixi.ruian.db.RuianRepository;
import cz.trixi.ruian.download.DownloadException;
import cz.trixi.ruian.importer.ImportAlreadyRunningException;
import cz.trixi.ruian.importer.ImportRun;
import cz.trixi.ruian.importer.ImportSourceInfo;
import cz.trixi.ruian.importer.ImportStatus;
import cz.trixi.ruian.importer.ImportTrigger;
import cz.trixi.ruian.importer.RuianImportService;
import cz.trixi.ruian.importer.UnknownImportSourceException;
import cz.trixi.ruian.model.CastObce;
import cz.trixi.ruian.model.Obec;
import cz.trixi.ruian.xml.RuianParseException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
class ApiControllerTest {

    private static final Obec KOPIDLNO = new Obec(573060, "Kopidlno");
    private static final CastObce LEDKOV = new CastObce(69302, "Ledkov", 573060);
    private static final String POINT_GEO_JSON = """
            {"type":"Point","coordinates":[15.2712,50.3312]}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RuianRepository repository;

    @MockitoBean
    private RuianImportService importService;

    @Test
    void listsObce() throws Exception {
        when(repository.findAllObce()).thenReturn(List.of(KOPIDLNO));

        mvc.perform(get("/api/obce"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].kod").value(573060))
                .andExpect(jsonPath("$[0].nazev").value("Kopidlno"))
                // geometry is served by the GeoJSON endpoints, not inlined here
                .andExpect(jsonPath("$[0].definicniBodWkt").doesNotExist());
    }

    @Test
    void returnsObec() throws Exception {
        when(repository.findObec(573060)).thenReturn(Optional.of(KOPIDLNO));

        mvc.perform(get("/api/obce/573060"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nazev").value("Kopidlno"));
    }

    @Test
    void returnsNotFoundForUnknownObec() throws Exception {
        when(repository.findObec(1)).thenReturn(Optional.empty());

        mvc.perform(get("/api/obce/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Obec 1 not found"));
    }

    @Test
    void returnsBadRequestForInvalidKod() throws Exception {
        mvc.perform(get("/api/obce/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsCastiObciOfObec() throws Exception {
        when(repository.findObec(573060)).thenReturn(Optional.of(KOPIDLNO));
        when(repository.findCastiObciByObec(573060)).thenReturn(List.of(LEDKOV));

        mvc.perform(get("/api/obce/573060/casti-obci"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kod").value(69302))
                .andExpect(jsonPath("$[0].nazev").value("Ledkov"))
                .andExpect(jsonPath("$[0].kodObce").value(573060));
    }

    @Test
    void returnsNotFoundForCastiObciOfUnknownObec() throws Exception {
        when(repository.findObec(1)).thenReturn(Optional.empty());

        mvc.perform(get("/api/obce/1/casti-obci"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsCastiObci() throws Exception {
        when(repository.findAllCastiObci()).thenReturn(List.of(LEDKOV));

        mvc.perform(get("/api/casti-obci"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nazev").value("Ledkov"));
    }

    @Test
    void returnsCastObce() throws Exception {
        when(repository.findCastObce(69302)).thenReturn(Optional.of(LEDKOV));

        mvc.perform(get("/api/casti-obci/69302"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kodObce").value(573060));
    }

    @Test
    void returnsNotFoundForUnknownCastObce() throws Exception {
        when(repository.findCastObce(1)).thenReturn(Optional.empty());

        mvc.perform(get("/api/casti-obci/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Cast obce 1 not found"));
    }

    @Test
    void returnsDefinicniBodOfObecAsGeoJson() throws Exception {
        when(repository.findObecDefinicniBodGeoJson(573060)).thenReturn(Optional.of(POINT_GEO_JSON));

        mvc.perform(get("/api/obce/573060/definicni-bod"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/geo+json"))
                .andExpect(jsonPath("$.type").value("Point"))
                .andExpect(jsonPath("$.coordinates[0]").value(15.2712));
    }

    @Test
    void returnsNotFoundWhenObecHasNoGeometry() throws Exception {
        when(repository.findObecDefinicniBodGeoJson(573060)).thenReturn(Optional.empty());

        mvc.perform(get("/api/obce/573060/definicni-bod"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Obec 573060 has no definicni bod"));
    }

    @Test
    void returnsHraniceOfObecAsGeoJson() throws Exception {
        when(repository.findObecHraniceGeoJson(573060))
                .thenReturn(Optional.of("""
                        {"type":"MultiPolygon","coordinates":[[[[15.26,50.33],[15.27,50.33],[15.26,50.33]]]]}"""));

        mvc.perform(get("/api/obce/573060/hranice"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/geo+json"))
                .andExpect(jsonPath("$.type").value("MultiPolygon"));
    }

    @Test
    void returnsNotFoundWhenObecHasNoHranice() throws Exception {
        when(repository.findObecHraniceGeoJson(573060)).thenReturn(Optional.empty());

        mvc.perform(get("/api/obce/573060/hranice"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsDefinicniBodOfCastObceAsGeoJson() throws Exception {
        when(repository.findCastObceDefinicniBodGeoJson(69302)).thenReturn(Optional.of(POINT_GEO_JSON));

        mvc.perform(get("/api/casti-obci/69302/definicni-bod"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/geo+json"))
                .andExpect(jsonPath("$.type").value("Point"));
    }

    @Test
    void returnsCastiObciAsFeatureCollection() throws Exception {
        when(repository.findObec(573060)).thenReturn(Optional.of(KOPIDLNO));
        when(repository.findCastiObciGeoJson(573060)).thenReturn("""
                {"type":"FeatureCollection","features":[{"type":"Feature",\
                "properties":{"kod":69302,"nazev":"Ledkov","kodObce":573060},\
                "geometry":{"type":"Point","coordinates":[15.2712,50.3312]}}]}""");

        mvc.perform(get("/api/obce/573060/casti-obci/geojson"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/geo+json"))
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features[0].properties.kod").value(69302));
    }

    @Test
    void returnsNotFoundForFeatureCollectionOfUnknownObec() throws Exception {
        when(repository.findObec(1)).thenReturn(Optional.empty());

        mvc.perform(get("/api/obce/1/casti-obci/geojson"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsImportSources() throws Exception {
        when(importService.sources()).thenReturn(List.of(
                new ImportSourceInfo("cuzk", "ČÚZK (aktuální data)",
                        "https://vdp.cuzk.cz/vymenny_format/soucasna/20260831_OB_573060_UKSH.xml.zip", true),
                new ImportSourceInfo("smartform", "smartform.cz (soubor ze zadání)",
                        "https://www.smartform.cz/download/kopidlno.xml.zip", false)));

        mvc.perform(get("/api/import/zdroje"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("cuzk"))
                .andExpect(jsonPath("$[0].primary").value(true))
                .andExpect(jsonPath("$[0].url").value("https://vdp.cuzk.cz/vymenny_format/soucasna/20260831_OB_573060_UKSH.xml.zip"))
                .andExpect(jsonPath("$[1].id").value("smartform"));
    }

    @Test
    void runsImportFromNamedSource() throws Exception {
        when(importService.importFromSource("smartform", ImportTrigger.MANUAL))
                .thenReturn(successfulRun("https://www.smartform.cz/download/kopidlno.xml.zip", ImportTrigger.MANUAL));

        mvc.perform(post("/api/import").param("zdroj", "smartform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("https://www.smartform.cz/download/kopidlno.xml.zip"));
    }

    @Test
    void returnsNotFoundForUnknownSource() throws Exception {
        when(importService.importFromSource("neznamy", ImportTrigger.MANUAL))
                .thenThrow(new UnknownImportSourceException("neznamy", List.of("cuzk", "smartform")));

        mvc.perform(post("/api/import").param("zdroj", "neznamy"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Unknown import source 'neznamy', known sources are [cuzk, smartform]"));
    }

    @Test
    void runsImport() throws Exception {
        when(importService.importFromDefaultSource(ImportTrigger.MANUAL)).thenReturn(successfulRun());

        mvc.perform(post("/api/import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.trigger").value("MANUAL"))
                .andExpect(jsonPath("$.obce").value(1))
                .andExpect(jsonPath("$.castiObci").value(5))
                .andExpect(jsonPath("$.startedAt").value("2026-09-16T10:00:00Z"));
    }

    @Test
    void returnsConflictWhenImportIsAlreadyRunning() throws Exception {
        when(importService.importFromDefaultSource(ImportTrigger.MANUAL))
                .thenThrow(new ImportAlreadyRunningException());

        mvc.perform(post("/api/import"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Another import is already running"));
    }

    @Test
    void returnsBadGatewayWhenImportSourceFails() throws Exception {
        when(importService.importFromDefaultSource(ImportTrigger.MANUAL))
                .thenThrow(new DownloadException("Unexpected HTTP status 404"));

        mvc.perform(post("/api/import"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Unexpected HTTP status 404"));
    }

    @Test
    void importsUploadedFile() throws Exception {
        when(importService.importFromFile(eq("kopidlno.xml"), any(), eq(ImportTrigger.UPLOAD)))
                .thenReturn(successfulRun("upload:kopidlno.xml", ImportTrigger.UPLOAD));

        mvc.perform(multipart("/api/import/soubor")
                        .file(new MockMultipartFile("soubor", "kopidlno.xml", "text/xml", "<vf:VymennyFormat/>".getBytes(UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trigger").value("UPLOAD"))
                .andExpect(jsonPath("$.source").value("upload:kopidlno.xml"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void rejectsEmptyUploadedFile() throws Exception {
        mvc.perform(multipart("/api/import/soubor")
                        .file(new MockMultipartFile("soubor", "prazdny.xml", "text/xml", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The uploaded file is empty"));
    }

    @Test
    void returnsBadGatewayWhenUploadedFileCannotBeParsed() throws Exception {
        when(importService.importFromFile(eq("spatny.xml"), any(), eq(ImportTrigger.UPLOAD)))
                .thenThrow(new RuianParseException("Invalid RÚIAN XML: unexpected end of document"));

        mvc.perform(multipart("/api/import/soubor")
                        .file(new MockMultipartFile("soubor", "spatny.xml", "text/xml", "<broken".getBytes(UTF_8))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Invalid RÚIAN XML: unexpected end of document"));
    }

    @Test
    void returnsConflictWhenUploadingDuringRunningImport() throws Exception {
        when(importService.importFromFile(eq("kopidlno.xml"), any(), eq(ImportTrigger.UPLOAD)))
                .thenThrow(new ImportAlreadyRunningException());

        mvc.perform(multipart("/api/import/soubor")
                        .file(new MockMultipartFile("soubor", "kopidlno.xml", "text/xml", "<vf:VymennyFormat/>".getBytes(UTF_8))))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsImportStatus() throws Exception {
        when(importService.status()).thenReturn(new ImportStatus(false, successfulRun()));

        mvc.perform(get("/api/import/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.lastRun.status").value("SUCCESS"));
    }

    private static ImportRun successfulRun() {
        return successfulRun("https://example.com/kopidlno.xml.zip", ImportTrigger.MANUAL);
    }

    private static ImportRun successfulRun(String source, ImportTrigger trigger) {
        return new ImportRun(source, trigger,
                Instant.parse("2026-09-16T10:00:00Z"), Instant.parse("2026-09-16T10:00:02Z"),
                ImportRun.Status.SUCCESS, 1, 5, null);
    }
}

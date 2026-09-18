package cz.trixi.ruian.api;

import cz.trixi.ruian.importer.ImportRun;
import cz.trixi.ruian.importer.ImportSourceInfo;
import cz.trixi.ruian.importer.ImportStatus;
import cz.trixi.ruian.importer.ImportTrigger;
import cz.trixi.ruian.importer.RuianImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Import from one of the configured sources or from an uploaded file, and the import status.
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final RuianImportService importService;

    public ImportController(RuianImportService importService) {
        this.importService = importService;
    }

    /**
     * Configured sources, the default one first.
     */
    @GetMapping("/zdroje")
    public List<ImportSourceInfo> sources() {
        return importService.sources();
    }

    /**
     * @param zdroj id of the source, the default source is used when not given
     */
    @PostMapping
    public ImportRun runImport(@RequestParam(name = "zdroj", required = false) String zdroj) {
        return StringUtils.hasText(zdroj)
                ? importService.importFromSource(zdroj, ImportTrigger.MANUAL)
                : importService.importFromDefaultSource(ImportTrigger.MANUAL);
    }

    /**
     * Imports an uploaded zipped or plain XML file.
     */
    @PostMapping(path = "/soubor", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportRun importFile(@RequestPart("soubor") MultipartFile soubor) {
        if (soubor.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is empty");
        }
        String fileName = StringUtils.getFilename(soubor.getOriginalFilename());
        try (InputStream in = soubor.getInputStream()) {
            return importService.importFromFile(
                    StringUtils.hasText(fileName) ? fileName : "soubor.xml", in, ImportTrigger.UPLOAD);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping("/status")
    public ImportStatus status() {
        return importService.status();
    }
}

package cz.trixi.ruian.importer;

import cz.trixi.ruian.db.RuianRepository;
import cz.trixi.ruian.download.XmlDownloader;
import cz.trixi.ruian.download.XmlReader;
import cz.trixi.ruian.model.RuianData;
import cz.trixi.ruian.xml.RuianXmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Runs imports and remembers the result of the last one.
 * Only one import runs at a time, a concurrent attempt fails with {@link ImportAlreadyRunningException}.
 */
@Service
public class RuianImportService {

    private static final Logger log = LoggerFactory.getLogger(RuianImportService.class);

    private final XmlDownloader downloader;
    private final XmlReader xmlReader;
    private final RuianXmlParser parser;
    private final RuianRepository repository;
    private final ImporterProperties properties;
    private final ImportUrlResolver urlResolver;

    private final ReentrantLock lock = new ReentrantLock();
    private volatile ImportRun lastRun;

    public RuianImportService(XmlDownloader downloader, XmlReader xmlReader, RuianXmlParser parser,
                              RuianRepository repository, ImporterProperties properties,
                              ImportUrlResolver urlResolver) {
        this.downloader = downloader;
        this.xmlReader = xmlReader;
        this.parser = parser;
        this.repository = repository;
        this.properties = properties;
        this.urlResolver = urlResolver;
    }

    /**
     * @return configured sources, the default one first
     */
    public List<ImportSourceInfo> sources() {
        return properties.sources().entrySet().stream()
                .map(this::toSourceInfo)
                .sorted(Comparator.comparing(ImportSourceInfo::primary).reversed()
                        .thenComparing(ImportSourceInfo::id))
                .toList();
    }

    public ImportRun importFromDefaultSource(ImportTrigger trigger) {
        return importFromSource(properties.defaultSource(), trigger);
    }

    public ImportRun importFromSource(String sourceId, ImportTrigger trigger) {
        ImporterProperties.Source source = properties.sources().get(sourceId);
        if (source == null) {
            throw new UnknownImportSourceException(sourceId, properties.sources().keySet());
        }
        return importFrom(urlResolver.resolve(source.url()), trigger);
    }

    public ImportRun importFrom(URI uri, ImportTrigger trigger) {
        return runExclusively(() -> doImport(uri.toString(), trigger, () -> downloader.download(uri, parser::parse)));
    }

    /**
     * Imports an uploaded file (zip or plain XML). The stream is read by the caller's thread,
     * it has to stay open until the method returns.
     */
    public ImportRun importFromFile(String fileName, InputStream in, ImportTrigger trigger) {
        String source = "upload:" + fileName;
        return runExclusively(() -> doImport(source, trigger, () -> xmlReader.read(in, source, parser::parse)));
    }

    public ImportStatus status() {
        return new ImportStatus(lock.isLocked(), lastRun);
    }

    private ImportSourceInfo toSourceInfo(Map.Entry<String, ImporterProperties.Source> entry) {
        String id = entry.getKey();
        ImporterProperties.Source source = entry.getValue();
        String name = source.name() != null && !source.name().isBlank() ? source.name() : id;
        return new ImportSourceInfo(id, name, urlResolver.resolve(source.url()).toString(),
                id.equals(properties.defaultSource()));
    }

    private ImportRun runExclusively(Supplier<ImportRun> importAction) {
        if (!lock.tryLock()) {
            throw new ImportAlreadyRunningException();
        }
        try {
            return importAction.get();
        } finally {
            lock.unlock();
        }
    }

    private ImportRun doImport(String source, ImportTrigger trigger, Supplier<RuianData> dataSupplier) {
        log.info("Starting {} import from {}", trigger, source);
        Instant startedAt = Instant.now();
        ImportRun run;
        try {
            RuianData data = dataSupplier.get();
            log.info("Parsed {} obec and {} cast obce elements", data.obce().size(), data.castiObci().size());

            repository.save(data);
            run = ImportRun.success(source, trigger, startedAt, Instant.now(), data);
            log.info("Import finished: {} obce, {} casti obci saved", data.obce().size(), data.castiObci().size());
        } catch (RuntimeException e) {
            lastRun = ImportRun.failure(source, trigger, startedAt, Instant.now(), e);
            throw e;
        }
        lastRun = run;
        return run;
    }
}

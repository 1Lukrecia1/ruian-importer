package cz.trixi.ruian.download;

import cz.trixi.ruian.importer.ImporterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;

/**
 * Downloads a zipped or plain XML file, see {@link XmlReader}.
 * Supports {@code http(s)://} URLs and {@code file:} URLs for local files.
 */
@Component
public class XmlDownloader {

    private static final Logger log = LoggerFactory.getLogger(XmlDownloader.class);

    private final HttpClient httpClient;
    private final ImporterProperties properties;
    private final XmlReader xmlReader;

    public XmlDownloader(ImporterProperties properties, XmlReader xmlReader) {
        this.properties = properties;
        this.xmlReader = xmlReader;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public <T> T download(URI uri, Function<InputStream, T> xmlProcessor) {
        log.info("Reading {}", uri);
        try {
            return xmlReader.read(open(uri), uri.toString(), xmlProcessor);
        } catch (IOException e) {
            throw new DownloadException("Failed to download " + uri + ": " + e.getMessage(), e);
        }
    }

    private InputStream open(URI uri) throws IOException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        return switch (scheme) {
            case "http", "https" -> openHttp(uri);
            case "file" -> Files.newInputStream(Path.of(uri));
            default -> throw new DownloadException("Unsupported URL scheme: " + uri);
        };
    }

    private InputStream openHttp(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.requestTimeout())
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new DownloadException("Unexpected HTTP status " + response.statusCode() + " for " + uri);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("Download interrupted", e));
        }
    }
}

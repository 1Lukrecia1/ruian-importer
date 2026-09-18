package cz.trixi.ruian.download;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads XML from a stream that is either a zip archive or a plain XML file (detected by content),
 * and hands it over to the processor as a stream, so nothing has to be stored on disk
 * or fully loaded into memory.
 */
@Component
public class XmlReader {

    private static final Logger log = LoggerFactory.getLogger(XmlReader.class);

    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};

    public <T> T read(InputStream in, String source, Function<InputStream, T> xmlProcessor) {
        try (InputStream stream = new BufferedInputStream(in)) {
            if (isZip(stream)) {
                return processZip(stream, source, xmlProcessor);
            }
            log.info("Processing plain XML from {}", source);
            return xmlProcessor.apply(stream);
        } catch (IOException e) {
            throw new DownloadException("Failed to read " + source + ": " + e.getMessage(), e);
        }
    }

    private static <T> T processZip(InputStream in, String source, Function<InputStream, T> xmlProcessor)
            throws IOException {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                    log.info("Processing zip entry {} from {}", entry.getName(), source);
                    return xmlProcessor.apply(zip);
                }
            }
            throw new DownloadException("No XML file found in the archive " + source);
        }
    }

    private static boolean isZip(InputStream in) throws IOException {
        in.mark(ZIP_MAGIC.length);
        byte[] header = in.readNBytes(ZIP_MAGIC.length);
        in.reset();
        return Arrays.equals(header, ZIP_MAGIC);
    }
}

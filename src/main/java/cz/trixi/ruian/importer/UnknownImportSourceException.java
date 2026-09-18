package cz.trixi.ruian.importer;

import java.util.Collection;

public class UnknownImportSourceException extends RuntimeException {

    public UnknownImportSourceException(String sourceId, Collection<String> knownSources) {
        super("Unknown import source '" + sourceId + "', known sources are " + knownSources);
    }
}

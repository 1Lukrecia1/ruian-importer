package cz.trixi.ruian.importer;

public class ImportAlreadyRunningException extends RuntimeException {

    public ImportAlreadyRunningException() {
        super("Another import is already running");
    }
}

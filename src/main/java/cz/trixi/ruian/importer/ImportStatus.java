package cz.trixi.ruian.importer;

/**
 * @param running whether an import is running right now
 * @param lastRun the last finished import, {@code null} if there was none since the application start
 */
public record ImportStatus(boolean running, ImportRun lastRun) {
}

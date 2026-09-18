package cz.trixi.ruian.importer;

/**
 * Import source as offered by the API.
 *
 * @param id      id from {@code importer.sources}
 * @param name    label for the UI
 * @param url     URL with placeholders already resolved
 * @param primary whether this is the default source (used on startup and by the schedule)
 */
public record ImportSourceInfo(String id, String name, String url, boolean primary) {
}

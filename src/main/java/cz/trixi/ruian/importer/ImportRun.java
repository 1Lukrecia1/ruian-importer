package cz.trixi.ruian.importer;

import cz.trixi.ruian.model.RuianData;

import java.time.Instant;

/**
 * Result of a single import.
 *
 * @param source    URL the data was downloaded from, or {@code upload:<file name>} for an uploaded file
 * @param obce      number of imported obce, {@code null} for a failed import
 * @param castiObci number of imported casti obci, {@code null} for a failed import
 * @param error     error message of a failed import
 */
public record ImportRun(
        String source,
        ImportTrigger trigger,
        Instant startedAt,
        Instant finishedAt,
        Status status,
        Integer obce,
        Integer castiObci,
        String error) {

    public enum Status {
        SUCCESS,
        FAILED
    }

    static ImportRun success(String source, ImportTrigger trigger, Instant startedAt, Instant finishedAt,
                             RuianData data) {
        return new ImportRun(source, trigger, startedAt, finishedAt, Status.SUCCESS,
                data.obce().size(), data.castiObci().size(), null);
    }

    static ImportRun failure(String source, ImportTrigger trigger, Instant startedAt, Instant finishedAt,
                             Exception e) {
        String error = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
        return new ImportRun(source, trigger, startedAt, finishedAt, Status.FAILED, null, null, error);
    }
}

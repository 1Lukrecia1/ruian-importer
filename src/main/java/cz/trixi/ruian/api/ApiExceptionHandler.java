package cz.trixi.ruian.api;

import cz.trixi.ruian.download.DownloadException;
import cz.trixi.ruian.importer.ImportAlreadyRunningException;
import cz.trixi.ruian.importer.UnknownImportSourceException;
import cz.trixi.ruian.xml.RuianParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders errors as RFC 9457 problem details.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ImportAlreadyRunningException.class)
    ResponseEntity<ProblemDetail> handleImportAlreadyRunning(ImportAlreadyRunningException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(UnknownImportSourceException.class)
    ResponseEntity<ProblemDetail> handleUnknownImportSource(UnknownImportSourceException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({DownloadException.class, RuianParseException.class})
    ResponseEntity<ProblemDetail> handleImportSourceFailure(RuntimeException e) {
        return problem(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}

package com.example.banking.exception;

import java.time.Instant;
import java.util.List;

/** Standard error body returned by every failing endpoint. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldIssue> details) {

    public record FieldIssue(String field, String message) {
    }

    public static ErrorResponse of(ErrorCode code, String message, String path, List<FieldIssue> details) {
        return new ErrorResponse(Instant.now(), code.getStatus().value(), code.name(), message, path, details);
    }
}

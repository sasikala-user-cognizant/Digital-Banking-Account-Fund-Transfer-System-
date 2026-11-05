package com.example.banking.util;

import com.example.banking.exception.BusinessException;
import com.example.banking.exception.ErrorCode;

public final class IdempotencyKeys {

    public static final int MAX_LENGTH = 64;

    private IdempotencyKeys() {
    }

    /** Returns the trimmed key, or null when absent/blank. Rejects keys longer than 64 characters. */
    public static String normalise(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key must be at most " + MAX_LENGTH + " characters");
        }
        return trimmed;
    }
}

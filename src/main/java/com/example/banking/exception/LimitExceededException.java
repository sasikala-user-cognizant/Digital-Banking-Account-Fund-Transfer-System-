package com.example.banking.exception;


public class LimitExceededException extends BusinessException {

    public LimitExceededException(String message) {
        super(ErrorCode.LIMIT_EXCEEDED, message);
    }
}

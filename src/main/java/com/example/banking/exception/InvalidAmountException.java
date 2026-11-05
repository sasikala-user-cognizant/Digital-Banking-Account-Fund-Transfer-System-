package com.example.banking.exception;


public class InvalidAmountException extends BusinessException {

    public InvalidAmountException(String message) {
        super(ErrorCode.INVALID_AMOUNT, message);
    }
}

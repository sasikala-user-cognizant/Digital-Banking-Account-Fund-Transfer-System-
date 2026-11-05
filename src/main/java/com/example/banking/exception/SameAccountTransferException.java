package com.example.banking.exception;


public class SameAccountTransferException extends BusinessException {

    public SameAccountTransferException() {
        super(ErrorCode.SAME_ACCOUNT_TRANSFER, "Source and destination accounts must be different");
    }
}

package com.example.banking.exception;


public class AccountInactiveException extends BusinessException {

    public AccountInactiveException(String accountNumber, Object status) {
        super(ErrorCode.ACCOUNT_INACTIVE, "Account " + accountNumber + " is not active (status: " + status + ")");
    }
}

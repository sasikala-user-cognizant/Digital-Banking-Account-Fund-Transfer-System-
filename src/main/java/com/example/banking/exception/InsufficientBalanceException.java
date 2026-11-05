package com.example.banking.exception;


public class InsufficientBalanceException extends BusinessException {

    public InsufficientBalanceException(String accountNumber, java.math.BigDecimal available, java.math.BigDecimal requested) {
        super(ErrorCode.INSUFFICIENT_BALANCE, "Available balance " + available.toPlainString()
                + " in account " + accountNumber + " is less than requested amount " + requested.toPlainString());
    }
}

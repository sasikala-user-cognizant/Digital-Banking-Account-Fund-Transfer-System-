package com.example.banking.dto.response;

import com.example.banking.entity.TransactionStatus;
import com.example.banking.entity.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String transactionRef,
        String accountNumber,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        TransactionStatus status,
        String counterpartyAccount,
        String transferRef,
        String description,
        Instant createdAt) {
}

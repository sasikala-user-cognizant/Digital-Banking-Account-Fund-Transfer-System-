package com.example.banking.dto.response;

import com.example.banking.entity.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Only the sender's balance is returned: the receiver's balance is not the sender's business. */
public record TransferResponse(
        String transferRef,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        TransactionStatus status,
        BigDecimal fromBalanceAfter,
        String description,
        Instant createdAt) {
}

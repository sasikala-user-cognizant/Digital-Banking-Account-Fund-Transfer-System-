package com.example.banking.dto.response;

import com.example.banking.entity.AccountStatus;
import com.example.banking.entity.AccountType;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        String accountNumber,
        Long customerId,
        String customerNumber,
        AccountType accountType,
        BigDecimal balance,
        String currency,
        AccountStatus status,
        Instant createdAt) {
}

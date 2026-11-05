package com.example.banking.dto.response;

import com.example.banking.entity.AccountStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record BalanceResponse(
        String accountNumber,
        BigDecimal balance,
        String currency,
        AccountStatus status,
        Instant asOf) {
}

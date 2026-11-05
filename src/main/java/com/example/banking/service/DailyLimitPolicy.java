package com.example.banking.service;

import com.example.banking.config.BankingProperties;
import com.example.banking.entity.Account;
import com.example.banking.exception.LimitExceededException;
import com.example.banking.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Daily debit limit shared by withdrawals and outgoing transfers. */
@Component
@RequiredArgsConstructor
public class DailyLimitPolicy {

    private final TransactionRepository transactionRepository;
    private final BankingProperties properties;
    private final Clock clock;

    public void enforce(Account account, BigDecimal amount) {
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        BigDecimal alreadyDebited = transactionRepository.sumDebitsSince(account.getId(), startOfDay);
        BigDecimal limit = properties.getLimits().getDailyDebitLimit();
        if (alreadyDebited.add(amount).compareTo(limit) > 0) {
            throw new LimitExceededException("Daily debit limit of " + limit.toPlainString()
                    + " would be exceeded (already debited today: " + alreadyDebited.toPlainString() + ")");
        }
    }
}

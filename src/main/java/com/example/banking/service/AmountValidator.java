package com.example.banking.service;

import com.example.banking.config.BankingProperties;
import com.example.banking.exception.InvalidAmountException;
import com.example.banking.exception.LimitExceededException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Shared amount rules for deposits, withdrawals and transfers. */
@Component
@RequiredArgsConstructor
public class AmountValidator {

    private final BankingProperties properties;

    /** Returns the amount normalised to scale 2, or throws INVALID_AMOUNT / LIMIT_EXCEEDED. */
    public BigDecimal validate(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidAmountException("Amount is required");
        }
        if (amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidAmountException("Amount must have at most 2 decimal places");
        }
        BigDecimal max = properties.getLimits().getMaxPerTransaction();
        if (amount.compareTo(max) > 0) {
            throw new LimitExceededException("Amount " + amount.toPlainString()
                    + " exceeds the per-transaction limit of " + max.toPlainString());
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}

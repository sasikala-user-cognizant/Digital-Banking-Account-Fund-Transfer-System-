package com.example.banking.service;

import com.example.banking.config.BankingProperties;
import com.example.banking.dto.request.AmountRequest;
import com.example.banking.dto.response.TransactionResponse;
import com.example.banking.entity.Account;
import com.example.banking.entity.Transaction;
import com.example.banking.entity.TransactionStatus;
import com.example.banking.entity.TransactionType;
import com.example.banking.exception.AccountInactiveException;
import com.example.banking.exception.BusinessException;
import com.example.banking.exception.DuplicateResourceException;
import com.example.banking.exception.ErrorCode;
import com.example.banking.exception.InsufficientBalanceException;
import com.example.banking.exception.InvalidAmountException;
import com.example.banking.exception.LimitExceededException;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.mapper.TransactionMapper;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import com.example.banking.util.NumberGenerator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deposits and withdrawals. Each call runs in one database transaction: the account row is
 * locked (SELECT ... FOR UPDATE), the balance is changed and the ledger row is written, or
 * everything is rolled back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TransactionService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final NumberGenerator numberGenerator;
    private final BankingProperties properties;
    private final Clock clock;

    public TransactionResponse deposit(String accountNumber, AmountRequest request, String idempotencyKey) {
        BigDecimal amount = validateAmount(request.amount());
        String key = normaliseKey(idempotencyKey);

        Account account = lockAccount(accountNumber);

        Optional<Transaction> replay = findReplay(account, key, TransactionType.DEPOSIT, amount);
        if (replay.isPresent()) {
            return transactionMapper.toResponse(replay.get());
        }
        requireActive(account);

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction saved = writeLedger(account, TransactionType.DEPOSIT, amount, key, request.description());
        log.info("Deposit {} of {} into {}", saved.getTransactionRef(), amount, accountNumber);
        return transactionMapper.toResponse(saved);
    }

    public TransactionResponse withdraw(String accountNumber, AmountRequest request, String idempotencyKey) {
        BigDecimal amount = validateAmount(request.amount());
        String key = normaliseKey(idempotencyKey);

        Account account = lockAccount(accountNumber);

        Optional<Transaction> replay = findReplay(account, key, TransactionType.WITHDRAWAL, amount);
        if (replay.isPresent()) {
            return transactionMapper.toResponse(replay.get());
        }
        requireActive(account);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(accountNumber, account.getBalance(), amount);
        }
        enforceDailyLimit(account, amount);

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        Transaction saved = writeLedger(account, TransactionType.WITHDRAWAL, amount, key, request.description());
        log.info("Withdrawal {} of {} from {}", saved.getTransactionRef(), amount, accountNumber);
        return transactionMapper.toResponse(saved);
    }

    // ---------------------------------------------------------------- helpers

    private Account lockAccount(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));
    }

    private void requireActive(Account account) {
        if (!account.isActive()) {
            throw new AccountInactiveException(account.getAccountNumber(), account.getStatus());
        }
    }

    /** Amount must be positive, have at most 2 decimal places and not exceed the per-transaction maximum. */
    private BigDecimal validateAmount(BigDecimal amount) {
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

    private void enforceDailyLimit(Account account, BigDecimal amount) {
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        BigDecimal alreadyDebited = transactionRepository.sumDebitsSince(account.getId(), startOfDay);
        BigDecimal limit = properties.getLimits().getDailyDebitLimit();
        if (alreadyDebited.add(amount).compareTo(limit) > 0) {
            throw new LimitExceededException("Daily debit limit of " + limit.toPlainString()
                    + " would be exceeded (already debited today: " + alreadyDebited.toPlainString() + ")");
        }
    }

    private String normaliseKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return trimmed;
    }

    /**
     * Same key + same request returns the original result; same key with a different request is rejected.
     * Runs after the account lock, so two concurrent requests with one key are handled one after the other.
     */
    private Optional<Transaction> findReplay(Account account, String key, TransactionType type, BigDecimal amount) {
        if (key == null) {
            return Optional.empty();
        }
        Optional<Transaction> existing = transactionRepository.findByAccountIdAndIdempotencyKey(account.getId(), key);
        existing.ifPresent(t -> {
            if (t.getType() != type || t.getAmount().compareTo(amount) != 0) {
                throw new DuplicateResourceException(
                        "Idempotency-Key '" + key + "' was already used for a different request");
            }
        });
        return existing;
    }

    private Transaction writeLedger(Account account, TransactionType type, BigDecimal amount, String key,
                                    String description) {
        Transaction txn = Transaction.builder()
                .transactionRef(numberGenerator.newReference())
                .account(account)
                .type(type)
                .amount(amount)
                .balanceAfter(account.getBalance())
                .status(TransactionStatus.SUCCESS)
                .idempotencyKey(key)
                .description(description == null || description.isBlank() ? null : description.trim())
                .build();
        return transactionRepository.save(txn);
    }
}

package com.example.banking.service;

import com.example.banking.dto.request.TransferRequest;
import com.example.banking.dto.response.TransferResponse;
import com.example.banking.entity.Account;
import com.example.banking.entity.Transaction;
import com.example.banking.entity.TransactionStatus;
import com.example.banking.entity.TransactionType;
import com.example.banking.exception.AccountInactiveException;
import com.example.banking.exception.BusinessException;
import com.example.banking.exception.DuplicateResourceException;
import com.example.banking.exception.ErrorCode;
import com.example.banking.exception.InsufficientBalanceException;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.exception.SameAccountTransferException;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import com.example.banking.util.IdempotencyKeys;
import com.example.banking.util.NumberGenerator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fund transfer between two accounts, atomic in a single database transaction:
 * both rows are locked in ascending account-number order (no deadlock between A->B and B->A),
 * both balances change and both ledger rows are written, or nothing is.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final NumberGenerator numberGenerator;
    private final AmountValidator amountValidator;
    private final DailyLimitPolicy dailyLimitPolicy;

    public TransferResponse transfer(TransferRequest request, String idempotencyKey) {
        BigDecimal amount = amountValidator.validate(request.amount());
        String fromNumber = request.fromAccount().trim();
        String toNumber = request.toAccount().trim();
        if (fromNumber.equals(toNumber)) {
            throw new SameAccountTransferException();
        }
        String key = IdempotencyKeys.normalise(idempotencyKey);

        // Lock both rows in a fixed (sorted) order.
        List<String> lockOrder = Stream.of(fromNumber, toNumber).sorted().toList();
        Map<String, Account> locked = accountRepository.findAllByAccountNumberInForUpdate(lockOrder).stream()
                .collect(Collectors.toMap(Account::getAccountNumber, Function.identity()));
        Account from = locked.get(fromNumber);
        if (from == null) {
            throw new ResourceNotFoundException("Account", fromNumber);
        }
        Account to = locked.get(toNumber);
        if (to == null) {
            throw new ResourceNotFoundException("Account", toNumber);
        }

        Optional<TransferResponse> replay = findReplay(from, key, toNumber, amount);
        if (replay.isPresent()) {
            return replay.get();
        }

        requireActive(from);
        requireActive(to);
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Currency mismatch: " + from.getCurrency() + " to " + to.getCurrency());
        }
        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(fromNumber, from.getBalance(), amount);
        }
        dailyLimitPolicy.enforce(from, amount);

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        accountRepository.saveAll(List.of(from, to));

        String description = request.description() == null || request.description().isBlank()
                ? null : request.description().trim();
        String transferRef = numberGenerator.newReference();
        Transaction debit = Transaction.builder()
                .transactionRef(numberGenerator.newReference())
                .account(from)
                .type(TransactionType.TRANSFER_DEBIT)
                .amount(amount)
                .balanceAfter(from.getBalance())
                .status(TransactionStatus.SUCCESS)
                .counterpartyAccount(toNumber)
                .transferRef(transferRef)
                .idempotencyKey(key)
                .description(description)
                .build();
        Transaction credit = Transaction.builder()
                .transactionRef(numberGenerator.newReference())
                .account(to)
                .type(TransactionType.TRANSFER_CREDIT)
                .amount(amount)
                .balanceAfter(to.getBalance())
                .status(TransactionStatus.SUCCESS)
                .counterpartyAccount(fromNumber)
                .transferRef(transferRef)
                .description(description)
                .build();
        List<Transaction> saved = transactionRepository.saveAll(List.of(debit, credit));

        log.info("Transfer {} of {} from {} to {}", transferRef, amount, fromNumber, toNumber);
        return toResponse(saved.get(0), fromNumber, toNumber);
    }

    private void requireActive(Account account) {
        if (!account.isActive()) {
            throw new AccountInactiveException(account.getAccountNumber(), account.getStatus());
        }
    }

    /** The idempotency key is stored on the debit row of the source account. */
    private Optional<TransferResponse> findReplay(Account from, String key, String toNumber, BigDecimal amount) {
        if (key == null) {
            return Optional.empty();
        }
        return transactionRepository.findByAccountIdAndIdempotencyKey(from.getId(), key).map(existing -> {
            boolean sameRequest = existing.getType() == TransactionType.TRANSFER_DEBIT
                    && existing.getAmount().compareTo(amount) == 0
                    && toNumber.equals(existing.getCounterpartyAccount());
            if (!sameRequest) {
                throw new DuplicateResourceException(
                        "Idempotency-Key '" + key + "' was already used for a different request");
            }
            return toResponse(existing, from.getAccountNumber(), toNumber);
        });
    }

    private TransferResponse toResponse(Transaction debit, String fromNumber, String toNumber) {
        return new TransferResponse(debit.getTransferRef(), fromNumber, toNumber, debit.getAmount(),
                debit.getStatus(), debit.getBalanceAfter(), debit.getDescription(), debit.getCreatedAt());
    }
}

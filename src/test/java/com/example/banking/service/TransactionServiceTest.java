package com.example.banking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.banking.config.BankingProperties;
import com.example.banking.dto.request.AmountRequest;
import com.example.banking.dto.response.TransactionResponse;
import com.example.banking.entity.Account;
import com.example.banking.entity.AccountStatus;
import com.example.banking.entity.AccountType;
import com.example.banking.entity.Customer;
import com.example.banking.entity.Transaction;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final String ACC = "100000000001";
    private static final Instant START_OF_DAY = Instant.parse("2026-10-27T00:00:00Z");

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock NumberGenerator numberGenerator;

    TransactionService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);
        service = new TransactionService(accountRepository, transactionRepository, new TransactionMapper(),
                numberGenerator, new BankingProperties(), fixed);
    }

    private Account account(String balance, AccountStatus status) {
        Customer customer = Customer.builder().id(1L).customerNumber("CUST0000000001").build();
        return Account.builder().id(10L).accountNumber(ACC).customer(customer).accountType(AccountType.SAVINGS)
                .balance(new BigDecimal(balance)).currency("INR").status(status).build();
    }

    private AmountRequest amount(String value) {
        return new AmountRequest(new BigDecimal(value), "test");
    }

    private void accountExists(Account account) {
        when(accountRepository.findByAccountNumberForUpdate(ACC)).thenReturn(Optional.of(account));
    }

    private void ledgerSaves() {
        when(numberGenerator.newReference()).thenReturn("ref-1");
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------ deposit

    @Test
    void deposit_success_increasesBalanceAndWritesLedger() {
        Account account = account("1000.00", AccountStatus.ACTIVE);
        accountExists(account);
        ledgerSaves();

        TransactionResponse response = service.deposit(ACC, amount("500.00"), null);

        assertThat(account.getBalance()).isEqualByComparingTo("1500.00");
        assertThat(response.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.amount()).isEqualByComparingTo("500.00");
        assertThat(response.balanceAfter()).isEqualByComparingTo("1500.00");
        assertThat(response.transactionRef()).isEqualTo("ref-1");
        verify(accountRepository).save(account);
    }

    @Test
    void deposit_zeroAmount_isInvalid() {
        assertThatThrownBy(() -> service.deposit(ACC, amount("0"), null))
                .isInstanceOf(InvalidAmountException.class);
        verify(accountRepository, never()).findByAccountNumberForUpdate(any());
    }

    @Test
    void deposit_negativeAmount_isInvalid() {
        assertThatThrownBy(() -> service.deposit(ACC, amount("-10.00"), null))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void deposit_moreThanTwoDecimals_isInvalid() {
        assertThatThrownBy(() -> service.deposit(ACC, amount("10.123"), null))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("2 decimal");
    }

    @Test
    void deposit_trailingZerosBeyondTwoDecimals_areAccepted() {
        Account account = account("0.00", AccountStatus.ACTIVE);
        accountExists(account);
        ledgerSaves();

        service.deposit(ACC, amount("10.500"), null);

        assertThat(account.getBalance()).isEqualByComparingTo("10.50");
    }

    @Test
    void deposit_nullAmount_isInvalid() {
        assertThatThrownBy(() -> service.deposit(ACC, new AmountRequest(null, null), null))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void deposit_overPerTransactionLimit_isRejected() {
        assertThatThrownBy(() -> service.deposit(ACC, amount("200000.01"), null))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    void deposit_unknownAccount_throwsNotFound() {
        when(accountRepository.findByAccountNumberForUpdate(ACC)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deposit(ACC, amount("10.00"), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deposit_frozenAccount_isRejectedWithoutChanges() {
        Account account = account("1000.00", AccountStatus.FROZEN);
        accountExists(account);

        assertThatThrownBy(() -> service.deposit(ACC, amount("10.00"), null))
                .isInstanceOf(AccountInactiveException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("1000.00");
        verify(transactionRepository, never()).save(any());
    }

    // ------------------------------------------------------------ withdraw

    @Test
    void withdraw_success_decreasesBalanceAndWritesLedger() {
        Account account = account("1000.00", AccountStatus.ACTIVE);
        accountExists(account);
        ledgerSaves();
        when(transactionRepository.sumDebitsSince(10L, START_OF_DAY)).thenReturn(BigDecimal.ZERO);

        TransactionResponse response = service.withdraw(ACC, amount("250.25"), null);

        assertThat(account.getBalance()).isEqualByComparingTo("749.75");
        assertThat(response.type()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(response.balanceAfter()).isEqualByComparingTo("749.75");
    }

    @Test
    void withdraw_exactBalance_leavesZero() {
        Account account = account("300.00", AccountStatus.ACTIVE);
        accountExists(account);
        ledgerSaves();
        when(transactionRepository.sumDebitsSince(eq(10L), any(Instant.class))).thenReturn(BigDecimal.ZERO);

        service.withdraw(ACC, amount("300.00"), null);

        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void withdraw_insufficientBalance_isRejectedWithoutChanges() {
        Account account = account("100.00", AccountStatus.ACTIVE);
        accountExists(account);

        assertThatThrownBy(() -> service.withdraw(ACC, amount("100.01"), null))
                .isInstanceOf(InsufficientBalanceException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdraw_inactiveAccount_isRejected() {
        accountExists(account("1000.00", AccountStatus.INACTIVE));

        assertThatThrownBy(() -> service.withdraw(ACC, amount("10.00"), null))
                .isInstanceOf(AccountInactiveException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdraw_dailyLimitExceeded_isRejected() {
        Account account = account("100000.00", AccountStatus.ACTIVE);
        accountExists(account);
        when(transactionRepository.sumDebitsSince(10L, START_OF_DAY)).thenReturn(new BigDecimal("480000.00"));

        assertThatThrownBy(() -> service.withdraw(ACC, amount("30000.00"), null))
                .isInstanceOf(LimitExceededException.class)
                .hasMessageContaining("Daily debit limit");
        assertThat(account.getBalance()).isEqualByComparingTo("100000.00");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdraw_justUnderDailyLimit_isAllowed() {
        Account account = account("100000.00", AccountStatus.ACTIVE);
        accountExists(account);
        ledgerSaves();
        when(transactionRepository.sumDebitsSince(10L, START_OF_DAY)).thenReturn(new BigDecimal("480000.00"));

        service.withdraw(ACC, amount("20000.00"), null);

        assertThat(account.getBalance()).isEqualByComparingTo("80000.00");
    }

    // ------------------------------------------------------------ idempotency

    @Test
    void withdraw_sameKeyAndRequest_returnsOriginalWithoutReapplying() {
        Account account = account("1000.00", AccountStatus.ACTIVE);
        accountExists(account);
        Transaction original = Transaction.builder().transactionRef("orig-ref").account(account)
                .type(TransactionType.WITHDRAWAL).amount(new BigDecimal("100.00"))
                .balanceAfter(new BigDecimal("900.00")).idempotencyKey("k-1").build();
        when(transactionRepository.findByAccountIdAndIdempotencyKey(10L, "k-1")).thenReturn(Optional.of(original));

        TransactionResponse response = service.withdraw(ACC, amount("100.00"), "k-1");

        assertThat(response.transactionRef()).isEqualTo("orig-ref");
        assertThat(account.getBalance()).isEqualByComparingTo("1000.00");
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdraw_sameKeyDifferentAmount_isRejected() {
        Account account = account("1000.00", AccountStatus.ACTIVE);
        accountExists(account);
        Transaction original = Transaction.builder().transactionRef("orig-ref").account(account)
                .type(TransactionType.WITHDRAWAL).amount(new BigDecimal("100.00"))
                .balanceAfter(new BigDecimal("900.00")).idempotencyKey("k-1").build();
        when(transactionRepository.findByAccountIdAndIdempotencyKey(10L, "k-1")).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.withdraw(ACC, amount("200.00"), "k-1"))
                .isInstanceOf(DuplicateResourceException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void deposit_newKey_isStoredOnLedgerRow() {
        Account account = account("0.00", AccountStatus.ACTIVE);
        accountExists(account);
        ledgerSaves();

        service.deposit(ACC, amount("50.00"), "  k-new  ");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("k-new");
        assertThat(captor.getValue().getBalanceAfter()).isEqualByComparingTo("50.00");
    }

    @Test
    void deposit_keyLongerThan64_isRejected() {
        String longKey = "x".repeat(65);

        assertThatThrownBy(() -> service.deposit(ACC, amount("10.00"), longKey))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}

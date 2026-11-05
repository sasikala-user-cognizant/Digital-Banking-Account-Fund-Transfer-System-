package com.example.banking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.banking.config.BankingProperties;
import com.example.banking.dto.request.TransferRequest;
import com.example.banking.dto.response.TransferResponse;
import com.example.banking.entity.Account;
import com.example.banking.entity.AccountStatus;
import com.example.banking.entity.AccountType;
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
import com.example.banking.exception.SameAccountTransferException;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import com.example.banking.util.NumberGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Captor;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final String A = "100000000001";
    private static final String B = "100000000002";
    private static final Instant START_OF_DAY = Instant.parse("2026-10-27T00:00:00Z");

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock NumberGenerator numberGenerator;
    @Captor ArgumentCaptor<List<Transaction>> ledgerCaptor;

    TransferService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);
        BankingProperties props = new BankingProperties();
        service = new TransferService(accountRepository, transactionRepository, numberGenerator,
                new AmountValidator(props), new DailyLimitPolicy(transactionRepository, props, fixed));
    }

    private Account account(Long id, String number, String balance, AccountStatus status) {
        return Account.builder().id(id).accountNumber(number).accountType(AccountType.SAVINGS)
                .balance(new BigDecimal(balance)).currency("INR").status(status).build();
    }

    private TransferRequest request(String from, String to, String amount) {
        return new TransferRequest(from, to, new BigDecimal(amount), "Rent");
    }

    private void locksReturn(Account... accounts) {
        when(accountRepository.findAllByAccountNumberInForUpdate(List.of(A, B))).thenReturn(List.of(accounts));
    }

    private void noDebitsToday() {
        when(transactionRepository.sumDebitsSince(10L, START_OF_DAY)).thenReturn(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------ success

    @Test
    void transfer_success_movesMoneyAndWritesTwoLinkedLedgerRows() {
        Account a = account(10L, A, "1000.00", AccountStatus.ACTIVE);
        Account b = account(11L, B, "200.00", AccountStatus.ACTIVE);
        locksReturn(a, b);
        noDebitsToday();
        when(numberGenerator.newReference()).thenReturn("t-ref", "d-ref", "c-ref");
        when(transactionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferResponse response = service.transfer(request(A, B, "250.50"), "k-1");

        assertThat(a.getBalance()).isEqualByComparingTo("749.50");
        assertThat(b.getBalance()).isEqualByComparingTo("450.50");
        assertThat(response.transferRef()).isEqualTo("t-ref");
        assertThat(response.fromBalanceAfter()).isEqualByComparingTo("749.50");
        assertThat(response.amount()).isEqualByComparingTo("250.50");

        verify(transactionRepository).saveAll(ledgerCaptor.capture());
        Transaction debit = ledgerCaptor.getValue().get(0);
        Transaction credit = ledgerCaptor.getValue().get(1);
        assertThat(debit.getType()).isEqualTo(TransactionType.TRANSFER_DEBIT);
        assertThat(debit.getAccount()).isSameAs(a);
        assertThat(debit.getCounterpartyAccount()).isEqualTo(B);
        assertThat(debit.getBalanceAfter()).isEqualByComparingTo("749.50");
        assertThat(debit.getIdempotencyKey()).isEqualTo("k-1");
        assertThat(credit.getType()).isEqualTo(TransactionType.TRANSFER_CREDIT);
        assertThat(credit.getAccount()).isSameAs(b);
        assertThat(credit.getCounterpartyAccount()).isEqualTo(A);
        assertThat(credit.getBalanceAfter()).isEqualByComparingTo("450.50");
        assertThat(credit.getIdempotencyKey()).isNull();
        assertThat(debit.getTransferRef()).isEqualTo("t-ref").isEqualTo(credit.getTransferRef());
        assertThat(debit.getAmount()).isEqualByComparingTo(credit.getAmount());
    }

    @Test
    void transfer_locksAccountsInSortedOrderRegardlessOfDirection() {
        // B -> A: the lock query must still receive [A, B] (strict stubbing fails the test otherwise)
        Account a = account(10L, A, "1000.00", AccountStatus.ACTIVE);
        Account b = account(11L, B, "200.00", AccountStatus.ACTIVE);
        locksReturn(a, b);
        when(transactionRepository.sumDebitsSince(11L, START_OF_DAY)).thenReturn(BigDecimal.ZERO);
        when(numberGenerator.newReference()).thenReturn("t-ref", "d-ref", "c-ref");
        when(transactionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transfer(request(B, A, "50.00"), null);

        assertThat(a.getBalance()).isEqualByComparingTo("1050.00");
        assertThat(b.getBalance()).isEqualByComparingTo("150.00");
    }

    @Test
    void transfer_entireBalance_leavesZero() {
        Account a = account(10L, A, "300.00", AccountStatus.ACTIVE);
        Account b = account(11L, B, "0.00", AccountStatus.ACTIVE);
        locksReturn(a, b);
        noDebitsToday();
        when(numberGenerator.newReference()).thenReturn("t-ref", "d-ref", "c-ref");
        when(transactionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transfer(request(A, B, "300.00"), null);

        assertThat(a.getBalance()).isEqualByComparingTo("0.00");
        assertThat(b.getBalance()).isEqualByComparingTo("300.00");
    }

    // ------------------------------------------------------------ validation

    @Test
    void transfer_sameAccount_isRejectedBeforeAnyDatabaseAccess() {
        assertThatThrownBy(() -> service.transfer(request(A, A, "10.00"), null))
                .isInstanceOf(SameAccountTransferException.class);
        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    void transfer_sameAccountWithWhitespace_isStillRejected() {
        assertThatThrownBy(() -> service.transfer(request(A, " " + A + " ", "10.00"), null))
                .isInstanceOf(SameAccountTransferException.class);
    }

    @Test
    void transfer_zeroAmount_isInvalid() {
        assertThatThrownBy(() -> service.transfer(request(A, B, "0"), null))
                .isInstanceOf(InvalidAmountException.class);
        verifyNoInteractions(accountRepository);
    }

    @Test
    void transfer_overPerTransactionLimit_isRejected() {
        assertThatThrownBy(() -> service.transfer(request(A, B, "200000.01"), null))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    void transfer_keyLongerThan64_isRejected() {
        assertThatThrownBy(() -> service.transfer(request(A, B, "10.00"), "x".repeat(65)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void transfer_unknownSource_throwsNotFound() {
        locksReturn(account(11L, B, "200.00", AccountStatus.ACTIVE));

        assertThatThrownBy(() -> service.transfer(request(A, B, "10.00"), null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(A);
    }

    @Test
    void transfer_unknownDestination_throwsNotFound() {
        locksReturn(account(10L, A, "1000.00", AccountStatus.ACTIVE));

        assertThatThrownBy(() -> service.transfer(request(A, B, "10.00"), null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(B);
    }

    // ------------------------------------------------------------ business rules

    @Test
    void transfer_insufficientBalance_changesNothing() {
        Account a = account(10L, A, "100.00", AccountStatus.ACTIVE);
        Account b = account(11L, B, "200.00", AccountStatus.ACTIVE);
        locksReturn(a, b);

        assertThatThrownBy(() -> service.transfer(request(A, B, "100.01"), null))
                .isInstanceOf(InsufficientBalanceException.class);
        assertThat(a.getBalance()).isEqualByComparingTo("100.00");
        assertThat(b.getBalance()).isEqualByComparingTo("200.00");
        verify(accountRepository, never()).saveAll(any());
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void transfer_frozenSource_isRejected() {
        locksReturn(account(10L, A, "1000.00", AccountStatus.FROZEN), account(11L, B, "0.00", AccountStatus.ACTIVE));

        assertThatThrownBy(() -> service.transfer(request(A, B, "10.00"), null))
                .isInstanceOf(AccountInactiveException.class)
                .hasMessageContaining(A);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void transfer_inactiveDestination_isRejectedAndSourceUntouched() {
        Account a = account(10L, A, "1000.00", AccountStatus.ACTIVE);
        locksReturn(a, account(11L, B, "0.00", AccountStatus.INACTIVE));

        assertThatThrownBy(() -> service.transfer(request(A, B, "10.00"), null))
                .isInstanceOf(AccountInactiveException.class)
                .hasMessageContaining(B);
        assertThat(a.getBalance()).isEqualByComparingTo("1000.00");
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void transfer_closedDestination_isRejected() {
        locksReturn(account(10L, A, "1000.00", AccountStatus.ACTIVE), account(11L, B, "0.00", AccountStatus.CLOSED));

        assertThatThrownBy(() -> service.transfer(request(A, B, "10.00"), null))
                .isInstanceOf(AccountInactiveException.class);
    }

    @Test
    void transfer_currencyMismatch_isRejected() {
        Account b = account(11L, B, "0.00", AccountStatus.ACTIVE);
        b.setCurrency("USD");
        locksReturn(account(10L, A, "1000.00", AccountStatus.ACTIVE), b);

        assertThatThrownBy(() -> service.transfer(request(A, B, "10.00"), null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
                    assertThat(e.getMessage()).contains("Currency mismatch");
                });
    }

    @Test
    void transfer_dailyLimitExceeded_isRejectedAndNothingChanges() {
        Account a = account(10L, A, "100000.00", AccountStatus.ACTIVE);
        Account b = account(11L, B, "0.00", AccountStatus.ACTIVE);
        locksReturn(a, b);
        when(transactionRepository.sumDebitsSince(10L, START_OF_DAY)).thenReturn(new BigDecimal("480000.00"));

        assertThatThrownBy(() -> service.transfer(request(A, B, "30000.00"), null))
                .isInstanceOf(LimitExceededException.class)
                .hasMessageContaining("Daily debit limit");
        assertThat(a.getBalance()).isEqualByComparingTo("100000.00");
        assertThat(b.getBalance()).isEqualByComparingTo("0.00");
        verify(transactionRepository, never()).saveAll(any());
    }

    // ------------------------------------------------------------ idempotency

    private Transaction originalDebit(String counterparty, String amount) {
        return Transaction.builder().transactionRef("orig-d").transferRef("orig-t")
                .type(TransactionType.TRANSFER_DEBIT).amount(new BigDecimal(amount))
                .balanceAfter(new BigDecimal("900.00")).counterpartyAccount(counterparty)
                .idempotencyKey("k-1").description("Rent").build();
    }

    @Test
    void transfer_sameKeyAndRequest_returnsOriginalWithoutMovingMoneyAgain() {
        Account a = account(10L, A, "900.00", AccountStatus.ACTIVE);
        Account b = account(11L, B, "300.00", AccountStatus.ACTIVE);
        locksReturn(a, b);
        when(transactionRepository.findByAccountIdAndIdempotencyKey(10L, "k-1"))
                .thenReturn(Optional.of(originalDebit(B, "100.00")));

        TransferResponse response = service.transfer(request(A, B, "100.00"), "k-1");

        assertThat(response.transferRef()).isEqualTo("orig-t");
        assertThat(response.fromBalanceAfter()).isEqualByComparingTo("900.00");
        assertThat(a.getBalance()).isEqualByComparingTo("900.00");
        assertThat(b.getBalance()).isEqualByComparingTo("300.00");
        verify(accountRepository, never()).saveAll(any());
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void transfer_sameKeyDifferentDestination_isRejected() {
        locksReturn(account(10L, A, "900.00", AccountStatus.ACTIVE), account(11L, B, "300.00", AccountStatus.ACTIVE));
        when(transactionRepository.findByAccountIdAndIdempotencyKey(10L, "k-1"))
                .thenReturn(Optional.of(originalDebit("100000000003", "100.00")));

        assertThatThrownBy(() -> service.transfer(request(A, B, "100.00"), "k-1"))
                .isInstanceOf(DuplicateResourceException.class);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void transfer_sameKeyDifferentAmount_isRejected() {
        locksReturn(account(10L, A, "900.00", AccountStatus.ACTIVE), account(11L, B, "300.00", AccountStatus.ACTIVE));
        when(transactionRepository.findByAccountIdAndIdempotencyKey(10L, "k-1"))
                .thenReturn(Optional.of(originalDebit(B, "100.00")));

        assertThatThrownBy(() -> service.transfer(request(A, B, "150.00"), "k-1"))
                .isInstanceOf(DuplicateResourceException.class);
    }
}

package com.example.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.banking.dto.request.AmountRequest;
import com.example.banking.dto.request.CreateAccountRequest;
import com.example.banking.dto.request.CreateCustomerRequest;
import com.example.banking.dto.request.TransferRequest;
import com.example.banking.dto.response.CustomerResponse;
import com.example.banking.dto.response.TransferResponse;
import com.example.banking.entity.Account;
import com.example.banking.entity.AccountStatus;
import com.example.banking.entity.AccountType;
import com.example.banking.entity.Transaction;
import com.example.banking.entity.TransactionType;
import com.example.banking.exception.AccountInactiveException;
import com.example.banking.exception.InsufficientBalanceException;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import com.example.banking.service.AccountService;
import com.example.banking.service.CustomerService;
import com.example.banking.service.TransactionService;
import com.example.banking.service.TransferService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Real services against in-memory H2: proves atomicity and ledger linkage, no mocks. */
@SpringBootTest(properties = "banking.seed.enabled=false")
class TransferFlowIntegrationTest {

    @Autowired CustomerService customerService;
    @Autowired AccountService accountService;
    @Autowired TransactionService transactionService;
    @Autowired TransferService transferService;
    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRepository transactionRepository;

    private String newFundedAccount(String email, String phone, String openingBalance) {
        CustomerResponse customer = customerService.create(
                new CreateCustomerRequest("Transfer", "Tester", email, phone, LocalDate.of(1991, 2, 3)));
        String number = accountService.openAccount(customer.id(), new CreateAccountRequest(AccountType.SAVINGS))
                .accountNumber();
        transactionService.deposit(number, new AmountRequest(new BigDecimal(openingBalance), "seed"), null);
        return number;
    }

    private BigDecimal balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }

    private TransferRequest transfer(String from, String to, String amount) {
        return new TransferRequest(from, to, new BigDecimal(amount), "integration test");
    }

    @Test
    void transfer_movesMoney_conservesTotal_writesTwoLinkedRows_andReplayIsIdempotent() {
        String from = newFundedAccount("xfer.one.a@example.com", "9000000101", "1000.00");
        String to = newFundedAccount("xfer.one.b@example.com", "9000000102", "200.00");
        BigDecimal totalBefore = balanceOf(from).add(balanceOf(to));

        TransferResponse result = transferService.transfer(transfer(from, to, "300.00"), "xfer-key-1");
        TransferResponse replay = transferService.transfer(transfer(from, to, "300.00"), "xfer-key-1");

        assertThat(balanceOf(from)).isEqualByComparingTo("700.00");
        assertThat(balanceOf(to)).isEqualByComparingTo("500.00");
        assertThat(balanceOf(from).add(balanceOf(to))).isEqualByComparingTo(totalBefore);
        assertThat(replay.transferRef()).isEqualTo(result.transferRef());

        List<Transaction> rows = transactionRepository.findByTransferRef(result.transferRef());
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(Transaction::getType)
                .containsExactlyInAnyOrder(TransactionType.TRANSFER_DEBIT, TransactionType.TRANSFER_CREDIT);
        assertThat(rows).allSatisfy(r -> assertThat(r.getAmount()).isEqualByComparingTo("300.00"));
    }

    @Test
    void transfer_insufficientBalance_changesNothing() {
        String from = newFundedAccount("xfer.two.a@example.com", "9000000103", "100.00");
        String to = newFundedAccount("xfer.two.b@example.com", "9000000104", "50.00");
        long ledgerBefore = transactionRepository.count();

        assertThatThrownBy(() -> transferService.transfer(transfer(from, to, "500.00"), null))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(balanceOf(from)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(to)).isEqualByComparingTo("50.00");
        assertThat(transactionRepository.count()).isEqualTo(ledgerBefore);
    }

    @Test
    void transfer_toFrozenAccount_changesNothing() {
        String from = newFundedAccount("xfer.three.a@example.com", "9000000105", "400.00");
        String to = newFundedAccount("xfer.three.b@example.com", "9000000106", "0.01");
        accountService.updateStatus(to, AccountStatus.FROZEN);
        long ledgerBefore = transactionRepository.count();

        assertThatThrownBy(() -> transferService.transfer(transfer(from, to, "100.00"), null))
                .isInstanceOf(AccountInactiveException.class);

        assertThat(balanceOf(from)).isEqualByComparingTo("400.00");
        assertThat(transactionRepository.count()).isEqualTo(ledgerBefore);
    }

    /**
     * All-or-nothing: the credit fails at the database (destination balance would overflow DECIMAL(19,2)),
     * so the already-applied debit and the ledger rows must be rolled back.
     */
    @Test
    void transfer_failureMidway_rollsBackDebitAndLedger() {
        String from = newFundedAccount("xfer.four.a@example.com", "9000000107", "500.00");
        String to = newFundedAccount("xfer.four.b@example.com", "9000000108", "10.00");
        Account destination = accountRepository.findByAccountNumber(to).orElseThrow();
        destination.setBalance(new BigDecimal("99999999999999999.99"));
        accountRepository.save(destination);
        long ledgerBefore = transactionRepository.count();

        assertThatThrownBy(() -> transferService.transfer(transfer(from, to, "100.00"), null))
                .isInstanceOf(Exception.class);

        assertThat(balanceOf(from)).isEqualByComparingTo("500.00");
        assertThat(balanceOf(to)).isEqualByComparingTo("99999999999999999.99");
        assertThat(transactionRepository.count()).isEqualTo(ledgerBefore);
    }
}

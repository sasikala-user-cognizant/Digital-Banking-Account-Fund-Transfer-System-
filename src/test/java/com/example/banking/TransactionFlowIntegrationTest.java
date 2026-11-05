package com.example.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.banking.dto.request.AmountRequest;
import com.example.banking.dto.request.CreateAccountRequest;
import com.example.banking.dto.request.CreateCustomerRequest;
import com.example.banking.dto.response.AccountResponse;
import com.example.banking.dto.response.CustomerResponse;
import com.example.banking.dto.response.TransactionResponse;
import com.example.banking.entity.AccountStatus;
import com.example.banking.entity.AccountType;
import com.example.banking.exception.AccountInactiveException;
import com.example.banking.exception.InsufficientBalanceException;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import com.example.banking.service.AccountService;
import com.example.banking.service.CustomerService;
import com.example.banking.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Runs the real services against the in-memory H2 database (no mocks). */
@SpringBootTest(properties = "banking.seed.enabled=false")
class TransactionFlowIntegrationTest {

    @Autowired CustomerService customerService;
    @Autowired AccountService accountService;
    @Autowired TransactionService transactionService;
    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRepository transactionRepository;

    private AccountResponse newAccount(String email, String phone) {
        CustomerResponse customer = customerService.create(
                new CreateCustomerRequest("Test", "User", email, phone, LocalDate.of(1992, 1, 1)));
        return accountService.openAccount(customer.id(), new CreateAccountRequest(AccountType.SAVINGS));
    }

    private AmountRequest amount(String value) {
        return new AmountRequest(new BigDecimal(value), null);
    }

    private BigDecimal balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }

    @Test
    void depositThenWithdraw_updatesBalanceAndLedger_andIdempotentReplayDoesNotReapply() {
        AccountResponse account = newAccount("flow.one@example.com", "9000000091");
        long before = transactionRepository.count();

        transactionService.deposit(account.accountNumber(), amount("1000.00"), null);
        TransactionResponse withdrawal = transactionService.withdraw(account.accountNumber(), amount("250.25"), "key-1");
        TransactionResponse replay = transactionService.withdraw(account.accountNumber(), amount("250.25"), "key-1");

        assertThat(withdrawal.balanceAfter()).isEqualByComparingTo("749.75");
        assertThat(replay.transactionRef()).isEqualTo(withdrawal.transactionRef());
        assertThat(balanceOf(account.accountNumber())).isEqualByComparingTo("749.75");
        assertThat(transactionRepository.count() - before).isEqualTo(2);
    }

    @Test
    void withdrawMoreThanBalance_isRejectedAndBalanceUnchanged() {
        AccountResponse account = newAccount("flow.two@example.com", "9000000092");
        transactionService.deposit(account.accountNumber(), amount("100.00"), null);
        long before = transactionRepository.count();

        assertThatThrownBy(() -> transactionService.withdraw(account.accountNumber(), amount("500.00"), null))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(balanceOf(account.accountNumber())).isEqualByComparingTo("100.00");
        assertThat(transactionRepository.count()).isEqualTo(before);
    }

    @Test
    void frozenAccount_rejectsDeposit() {
        AccountResponse account = newAccount("flow.three@example.com", "9000000093");
        accountService.updateStatus(account.accountNumber(), AccountStatus.FROZEN);

        assertThatThrownBy(() -> transactionService.deposit(account.accountNumber(), amount("10.00"), null))
                .isInstanceOf(AccountInactiveException.class);
        assertThat(balanceOf(account.accountNumber())).isEqualByComparingTo("0.00");
    }
}

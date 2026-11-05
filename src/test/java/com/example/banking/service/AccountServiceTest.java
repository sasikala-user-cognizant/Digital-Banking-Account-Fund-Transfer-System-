package com.example.banking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.banking.config.BankingProperties;
import com.example.banking.dto.request.CreateAccountRequest;
import com.example.banking.dto.response.AccountResponse;
import com.example.banking.dto.response.BalanceResponse;
import com.example.banking.entity.Account;
import com.example.banking.entity.AccountStatus;
import com.example.banking.entity.AccountType;
import com.example.banking.entity.Customer;
import com.example.banking.entity.CustomerStatus;
import com.example.banking.exception.BusinessException;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.mapper.AccountMapper;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.CustomerRepository;
import com.example.banking.util.NumberGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock CustomerRepository customerRepository;
    @Mock NumberGenerator numberGenerator;

    AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository, customerRepository, new AccountMapper(),
                numberGenerator, new BankingProperties());
    }

    private Customer customer(CustomerStatus status) {
        return Customer.builder().id(1L).customerNumber("CUST0000000001").firstName("Asha").lastName("Raman")
                .email("a@example.com").phone("9876543210").dateOfBirth(LocalDate.of(1990, 5, 15))
                .status(status).build();
    }

    private Account account(String number, String balance, AccountStatus status) {
        return Account.builder().id(10L).accountNumber(number).customer(customer(CustomerStatus.ACTIVE))
                .accountType(AccountType.SAVINGS).balance(new BigDecimal(balance)).currency("INR")
                .status(status).build();
    }

    @Test
    void openAccount_success_startsActiveWithZeroBalance() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(CustomerStatus.ACTIVE)));
        when(numberGenerator.newAccountNumber()).thenReturn("100000000001");
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = service.openAccount(1L, new CreateAccountRequest(AccountType.SAVINGS));

        assertThat(response.accountNumber()).isEqualTo("100000000001");
        assertThat(response.balance()).isEqualByComparingTo("0");
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.customerId()).isEqualTo(1L);
    }

    @Test
    void openAccount_unknownCustomer_throwsNotFound() {
        when(customerRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.openAccount(5L, new CreateAccountRequest(AccountType.CURRENT)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void openAccount_inactiveCustomer_isRejected() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(CustomerStatus.INACTIVE)));

        assertThatThrownBy(() -> service.openAccount(1L, new CreateAccountRequest(AccountType.SAVINGS)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inactive");
        verify(accountRepository, never()).save(any());
    }

    @Test
    void openAccount_retriesWhenGeneratedNumberAlreadyExists() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(CustomerStatus.ACTIVE)));
        when(numberGenerator.newAccountNumber()).thenReturn("100000000001", "100000000002");
        when(accountRepository.existsByAccountNumber("100000000001")).thenReturn(true);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = service.openAccount(1L, new CreateAccountRequest(AccountType.SAVINGS));

        assertThat(response.accountNumber()).isEqualTo("100000000002");
    }

    @Test
    void getBalance_returnsCurrentBalance() {
        when(accountRepository.findByAccountNumber("100000000001"))
                .thenReturn(Optional.of(account("100000000001", "2500.50", AccountStatus.ACTIVE)));

        BalanceResponse response = service.getBalance("100000000001");

        assertThat(response.balance()).isEqualByComparingTo("2500.50");
        assertThat(response.currency()).isEqualTo("INR");
    }

    @Test
    void getByNumber_unknownAccount_throwsNotFound() {
        when(accountRepository.findByAccountNumber("999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByNumber("999")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listByCustomer_unknownCustomer_throwsNotFound() {
        when(customerRepository.existsById(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.listByCustomer(7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listByCustomer_returnsAccounts() {
        when(customerRepository.existsById(1L)).thenReturn(true);
        when(accountRepository.findByCustomerId(1L))
                .thenReturn(List.of(account("100000000001", "0.00", AccountStatus.ACTIVE)));

        assertThat(service.listByCustomer(1L)).hasSize(1);
    }

    @Test
    void updateStatus_freeze_succeeds() {
        when(accountRepository.findByAccountNumberForUpdate("100000000001"))
                .thenReturn(Optional.of(account("100000000001", "100.00", AccountStatus.ACTIVE)));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = service.updateStatus("100000000001", AccountStatus.FROZEN);

        assertThat(response.status()).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    void updateStatus_closeWithBalance_isRejected() {
        when(accountRepository.findByAccountNumberForUpdate("100000000001"))
                .thenReturn(Optional.of(account("100000000001", "100.00", AccountStatus.ACTIVE)));

        assertThatThrownBy(() -> service.updateStatus("100000000001", AccountStatus.CLOSED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("balance is zero");
        verify(accountRepository, never()).save(any());
    }

    @Test
    void updateStatus_closeWithZeroBalance_succeeds() {
        when(accountRepository.findByAccountNumberForUpdate("100000000001"))
                .thenReturn(Optional.of(account("100000000001", "0.00", AccountStatus.ACTIVE)));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.updateStatus("100000000001", AccountStatus.CLOSED).status())
                .isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void updateStatus_closedAccountCannotBeReopened() {
        when(accountRepository.findByAccountNumberForUpdate("100000000001"))
                .thenReturn(Optional.of(account("100000000001", "0.00", AccountStatus.CLOSED)));

        assertThatThrownBy(() -> service.updateStatus("100000000001", AccountStatus.ACTIVE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("closed");
        verify(accountRepository, never()).save(any());
    }
}

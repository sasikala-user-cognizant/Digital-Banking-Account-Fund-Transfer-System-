package com.example.banking.service;

import com.example.banking.config.BankingProperties;
import com.example.banking.dto.request.CreateAccountRequest;
import com.example.banking.dto.response.AccountResponse;
import com.example.banking.dto.response.BalanceResponse;
import com.example.banking.entity.Account;
import com.example.banking.entity.AccountStatus;
import com.example.banking.entity.Customer;
import com.example.banking.exception.BusinessException;
import com.example.banking.exception.ErrorCode;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.mapper.AccountMapper;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.CustomerRepository;
import com.example.banking.util.NumberGenerator;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final AccountMapper accountMapper;
    private final NumberGenerator numberGenerator;
    private final BankingProperties properties;

    public AccountResponse openAccount(Long customerId, CreateAccountRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        if (!customer.isActive()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Customer " + customer.getCustomerNumber() + " is inactive and cannot open accounts");
        }

        String accountNumber;
        do {
            accountNumber = numberGenerator.newAccountNumber();
        } while (accountRepository.existsByAccountNumber(accountNumber));

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .customer(customer)
                .accountType(request.accountType())
                .balance(BigDecimal.ZERO.setScale(2))
                .currency(properties.getAccount().getDefaultCurrency())
                .status(AccountStatus.ACTIVE)
                .build();

        Account saved = accountRepository.save(account);
        log.info("Opened {} account {} for customer {}", saved.getAccountType(),
                saved.getAccountNumber(), customer.getCustomerNumber());
        return accountMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getByNumber(String accountNumber) {
        return accountMapper.toResponse(findOrThrow(accountNumber));
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountNumber) {
        return accountMapper.toBalance(findOrThrow(accountNumber));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listByCustomer(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", customerId);
        }
        return accountRepository.findByCustomerId(customerId).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    public AccountResponse updateStatus(String accountNumber, AccountStatus newStatus) {
        Account account = accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Account " + accountNumber + " is closed and cannot be modified");
        }
        if (newStatus == AccountStatus.CLOSED && account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Account " + accountNumber + " can be closed only when its balance is zero (current balance "
                            + account.getBalance().toPlainString() + ")");
        }

        account.setStatus(newStatus);
        log.info("Account {} status set to {}", accountNumber, newStatus);
        return accountMapper.toResponse(accountRepository.save(account));
    }

    private Account findOrThrow(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));
    }
}

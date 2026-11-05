package com.example.banking.config;

import com.example.banking.dto.request.AmountRequest;
import com.example.banking.dto.request.CreateAccountRequest;
import com.example.banking.dto.request.CreateCustomerRequest;
import com.example.banking.dto.response.AccountResponse;
import com.example.banking.dto.response.CustomerResponse;
import com.example.banking.entity.AccountStatus;
import com.example.banking.entity.AccountType;
import com.example.banking.repository.CustomerRepository;
import com.example.banking.service.AccountService;
import com.example.banking.service.CustomerService;
import com.example.banking.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Loads SYNTHETIC demo data through the real services, so every opening balance has a ledger entry.
 * Enabled with banking.seed.enabled=true (default in application.yml). Skipped if customers already exist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "banking.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final AccountService accountService;
    private final TransactionService transactionService;

    @Override
    public void run(String... args) {
        if (customerRepository.count() > 0) {
            log.info("Seed skipped: customers already exist");
            return;
        }
        log.info("Seeding synthetic demo data ...");
        seed("Asha", "Raman", "asha.raman@example.com", "9000000001", LocalDate.of(1990, 5, 15),
                AccountType.SAVINGS, "25000.00", false);
        seed("Vikram", "Iyer", "vikram.iyer@example.com", "9000000002", LocalDate.of(1985, 11, 2),
                AccountType.SAVINGS, "50000.00", false);
        seed("Meera", "Nair", "meera.nair@example.com", "9000000003", LocalDate.of(1995, 3, 21),
                AccountType.CURRENT, "120000.00", false);
        seed("Karthik", "Subramanian", "karthik.s@example.com", "9000000004", LocalDate.of(1988, 7, 9),
                AccountType.SAVINGS, "1000.00", false);
        seed("Divya", "Menon", "divya.menon@example.com", "9000000005", LocalDate.of(1992, 12, 30),
                AccountType.SAVINGS, "10000.00", true);
        log.info("Seeding complete.");
    }

    private void seed(String first, String last, String email, String phone, LocalDate dob,
                      AccountType type, String openingDeposit, boolean freeze) {
        CustomerResponse customer = customerService.create(new CreateCustomerRequest(first, last, email, phone, dob));
        AccountResponse account = accountService.openAccount(customer.id(), new CreateAccountRequest(type));
        transactionService.deposit(account.accountNumber(),
                new AmountRequest(new BigDecimal(openingDeposit), "Opening balance (synthetic)"), null);
        if (freeze) {
            accountService.updateStatus(account.accountNumber(), AccountStatus.FROZEN);
        }
        log.info("  customer id={} {} {} | account {} | {} {} | status {}", customer.id(), first, last,
                account.accountNumber(), type, openingDeposit, freeze ? "FROZEN" : "ACTIVE");
    }
}

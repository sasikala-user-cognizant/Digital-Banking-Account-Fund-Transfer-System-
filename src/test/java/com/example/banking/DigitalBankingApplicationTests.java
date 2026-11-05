package com.example.banking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.banking.config.BankingProperties;
import com.example.banking.exception.GlobalExceptionHandler;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.CustomerRepository;
import com.example.banking.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Smoke test: context starts, schema is generated, core beans are wired. */
@SpringBootTest(properties = "banking.seed.enabled=false")
class DigitalBankingApplicationTests {

    @Autowired CustomerRepository customers;
    @Autowired AccountRepository accounts;
    @Autowired TransactionRepository transactions;
    @Autowired BankingProperties properties;
    @Autowired GlobalExceptionHandler exceptionHandler;

    @Test
    void contextLoads() {
        assertThat(customers).isNotNull();
        assertThat(accounts).isNotNull();
        assertThat(transactions).isNotNull();
        assertThat(exceptionHandler).isNotNull();
        assertThat(properties.getLimits().getMaxPerTransaction()).isPositive();
    }
}

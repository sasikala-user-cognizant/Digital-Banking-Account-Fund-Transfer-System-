package com.example.banking.config;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Typed binding for the {@code banking.*} keys in application.yml. */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "banking")
public class BankingProperties {

    private Limits limits = new Limits();
    private Account account = new Account();

    @Getter
    @Setter
    public static class Limits {
        private BigDecimal maxPerTransaction = new BigDecimal("200000.00");
        private BigDecimal dailyDebitLimit = new BigDecimal("500000.00");
    }

    @Getter
    @Setter
    public static class Account {
        private String defaultCurrency = "INR";
        private int minimumCustomerAge = 18;
    }
}

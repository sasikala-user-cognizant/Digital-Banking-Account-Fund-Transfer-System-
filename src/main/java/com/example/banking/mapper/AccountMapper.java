package com.example.banking.mapper;

import com.example.banking.dto.response.AccountResponse;
import com.example.banking.dto.response.BalanceResponse;
import com.example.banking.entity.Account;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Call from inside a transaction: it touches the lazy customer association. */
@Component
public class AccountMapper {

    public AccountResponse toResponse(Account a) {
        return new AccountResponse(a.getAccountNumber(), a.getCustomer().getId(),
                a.getCustomer().getCustomerNumber(), a.getAccountType(), a.getBalance(),
                a.getCurrency(), a.getStatus(), a.getCreatedAt());
    }

    public BalanceResponse toBalance(Account a) {
        return new BalanceResponse(a.getAccountNumber(), a.getBalance(), a.getCurrency(),
                a.getStatus(), Instant.now());
    }
}

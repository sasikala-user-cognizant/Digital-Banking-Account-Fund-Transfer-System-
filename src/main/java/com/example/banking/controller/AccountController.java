package com.example.banking.controller;

import com.example.banking.dto.request.CreateAccountRequest;
import com.example.banking.dto.request.UpdateAccountStatusRequest;
import com.example.banking.dto.response.AccountResponse;
import com.example.banking.dto.response.BalanceResponse;
import com.example.banking.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account management and balance enquiry")
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/customers/{customerId}/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open an account for a customer")
    public AccountResponse open(@PathVariable Long customerId,
                                @Valid @RequestBody CreateAccountRequest request) {
        return accountService.openAccount(customerId, request);
    }

    @GetMapping("/customers/{customerId}/accounts")
    @Operation(summary = "List a customer's accounts")
    public List<AccountResponse> listForCustomer(@PathVariable Long customerId) {
        return accountService.listByCustomer(customerId);
    }

    @GetMapping("/accounts/{accountNumber}")
    @Operation(summary = "Get account details")
    public AccountResponse get(@PathVariable String accountNumber) {
        return accountService.getByNumber(accountNumber);
    }

    @GetMapping("/accounts/{accountNumber}/balance")
    @Operation(summary = "Balance enquiry")
    public BalanceResponse balance(@PathVariable String accountNumber) {
        return accountService.getBalance(accountNumber);
    }

    @PatchMapping("/accounts/{accountNumber}/status")
    @Operation(summary = "Activate, deactivate, freeze or close an account")
    public AccountResponse updateStatus(@PathVariable String accountNumber,
                                        @Valid @RequestBody UpdateAccountStatusRequest request) {
        return accountService.updateStatus(accountNumber, request.status());
    }
}

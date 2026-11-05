package com.example.banking.controller;

import com.example.banking.dto.request.AmountRequest;
import com.example.banking.dto.response.TransactionResponse;
import com.example.banking.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts/{accountNumber}")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Deposits and withdrawals")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Deposit money into an account")
    public TransactionResponse deposit(
            @PathVariable String accountNumber,
            @Parameter(description = "Optional. Repeating a request with the same key returns the original result.")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {
        return transactionService.deposit(accountNumber, request, idempotencyKey);
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Withdraw money from an account")
    public TransactionResponse withdraw(
            @PathVariable String accountNumber,
            @Parameter(description = "Optional. Repeating a request with the same key returns the original result.")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {
        return transactionService.withdraw(accountNumber, request, idempotencyKey);
    }
}

package com.example.banking.controller;

import com.example.banking.dto.request.TransferRequest;
import com.example.banking.dto.response.TransferResponse;
import com.example.banking.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Tag(name = "Transfers", description = "Fund transfer between accounts")
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Transfer funds between two accounts",
            description = "Atomic: the debit and the credit both happen, or neither does.")
    public TransferResponse transfer(
            @Parameter(description = "Optional. Repeating a request with the same key returns the original result.")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return transferService.transfer(request, idempotencyKey);
    }
}

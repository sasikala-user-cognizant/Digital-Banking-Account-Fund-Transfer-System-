package com.example.banking.dto.request;

import com.example.banking.entity.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(
        @Schema(example = "SAVINGS") @NotNull AccountType accountType) {
}

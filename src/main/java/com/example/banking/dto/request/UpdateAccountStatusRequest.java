package com.example.banking.dto.request;

import com.example.banking.entity.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(
        @Schema(example = "FROZEN") @NotNull AccountStatus status) {
}

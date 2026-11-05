package com.example.banking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TransferRequest(
        @Schema(example = "100000000001") @NotBlank String fromAccount,
        @Schema(example = "100000000002") @NotBlank String toAccount,
        @Schema(example = "2500.00") @NotNull BigDecimal amount,
        @Schema(example = "Rent") @Size(max = 200) String description) {
}

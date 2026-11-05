package com.example.banking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Body for deposit and withdrawal. Positivity and decimal places are checked in the service (INVALID_AMOUNT). */
public record AmountRequest(
        @Schema(example = "5000.00") @NotNull BigDecimal amount,
        @Schema(example = "Salary credit") @Size(max = 200) String description) {
}

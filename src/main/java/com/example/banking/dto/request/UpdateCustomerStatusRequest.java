package com.example.banking.dto.request;

import com.example.banking.entity.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateCustomerStatusRequest(
        @Schema(example = "INACTIVE") @NotNull CustomerStatus status) {
}

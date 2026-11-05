package com.example.banking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateCustomerRequest(
        @Schema(example = "Asha") @NotBlank @Size(max = 50) String firstName,
        @Schema(example = "Raman") @NotBlank @Size(max = 50) String lastName,
        @Schema(example = "asha.raman@example.com") @NotBlank @Email @Size(max = 100) String email,
        @Schema(example = "9876543210") @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{10,14}$", message = "must be 10 to 14 digits, optional leading +") String phone,
        @Schema(example = "1990-05-15") @NotNull @Past LocalDate dateOfBirth) {
}

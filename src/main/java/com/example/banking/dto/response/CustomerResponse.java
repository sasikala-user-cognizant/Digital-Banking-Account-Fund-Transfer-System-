package com.example.banking.dto.response;

import com.example.banking.entity.CustomerStatus;
import java.time.Instant;
import java.time.LocalDate;

public record CustomerResponse(
        Long id,
        String customerNumber,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        CustomerStatus status,
        Instant createdAt) {
}

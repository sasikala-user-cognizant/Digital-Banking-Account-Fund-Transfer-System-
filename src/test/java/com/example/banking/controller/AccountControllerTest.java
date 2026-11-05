package com.example.banking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.banking.dto.response.AccountResponse;
import com.example.banking.dto.response.BalanceResponse;
import com.example.banking.entity.AccountStatus;
import com.example.banking.entity.AccountType;
import com.example.banking.exception.BusinessException;
import com.example.banking.exception.ErrorCode;
import com.example.banking.service.AccountService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AccountService accountService;

    @Test
    void openAccount_returns201() throws Exception {
        when(accountService.openAccount(eq(1L), any())).thenReturn(new AccountResponse("100000000001", 1L,
                "CUST0000000001", AccountType.SAVINGS, new BigDecimal("0.00"), "INR", AccountStatus.ACTIVE, null));

        mockMvc.perform(post("/api/v1/customers/1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"SAVINGS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value("100000000001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void openAccount_unknownAccountType_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/customers/1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"PLATINUM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void openAccount_missingAccountType_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/customers/1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void balance_returns200() throws Exception {
        when(accountService.getBalance("100000000001")).thenReturn(new BalanceResponse("100000000001",
                new BigDecimal("2500.50"), "INR", AccountStatus.ACTIVE, Instant.parse("2026-10-27T10:00:00Z")));

        mockMvc.perform(get("/api/v1/accounts/100000000001/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(2500.50))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void closeAccountWithBalance_returns422() throws Exception {
        when(accountService.updateStatus("100000000001", AccountStatus.CLOSED))
                .thenThrow(new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "balance is zero required"));

        mockMvc.perform(patch("/api/v1/accounts/100000000001/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void unknownEndpoint_returns404InStandardFormat() throws Exception {
        mockMvc.perform(get("/api/v1/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }
}

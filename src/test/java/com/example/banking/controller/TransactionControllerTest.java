package com.example.banking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.banking.dto.response.TransactionResponse;
import com.example.banking.entity.TransactionStatus;
import com.example.banking.entity.TransactionType;
import com.example.banking.exception.AccountInactiveException;
import com.example.banking.exception.InsufficientBalanceException;
import com.example.banking.exception.InvalidAmountException;
import com.example.banking.service.TransactionService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    private static final String ACC = "100000000001";

    @Autowired MockMvc mockMvc;
    @MockBean TransactionService transactionService;

    private TransactionResponse sample(TransactionType type, String amount, String balanceAfter) {
        return new TransactionResponse("ref-1", ACC, type, new BigDecimal(amount), new BigDecimal(balanceAfter),
                TransactionStatus.SUCCESS, null, null, "test", null);
    }

    @Test
    void deposit_returns201AndPassesIdempotencyKey() throws Exception {
        when(transactionService.deposit(eq(ACC), any(), eq("key-1")))
                .thenReturn(sample(TransactionType.DEPOSIT, "5000.00", "15000.00"));

        mockMvc.perform(post("/api/v1/accounts/" + ACC + "/deposit")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000.00,\"description\":\"Salary credit\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.balanceAfter").value(15000.00));

        verify(transactionService).deposit(eq(ACC), any(), eq("key-1"));
    }

    @Test
    void withdraw_withoutKey_passesNull() throws Exception {
        when(transactionService.withdraw(eq(ACC), any(), isNull()))
                .thenReturn(sample(TransactionType.WITHDRAWAL, "100.00", "900.00"));

        mockMvc.perform(post("/api/v1/accounts/" + ACC + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"));
    }

    @Test
    void deposit_missingAmount_returns400WithDetails() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/" + ACC + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"no amount\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("amount"));
    }

    @Test
    void deposit_nonNumericAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/" + ACC + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void deposit_invalidAmount_returns400() throws Exception {
        when(transactionService.deposit(eq(ACC), any(), any()))
                .thenThrow(new InvalidAmountException("Amount must be greater than zero"));

        mockMvc.perform(post("/api/v1/accounts/" + ACC + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_AMOUNT"));
    }

    @Test
    void withdraw_insufficientBalance_returns422() throws Exception {
        when(transactionService.withdraw(eq(ACC), any(), any()))
                .thenThrow(new InsufficientBalanceException(ACC, new BigDecimal("50.00"), new BigDecimal("100.00")));

        mockMvc.perform(post("/api/v1/accounts/" + ACC + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.path").value("/api/v1/accounts/" + ACC + "/withdraw"));
    }

    @Test
    void deposit_inactiveAccount_returns422() throws Exception {
        when(transactionService.deposit(eq(ACC), any(), any()))
                .thenThrow(new AccountInactiveException(ACC, "FROZEN"));

        mockMvc.perform(post("/api/v1/accounts/" + ACC + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("ACCOUNT_INACTIVE"));
    }
}

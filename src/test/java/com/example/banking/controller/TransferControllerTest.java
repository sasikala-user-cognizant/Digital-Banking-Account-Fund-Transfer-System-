package com.example.banking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.banking.dto.response.TransferResponse;
import com.example.banking.entity.TransactionStatus;
import com.example.banking.exception.AccountInactiveException;
import com.example.banking.exception.InsufficientBalanceException;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.exception.SameAccountTransferException;
import com.example.banking.service.TransferService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    private static final String BODY =
            "{\"fromAccount\":\"100000000001\",\"toAccount\":\"100000000002\",\"amount\":2500.00,\"description\":\"Rent\"}";

    @Autowired MockMvc mockMvc;
    @MockBean TransferService transferService;

    @Test
    void transfer_returns201AndPassesIdempotencyKey() throws Exception {
        when(transferService.transfer(any(), eq("key-9"))).thenReturn(new TransferResponse("t-ref", "100000000001",
                "100000000002", new BigDecimal("2500.00"), TransactionStatus.SUCCESS, new BigDecimal("12500.00"),
                "Rent", null));

        mockMvc.perform(post("/api/v1/transfers").header("Idempotency-Key", "key-9")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferRef").value("t-ref"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.fromBalanceAfter").value(12500.00));

        verify(transferService).transfer(any(), eq("key-9"));
    }

    @Test
    void transfer_missingFields_returns400WithDetails() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"nothing else\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void transfer_sameAccount_returns422() throws Exception {
        when(transferService.transfer(any(), any())).thenThrow(new SameAccountTransferException());

        mockMvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("SAME_ACCOUNT_TRANSFER"));
    }

    @Test
    void transfer_insufficientBalance_returns422() throws Exception {
        when(transferService.transfer(any(), any())).thenThrow(
                new InsufficientBalanceException("100000000001", new BigDecimal("10.00"), new BigDecimal("2500.00")));

        mockMvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_BALANCE"));
    }

    @Test
    void transfer_inactiveAccount_returns422() throws Exception {
        when(transferService.transfer(any(), any())).thenThrow(new AccountInactiveException("100000000002", "FROZEN"));

        mockMvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void transfer_unknownAccount_returns404() throws Exception {
        when(transferService.transfer(any(), any())).thenThrow(new ResourceNotFoundException("Account", "100000000002"));

        mockMvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }
}

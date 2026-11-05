package com.example.banking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.banking.dto.response.CustomerResponse;
import com.example.banking.entity.CustomerStatus;
import com.example.banking.exception.DuplicateResourceException;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.service.CustomerService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean CustomerService customerService;

    private static final String VALID_BODY = """
            {"firstName":"Asha","lastName":"Raman","email":"asha@example.com",
             "phone":"9876543210","dateOfBirth":"1990-05-15"}""";

    @Test
    void create_validRequest_returns201() throws Exception {
        when(customerService.create(any())).thenReturn(new CustomerResponse(1L, "CUST0000000001", "Asha", "Raman",
                "asha@example.com", "9876543210", LocalDate.of(1990, 5, 15), CustomerStatus.ACTIVE, null));

        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerNumber").value("CUST0000000001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void create_invalidRequest_returns400WithFieldDetails() throws Exception {
        String body = """
                {"firstName":"","lastName":"Raman","email":"not-an-email",
                 "phone":"123","dateOfBirth":"2090-01-01"}""";

        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void create_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void create_duplicateEmail_returns409() throws Exception {
        when(customerService.create(any())).thenThrow(new DuplicateResourceException("email exists"));

        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void get_unknownCustomer_returns404() throws Exception {
        when(customerService.get(99L)).thenThrow(new ResourceNotFoundException("Customer", 99L));

        mockMvc.perform(get("/api/v1/customers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/customers/99"));
    }

    @Test
    void get_nonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/customers/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}

package com.example.banking.controller;

import com.example.banking.dto.request.CreateCustomerRequest;
import com.example.banking.dto.request.UpdateCustomerRequest;
import com.example.banking.dto.request.UpdateCustomerStatusRequest;
import com.example.banking.dto.response.CustomerResponse;
import com.example.banking.dto.response.PageResponse;
import com.example.banking.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer management")
public class CustomerController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerService customerService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a customer")
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        return customerService.create(request);
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get a customer by id")
    public CustomerResponse get(@PathVariable Long customerId) {
        return customerService.get(customerId);
    }

    @GetMapping
    @Operation(summary = "List customers (paged)")
    public PageResponse<CustomerResponse> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return customerService.list(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    @PutMapping("/{customerId}")
    @Operation(summary = "Update customer contact details")
    public CustomerResponse update(@PathVariable Long customerId,
                                   @Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.update(customerId, request);
    }

    @PatchMapping("/{customerId}/status")
    @Operation(summary = "Activate or deactivate a customer")
    public CustomerResponse updateStatus(@PathVariable Long customerId,
                                         @Valid @RequestBody UpdateCustomerStatusRequest request) {
        return customerService.updateStatus(customerId, request.status());
    }
}

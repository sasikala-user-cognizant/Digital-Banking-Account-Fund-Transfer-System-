package com.example.banking.mapper;

import com.example.banking.dto.response.CustomerResponse;
import com.example.banking.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getCustomerNumber(), c.getFirstName(), c.getLastName(),
                c.getEmail(), c.getPhone(), c.getDateOfBirth(), c.getStatus(), c.getCreatedAt());
    }
}

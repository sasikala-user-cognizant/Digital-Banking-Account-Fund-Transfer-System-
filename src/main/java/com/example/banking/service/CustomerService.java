package com.example.banking.service;

import com.example.banking.config.BankingProperties;
import com.example.banking.dto.request.CreateCustomerRequest;
import com.example.banking.dto.request.UpdateCustomerRequest;
import com.example.banking.dto.response.CustomerResponse;
import com.example.banking.dto.response.PageResponse;
import com.example.banking.entity.Customer;
import com.example.banking.entity.CustomerStatus;
import com.example.banking.exception.BusinessException;
import com.example.banking.exception.DuplicateResourceException;
import com.example.banking.exception.ErrorCode;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.mapper.CustomerMapper;
import com.example.banking.repository.CustomerRepository;
import com.example.banking.util.NumberGenerator;
import java.time.LocalDate;
import java.time.Period;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final NumberGenerator numberGenerator;
    private final BankingProperties properties;

    public CustomerResponse create(CreateCustomerRequest request) {
        String email = normalise(request.email());
        if (customerRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("A customer with email " + email + " already exists");
        }
        validateAge(request.dateOfBirth());

        String customerNumber;
        do {
            customerNumber = numberGenerator.newCustomerNumber();
        } while (customerRepository.findByCustomerNumber(customerNumber).isPresent());

        Customer customer = Customer.builder()
                .customerNumber(customerNumber)
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(email)
                .phone(request.phone().trim())
                .dateOfBirth(request.dateOfBirth())
                .status(CustomerStatus.ACTIVE)
                .build();

        Customer saved = customerRepository.save(customer);
        log.info("Created customer {}", saved.getCustomerNumber());
        return customerMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return customerMapper.toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(int page, int size) {
        return PageResponse.from(customerRepository
                .findAll(PageRequest.of(page, size, Sort.by("id")))
                .map(customerMapper::toResponse));
    }

    public CustomerResponse update(Long id, UpdateCustomerRequest request) {
        Customer customer = findOrThrow(id);
        String email = normalise(request.email());
        if (!customer.getEmail().equalsIgnoreCase(email) && customerRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("A customer with email " + email + " already exists");
        }
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setEmail(email);
        customer.setPhone(request.phone().trim());
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    public CustomerResponse updateStatus(Long id, CustomerStatus status) {
        Customer customer = findOrThrow(id);
        customer.setStatus(status);
        log.info("Customer {} status set to {}", customer.getCustomerNumber(), status);
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    private Customer findOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    private void validateAge(LocalDate dateOfBirth) {
        int minAge = properties.getAccount().getMinimumCustomerAge();
        if (Period.between(dateOfBirth, LocalDate.now()).getYears() < minAge) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Customer must be at least " + minAge + " years old");
        }
    }

    private String normalise(String email) {
        return email.trim().toLowerCase();
    }
}

package com.example.banking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.banking.config.BankingProperties;
import com.example.banking.dto.request.CreateCustomerRequest;
import com.example.banking.dto.request.UpdateCustomerRequest;
import com.example.banking.dto.response.CustomerResponse;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock NumberGenerator numberGenerator;

    CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customerRepository, new CustomerMapper(), numberGenerator,
                new BankingProperties());
    }

    private CreateCustomerRequest validRequest() {
        return new CreateCustomerRequest("Asha", "Raman", "Asha.Raman@Example.com", "9876543210",
                LocalDate.of(1990, 5, 15));
    }

    private Customer existing(Long id, String email) {
        return Customer.builder().id(id).customerNumber("CUST0000000001").firstName("Asha").lastName("Raman")
                .email(email).phone("9876543210").dateOfBirth(LocalDate.of(1990, 5, 15))
                .status(CustomerStatus.ACTIVE).build();
    }

    @Test
    void create_success_normalisesEmailAndDefaultsToActive() {
        when(numberGenerator.newCustomerNumber()).thenReturn("CUST0000000001");
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        CustomerResponse response = service.create(validRequest());

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.customerNumber()).isEqualTo("CUST0000000001");
        assertThat(response.email()).isEqualTo("asha.raman@example.com");
        assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void create_duplicateEmail_throwsAndDoesNotSave() {
        when(customerRepository.existsByEmailIgnoreCase("asha.raman@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(validRequest()))
                .isInstanceOf(DuplicateResourceException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void create_underAge_throwsBusinessRuleViolation() {
        CreateCustomerRequest minor = new CreateCustomerRequest("Kid", "Test", "kid@example.com",
                "9876543210", LocalDate.now().minusYears(17));

        assertThatThrownBy(() -> service.create(minor))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void get_unknownId_throwsNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_changingToExistingEmail_throwsDuplicate() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing(1L, "a@example.com")));
        when(customerRepository.existsByEmailIgnoreCase("b@example.com")).thenReturn(true);

        UpdateCustomerRequest request = new UpdateCustomerRequest("Asha", "Raman", "b@example.com", "9876543210");

        assertThatThrownBy(() -> service.update(1L, request)).isInstanceOf(DuplicateResourceException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void update_sameEmail_updatesContactDetails() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing(1L, "a@example.com")));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = service.update(1L,
                new UpdateCustomerRequest("Asha", "Raman", "A@example.com", "9123456789"));

        assertThat(response.phone()).isEqualTo("9123456789");
        assertThat(response.email()).isEqualTo("a@example.com");
    }

    @Test
    void updateStatus_setsNewStatus() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing(1L, "a@example.com")));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = service.updateStatus(1L, CustomerStatus.INACTIVE);

        assertThat(response.status()).isEqualTo(CustomerStatus.INACTIVE);
    }
}

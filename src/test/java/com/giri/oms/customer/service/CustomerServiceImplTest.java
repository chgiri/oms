package com.giri.oms.customer.service;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.common.exception.InvalidSortFieldException;
import com.giri.oms.customer.dto.CustomerRequest;
import com.giri.oms.customer.dto.CustomerResponse;
import com.giri.oms.customer.entity.Customer;
import com.giri.oms.customer.entity.CustomerStatus;
import com.giri.oms.customer.exception.CustomerEmailAlreadyExistsException;
import com.giri.oms.customer.exception.CustomerNotFoundException;
import com.giri.oms.customer.mapper.CustomerMapper;
import com.giri.oms.customer.repository.CustomerRepository;
import com.giri.oms.customer.service.impl.CustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests — no Spring context, no DB. Repository and mapper are mocked
 * so these run in milliseconds and only exercise CustomerServiceImpl's own logic.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerMapper customerMapper;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private Customer customer;
    private CustomerRequest customerRequest;
    private CustomerResponse customerResponse;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(1L);
        customer.setFirstName("Ada");
        customer.setLastName("Lovelace");
        customer.setEmail("alice@example.com");
        customer.setPhone("+1 555-0100");
        customer.setStreet("123 Main St");
        customer.setCity("Springfield");
        customer.setState("IL");
        customer.setPostalCode("62701");
        customer.setCountry("USA");
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());

        customerRequest = new CustomerRequest();
        customerRequest.setFirstName("Ada");
        customerRequest.setLastName("Lovelace");
        customerRequest.setEmail("alice@example.com");
        customerRequest.setPhone("+1 555-0100");
        customerRequest.setStreet("123 Main St");
        customerRequest.setCity("Springfield");
        customerRequest.setState("IL");
        customerRequest.setPostalCode("62701");
        customerRequest.setCountry("USA");
        customerRequest.setStatus(CustomerStatus.ACTIVE);

        customerResponse = new CustomerResponse(
                1L, "Ada", "Lovelace", "alice@example.com", "+1 555-0100",
                "123 Main St", "Springfield", "IL", "62701", "USA",
                CustomerStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
    }

    @Nested
    class CreateCustomer {

        @Test
        void savesAndReturnsMappedResponse() {
            when(customerRepository.existsByEmailIgnoreCase(customerRequest.getEmail())).thenReturn(false);
            when(customerMapper.mapToCustomer(customerRequest)).thenReturn(customer);
            when(customerRepository.save(customer)).thenReturn(customer);
            when(customerMapper.mapToCustomerResponse(customer)).thenReturn(customerResponse);

            CustomerResponse result = customerService.createCustomer(customerRequest);

            assertThat(result).isEqualTo(customerResponse);
            verify(customerRepository).save(customer);
        }

        @Test
        void throwsCustomerEmailAlreadyExistsException_whenEmailIsTaken() {
            when(customerRepository.existsByEmailIgnoreCase(customerRequest.getEmail())).thenReturn(true);

            assertThatThrownBy(() -> customerService.createCustomer(customerRequest))
                    .isInstanceOf(CustomerEmailAlreadyExistsException.class)
                    .hasMessageContaining(customerRequest.getEmail());

            verify(customerRepository, never()).save(any());
        }
    }

    @Nested
    class GetCustomerById {

        @Test
        void returnsMappedResponse_whenCustomerExists() {
            when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(customerMapper.mapToCustomerResponse(customer)).thenReturn(customerResponse);

            CustomerResponse result = customerService.getCustomerById(1L);

            assertThat(result).isEqualTo(customerResponse);
        }

        @Test
        void throwsCustomerNotFoundException_whenCustomerDoesNotExist() {
            when(customerRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.getCustomerById(99L))
                    .isInstanceOf(CustomerNotFoundException.class)
                    .hasMessageContaining("99");

            verify(customerMapper, never()).mapToCustomerResponse(any());
        }
    }

    @Nested
    class GetAllCustomers {

        @Test
        void returnsPagedResponse_whenSortFieldIsValid() {
            Page<Customer> customerPage = new PageImpl<>(List.of(customer));
            when(customerRepository.findAll(any(Pageable.class))).thenReturn(customerPage);
            when(customerMapper.mapToCustomerResponse(customer)).thenReturn(customerResponse);

            PagedResponse<CustomerResponse> result = customerService.getAllCustomers(0, 10, "lastName", "asc");

            assertThat(result.getContent()).containsExactly(customerResponse);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        void throwsInvalidSortFieldException_whenSortFieldIsNotAllowed() {
            assertThatThrownBy(() -> customerService.getAllCustomers(0, 10, "secretInternalField", "asc"))
                    .isInstanceOf(InvalidSortFieldException.class)
                    .hasMessageContaining("secretInternalField");

            verifyNoInteractions(customerRepository);
        }

        @Test
        void sortsByStatus_nowThatItsOnTheAllowList() {
            when(customerRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(customer)));
            when(customerMapper.mapToCustomerResponse(any())).thenReturn(customerResponse);

            PagedResponse<CustomerResponse> result = customerService.getAllCustomers(0, 10, "status", "asc");

            assertThat(result.getContent()).containsExactly(customerResponse);
        }

        @Test
        void sortsDescending_whenSortDirIsDesc() {
            when(customerRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(customer)));
            when(customerMapper.mapToCustomerResponse(any())).thenReturn(customerResponse);

            customerService.getAllCustomers(0, 10, "email", "desc");

            var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(customerRepository).findAll(pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getSort().getOrderFor("email").isDescending()).isTrue();
        }
    }

    @Nested
    class UpdateCustomer {

        @Test
        void updatesAndReturnsMappedResponse_whenCustomerExists() {
            when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(customerRepository.save(customer)).thenReturn(customer);
            when(customerMapper.mapToCustomerResponse(customer)).thenReturn(customerResponse);

            CustomerResponse result = customerService.updateCustomer(1L, customerRequest);

            assertThat(result).isEqualTo(customerResponse);
            verify(customerMapper).mapToCustomer(customerRequest, customer);
            verify(customerRepository).save(customer);
        }

        @Test
        void throwsCustomerNotFoundException_whenCustomerDoesNotExist() {
            when(customerRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.updateCustomer(99L, customerRequest))
                    .isInstanceOf(CustomerNotFoundException.class);

            verify(customerRepository, never()).save(any());
        }

        @Test
        void allowsUpdate_whenEmailIsUnchanged() {
            // customer's current email matches the request email exactly — should NOT
            // trigger the duplicate-email check against itself
            when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(customerRepository.save(customer)).thenReturn(customer);
            when(customerMapper.mapToCustomerResponse(customer)).thenReturn(customerResponse);

            customerService.updateCustomer(1L, customerRequest);

            verify(customerRepository, never()).existsByEmailIgnoreCase(any());
        }

        @Test
        void throwsCustomerEmailAlreadyExistsException_whenChangingToAnotherCustomersEmail() {
            when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

            CustomerRequest changedEmailRequest = new CustomerRequest();
            changedEmailRequest.setFirstName("Ada");
            changedEmailRequest.setLastName("Lovelace");
            changedEmailRequest.setEmail("taken@example.com");
            changedEmailRequest.setStatus(CustomerStatus.ACTIVE);

            when(customerRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

            assertThatThrownBy(() -> customerService.updateCustomer(1L, changedEmailRequest))
                    .isInstanceOf(CustomerEmailAlreadyExistsException.class);

            verify(customerRepository, never()).save(any());
        }
    }

    @Nested
    class DeleteCustomer {

        @Test
        void deletesCustomer_whenItExists() {
            when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

            customerService.deleteCustomer(1L);

            verify(customerRepository).deleteById(1L);
        }

        @Test
        void throwsCustomerNotFoundException_whenCustomerDoesNotExist_andNeverDeletes() {
            when(customerRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.deleteCustomer(99L))
                    .isInstanceOf(CustomerNotFoundException.class);

            verify(customerRepository, never()).deleteById(anyLong());
        }
    }

    @Nested
    class SearchCustomers {

        @Test
        void delegatesToRepositoryAndMapsResults() {
            Page<Customer> customerPage = new PageImpl<>(List.of(customer));
            Pageable pageable = PageRequest.of(0, 10); // unsorted

            when(customerRepository.searchCustomers("ada", null, null, pageable))
                    .thenReturn(customerPage);
            when(customerMapper.mapToCustomerResponse(customer)).thenReturn(customerResponse);

            Page<CustomerResponse> result = customerService.searchCustomers("ada", null, null, pageable);

            assertThat(result.getContent()).containsExactly(customerResponse);
        }

        @Test
        void normalizesSortFieldCaseBeforeDelegatingToRepository() {
            // Client sends "FIRSTNAME" (wrong case) — service should rewrite it to the
            // canonical "firstName" before it ever reaches the repository/JPQL layer.
            Pageable requestedPageable = PageRequest.of(0, 10, Sort.by("FIRSTNAME").ascending());
            when(customerRepository.searchCustomers(any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(customer)));
            when(customerMapper.mapToCustomerResponse(any())).thenReturn(customerResponse);

            customerService.searchCustomers(null, null, null, requestedPageable);

            var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(customerRepository).searchCustomers(any(), any(), any(), pageableCaptor.capture());
            Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("firstName");
            assertThat(order).isNotNull();
            assertThat(order.isAscending()).isTrue();
        }

        @Test
        void throwsInvalidSortFieldException_whenSortFieldNotOnAllowList() {
            Pageable requestedPageable = PageRequest.of(0, 10, Sort.by("bogusField").ascending());

            assertThatThrownBy(() -> customerService.searchCustomers(null, null, null, requestedPageable))
                    .isInstanceOf(InvalidSortFieldException.class)
                    .hasMessageContaining("bogusField");

            verifyNoInteractions(customerRepository);
        }
    }

    @Nested
    class SearchCustomersBySpecification {

        @Test
        void delegatesToRepositoryFindAllWithSpecAndMapsResults() {
            Page<Customer> customerPage = new PageImpl<>(List.of(customer));
            when(customerRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                    .thenReturn(customerPage);
            when(customerMapper.mapToCustomerResponse(customer)).thenReturn(customerResponse);

            Page<CustomerResponse> result = customerService.searchCustomersBySpecification(
                    "ada", null, CustomerStatus.ACTIVE, PageRequest.of(0, 10));

            assertThat(result.getContent()).containsExactly(customerResponse);
        }
    }
}
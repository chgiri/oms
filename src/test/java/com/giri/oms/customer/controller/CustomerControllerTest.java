package com.giri.oms.customer.controller;

import tools.jackson.databind.json.JsonMapper;
import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.customer.dto.CustomerRequest;
import com.giri.oms.customer.dto.CustomerResponse;
import com.giri.oms.customer.entity.CustomerStatus;
import com.giri.oms.customer.exception.CustomerEmailAlreadyExistsException;
import com.giri.oms.customer.exception.CustomerNotFoundException;
import com.giri.oms.customer.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest loads only the web layer (this controller + @ControllerAdvice
 * classes, auto-detected — no explicit @Import needed) — the service is
 * mocked, so this verifies HTTP status codes, JSON shape, Bean Validation
 * triggering, and exception-handler wiring, without touching the DB or
 * business logic.
 */
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private CustomerService customerService;

    private CustomerResponse customerResponse;
    private CustomerRequest validRequest;

    @BeforeEach
    void setUp() {
        customerResponse = new CustomerResponse(
                1L, "Ada", "Lovelace", "alice@example.com", "+1 555-0100",
                "123 Main St", "Springfield", "IL", "62701", "USA",
                CustomerStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());

        validRequest = new CustomerRequest();
        validRequest.setFirstName("Ada");
        validRequest.setLastName("Lovelace");
        validRequest.setEmail("alice@example.com");
        validRequest.setPhone("+1 555-0100");
        validRequest.setStreet("123 Main St");
        validRequest.setCity("Springfield");
        validRequest.setState("IL");
        validRequest.setPostalCode("62701");
        validRequest.setCountry("USA");
        validRequest.setStatus(CustomerStatus.ACTIVE);
    }

    @Nested
    class CreateCustomer {

        @Test
        void returns201AndBody_whenRequestIsValid() throws Exception {
            when(customerService.createCustomer(any())).thenReturn(customerResponse);

            mockMvc.perform(post("/api/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.email").value("alice@example.com"))
                    .andExpect(jsonPath("$.city").value("Springfield"));
        }

        @Test
        void returns400_whenFirstNameIsBlank() throws Exception {
            validRequest.setFirstName("");

            mockMvc.perform(post("/api/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.firstName").exists());
        }

        @Test
        void returns400_whenEmailIsMalformed() throws Exception {
            validRequest.setEmail("not-an-email");

            mockMvc.perform(post("/api/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").exists());
        }

        @Test
        void returns400_whenStatusIsMissing() throws Exception {
            validRequest.setStatus(null);

            mockMvc.perform(post("/api/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.status").exists());
        }

        @Test
        void returns400_whenPhoneIsMalformed() throws Exception {
            validRequest.setPhone("abc-not-a-phone");

            mockMvc.perform(post("/api/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.phone").exists());
        }

        @Test
        void returns201_whenPhoneIsEmptyString() throws Exception {
            // Regex is "^$|^[+]?[0-9 ()-]{7,20}$" — empty string is explicitly allowed
            // since phone is optional.
            validRequest.setPhone("");
            when(customerService.createCustomer(any())).thenReturn(customerResponse);

            mockMvc.perform(post("/api/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isCreated());
        }

        @Test
        void returns409_whenEmailAlreadyExists() throws Exception {
            when(customerService.createCustomer(any()))
                    .thenThrow(new CustomerEmailAlreadyExistsException(validRequest.getEmail()));

            mockMvc.perform(post("/api/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class GetCustomerById {

        @Test
        void returns200AndBody_whenCustomerExists() throws Exception {
            when(customerService.getCustomerById(1L)).thenReturn(customerResponse);

            mockMvc.perform(get("/api/customers/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lastName").value("Lovelace"));
        }

        @Test
        void returns404_whenCustomerDoesNotExist() throws Exception {
            when(customerService.getCustomerById(99L)).thenThrow(new CustomerNotFoundException(99L));

            mockMvc.perform(get("/api/customers/{id}", 99L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("99")));
        }
    }

    @Nested
    class GetAllCustomers {

        @Test
        void returns200AndPagedResponse() throws Exception {
            PagedResponse<CustomerResponse> paged = new PagedResponse<>(
                    List.of(customerResponse), 0, 10, 1, 1, true);
            when(customerService.getAllCustomers(0, 10, "id", "asc")).thenReturn(paged);

            mockMvc.perform(get("/api/customers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].lastName").value("Lovelace"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    @Nested
    class UpdateCustomer {

        @Test
        void returns200_whenRequestIsValid() throws Exception {
            when(customerService.updateCustomer(eq(1L), any())).thenReturn(customerResponse);

            mockMvc.perform(put("/api/customers/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("alice@example.com"));
        }

        @Test
        void returns404_whenCustomerDoesNotExist() throws Exception {
            when(customerService.updateCustomer(eq(99L), any())).thenThrow(new CustomerNotFoundException(99L));

            mockMvc.perform(put("/api/customers/{id}", 99L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void returns409_whenChangingToAnotherCustomersEmail() throws Exception {
            when(customerService.updateCustomer(eq(1L), any()))
                    .thenThrow(new CustomerEmailAlreadyExistsException("taken@example.com"));

            mockMvc.perform(put("/api/customers/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isConflict());
        }

        @Test
        void returns400_whenRequestFailsValidation() throws Exception {
            validRequest.setFirstName(null);

            mockMvc.perform(put("/api/customers/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class DeleteCustomer {

        @Test
        void returns204_whenCustomerIsDeleted() throws Exception {
            mockMvc.perform(delete("/api/customers/{id}", 1L))
                    .andExpect(status().isNoContent());
        }

        @Test
        void returns404_whenCustomerDoesNotExist() throws Exception {
            org.mockito.Mockito.doThrow(new CustomerNotFoundException(99L))
                    .when(customerService).deleteCustomer(99L);

            mockMvc.perform(delete("/api/customers/{id}", 99L))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class SearchCustomers {

        @Test
        void returns200AndFiltersByQueryParams_jpqlApproach() throws Exception {
            Page<CustomerResponse> page = new PageImpl<>(List.of(customerResponse), PageRequest.of(0, 10), 1);
            when(customerService.searchCustomers(eq("ada"), any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/customers/search").param("name", "ada"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].lastName").value("Lovelace"));
        }

        @Test
        void returns200AndFiltersByStatus() throws Exception {
            Page<CustomerResponse> page = new PageImpl<>(List.of(customerResponse), PageRequest.of(0, 10), 1);
            when(customerService.searchCustomers(any(), any(), eq(CustomerStatus.ACTIVE), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/customers/search").param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
        }

        @Test
        void returns200AndFiltersByQueryParams_specificationApproach() throws Exception {
            Page<CustomerResponse> page = new PageImpl<>(List.of(customerResponse), PageRequest.of(0, 10), 1);
            when(customerService.searchCustomersBySpecification(eq("ada"), any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/customers/search/advanced").param("name", "ada"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].lastName").value("Lovelace"));
        }
    }
}
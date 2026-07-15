package com.giri.oms.customer.controller;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.customer.dto.CustomerRequest;
import com.giri.oms.customer.dto.CustomerResponse;
import com.giri.oms.customer.entity.CustomerStatus;
import com.giri.oms.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer management")
public class CustomerController {

    private final CustomerService customerService;

    // Build Add Customer REST API
    @PostMapping
    @Operation(summary = "Create a new customer",
            description = "Creates a new customer and returns the saved record, including its generated ID and timestamps. "
                    + "Email must be unique — creating with an email that's already in use returns a 409. "
                    + "If `status` is omitted, it defaults to ACTIVE.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Customer created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(name = "Created customer", value = """
                                    {
                                      "id": 1,
                                      "firstName": "Ada",
                                      "lastName": "Lovelace",
                                      "email": "alice@example.com",
                                      "phone": "+1 555-0100",
                                      "street": "123 Main St",
                                      "city": "Springfield",
                                      "state": "IL",
                                      "postalCode": "62701",
                                      "country": "USA",
                                      "status": "ACTIVE",
                                      "createdAt": "2026-07-01T10:15:30",
                                      "updatedAt": "2026-07-01T10:15:30"
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "Validation error — e.g. blank name, malformed email, invalid phone"),
            @ApiResponse(responseCode = "409", description = "A customer with this email already exists")
    })
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest customerRequest) {
        log.info("POST /api/customers — creating customer: {}", customerRequest.getEmail());
        CustomerResponse savedCustomer = customerService.createCustomer(customerRequest);
        return new ResponseEntity<>(savedCustomer, HttpStatus.CREATED);
    }

    // Build Get Customer REST API
    @GetMapping("{id}")
    @Operation(summary = "Get a customer by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer found"),
            @ApiResponse(responseCode = "404", description = "No customer exists with the given ID")
    })
    public ResponseEntity<CustomerResponse> getCustomerById(
            @Parameter(description = "ID of the customer to fetch", example = "1")
            @PathVariable("id") Long customerId) {
        log.debug("GET /api/customers/{} — fetching customer", customerId);
        CustomerResponse customerResponse = customerService.getCustomerById(customerId);
        return ResponseEntity.ok(customerResponse);
    }

    // Build Get All Customers REST API
    @GetMapping
    @Operation(summary = "Get all customers (paginated)",
            description = "Returns customers page by page. `sortBy` is restricted to an allow-list "
                    + "(id, firstName, lastName, email, status, createdAt, updatedAt) — any other value returns a 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of customers returned"),
            @ApiResponse(responseCode = "400", description = "Invalid sortBy field")
    })
    public ResponseEntity<PagedResponse<CustomerResponse>> getAllCustomers(
            @Parameter(description = "Page number, 0-indexed", example = "0")
            @RequestParam(value = "pageNo", defaultValue = "0") int pageNo,
            @Parameter(description = "Number of items per page (capped server-side)", example = "10")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @Parameter(description = "Field to sort by — id, firstName, lastName, email, status, createdAt, or updatedAt", example = "id")
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @Parameter(description = "Sort direction", schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir) {

        log.debug("GET /api/customers — fetching all customers");
        PagedResponse<CustomerResponse> response = customerService.getAllCustomers(pageNo, pageSize, sortBy, sortDir);
        return ResponseEntity.ok(response);
    }

    // Build Update Customer REST API
    @PutMapping("{id}")
    @Operation(summary = "Update a customer",
            description = "Fully replaces the customer's name, email, phone, address, and status. "
                    + "All fields are re-validated as on create. "
                    + "Changing the email to one already used by another customer returns a 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer updated"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "No customer exists with the given ID"),
            @ApiResponse(responseCode = "409", description = "Another customer already uses this email")
    })
    public ResponseEntity<CustomerResponse> updateCustomer(
            @Parameter(description = "ID of the customer to update", example = "1")
            @PathVariable("id") Long id,
            @Valid @RequestBody CustomerRequest customerRequest) {

        log.info("PUT /api/customers/{} — updating customer", id);
        CustomerResponse updatedCustomer = customerService.updateCustomer(id, customerRequest);
        return ResponseEntity.ok(updatedCustomer);
    }

    // Build Delete Customer REST API
    @DeleteMapping("{id}")
    @Operation(summary = "Delete a customer")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Customer deleted"),
            @ApiResponse(responseCode = "404", description = "No customer exists with the given ID")
    })
    public ResponseEntity<Void> deleteCustomer(
            @Parameter(description = "ID of the customer to delete", example = "1")
            @PathVariable("id") Long customerId) {
        log.info("DELETE /api/customers/{} — deleting customer", customerId);

        customerService.deleteCustomer(customerId);
        return ResponseEntity.noContent().build();
    }

    // Build Search Customers REST API - @Query (JPQL) approach
    @GetMapping("/search")
    @Operation(summary = "Search customers (JPQL query approach)",
            description = "Filters by any combination of name (partial match), email (partial match), and status. "
                    + "All filters are optional — omitting all of them returns every customer, paginated. "
                    + "Functionally equivalent to /search/advanced; this variant is implemented with a hand-written JPQL @Query.")
    public ResponseEntity<Page<CustomerResponse>> searchCustomers(
            @Parameter(description = "Partial, case-insensitive match on customer name", example = "alice")
            @RequestParam(required = false) String name,
            @Parameter(description = "Partial, case-insensitive match on email", example = "example.com")
            @RequestParam(required = false) String email,
            @Parameter(description = "Filter by customer status")
            @RequestParam(required = false) CustomerStatus status,
            @PageableDefault(size = 10, sort = "firstName") Pageable pageable
    ) {
        log.debug("GET /api/customers/search — name={}, email={}, status={}, page={}, size={}",
                name, email, status, pageable.getPageNumber(), pageable.getPageSize());

        Page<CustomerResponse> results = customerService.searchCustomers(name, email, status, pageable);
        return ResponseEntity.ok(results);
    }

    // Build Search Customers REST API — JpaSpecificationExecutor approach
    @GetMapping("/search/advanced")
    @Operation(summary = "Search customers (JPA Specification approach)",
            description = "Same filters and behavior as /search, implemented with JpaSpecificationExecutor instead of JPQL. "
                    + "Kept alongside /search for comparison — see project docs for which is preferred going forward.")
    public ResponseEntity<Page<CustomerResponse>> searchCustomersAdvanced(
            @Parameter(description = "Partial, case-insensitive match on first or last name", example = "ada")
            @RequestParam(required = false) String name,
            @Parameter(description = "Partial, case-insensitive match on email", example = "example.com")
            @RequestParam(required = false) String email,
            @Parameter(description = "Filter by customer status")
            @RequestParam(required = false) CustomerStatus status,
            @PageableDefault(size = 10, sort = "firstName") Pageable pageable
    ) {
        Page<CustomerResponse> results = customerService.searchCustomersBySpecification(
                name, email, status, pageable);

        log.debug("GET /api/customers/search/advanced — name={}, email={}, status={}, page={}, size={}",
                name, email, status, pageable.getPageNumber(), pageable.getPageSize());

        return ResponseEntity.ok(results);
    }

}
package com.giri.oms.customer.service.impl;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.common.exception.InvalidSortFieldException;
import com.giri.oms.customer.constants.CustomerConstants;
import com.giri.oms.customer.dto.CustomerRequest;
import com.giri.oms.customer.dto.CustomerResponse;
import com.giri.oms.customer.entity.Customer;
import com.giri.oms.customer.entity.CustomerStatus;
import com.giri.oms.customer.exception.CustomerEmailAlreadyExistsException;
import com.giri.oms.customer.exception.CustomerNotFoundException;
import com.giri.oms.customer.mapper.CustomerMapper;
import com.giri.oms.customer.repository.CustomerRepository;
import com.giri.oms.customer.service.CustomerService;
import com.giri.oms.customer.specification.CustomerSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // class-level default: every method is read-only unless overridden below
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "firstName", "lastName", "email", "status", "createdAt", "updatedAt");

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    public CustomerResponse createCustomer(CustomerRequest request) {
        log.debug("Creating customer with email: {}", request.getEmail());

        if (customerRepository.existsByEmailIgnoreCase(request.getEmail())) {
            log.warn("Attempted to create customer with duplicate email: {}", request.getEmail());
            throw new CustomerEmailAlreadyExistsException(request.getEmail());
        }

        Customer customer = customerMapper.mapToCustomer(request);
        Customer savedCustomer = customerRepository.save(customer);

        log.info(CustomerConstants.CUSTOMER_CREATED_LOG, savedCustomer.getId());
        return customerMapper.mapToCustomerResponse(savedCustomer);
    }

    @Override
    public CustomerResponse getCustomerById(Long customerId) {
        log.debug("Fetching customer with id: {}", customerId);
        return customerMapper.mapToCustomerResponse(getExistingCustomer(customerId));
    }

    @Override
    public PagedResponse<CustomerResponse> getAllCustomers(int pageNo, int pageSize, String sortBy, String sortDir) {
        log.debug("Fetching all customers");

        validateSortField(sortBy);

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.DESC.name())
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);

        Page<Customer> customerPage = customerRepository.findAll(pageable);
        Page<CustomerResponse> responsePage = customerPage.map(customerMapper::mapToCustomerResponse);

        return PagedResponse.of(responsePage);
    }

    private void validateSortField(String sortBy) {
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new InvalidSortFieldException(sortBy, ALLOWED_SORT_FIELDS);
        }
    }

    /**
     * Search endpoints take a raw Pageable straight from request query params (unlike
     * getAllCustomers, which validates sortBy up front). A client can send any sort
     * property in any case — e.g. sort=firstname instead of sort=firstName — which,
     * left unchecked, reaches Hibernate as a literal JPQL path and blows up as an
     * UnknownPathException (since JPQL attribute paths are case-sensitive). This
     * validates each sort property against the same allow-list and rewrites it to
     * the correct case, so a case-insensitive match still works and anything not on
     * the allow-list gets a clean 400 via InvalidSortFieldException instead of a 500.
     */
    private Pageable normalizeSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }

        List<Sort.Order> normalizedOrders = pageable.getSort().stream()
                .map(order -> {
                    String canonicalField = ALLOWED_SORT_FIELDS.stream()
                            .filter(field -> field.equalsIgnoreCase(order.getProperty()))
                            .findFirst()
                            .orElseThrow(() -> new InvalidSortFieldException(order.getProperty(), ALLOWED_SORT_FIELDS));
                    return new Sort.Order(order.getDirection(), canonicalField);
                })
                .toList();

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(normalizedOrders));
    }


    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    public CustomerResponse updateCustomer(Long customerId, CustomerRequest request) {
        log.debug("Updating customer with id: {}", customerId);

        Customer customer = getExistingCustomer(customerId);

        // Only re-check uniqueness if the email is actually changing — otherwise a
        // customer updating their own record with their own unchanged email would
        // incorrectly trigger a "duplicate" error against themselves.
        if (!customer.getEmail().equalsIgnoreCase(request.getEmail())
                && customerRepository.existsByEmailIgnoreCase(request.getEmail())) {
            log.warn("Attempted to update customer {} to duplicate email: {}", customerId, request.getEmail());
            throw new CustomerEmailAlreadyExistsException(request.getEmail());
        }

        customerMapper.mapToCustomer(request, customer);
        Customer updatedCustomer = customerRepository.save(customer);

        log.info(CustomerConstants.CUSTOMER_UPDATED_LOG, updatedCustomer.getId());
        return customerMapper.mapToCustomerResponse(updatedCustomer);
    }

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    public void deleteCustomer(Long customerId) {
        log.debug("Deleting customer with id: {}", customerId);

        getExistingCustomer(customerId);
        customerRepository.deleteById(customerId);

        log.info(CustomerConstants.CUSTOMER_DELETED_LOG, customerId);
    }

    @Override
    public Page<CustomerResponse> searchCustomers(String name, String email, CustomerStatus status, Pageable pageable) {
        Page<Customer> customers = customerRepository.searchCustomers(name, email, status, normalizeSort(pageable));
        return customers.map(customerMapper::mapToCustomerResponse);
    }

    @Override
    public Page<CustomerResponse> searchCustomersBySpecification(String name, String email, CustomerStatus status, Pageable pageable) {
        var spec = CustomerSpecification.buildSearchSpec(name, email, status);
        Page<Customer> customers = customerRepository.findAll(spec, normalizeSort(pageable));
        return customers.map(customerMapper::mapToCustomerResponse);
    }

    private Customer getExistingCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> {
                    log.warn("Customer not found with id: {}", customerId);
                    return new CustomerNotFoundException(customerId);
                });
    }

}
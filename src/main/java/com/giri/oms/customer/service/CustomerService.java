package com.giri.oms.customer.service;

import com.giri.oms.customer.dto.CustomerRequest;
import com.giri.oms.customer.dto.CustomerResponse;
import com.giri.oms.customer.entity.CustomerStatus;
import com.giri.oms.common.dto.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerService {

    CustomerResponse createCustomer(CustomerRequest request);

    CustomerResponse getCustomerById(Long customerId);

    PagedResponse<CustomerResponse> getAllCustomers(int pageNo, int pageSize, String sortBy, String sortDir);

    CustomerResponse updateCustomer(Long customerId, CustomerRequest request);

    void deleteCustomer(Long customerId);

    Page<CustomerResponse> searchCustomers(String name, String email, CustomerStatus status, Pageable pageable);

    Page<CustomerResponse> searchCustomersBySpecification(String name, String email, CustomerStatus status, Pageable pageable);

}
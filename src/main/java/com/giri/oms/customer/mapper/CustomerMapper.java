package com.giri.oms.customer.mapper;

import com.giri.oms.customer.dto.CustomerRequest;
import com.giri.oms.customer.dto.CustomerResponse;
import com.giri.oms.customer.entity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    CustomerResponse mapToCustomerResponse(Customer customer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Customer mapToCustomer(CustomerRequest customerRequest);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void mapToCustomer(CustomerRequest customerRequest, @MappingTarget Customer customer);
}
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

    // "version" (from BaseEntity) is intentionally NOT mapped — it's an @Version
    // column Hibernate manages itself; see InventoryMapper for the full rationale.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Customer mapToCustomer(CustomerRequest customerRequest);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void mapToCustomer(CustomerRequest customerRequest, @MappingTarget Customer customer);
}
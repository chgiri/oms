package com.giri.oms.order.mapper;

import com.giri.oms.order.dto.OrderItemResponse;
import com.giri.oms.order.dto.OrderResponse;
import com.giri.oms.order.entity.Order;
import com.giri.oms.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", expression = "java(order.getCustomer().getFirstName() + \" \" + order.getCustomer().getLastName())")
    OrderResponse mapToOrderResponse(Order order);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    OrderItemResponse mapToOrderItemResponse(OrderItem orderItem);

    // Order and OrderItem are intentionally NOT built from OrderRequest here — resolving
    // a Customer and each line item's Product requires repository lookups (to validate
    // they exist and to snapshot the product's current price), which is business logic
    // that belongs in the service layer, not the mapper.
}

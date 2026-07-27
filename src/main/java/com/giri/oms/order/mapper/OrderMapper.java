package com.giri.oms.order.mapper;

import com.giri.oms.order.dto.OrderItemResponse;
import com.giri.oms.order.dto.OrderResponse;
import com.giri.oms.order.entity.Order;
import com.giri.oms.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    // customerId/customerName and productId/productName are now plain snapshot
    // fields on Order/OrderItem (set once at creation — see OrderServiceImpl),
    // so these map straight across with no cross-module navigation.
    OrderResponse mapToOrderResponse(Order order);

    OrderItemResponse mapToOrderItemResponse(OrderItem orderItem);

    // Order and OrderItem are intentionally NOT built from OrderRequest here — resolving
    // the customer and each line item's product requires calls to CustomerService/
    // ProductService (to validate they exist and to snapshot the product's current
    // price), which is business logic that belongs in the service layer, not the mapper.
}

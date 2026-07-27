package com.giri.oms.order.service;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.order.dto.OrderRequest;
import com.giri.oms.order.dto.OrderResponse;
import com.giri.oms.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface OrderService {

    OrderResponse createOrder(OrderRequest request);

    OrderResponse getOrderById(Long orderId);

    PagedResponse<OrderResponse> getAllOrders(int pageNo, int pageSize, String sortBy, String sortDir);

    OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus);

    /**
     * Verifies the order exists and is currently AWAITING_PAYMENT, throwing
     * OrderNotFoundException or IllegalOrderStateException otherwise. Used by
     * the payment module before recording a new payment (see
     * PaymentServiceImpl.createPayment) so that payment never has to depend on
     * order.entity.OrderStatus (which isn't part of this module's exposed
     * API — see ModularityTests) just to run this one check.
     */
    void assertAwaitingPayment(Long orderId);

    void deleteOrder(Long orderId);

    Page<OrderResponse> searchOrders(Long customerId, OrderStatus status, BigDecimal minTotal, BigDecimal maxTotal, Pageable pageable);

    Page<OrderResponse> searchOrdersBySpecification(Long customerId, OrderStatus status, BigDecimal minTotal, BigDecimal maxTotal, Pageable pageable);

}
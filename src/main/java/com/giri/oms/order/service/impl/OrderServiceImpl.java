package com.giri.oms.order.service.impl;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.common.exception.InvalidSortFieldException;
import com.giri.oms.customer.entity.Customer;
import com.giri.oms.customer.exception.CustomerNotFoundException;
import com.giri.oms.customer.repository.CustomerRepository;
import com.giri.oms.messaging.event.EventType;
import com.giri.oms.messaging.event.OrderConfirmedEvent;
import com.giri.oms.messaging.event.OrderConfirmedEventFactory;
import com.giri.oms.messaging.event.OrderCreatedEvent;
import com.giri.oms.messaging.event.OrderCreatedEventFactory;
import com.giri.oms.messaging.outbox.OutboxService;
import com.giri.oms.order.constants.OrderConstants;
import com.giri.oms.order.dto.OrderItemRequest;
import com.giri.oms.order.dto.OrderItemResponse;
import com.giri.oms.order.dto.OrderRequest;
import com.giri.oms.order.dto.OrderResponse;
import com.giri.oms.order.entity.Order;
import com.giri.oms.order.entity.OrderItem;
import com.giri.oms.order.entity.OrderStatus;
import com.giri.oms.order.exception.IllegalOrderStateException;
import com.giri.oms.order.exception.OrderNotFoundException;
import com.giri.oms.order.mapper.OrderMapper;
import com.giri.oms.order.repository.OrderRepository;
import com.giri.oms.order.service.OrderService;
import com.giri.oms.order.specification.OrderSpecification;
import com.giri.oms.product.entity.Product;
import com.giri.oms.product.exception.ProductNotFoundException;
import com.giri.oms.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // class-level default: every method is read-only unless overridden below
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final OutboxService outboxService;
    private final OrderCreatedEventFactory orderCreatedEventFactory;
    private final OrderConfirmedEventFactory orderConfirmedEventFactory;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "status", "totalAmount", "createdAt", "updatedAt");

    // Defines which statuses an order may move to from its current one. Any pair not
    // listed here (including staying in place) is rejected as an illegal transition.
    // DELIVERED and CANCELLED are terminal — absent as keys, so any move from them fails.
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        // PENDING -> AWAITING_PAYMENT: inventory reservation succeeded (Phase 2).
        // PENDING -> CANCELLED: inventory reservation failed, or a manual cancel
        // before reservation completes.
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLED));
        // AWAITING_PAYMENT -> CONFIRMED: payment confirmed (Phase 3).
        // AWAITING_PAYMENT -> CANCELLED: payment failed, or a manual cancel while
        // waiting on payment (Phase 4 is responsible for releasing the stock this
        // reserved).
        ALLOWED_TRANSITIONS.put(OrderStatus.AWAITING_PAYMENT, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED));
    }

    // Orders can only be deleted before they've actually shipped — once stock has
    // moved, deleting the record would silently lose the audit trail.
    private static final Set<OrderStatus> DELETABLE_STATUSES = EnumSet.of(OrderStatus.PENDING, OrderStatus.CANCELLED);

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    public OrderResponse createOrder(OrderRequest request) {
        log.debug("Creating order for customer id: {}", request.getCustomerId());

        Customer customer = getExistingCustomer(request.getCustomerId());

        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = getExistingProduct(itemRequest.getProductId());

            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPrice(unitPrice);
            item.setSubtotal(subtotal);
            order.addItem(item);

            totalAmount = totalAmount.add(subtotal);
        }
        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);

        enqueueOrderCreatedEvent(savedOrder);

        log.info(OrderConstants.ORDER_CREATED_LOG, savedOrder.getId());
        return mapToOrderResponse(savedOrder);
    }

    private void enqueueOrderCreatedEvent(Order order) {
        UUID eventId = UUID.randomUUID();
        OrderCreatedEvent event = orderCreatedEventFactory.create(order, eventId);
        outboxService.enqueue(
                eventId,
                orderCreatedEventFactory.aggregateType(),
                orderCreatedEventFactory.aggregateId(order),
                EventType.ORDER_CREATED,
                orderCreatedEventFactory.topic(),
                orderCreatedEventFactory.partitionKey(order),
                event);
    }

    @Override
    public OrderResponse getOrderById(Long orderId) {
        log.debug("Fetching order with id: {}", orderId);
        return mapToOrderResponse(getExistingOrder(orderId));
    }

    @Override
    public PagedResponse<OrderResponse> getAllOrders(int pageNo, int pageSize, String sortBy, String sortDir) {
        log.debug("Fetching all orders");

        validateSortField(sortBy);

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.DESC.name())
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);

        Page<Order> orderPage = orderRepository.findAll(pageable);
        Page<OrderResponse> responsePage = orderPage.map(this::mapToOrderResponse);

        return PagedResponse.of(responsePage);
    }

    private void validateSortField(String sortBy) {
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new InvalidSortFieldException(sortBy, ALLOWED_SORT_FIELDS);
        }
    }

    /**
     * Search endpoints take a raw Pageable straight from request query params (unlike
     * getAllOrders, which validates sortBy up front). A client can send any sort
     * property in any case, which — left unchecked — reaches Hibernate as a literal
     * JPQL path and blows up as an UnknownPathException (JPQL attribute paths are
     * case-sensitive). This validates each sort property against the same allow-list
     * and rewrites it to the correct case, so a case-insensitive match still works
     * and anything not on the allow-list gets a clean 400 via InvalidSortFieldException
     * instead of a 500.
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
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        log.debug("Updating order id: {} status to: {}", orderId, newStatus);

        Order order = getExistingOrder(orderId);
        OrderStatus currentStatus = order.getStatus();

        Set<OrderStatus> allowedNextStatuses = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedNextStatuses.contains(newStatus)) {
            log.warn("Rejected illegal status transition for order id: {} — {} -> {}", orderId, currentStatus, newStatus);
            throw new IllegalOrderStateException(
                    String.format(OrderConstants.INVALID_STATUS_TRANSITION_MESSAGE, orderId, currentStatus, newStatus));
        }

        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);

        // Enqueued in the same transaction as the status change above (see
        // InventoryReservationServiceImpl.reserveForOrder for the fuller version
        // of this note on why that matters). This is what drives Phase 3's
        // shipment creation.
        if (newStatus == OrderStatus.CONFIRMED) {
            enqueueOrderConfirmedEvent(updatedOrder);
        }

        log.info(OrderConstants.ORDER_STATUS_UPDATED_LOG, updatedOrder.getId(), newStatus);
        return mapToOrderResponse(updatedOrder);
    }

    private void enqueueOrderConfirmedEvent(Order order) {
        UUID eventId = UUID.randomUUID();
        OrderConfirmedEvent event = orderConfirmedEventFactory.confirmed(order.getId(), eventId);
        outboxService.enqueue(
                eventId,
                orderConfirmedEventFactory.aggregateType(),
                orderConfirmedEventFactory.aggregateId(order.getId()),
                EventType.ORDER_CONFIRMED,
                orderConfirmedEventFactory.topic(),
                orderConfirmedEventFactory.partitionKey(order.getId()),
                event);
    }

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    public void deleteOrder(Long orderId) {
        log.debug("Deleting order with id: {}", orderId);

        Order order = getExistingOrder(orderId);
        if (!DELETABLE_STATUSES.contains(order.getStatus())) {
            log.warn("Rejected delete of order id: {} in non-deletable status: {}", orderId, order.getStatus());
            throw new IllegalOrderStateException(
                    String.format(OrderConstants.ORDER_NOT_DELETABLE_MESSAGE, orderId, order.getStatus()));
        }

        orderRepository.deleteById(orderId);

        log.info(OrderConstants.ORDER_DELETED_LOG, orderId);
    }

    @Override
    public Page<OrderResponse> searchOrders(Long customerId, OrderStatus status, BigDecimal minTotal, BigDecimal maxTotal, Pageable pageable) {
        Page<Order> results = orderRepository.searchOrders(customerId, status, minTotal, maxTotal, normalizeSort(pageable));
        return results.map(this::mapToOrderResponse);
    }

    @Override
    public Page<OrderResponse> searchOrdersBySpecification(Long customerId, OrderStatus status, BigDecimal minTotal, BigDecimal maxTotal, Pageable pageable) {
        var spec = OrderSpecification.buildSearchSpec(customerId, status, minTotal, maxTotal);
        Page<Order> results = orderRepository.findAll(spec, normalizeSort(pageable));
        return results.map(this::mapToOrderResponse);
    }

    private OrderResponse mapToOrderResponse(Order order) {
        OrderResponse response = orderMapper.mapToOrderResponse(order);
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(orderMapper::mapToOrderItemResponse)
                .toList();
        response.setItems(itemResponses);
        return response;
    }

    private Order getExistingOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found with id: {}", orderId);
                    return new OrderNotFoundException(orderId);
                });
    }

    private Customer getExistingCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> {
                    log.warn("Customer not found with id: {} while placing order", customerId);
                    return new CustomerNotFoundException(customerId);
                });
    }

    private Product getExistingProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> {
                    log.warn("Product not found with id: {} while placing order", productId);
                    return new ProductNotFoundException(productId);
                });
    }

}
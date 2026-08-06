package com.giri.oms.order.service;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.common.exception.InvalidSortFieldException;
import com.giri.oms.customerclient.exception.CustomerNotFoundException;
import com.giri.oms.customerclient.dto.CustomerClientResponse;
import com.giri.oms.customerclient.exception.CustomerServiceUnavailableException;
import com.giri.oms.customerclient.service.CustomerClient;
import com.giri.oms.messaging.event.OrderCreatedEventFactory;
import com.giri.oms.messaging.event.OrderConfirmedEventFactory;
import com.giri.oms.messaging.event.OrderCancelledEvent;
import com.giri.oms.messaging.event.OrderCancelledEventFactory;
import com.giri.oms.messaging.outbox.OutboxService;
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
import com.giri.oms.order.service.impl.OrderServiceImpl;
import com.giri.oms.productclient.exception.ProductNotFoundException;
import com.giri.oms.productclient.dto.ProductClientResponse;
import com.giri.oms.productclient.exception.ProductServiceUnavailableException;
import com.giri.oms.productclient.service.ProductClient;
import com.giri.oms.messaging.event.EventType;
import com.giri.oms.messaging.event.OrderCreatedEvent;
import com.giri.oms.messaging.event.EventSchemaVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests — no Spring context, no DB. Repository, CustomerClient,
 * ProductClient, and mapper are all mocked so these run in milliseconds and
 * only exercise OrderServiceImpl's own logic. CustomerClient/ProductClient
 * (Stage 4 of the microservices-prep plan) are real network calls in
 * production — see the CustomerServiceUnavailable/ProductServiceUnavailable
 * nested tests below for coverage of what happens when either call fails,
 * which didn't exist as a failure mode before Stage 4.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private ProductClient productClient;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OutboxService outboxService;

    @Mock
    private OrderCreatedEventFactory orderCreatedEventFactory;

    @Mock
    private OrderConfirmedEventFactory orderConfirmedEventFactory;

    @Mock
    private OrderCancelledEventFactory orderCancelledEventFactory;

    @InjectMocks
    private OrderServiceImpl orderService;

    private CustomerClientResponse customer;
    private ProductClientResponse product;
    private Order order;
    private OrderRequest orderRequest;
    private OrderResponse orderResponse;

    @BeforeEach
    void setUp() {
        customer = new CustomerClientResponse(1L, "Ada", "Lovelace");

        product = new ProductClientResponse(1L, "Wireless Mouse", new BigDecimal("25.99"));

        OrderItem orderItem = new OrderItem();
        orderItem.setId(1L);
        orderItem.setProductId(product.id());
        orderItem.setProductName(product.name());
        orderItem.setQuantity(3);
        orderItem.setUnitPrice(new BigDecimal("25.99"));
        orderItem.setSubtotal(new BigDecimal("77.97"));

        order = new Order();
        order.setId(1L);
        order.setCustomerId(customer.id());
        order.setCustomerName(customer.firstName() + " " + customer.lastName());
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("77.97"));
        order.addItem(orderItem);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        OrderItemRequest itemRequest = new OrderItemRequest(1L, 3);
        orderRequest = new OrderRequest(1L, List.of(itemRequest));

        OrderItemResponse itemResponse = new OrderItemResponse(
                1L, 1L, "Wireless Mouse", 3, new BigDecimal("25.99"), new BigDecimal("77.97"));
        orderResponse = new OrderResponse(
                1L, 1L, "Ada Lovelace", OrderStatus.PENDING, new BigDecimal("77.97"),
                List.of(itemResponse), LocalDateTime.now(), LocalDateTime.now());
    }

    @Nested
    class CreateOrder {

        @Test
        void savesAndReturnsMappedResponse() {
            // Only stubbed here (and in computesTotalAmountAsSumOfLineItemSubtotals
            // below) — the two exception-path tests in this class throw before
            // OrderServiceImpl ever reaches the outbox-enqueue code that calls
            // these, so stubbing them anywhere those tests would also run trips
            // Mockito's strict-stubs UnnecessaryStubbingException.
            when(orderCreatedEventFactory.aggregateType()).thenReturn("Order");
            when(orderCreatedEventFactory.aggregateId(1L)).thenReturn("1");
            when(orderCreatedEventFactory.topic()).thenReturn("oms.order.events");
            when(orderCreatedEventFactory.partitionKey(1L)).thenReturn("1");
            when(customerClient.getCustomer(1L)).thenReturn(customer);
            when(productClient.getProduct(1L)).thenReturn(product);
            when(orderRepository.save(any(Order.class))).thenReturn(order);
            when(orderCreatedEventFactory.create(eq(1L), eq(1L), eq("PENDING"), eq(new BigDecimal("77.97")),
                    anyList(), any(UUID.class))).thenReturn(
                    new OrderCreatedEvent(UUID.randomUUID(), 1L, 1L, "PENDING", new BigDecimal("77.97"), List.of(), LocalDateTime.now(), EventSchemaVersion.V1));
            when(orderMapper.mapToOrderResponse(order)).thenReturn(orderResponse);
            when(orderMapper.mapToOrderItemResponse(any(OrderItem.class)))
                    .thenReturn(orderResponse.getItems().get(0));

            OrderResponse result = orderService.createOrder(orderRequest);

            assertThat(result.getTotalAmount()).isEqualByComparingTo("77.97");
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
            verify(outboxService).enqueue(any(UUID.class), eq("Order"), eq("1"), eq(EventType.ORDER_CREATED),
                    eq("oms.order.events"), eq("1"), any(OrderCreatedEvent.class));
        }

        @Test
        void computesTotalAmountAsSumOfLineItemSubtotals() {
            ProductClientResponse keyboard = new ProductClientResponse(2L, "Mechanical Keyboard", new BigDecimal("89.99"));

            OrderRequest multiItemRequest = new OrderRequest(1L, List.of(
                    new OrderItemRequest(1L, 2),   // 2 * 25.99 = 51.98
                    new OrderItemRequest(2L, 1)    // 1 * 89.99 = 89.99
            ));

            when(customerClient.getCustomer(1L)).thenReturn(customer);
            when(productClient.getProduct(1L)).thenReturn(product);
            when(productClient.getProduct(2L)).thenReturn(keyboard);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(orderCreatedEventFactory.create(any(), eq(1L), eq("PENDING"), eq(new BigDecimal("141.97")),
                    anyList(), any(UUID.class))).thenReturn(
                    new OrderCreatedEvent(UUID.randomUUID(), 1L, 1L, "PENDING", new BigDecimal("141.97"), List.of(), LocalDateTime.now(), EventSchemaVersion.V1));
            when(orderMapper.mapToOrderResponse(any(Order.class))).thenReturn(orderResponse);
            when(orderMapper.mapToOrderItemResponse(any(OrderItem.class)))
                    .thenReturn(orderResponse.getItems().get(0));

            orderService.createOrder(multiItemRequest);

            var orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getTotalAmount()).isEqualByComparingTo("141.97");
        }

        @Test
        void throwsCustomerNotFoundException_whenCustomerDoesNotExist() {
            when(customerClient.getCustomer(99L)).thenThrow(new CustomerNotFoundException(99L));
            orderRequest.setCustomerId(99L);

            assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                    .isInstanceOf(CustomerNotFoundException.class)
                    .hasMessageContaining("99");

            verify(orderRepository, never()).save(any());
            verify(outboxService, never()).enqueue(any(), any(), any(), any(), any(), any(), any());
        }

        // Stage 4 of the microservices-prep plan: new failure mode that
        // simply didn't exist before CustomerClient was a real network call
        // — see CustomerServiceUnavailableException's Javadoc and Stage 0's
        // resilience decision (fail closed, no fallback). Deliberately its
        // own test rather than an extra assertion on the not-found test
        // above, same reasoning as propagatesProductServiceUnavailableException
        // below: 404 and "customer-service is down" are different exception
        // types with different meanings.
        @Test
        void propagatesCustomerServiceUnavailableException_whenCustomerServiceIsUnreachable() {
            when(customerClient.getCustomer(1L))
                    .thenThrow(new CustomerServiceUnavailableException(1L, new RuntimeException("connection refused")));

            assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                    .isInstanceOf(CustomerServiceUnavailableException.class);

            verify(orderRepository, never()).save(any());
            verify(outboxService, never()).enqueue(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void throwsProductNotFoundException_whenAnItemsProductDoesNotExist() {
            when(customerClient.getCustomer(1L)).thenReturn(customer);
            when(productClient.getProduct(99L)).thenThrow(new ProductNotFoundException(99L));
            orderRequest.setItems(List.of(new OrderItemRequest(99L, 1)));

            assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("99");

            verify(orderRepository, never()).save(any());
            verify(outboxService, never()).enqueue(any(), any(), any(), any(), any(), any(), any());
        }

        // Stage 4 of the microservices-prep plan: new failure mode that
        // simply didn't exist before ProductClient was a real network call —
        // see ProductServiceUnavailableException's Javadoc and Stage 0's
        // resilience decision (fail closed, no fallback). This is
        // deliberately its own test, not just an extra assertion tacked onto
        // the not-found test above: 404 and "product-service is down" are
        // different exception types with different meanings, and this
        // confirms createOrder doesn't swallow or reinterpret the latter as
        // the former (or as a generic 500).
        @Test
        void propagatesProductServiceUnavailableException_whenProductServiceIsUnreachable() {
            when(customerClient.getCustomer(1L)).thenReturn(customer);
            when(productClient.getProduct(1L))
                    .thenThrow(new ProductServiceUnavailableException(1L, new RuntimeException("connection refused")));

            assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                    .isInstanceOf(ProductServiceUnavailableException.class);

            verify(orderRepository, never()).save(any());
            verify(outboxService, never()).enqueue(any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    class GetOrderById {

        @Test
        void returnsMappedResponse_whenOrderExists() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderMapper.mapToOrderResponse(order)).thenReturn(orderResponse);
            when(orderMapper.mapToOrderItemResponse(any(OrderItem.class)))
                    .thenReturn(orderResponse.getItems().get(0));

            OrderResponse result = orderService.getOrderById(1L);

            assertThat(result).isEqualTo(orderResponse);
        }

        @Test
        void throwsOrderNotFoundException_whenOrderDoesNotExist() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderById(99L))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("99");

            verify(orderMapper, never()).mapToOrderResponse(any());
        }
    }

    @Nested
    class GetAllOrders {

        @Test
        void returnsPagedResponse_whenSortFieldIsValid() {
            Page<Order> orderPage = new PageImpl<>(List.of(order));
            when(orderRepository.findAll(any(Pageable.class))).thenReturn(orderPage);
            when(orderMapper.mapToOrderResponse(order)).thenReturn(orderResponse);
            when(orderMapper.mapToOrderItemResponse(any(OrderItem.class)))
                    .thenReturn(orderResponse.getItems().get(0));

            PagedResponse<OrderResponse> result = orderService.getAllOrders(0, 10, "status", "asc");

            assertThat(result.getContent()).containsExactly(orderResponse);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        void throwsInvalidSortFieldException_whenSortFieldIsNotAllowed() {
            assertThatThrownBy(() -> orderService.getAllOrders(0, 10, "secretInternalField", "asc"))
                    .isInstanceOf(InvalidSortFieldException.class)
                    .hasMessageContaining("secretInternalField");

            verifyNoInteractions(orderRepository);
        }
    }

    @Nested
    class UpdateOrderStatus {

        @Test
        void transitionsAndReturnsMappedResponse_whenTransitionIsAllowed() {
            // order starts PENDING; PENDING -> AWAITING_PAYMENT is the Phase 2
            // transition (inventory reserved).
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(orderMapper.mapToOrderResponse(order)).thenReturn(orderResponse);
            when(orderMapper.mapToOrderItemResponse(any(OrderItem.class)))
                    .thenReturn(orderResponse.getItems().get(0));

            orderService.updateOrderStatus(1L, OrderStatus.AWAITING_PAYMENT);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
            verify(orderRepository).save(order);
        }

        @Test
        void transitionsAndReturnsMappedResponse_whenConfirmingFromAwaitingPayment() {
            // The Phase 3 transition (payment confirmed).
            order.setStatus(OrderStatus.AWAITING_PAYMENT);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(orderMapper.mapToOrderResponse(order)).thenReturn(orderResponse);
            when(orderMapper.mapToOrderItemResponse(any(OrderItem.class)))
                    .thenReturn(orderResponse.getItems().get(0));

            orderService.updateOrderStatus(1L, OrderStatus.CONFIRMED);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(orderRepository).save(order);
        }

        @Test
        void throwsOrderNotFoundException_whenOrderDoesNotExist() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.updateOrderStatus(99L, OrderStatus.CONFIRMED))
                    .isInstanceOf(OrderNotFoundException.class);

            verify(orderRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"CONFIRMED", "SHIPPED", "DELIVERED"})
        void throwsIllegalOrderStateException_whenTransitionSkipsAheadOfAllowedNextStatuses(OrderStatus illegalTarget) {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order)); // order starts PENDING

            assertThatThrownBy(() -> orderService.updateOrderStatus(1L, illegalTarget))
                    .isInstanceOf(IllegalOrderStateException.class)
                    .hasMessageContaining("PENDING");

            verify(orderRepository, never()).save(any());
        }

        @Test
        void throwsIllegalOrderStateException_whenTransitioningAwayFromDeliveredTerminalState() {
            order.setStatus(OrderStatus.DELIVERED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.updateOrderStatus(1L, OrderStatus.CANCELLED))
                    .isInstanceOf(IllegalOrderStateException.class);

            verify(orderRepository, never()).save(any());
        }

        // ---- Phase 4: cancellation enqueues OrderCancelled for the inventory
        // module to react to. Previously untested — nothing in this class ever
        // asserted the outbox call, and orderCancelledEventFactory wasn't even
        // mocked, so exercising this path would have thrown an NPE. ----

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PENDING", "AWAITING_PAYMENT", "CONFIRMED"})
        void enqueuesOrderCancelledEvent_whenTransitioningToCancelled_fromAnyNonTerminalStatus(OrderStatus fromStatus) {
            // PENDING -> CANCELLED: e.g. inventory reservation failed, or a manual
            //   cancel before anything was ever reserved.
            // AWAITING_PAYMENT -> CANCELLED: driven by OrderSagaEventConsumer
            //   reacting to PaymentFailed — stock was reserved, so it must be released.
            // CONFIRMED -> CANCELLED: manual cancel after payment but before shipping —
            //   stock is still reserved at this point too.
            order.setStatus(fromStatus);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(orderMapper.mapToOrderResponse(order)).thenReturn(orderResponse);
            when(orderMapper.mapToOrderItemResponse(any(OrderItem.class)))
                    .thenReturn(orderResponse.getItems().get(0));
            when(orderCancelledEventFactory.aggregateType()).thenReturn("Order");
            when(orderCancelledEventFactory.aggregateId(1L)).thenReturn("1");
            when(orderCancelledEventFactory.topic()).thenReturn("oms.order.events");
            when(orderCancelledEventFactory.partitionKey(1L)).thenReturn("1");
            when(orderCancelledEventFactory.cancelled(eq(1L), any(UUID.class))).thenReturn(
                    new OrderCancelledEvent(UUID.randomUUID(), 1L, LocalDateTime.now(), EventSchemaVersion.V1));

            orderService.updateOrderStatus(1L, OrderStatus.CANCELLED);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(outboxService).enqueue(any(UUID.class), eq("Order"), eq("1"), eq(EventType.ORDER_CANCELLED),
                    eq("oms.order.events"), eq("1"), any(OrderCancelledEvent.class));
        }

        @Test
        void doesNotEnqueueOrderCancelledEvent_whenTransitioningToConfirmed() {
            // Sanity check on the other side of the branch in updateOrderStatus —
            // a CONFIRMED transition must never also fire an OrderCancelled event.
            order.setStatus(OrderStatus.AWAITING_PAYMENT);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(orderMapper.mapToOrderResponse(order)).thenReturn(orderResponse);
            when(orderMapper.mapToOrderItemResponse(any(OrderItem.class)))
                    .thenReturn(orderResponse.getItems().get(0));

            orderService.updateOrderStatus(1L, OrderStatus.CONFIRMED);

            verify(outboxService, never()).enqueue(any(), any(), any(), eq(EventType.ORDER_CANCELLED), any(), any(), any());
            verifyNoInteractions(orderCancelledEventFactory);
        }
    }

    @Nested
    class DeleteOrder {

        @Test
        void deletesOrder_whenStatusIsPending() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order)); // PENDING

            orderService.deleteOrder(1L);

            verify(orderRepository).deleteById(1L);
        }

        @Test
        void deletesOrder_whenStatusIsCancelled() {
            order.setStatus(OrderStatus.CANCELLED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            orderService.deleteOrder(1L);

            verify(orderRepository).deleteById(1L);
        }

        @Test
        void throwsOrderNotFoundException_whenOrderDoesNotExist() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.deleteOrder(99L))
                    .isInstanceOf(OrderNotFoundException.class);

            verify(orderRepository, never()).deleteById(anyLong());
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"AWAITING_PAYMENT", "CONFIRMED", "SHIPPED", "DELIVERED"})
        void throwsIllegalOrderStateException_whenStatusDoesNotAllowDeletion(OrderStatus nonDeletableStatus) {
            order.setStatus(nonDeletableStatus);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.deleteOrder(1L))
                    .isInstanceOf(IllegalOrderStateException.class);

            verify(orderRepository, never()).deleteById(anyLong());
        }
    }

    @Nested
    class SearchOrders {

        @Test
        void delegatesToRepositoryAndMapsResults() {
            Page<Order> orderPage = new PageImpl<>(List.of(order));
            Pageable pageable = PageRequest.of(0, 10); // unsorted

            when(orderRepository.searchOrders(1L, null, null, null, pageable))
                    .thenReturn(orderPage);
            when(orderMapper.mapToOrderResponse(order)).thenReturn(orderResponse);
            when(orderMapper.mapToOrderItemResponse(any(OrderItem.class)))
                    .thenReturn(orderResponse.getItems().get(0));

            Page<OrderResponse> result = orderService.searchOrders(1L, null, null, null, pageable);

            assertThat(result.getContent()).containsExactly(orderResponse);
        }

        @Test
        void normalizesSortFieldCaseBeforeDelegatingToRepository() {
            Pageable requestedPageable = PageRequest.of(0, 10, Sort.by("STATUS").ascending());
            when(orderRepository.searchOrders(any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(order)));
            when(orderMapper.mapToOrderResponse(any())).thenReturn(orderResponse);
            when(orderMapper.mapToOrderItemResponse(any(OrderItem.class)))
                    .thenReturn(orderResponse.getItems().get(0));

            orderService.searchOrders(null, null, null, null, requestedPageable);

            var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(orderRepository).searchOrders(any(), any(), any(), any(), pageableCaptor.capture());
            Sort.Order sortOrder = pageableCaptor.getValue().getSort().getOrderFor("status");
            assertThat(sortOrder).isNotNull();
            assertThat(sortOrder.isAscending()).isTrue();
        }

        @Test
        void throwsInvalidSortFieldException_whenSortFieldNotOnAllowList() {
            Pageable requestedPageable = PageRequest.of(0, 10, Sort.by("bogusField").ascending());

            assertThatThrownBy(() -> orderService.searchOrders(null, null, null, null, requestedPageable))
                    .isInstanceOf(InvalidSortFieldException.class)
                    .hasMessageContaining("bogusField");

            verifyNoInteractions(orderRepository);
        }
    }

    @Nested
    class SearchOrdersBySpecification {

        @Test
        void delegatesToRepositoryFindAllWithSpecAndMapsResults() {
            Page<Order> orderPage = new PageImpl<>(List.of(order));
            when(orderRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                    .thenReturn(orderPage);
            when(orderMapper.mapToOrderResponse(order)).thenReturn(orderResponse);
            when(orderMapper.mapToOrderItemResponse(any(OrderItem.class)))
                    .thenReturn(orderResponse.getItems().get(0));

            Page<OrderResponse> result = orderService.searchOrdersBySpecification(
                    1L, OrderStatus.PENDING, new BigDecimal("50"), new BigDecimal("100"), PageRequest.of(0, 10));

            assertThat(result.getContent()).containsExactly(orderResponse);
        }
    }
}
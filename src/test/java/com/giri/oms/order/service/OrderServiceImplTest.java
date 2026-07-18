package com.giri.oms.order.service;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.common.exception.InvalidSortFieldException;
import com.giri.oms.customer.entity.Customer;
import com.giri.oms.customer.entity.CustomerStatus;
import com.giri.oms.customer.exception.CustomerNotFoundException;
import com.giri.oms.customer.repository.CustomerRepository;
import com.giri.oms.messaging.event.OrderCreatedEventFactory;
import com.giri.oms.messaging.event.OrderConfirmedEventFactory;
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
import com.giri.oms.product.entity.Product;
import com.giri.oms.product.exception.ProductNotFoundException;
import com.giri.oms.product.repository.ProductRepository;
import com.giri.oms.messaging.event.EventType;
import com.giri.oms.messaging.event.OrderCreatedEvent;
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
 * Pure unit tests — no Spring context, no DB. Repository, CustomerRepository,
 * ProductRepository, and mapper are all mocked so these run in milliseconds and
 * only exercise OrderServiceImpl's own logic.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OutboxService outboxService;

    @Mock
    private OrderCreatedEventFactory orderCreatedEventFactory;

    @Mock
    private OrderConfirmedEventFactory orderConfirmedEventFactory;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Customer customer;
    private Product product;
    private Order order;
    private OrderRequest orderRequest;
    private OrderResponse orderResponse;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(1L);
        customer.setFirstName("Ada");
        customer.setLastName("Lovelace");
        customer.setEmail("ada@example.com");
        customer.setStatus(CustomerStatus.ACTIVE);

        product = new Product();
        product.setId(1L);
        product.setName("Wireless Mouse");
        product.setPrice(new BigDecimal("25.99"));

        OrderItem orderItem = new OrderItem();
        orderItem.setId(1L);
        orderItem.setProduct(product);
        orderItem.setQuantity(3);
        orderItem.setUnitPrice(new BigDecimal("25.99"));
        orderItem.setSubtotal(new BigDecimal("77.97"));

        order = new Order();
        order.setId(1L);
        order.setCustomer(customer);
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
            when(orderCreatedEventFactory.aggregateId(any(Order.class))).thenReturn("1");
            when(orderCreatedEventFactory.topic()).thenReturn("oms.order.events");
            when(orderCreatedEventFactory.partitionKey(any(Order.class))).thenReturn("1");
            when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(orderRepository.save(any(Order.class))).thenReturn(order);
            when(orderCreatedEventFactory.create(eq(order), any(UUID.class))).thenReturn(
                    new OrderCreatedEvent(UUID.randomUUID(), 1L, 1L, "PENDING", new BigDecimal("77.97"), List.of(), LocalDateTime.now()));
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
            Product keyboard = new Product();
            keyboard.setId(2L);
            keyboard.setName("Mechanical Keyboard");
            keyboard.setPrice(new BigDecimal("89.99"));

            OrderRequest multiItemRequest = new OrderRequest(1L, List.of(
                    new OrderItemRequest(1L, 2),   // 2 * 25.99 = 51.98
                    new OrderItemRequest(2L, 1)    // 1 * 89.99 = 89.99
            ));

            when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.findById(2L)).thenReturn(Optional.of(keyboard));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(orderCreatedEventFactory.create(any(Order.class), any(UUID.class))).thenReturn(
                    new OrderCreatedEvent(UUID.randomUUID(), 1L, 1L, "PENDING", new BigDecimal("141.97"), List.of(), LocalDateTime.now()));
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
            when(customerRepository.findById(99L)).thenReturn(Optional.empty());
            orderRequest.setCustomerId(99L);

            assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                    .isInstanceOf(CustomerNotFoundException.class)
                    .hasMessageContaining("99");

            verify(orderRepository, never()).save(any());
            verify(outboxService, never()).enqueue(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void throwsProductNotFoundException_whenAnItemsProductDoesNotExist() {
            when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(productRepository.findById(99L)).thenReturn(Optional.empty());
            orderRequest.setItems(List.of(new OrderItemRequest(99L, 1)));

            assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("99");

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
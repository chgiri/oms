package com.giri.oms.order.repository;

import com.giri.oms.common.AbstractIntegrationTest;
import com.giri.oms.order.entity.Order;
import com.giri.oms.order.entity.OrderItem;
import com.giri.oms.order.entity.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @DataJpaTest boots only the JPA slice (repositories, entity manager) — much
 * faster than a full @SpringBootTest, while still running against a real
 * Postgres container so native/Postgres-specific queries are validated for real.
 *
 * @AutoConfigureTestDatabase(replace = NONE) is required — otherwise @DataJpaTest
 * tries to swap in an embedded database, which isn't even on this project's
 * classpath, instead of using the Testcontainers-provided Postgres.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    // Stage 5 of the microservices-prep plan: no real Customer row needed
    // anymore either — same reasoning as MOUSE_ID/etc. below for Product.
    // orders.customer_id has had its FK dropped since Phase 2
    // (V19__drop_cross_module_fk_constraints.sql), and customer now lives
    // entirely in customer-service's own database, unreachable from this
    // @DataJpaTest slice anyway. Plain ids/names are enough for every query
    // these tests exercise.
    private static final Long ADA_ID = 1L;
    private static final Long ALAN_ID = 2L;

    // Stage 5 of the microservices-prep plan: no real Product row needed
    // anymore — order_items.product_id has had its FK dropped since Phase 2
    // (V19__drop_cross_module_fk_constraints.sql), and product now lives
    // entirely in product-service's own database, unreachable from this
    // @DataJpaTest slice anyway. A plain id/name/price is enough to build
    // the OrderItem snapshot fields these tests actually exercise.
    private static final Long MOUSE_ID = 1L;
    private static final String MOUSE_NAME = "Wireless Mouse";
    private static final BigDecimal MOUSE_PRICE = new BigDecimal("25.99");

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        orderRepository.save(order(ADA_ID, "Ada Lovelace", OrderStatus.PENDING, "77.97", 3));
        orderRepository.save(order(ADA_ID, "Ada Lovelace", OrderStatus.DELIVERED, "25.99", 1));
        orderRepository.save(order(ALAN_ID, "Alan Turing", OrderStatus.CANCELLED, "51.98", 2));
    }

    private Order order(Long customerId, String customerName, OrderStatus status, String total, int quantity) {
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setCustomerName(customerName);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal(total));

        OrderItem item = new OrderItem();
        item.setProductId(MOUSE_ID);
        item.setProductName(MOUSE_NAME);
        item.setQuantity(quantity);
        item.setUnitPrice(MOUSE_PRICE);
        item.setSubtotal(MOUSE_PRICE.multiply(BigDecimal.valueOf(quantity)));
        order.addItem(item);

        return order;
    }

    @Test
    void findByCustomerId_returnsAllOrdersForThatCustomer() {
        List<Order> results = orderRepository.findByCustomerId(ADA_ID);

        assertThat(results).hasSize(2);
    }

    @Test
    void findByStatus_returnsAllOrdersWithThatStatus() {
        List<Order> results = orderRepository.findByStatus(OrderStatus.CANCELLED);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCustomerId()).isEqualTo(ALAN_ID);
    }

    @Test
    void cascadeSave_persistsOrderItemsWithTheOrder() {
        List<Order> results = orderRepository.findByCustomerId(ADA_ID);

        Order pendingOrder = results.stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING)
                .findFirst()
                .orElseThrow();

        assertThat(pendingOrder.getItems()).hasSize(1);
        assertThat(pendingOrder.getItems().get(0).getSubtotal()).isEqualByComparingTo("77.97");
    }

    @Test
    void searchOrders_filtersOnAllProvidedCriteria() {
        Page<Order> results = orderRepository.searchOrders(
                ADA_ID, OrderStatus.PENDING, null, null, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void searchOrders_filtersByTotalAmountRange() {
        Page<Order> results = orderRepository.searchOrders(
                null, null, new BigDecimal("50.00"), new BigDecimal("80.00"), PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(2);
    }

    @Test
    void searchOrders_withAllNullFilters_returnsEverything() {
        Page<Order> results = orderRepository.searchOrders(
                null, null, null, null, PageRequest.of(0, 10));

        assertThat(results.getTotalElements()).isEqualTo(3);
    }
}
package com.giri.oms.order.repository;

import com.giri.oms.common.AbstractIntegrationTest;
import com.giri.oms.customer.entity.Customer;
import com.giri.oms.customer.entity.CustomerStatus;
import com.giri.oms.customer.repository.CustomerRepository;
import com.giri.oms.order.entity.Order;
import com.giri.oms.order.entity.OrderItem;
import com.giri.oms.order.entity.OrderStatus;
import com.giri.oms.product.entity.Product;
import com.giri.oms.product.repository.ProductRepository;
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

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    private Customer ada;
    private Customer alan;
    private Product mouse;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();

        ada = customerRepository.save(customer("Ada", "Lovelace", "ada@example.com"));
        alan = customerRepository.save(customer("Alan", "Turing", "alan@example.com"));
        mouse = productRepository.save(product("Wireless Mouse", "25.99", 200));

        orderRepository.save(order(ada, OrderStatus.PENDING, "77.97", mouse, 3));
        orderRepository.save(order(ada, OrderStatus.DELIVERED, "25.99", mouse, 1));
        orderRepository.save(order(alan, OrderStatus.CANCELLED, "51.98", mouse, 2));
    }

    private Customer customer(String firstName, String lastName, String email) {
        Customer customer = new Customer();
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setEmail(email);
        customer.setStatus(CustomerStatus.ACTIVE);
        return customer;
    }

    private Product product(String name, String price, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        return product;
    }

    private Order order(Customer customer, OrderStatus status, String total, Product product, int quantity) {
        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal(total));

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        item.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        order.addItem(item);

        return order;
    }

    @Test
    void findByCustomerId_returnsAllOrdersForThatCustomer() {
        List<Order> results = orderRepository.findByCustomerId(ada.getId());

        assertThat(results).hasSize(2);
    }

    @Test
    void findByStatus_returnsAllOrdersWithThatStatus() {
        List<Order> results = orderRepository.findByStatus(OrderStatus.CANCELLED);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCustomer().getId()).isEqualTo(alan.getId());
    }

    @Test
    void cascadeSave_persistsOrderItemsWithTheOrder() {
        List<Order> results = orderRepository.findByCustomerId(ada.getId());

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
                ada.getId(), OrderStatus.PENDING, null, null, PageRequest.of(0, 10));

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

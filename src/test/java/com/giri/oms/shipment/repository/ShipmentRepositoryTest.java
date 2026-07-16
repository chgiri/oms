package com.giri.oms.shipment.repository;

import com.giri.oms.common.AbstractIntegrationTest;
import com.giri.oms.customer.entity.Customer;
import com.giri.oms.customer.entity.CustomerStatus;
import com.giri.oms.customer.repository.CustomerRepository;
import com.giri.oms.order.entity.Order;
import com.giri.oms.order.entity.OrderItem;
import com.giri.oms.order.entity.OrderStatus;
import com.giri.oms.order.repository.OrderRepository;
import com.giri.oms.product.entity.Product;
import com.giri.oms.product.repository.ProductRepository;
import com.giri.oms.shipment.entity.Shipment;
import com.giri.oms.shipment.entity.ShipmentStatus;
import com.giri.oms.shipment.entity.ShippingCarrier;
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
class ShipmentRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    private Order order1;
    private Order order2;

    @BeforeEach
    void setUp() {
        shipmentRepository.deleteAll();
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();

        Customer ada = customerRepository.save(customer("Ada", "Lovelace", "ada@example.com"));
        Product mouse = productRepository.save(product("Wireless Mouse", "25.99"));

        order1 = orderRepository.save(order(ada, "77.97", mouse, 3));
        order2 = orderRepository.save(order(ada, "25.99", mouse, 1));

        shipmentRepository.save(shipment(order1, ShippingCarrier.UPS, ShipmentStatus.PENDING));
        shipmentRepository.save(shipment(order1, ShippingCarrier.FEDEX, ShipmentStatus.RETURNED));
        shipmentRepository.save(shipment(order2, ShippingCarrier.USPS, ShipmentStatus.DELIVERED));
    }

    private Customer customer(String firstName, String lastName, String email) {
        Customer customer = new Customer();
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setEmail(email);
        customer.setStatus(CustomerStatus.ACTIVE);
        return customer;
    }

    private Product product(String name, String price) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        return product;
    }

    private Order order(Customer customer, String total, Product product, int quantity) {
        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal(total));

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        item.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        order.addItem(item);

        return order;
    }

    private Shipment shipment(Order order, ShippingCarrier carrier, ShipmentStatus status) {
        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setCarrier(carrier);
        shipment.setStatus(status);
        return shipment;
    }

    @Test
    void findByOrderId_returnsAllShipmentsForThatOrder() {
        List<Shipment> results = shipmentRepository.findByOrderId(order1.getId());

        assertThat(results).hasSize(2);
    }

    @Test
    void findByStatus_returnsAllShipmentsWithThatStatus() {
        List<Shipment> results = shipmentRepository.findByStatus(ShipmentStatus.DELIVERED);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOrder().getId()).isEqualTo(order2.getId());
    }

    @Test
    void searchShipments_filtersOnAllProvidedCriteria() {
        Page<Shipment> results = shipmentRepository.searchShipments(
                order1.getId(), ShipmentStatus.PENDING, null, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getStatus()).isEqualTo(ShipmentStatus.PENDING);
    }

    @Test
    void searchShipments_filtersByCarrier() {
        Page<Shipment> results = shipmentRepository.searchShipments(
                null, null, ShippingCarrier.USPS, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getCarrier()).isEqualTo(ShippingCarrier.USPS);
    }

    @Test
    void searchShipments_withAllNullFilters_returnsEverything() {
        Page<Shipment> results = shipmentRepository.searchShipments(
                null, null, null, PageRequest.of(0, 10));

        assertThat(results.getTotalElements()).isEqualTo(3);
    }
}

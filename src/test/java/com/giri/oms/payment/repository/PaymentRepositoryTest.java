package com.giri.oms.payment.repository;

import com.giri.oms.common.AbstractIntegrationTest;
import com.giri.oms.customer.entity.Customer;
import com.giri.oms.customer.entity.CustomerStatus;
import com.giri.oms.customer.repository.CustomerRepository;
import com.giri.oms.order.entity.Order;
import com.giri.oms.order.entity.OrderItem;
import com.giri.oms.order.entity.OrderStatus;
import com.giri.oms.order.repository.OrderRepository;
import com.giri.oms.payment.entity.Payment;
import com.giri.oms.payment.entity.PaymentMethod;
import com.giri.oms.payment.entity.PaymentStatus;
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
class PaymentRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

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
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();

        Customer ada = customerRepository.save(customer("Ada", "Lovelace", "ada@example.com"));
        Product mouse = productRepository.save(product("Wireless Mouse", "25.99", 200));

        order1 = orderRepository.save(order(ada, "77.97", mouse, 3));
        order2 = orderRepository.save(order(ada, "25.99", mouse, 1));

        paymentRepository.save(payment(order1, "77.97", PaymentMethod.CREDIT_CARD, PaymentStatus.PENDING));
        paymentRepository.save(payment(order1, "77.97", PaymentMethod.CREDIT_CARD, PaymentStatus.FAILED));
        paymentRepository.save(payment(order2, "25.99", PaymentMethod.PAYPAL, PaymentStatus.COMPLETED));
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

    private Payment payment(Order order, String amount, PaymentMethod method, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(new BigDecimal(amount));
        payment.setMethod(method);
        payment.setStatus(status);
        return payment;
    }

    @Test
    void findByOrderId_returnsAllPaymentsForThatOrder() {
        List<Payment> results = paymentRepository.findByOrderId(order1.getId());

        assertThat(results).hasSize(2);
    }

    @Test
    void findByStatus_returnsAllPaymentsWithThatStatus() {
        List<Payment> results = paymentRepository.findByStatus(PaymentStatus.COMPLETED);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOrder().getId()).isEqualTo(order2.getId());
    }

    @Test
    void searchPayments_filtersOnAllProvidedCriteria() {
        Page<Payment> results = paymentRepository.searchPayments(
                order1.getId(), PaymentStatus.PENDING, null, null, null, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void searchPayments_filtersByMethod() {
        Page<Payment> results = paymentRepository.searchPayments(
                null, null, PaymentMethod.PAYPAL, null, null, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getMethod()).isEqualTo(PaymentMethod.PAYPAL);
    }

    @Test
    void searchPayments_filtersByAmountRange() {
        Page<Payment> results = paymentRepository.searchPayments(
                null, null, null, new BigDecimal("50.00"), new BigDecimal("100.00"), PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(2);
    }

    @Test
    void searchPayments_withAllNullFilters_returnsEverything() {
        Page<Payment> results = paymentRepository.searchPayments(
                null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(results.getTotalElements()).isEqualTo(3);
    }
}

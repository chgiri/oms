package com.giri.oms.order.repository;

import com.giri.oms.common.AbstractIntegrationTest;
import com.giri.oms.customer.entity.Customer;
import com.giri.oms.customer.entity.CustomerStatus;
import com.giri.oms.customer.repository.CustomerRepository;
import com.giri.oms.order.entity.Order;
import com.giri.oms.order.entity.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves optimistic locking (@Version on BaseEntity) actually catches a real
 * concurrent-write conflict, rather than just trusting that Hibernate/JPA docs
 * say it should work.
 *
 * @DataJpaTest wraps the whole test method in one transaction by default, and
 * Hibernate's first-level cache would normally hand back the SAME managed Java
 * object on every findById() call within that one transaction — which would
 * make it impossible to simulate "two different users independently loaded
 * this row before either of them wrote to it." entityManager.clear() detaches
 * everything from the persistence context, forcing the next findById() to
 * issue a fresh SELECT and return a genuinely distinct object — exactly what's
 * needed to simulate that race, without needing real multi-threading.
 *
 * @AutoConfigureTestDatabase(replace = NONE) is required — otherwise @DataJpaTest
 * tries to swap in an embedded database, which isn't even on this project's
 * classpath, instead of using the Testcontainers-provided Postgres.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderOptimisticLockingTest extends AbstractIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Long orderId;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        customerRepository.deleteAll();

        Customer customer = new Customer();
        customer.setFirstName("Ada");
        customer.setLastName("Lovelace");
        customer.setEmail("ada.optimistic-lock-test@example.com");
        customer.setStatus(CustomerStatus.ACTIVE);
        customerRepository.saveAndFlush(customer);

        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("99.99"));
        orderRepository.saveAndFlush(order);

        orderId = order.getId();
        entityManager.clear();
    }

    @Test
    void secondConcurrentWriter_getsOptimisticLockingFailure_insteadOfSilentlyOverwritingTheFirst() {
        // Two independent "users" both load the same order before either of them writes —
        // simulated here as two separate finds with a clear() in between, so each returns
        // its own distinct Java object rather than the same cached instance.
        Order firstUsersView = orderRepository.findById(orderId).orElseThrow();
        entityManager.clear();
        Order secondUsersView = orderRepository.findById(orderId).orElseThrow();

        assertThat(firstUsersView.getVersion())
                .as("both views should start from the same version, as if loaded at the same time")
                .isEqualTo(secondUsersView.getVersion());

        // Detach secondUsersView too, before writing firstUsersView. Without this, secondUsersView
        // would still be the managed instance sitting in the persistence context — and merging
        // firstUsersView would silently update THAT existing managed object instead of doing an
        // independent version comparison, defeating the whole simulation.
        entityManager.clear();

        // First user confirms the order — this write succeeds and bumps the version in the DB.
        firstUsersView.setStatus(OrderStatus.CONFIRMED);
        orderRepository.saveAndFlush(firstUsersView);

        // Detach again — the merge above just registered a newly-managed instance for this same
        // row in the persistence context. Clearing it ensures the next merge is also forced to do
        // a genuinely independent fresh load-and-compare, rather than reusing that instance too.
        entityManager.clear();

        // Second user, working from their now-stale view (still carrying the old version),
        // tries to cancel the same order — without optimistic locking, this would silently
        // overwrite the first user's CONFIRMED status with no error to either caller.
        secondUsersView.setStatus(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> orderRepository.saveAndFlush(secondUsersView))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // The first user's update is the one that actually stuck — not silently discarded,
        // and not silently overwritten by the second, conflicting write.
        entityManager.clear();
        Order finalState = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void sequentialUpdates_fromTheSameLoadedInstance_succeedNormally() {
        // Sanity check: optimistic locking should never get in the way of the *normal*,
        // non-concurrent case — load, modify, save, load again, modify, save again.
        Order order = orderRepository.findById(orderId).orElseThrow();
        long versionAfterLoad = order.getVersion();

        order.setStatus(OrderStatus.CONFIRMED);
        Order afterFirstSave = orderRepository.saveAndFlush(order);
        long versionAfterFirstSave = afterFirstSave.getVersion(); // capture the value now — afterFirstSave
        // is the same managed reference as order,
        // and will be mutated again below
        assertThat(versionAfterFirstSave).isGreaterThan(versionAfterLoad);

        afterFirstSave.setStatus(OrderStatus.SHIPPED);
        Order afterSecondSave = orderRepository.saveAndFlush(afterFirstSave);
        assertThat(afterSecondSave.getVersion()).isGreaterThan(versionAfterFirstSave);
        assertThat(afterSecondSave.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }
}
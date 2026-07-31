package com.giri.oms;

import com.giri.oms.common.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 step 6 of the microservices-prep plan: V19 dropped the
 * cross-module FK constraints (fk_orders_customer, fk_order_items_product,
 * fk_inventory_product, fk_payments_order, and — historically — fk_shipments_order)
 * — Postgres no longer rejects an orders.customer_id, order_items.product_id,
 * inventory.product_id, or payments.order_id that points at a row which
 * doesn't exist. Application-level validation (OrderServiceImpl,
 * InventoryServiceImpl, PaymentServiceImpl) is what prevents a *new* orphan
 * from being created going forward — this test is the one-time check the
 * plan calls for before relying on that alone: it proves the detection query
 * itself is correct (it really does flag a dangling reference, not just
 * trivially pass on empty tables) and, run against a real pre-migration
 * dataset, is what you'd use to confirm nothing was already orphaned before
 * V19 ran.
 *
 * The shipments.order_id case this test used to cover (ShipmentsOrderId) was
 * removed along with the rest of the shipment module at Stage 5 — V22
 * dropped the oms_shipment schema entirely once shipment-service's cutover
 * was confirmed stable, so there's no longer a table here to check against.
 *
 * fk_order_items_order (order_items.order_id -> orders.id) is intentionally
 * out of scope: it was never dropped (Order/OrderItem stay in the same
 * future service — see V19's comment), and Postgres still enforces it.
 *
 * Table references below are schema-qualified (oms_order.orders, not orders)
 * because Phase 3 step 7 (V20) moved every table out of "public" into its own
 * per-module schema — none of these are on the default search_path anymore,
 * so an unqualified name would fail to resolve at all rather than just risk
 * resolving to the wrong thing.
 *
 * Raw JdbcTemplate rather than the JPA repositories: inserting a genuinely
 * orphaned row is the entire point of the "detects" tests, and every
 * repository/service path in this codebase now validates the parent exists
 * before saving — the only way to get an orphan into the table at all (short
 * of a data migration bug, which is exactly the scenario this test stands in
 * for) is to write around the application layer, the same way a bad backfill
 * or a manual DB fix might.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CrossModuleForeignKeyIntegrityTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Children first, in dependency order, so the deletes themselves never
        // trip the FK that's still live (fk_order_items_order). oms_shipment
        // is no longer among these — see the class Javadoc's Stage 5 note.
        jdbcTemplate.update("DELETE FROM oms_payment.payments");
        jdbcTemplate.update("DELETE FROM oms_order.order_items");
        jdbcTemplate.update("DELETE FROM oms_inventory.inventory");
        jdbcTemplate.update("DELETE FROM oms_order.orders");
        jdbcTemplate.update("DELETE FROM oms_product.products");
        jdbcTemplate.update("DELETE FROM oms_customer.customers");
    }

    private void insertCustomer(long id) {
        jdbcTemplate.update("""
                INSERT INTO oms_customer.customers (id, first_name, last_name, email, status, created_at, updated_at)
                VALUES (?, 'Jane', 'Doe', ?, 'ACTIVE', now(), now())
                """, id, "jane.doe." + id + "@example.com");
    }

    private void insertProduct(long id) {
        jdbcTemplate.update("""
                INSERT INTO oms_product.products (id, name, price, status, created_at, updated_at)
                VALUES (?, 'Widget', 9.99, 'ACTIVE', now(), now())
                """, id);
    }

    private void insertOrder(long id, long customerId) {
        jdbcTemplate.update("""
                INSERT INTO oms_order.orders (id, customer_id, customer_name, status, total_amount, created_at, updated_at)
                VALUES (?, ?, 'Jane Doe', 'PENDING', 100.00, now(), now())
                """, id, customerId);
    }

    private int orphanCount(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }

    @Nested
    class OrdersCustomerId {

        private static final String ORPHAN_QUERY = """
                SELECT COUNT(*) FROM oms_order.orders o
                LEFT JOIN oms_customer.customers c ON o.customer_id = c.id
                WHERE c.id IS NULL
                """;

        @Test
        void noOrphans_whenEveryOrderReferencesAnExistingCustomer() {
            insertCustomer(1L);
            insertOrder(1L, 1L);

            assertThat(orphanCount(ORPHAN_QUERY)).isZero();
        }

        @Test
        void detectsOrphan_whenCustomerIdReferencesNoExistingCustomer() {
            // No customer 999 exists — only possible to insert this now that
            // fk_orders_customer is gone (see V19).
            insertOrder(1L, 999L);

            assertThat(orphanCount(ORPHAN_QUERY)).isEqualTo(1);
        }
    }

    @Nested
    class OrderItemsProductId {

        private static final String ORPHAN_QUERY = """
                SELECT COUNT(*) FROM oms_order.order_items oi
                LEFT JOIN oms_product.products p ON oi.product_id = p.id
                WHERE p.id IS NULL
                """;

        private void insertOrderItem(long id, long orderId, long productId) {
            jdbcTemplate.update("""
                    INSERT INTO oms_order.order_items (id, order_id, product_id, product_name, quantity, unit_price, subtotal, created_at, updated_at)
                    VALUES (?, ?, ?, 'Widget', 2, 9.99, 19.98, now(), now())
                    """, id, orderId, productId);
        }

        @Test
        void noOrphans_whenEveryOrderItemReferencesAnExistingProduct() {
            insertCustomer(1L);
            insertOrder(1L, 1L);
            insertProduct(1L);
            insertOrderItem(1L, 1L, 1L);

            assertThat(orphanCount(ORPHAN_QUERY)).isZero();
        }

        @Test
        void detectsOrphan_whenProductIdReferencesNoExistingProduct() {
            insertCustomer(1L);
            insertOrder(1L, 1L);
            insertOrderItem(1L, 1L, 999L);

            assertThat(orphanCount(ORPHAN_QUERY)).isEqualTo(1);
        }
    }

    @Nested
    class InventoryProductId {

        private static final String ORPHAN_QUERY = """
                SELECT COUNT(*) FROM oms_inventory.inventory i
                LEFT JOIN oms_product.products p ON i.product_id = p.id
                WHERE p.id IS NULL
                """;

        private void insertInventory(long id, long productId) {
            jdbcTemplate.update("""
                    INSERT INTO oms_inventory.inventory (id, product_id, location, quantity_available, quantity_reserved, reorder_level, created_at, updated_at)
                    VALUES (?, ?, 'WH-EAST-01', 100, 0, 10, now(), now())
                    """, id, productId);
        }

        @Test
        void noOrphans_whenEveryInventoryRecordReferencesAnExistingProduct() {
            insertProduct(1L);
            insertInventory(1L, 1L);

            assertThat(orphanCount(ORPHAN_QUERY)).isZero();
        }

        @Test
        void detectsOrphan_whenProductIdReferencesNoExistingProduct() {
            insertInventory(1L, 999L);

            assertThat(orphanCount(ORPHAN_QUERY)).isEqualTo(1);
        }
    }

    @Nested
    class PaymentsOrderId {

        private static final String ORPHAN_QUERY = """
                SELECT COUNT(*) FROM oms_payment.payments pay
                LEFT JOIN oms_order.orders o ON pay.order_id = o.id
                WHERE o.id IS NULL
                """;

        private void insertPayment(long id, long orderId) {
            jdbcTemplate.update("""
                    INSERT INTO oms_payment.payments (id, order_id, amount, method, status, created_at, updated_at)
                    VALUES (?, ?, 100.00, 'CREDIT_CARD', 'PENDING', now(), now())
                    """, id, orderId);
        }

        @Test
        void noOrphans_whenEveryPaymentReferencesAnExistingOrder() {
            insertCustomer(1L);
            insertOrder(1L, 1L);
            insertPayment(1L, 1L);

            assertThat(orphanCount(ORPHAN_QUERY)).isZero();
        }

        @Test
        void detectsOrphan_whenOrderIdReferencesNoExistingOrder() {
            insertPayment(1L, 999L);

            assertThat(orphanCount(ORPHAN_QUERY)).isEqualTo(1);
        }
    }
}

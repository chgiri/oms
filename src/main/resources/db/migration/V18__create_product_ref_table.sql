-- Phase 1 step 3 of the microservices-prep plan: inventory's local
-- read-only replica of product name, kept in sync going forward by
-- ProductEventInventoryConsumer reacting to ProductCreated/ProductUpdated
-- on the product-events topic (see EventType, ProductEventFactory).
--
-- That consumer only sees events published from here on — it can't retroactively
-- see products that were created before Phase 1 step 1 added event publishing,
-- and depending on when this consumer group first connects relative to the
-- product-events topic's retention, it may not see everything published
-- since then either. So this migration backfills product_ref directly from
-- the current products table in the same statement, exactly once, the same
-- way V16 backfilled orders.customer_name/order_items.product_name from
-- current row state rather than relying on replaying history.
--
-- No FK to products — see ProductRef's Javadoc; this table has no
-- relationship to the product module's schema, by design.
--
-- Every existing product is backfilled regardless of status (ACTIVE or
-- DISCONTINUED) — inventory rows that already reference a discontinued
-- product still need its name.

CREATE TABLE product_ref (
    product_id  BIGINT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMP(6) NOT NULL
);

INSERT INTO product_ref (product_id, name, updated_at)
SELECT id, name, updated_at FROM products;

-- Order/OrderItem stop holding a JPA association into the customer/product
-- modules' entities (see Order.customerId, OrderItem.productId, and
-- ModuleBoundaryTest — the order module may no longer reach into another
-- module's entity/repository packages). customer_id and product_id stay
-- exactly as they were: plain FK columns, still enforced by the existing
-- fk_orders_customer / fk_order_items_product constraints below. What's new is
-- a name *snapshot*, taken once at order-placement time, matching the existing
-- unit_price snapshot on order_items — so historical orders keep showing the
-- name as it was when the order was placed, and reading an order never needs a
-- round-trip into another module.
--
-- Added nullable first so the backfill can run, then set NOT NULL — matches
-- how a new required column has to be introduced against a table that may
-- already have rows.

ALTER TABLE orders ADD COLUMN customer_name VARCHAR(201);

UPDATE orders o
SET customer_name = c.first_name || ' ' || c.last_name
FROM customers c
WHERE c.id = o.customer_id;

ALTER TABLE orders ALTER COLUMN customer_name SET NOT NULL;

ALTER TABLE order_items ADD COLUMN product_name VARCHAR(255);

UPDATE order_items oi
SET product_name = p.name
FROM products p
WHERE p.id = oi.product_id;

ALTER TABLE order_items ALTER COLUMN product_name SET NOT NULL;

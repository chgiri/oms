-- Phase 2 of the microservices-prep plan: drop the DB-level FK constraints
-- that cross what would become service boundaries. Application-level
-- validation is already in place for all five relationships (see
-- OrderServiceImpl -> CustomerService/ProductService, InventoryServiceImpl ->
-- ProductService, PaymentServiceImpl -> OrderService.assertAwaitingPayment,
-- ShipmentServiceImpl/ShipmentAutoCreationServiceImpl -> OrderService), so
-- this is safe: nothing relies on Postgres to reject an invalid id anymore.
--
-- The plain BIGINT columns (customer_id, product_id, order_id) are left in
-- place untouched — they're just foreign identifiers now, same as
-- Order.customerId already modeled it at the JPA layer even before this
-- migration. The supporting indexes (idx_orders_customer_id,
-- idx_order_items_product_id, idx_inventory_product_id,
-- idx_payments_order_id, idx_shipments_order_id) are also left in place:
-- they still speed up lookups by these ids and were never part of the FK
-- constraint itself.
--
-- fk_order_items_order (order_items.order_id -> orders.id) is deliberately
-- NOT dropped here: Order and OrderItem stay in the same future service
-- (see the plan doc's Phase 4 extraction order), so that FK is fine exactly
-- as it is.

ALTER TABLE orders DROP CONSTRAINT fk_orders_customer;
ALTER TABLE order_items DROP CONSTRAINT fk_order_items_product;
ALTER TABLE inventory DROP CONSTRAINT fk_inventory_product;
ALTER TABLE payments DROP CONSTRAINT fk_payments_order;
ALTER TABLE shipments DROP CONSTRAINT fk_shipments_order;

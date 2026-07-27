-- Phase 3 step 7 of the microservices-prep plan: split the single "public"
-- schema into one schema per module, still inside the same database/instance.
-- This is the cheap, reversible dry run the plan calls for — a stray
-- unqualified join or a leftover cross-schema reference now fails outright
-- (the table simply isn't on the connection's search_path any more) instead
-- of silently resolving against "public", the same way it would once each
-- schema is a genuinely separate database in Phase 3 step 8 / Phase 4.
--
-- Schema names are prefixed oms_ rather than the bare module name
-- (oms_order, not order) specifically to sidestep "order" being a reserved
-- SQL keyword — every other module name happens to be safe unquoted, but
-- picking one convention for all eight keeps them visually consistent and
-- avoids relying on quoted-identifier handling being right everywhere
-- (migrations, JPA @Table(schema=...), any future ad-hoc psql/tooling).
--
-- One schema per module that actually owns a table, matching the module ->
-- table ownership already established: auth/customer/product/order/payment/
-- shipment each own their obvious table(s); inventory owns inventory,
-- inventory_reservations, AND product_ref (product_ref is inventory's own
-- local read replica of product data — Phase 1 step 3 — not product's
-- table, even though the name says "product"); outbox_events is genuinely
-- shared infrastructure (every module's service enqueues into the same
-- table as part of its own business transaction, which is exactly why it
-- has to stay in the SAME database as the business tables even after a
-- future split — see the note at the bottom of this file) so it gets its
-- own oms_messaging schema rather than living under any one module.
--
-- common and security own no tables and so get no schema here.
--
-- ALTER TABLE ... SET SCHEMA moves the table's indexes, constraints, and
-- owned identity sequences along with it automatically — nothing else to
-- do per table.

CREATE SCHEMA IF NOT EXISTS oms_auth;
CREATE SCHEMA IF NOT EXISTS oms_customer;
CREATE SCHEMA IF NOT EXISTS oms_product;
CREATE SCHEMA IF NOT EXISTS oms_inventory;
CREATE SCHEMA IF NOT EXISTS oms_order;
CREATE SCHEMA IF NOT EXISTS oms_payment;
CREATE SCHEMA IF NOT EXISTS oms_shipment;
CREATE SCHEMA IF NOT EXISTS oms_messaging;

ALTER TABLE users SET SCHEMA oms_auth;

ALTER TABLE customers SET SCHEMA oms_customer;

ALTER TABLE products SET SCHEMA oms_product;

ALTER TABLE inventory SET SCHEMA oms_inventory;
ALTER TABLE inventory_reservations SET SCHEMA oms_inventory;
ALTER TABLE product_ref SET SCHEMA oms_inventory;

ALTER TABLE orders SET SCHEMA oms_order;
ALTER TABLE order_items SET SCHEMA oms_order;

ALTER TABLE payments SET SCHEMA oms_payment;

ALTER TABLE shipments SET SCHEMA oms_shipment;

ALTER TABLE outbox_events SET SCHEMA oms_messaging;

-- NOTE for Phase 4 (out of scope for this migration): the outbox pattern's
-- entire correctness guarantee is "the business row and its outbox row
-- commit in the same transaction, against the same database". A single
-- shared oms_messaging.outbox_events table works for that today because
-- every module's write is still in this one database. The moment any
-- module is extracted into its own physical database (Phase 3 step 8 or
-- Phase 4), that module needs its OWN local outbox table again — a
-- cross-database transaction isn't atomic, so Order writing to a shared
-- outbox table living in Product's future database would silently
-- reintroduce the dual-write problem the outbox pattern exists to avoid.
-- flyway_schema_history itself is left in its current (public) schema —
-- Flyway's own bookkeeping isn't a business table and doesn't need to move.

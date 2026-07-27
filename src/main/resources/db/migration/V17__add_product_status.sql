-- Phase 1 of the microservices-prep plan: Product moves to soft-delete so
-- that dropping fk_inventory_product / fk_order_items_product in Phase 2
-- doesn't turn deleteProduct into a silent orphaning bug. Mirrors the
-- customers.status column added in V2 (see CustomerStatus) — same
-- ACTIVE/DISCONTINUED-style flag, just a Product-specific value set.
--
-- Added with a DEFAULT so every existing row backfills to ACTIVE in the same
-- statement, unlike V16's snapshot columns which needed a separate UPDATE —
-- there's no per-row source data to copy here, just one constant value.

ALTER TABLE products ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE products ADD CONSTRAINT ck_products_status CHECK (status IN ('ACTIVE', 'DISCONTINUED'));

CREATE INDEX idx_products_status ON products (status);

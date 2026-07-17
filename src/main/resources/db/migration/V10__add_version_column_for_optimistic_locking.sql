-- Optimistic locking column for every entity extending BaseEntity.
-- DEFAULT 0 backfills existing rows without requiring a NOT NULL violation;
-- new rows going forward get their version managed entirely by Hibernate.

ALTER TABLE products   ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE customers  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE inventory  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders     ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payments   ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE shipments  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users      ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

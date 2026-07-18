-- Phase 2/3 of the Kafka rollout introduces an AWAITING_PAYMENT order status
-- (set once inventory has been reserved, cleared once payment is confirmed or
-- the order is cancelled). The check constraint from V4 only allow-lists the
-- original five statuses, so it must be widened before the column can hold it.
ALTER TABLE orders DROP CONSTRAINT ck_orders_status;

ALTER TABLE orders ADD CONSTRAINT ck_orders_status
    CHECK (status IN ('PENDING', 'AWAITING_PAYMENT', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'));

-- Phase 4's release-stock-on-cancel flow needs to know exactly which Inventory
-- row (location) a reservation decremented, so it can credit that same row
-- back rather than guessing among however many locations hold the product.
-- V12's audit trail didn't need this yet since nothing released a reservation
-- until now.
--
-- Nullable: any reservation rows already sitting in a running system predate
-- this column. Every row the application writes from here on always sets it
-- (see InventoryReservationServiceImpl.doReserveLineItem); release treats a
-- null defensively rather than failing on it (see
-- InventoryReservationServiceImpl.doReleaseLineItem).
ALTER TABLE inventory_reservations ADD COLUMN inventory_id BIGINT;

ALTER TABLE inventory_reservations
    ADD CONSTRAINT fk_inventory_reservations_inventory FOREIGN KEY (inventory_id) REFERENCES inventory (id);

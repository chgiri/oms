-- Product stock is redundant with per-location tracking in the inventory table
-- (inventory.quantity_available / quantity_reserved). Dropping it here instead
-- of editing V1 directly, since already-applied migrations are never modified.
ALTER TABLE products DROP COLUMN stock;

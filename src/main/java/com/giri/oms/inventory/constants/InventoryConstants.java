package com.giri.oms.inventory.constants;

public final class InventoryConstants {

    private InventoryConstants() {
        // utility class — no instances
    }

    // ---- Exception messages (used with String.format) ----
    public static final String INVENTORY_NOT_FOUND_MESSAGE = "Inventory record not found with id: %d";
    public static final String INVENTORY_ALREADY_EXISTS_MESSAGE =
            "An inventory record already exists for product id %d at location: %s";
    public static final String INSUFFICIENT_STOCK_MESSAGE =
            "Insufficient stock for product id %d: requested %d, available %d";

    // ---- Bean Validation messages ----
    public static final String PRODUCT_ID_REQUIRED_MESSAGE = "Product ID must not be null";
    public static final String LOCATION_REQUIRED_MESSAGE = "Location must not be blank";
    public static final String LOCATION_SIZE_MESSAGE = "Location must be at most 100 characters";
    public static final String QUANTITY_AVAILABLE_REQUIRED_MESSAGE = "Quantity available must not be null";
    public static final String QUANTITY_AVAILABLE_POSITIVE_MESSAGE = "Quantity available must not be negative";
    public static final String QUANTITY_RESERVED_REQUIRED_MESSAGE = "Quantity reserved must not be null";
    public static final String QUANTITY_RESERVED_POSITIVE_MESSAGE = "Quantity reserved must not be negative";
    public static final String REORDER_LEVEL_REQUIRED_MESSAGE = "Reorder level must not be null";
    public static final String REORDER_LEVEL_POSITIVE_MESSAGE = "Reorder level must not be negative";

    // ---- Success / log messages ----
    public static final String INVENTORY_CREATED_LOG = "Inventory record created successfully with id: {}";
    public static final String INVENTORY_UPDATED_LOG = "Inventory record updated successfully with id: {}";
    public static final String INVENTORY_DELETED_LOG = "Inventory record deleted successfully with id: {}";
    public static final String STOCK_RESERVED_LOG =
            "Reserved {} unit(s) of product id {} for order id {} at inventory location: {}";
    public static final String STOCK_RELEASED_LOG =
            "Released {} unit(s) of product id {} reserved for order id {} back to inventory location: {}";
    public static final String RESERVATION_SKIPPED_ALREADY_PROCESSED_LOG =
            "Skipping reservation for order id {} / product id {} — already reserved (duplicate delivery)";
}
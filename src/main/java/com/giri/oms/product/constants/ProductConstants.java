package com.giri.oms.product.constants;

/**
 * Centralized message strings for the Product module — exception messages,
 * validation messages, and (later) log/response messages all live here
 * instead of being hardcoded inline where they're used.
 */
public final class ProductConstants {

    private ProductConstants() {
        // utility class — no instances
    }

    // ---- Exception messages (used with String.format) ----
    public static final String PRODUCT_NOT_FOUND_MESSAGE = "Product not found with id: %d";
    public static final String PRODUCT_ALREADY_EXISTS_MESSAGE = "Product already exists with name: %s";
    public static final String INSUFFICIENT_STOCK_MESSAGE =
            "Insufficient stock for product id: %d. Requested: %d, Available: %d";

    // ---- Bean Validation messages ----
    public static final String NAME_REQUIRED_MESSAGE = "Product name must not be blank";
    public static final String PRICE_REQUIRED_MESSAGE = "Product price must not be null";
    public static final String PRICE_POSITIVE_MESSAGE = "Product price must be greater than zero";
    public static final String PRICE_DIGITS_MESSAGE = "Price must have up to 3 integer digits and 2 decimals";
    public static final String STOCK_REQUIRED_MESSAGE = "Product stock must not be null";
    public static final String STOCK_POSITIVE_OR_ZERO_MESSAGE = "Product stock must not be negative";

    // ---- Success / log messages ----
    public static final String PRODUCT_CREATED_LOG = "Product created successfully with id: {}";
    public static final String PRODUCT_UPDATED_LOG = "Product updated successfully with id: {}";
    public static final String PRODUCT_DELETED_LOG = "Product deleted successfully with id: {}";
}
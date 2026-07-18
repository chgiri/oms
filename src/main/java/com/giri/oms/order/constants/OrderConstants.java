package com.giri.oms.order.constants;

public final class OrderConstants {

    private OrderConstants() {
        // utility class — no instances
    }

    // ---- Exception messages (used with String.format) ----
    // ORDER_NOT_FOUND message now lives in com.giri.oms.common.exception.ErrorCode
    // alongside its error code.
    public static final String INVALID_STATUS_TRANSITION_MESSAGE =
            "Cannot transition order id %d from status %s to %s";
    public static final String ORDER_NOT_DELETABLE_MESSAGE =
            "Order id %d cannot be deleted while in status %s — only PENDING or CANCELLED orders can be deleted";

    // ---- Bean Validation messages ----
    public static final String CUSTOMER_ID_REQUIRED_MESSAGE = "Customer ID must not be null";
    public static final String ITEMS_REQUIRED_MESSAGE = "Order must contain at least one item";
    public static final String ITEM_PRODUCT_ID_REQUIRED_MESSAGE = "Product ID must not be null";
    public static final String ITEM_QUANTITY_REQUIRED_MESSAGE = "Quantity must not be null";
    public static final String ITEM_QUANTITY_POSITIVE_MESSAGE = "Quantity must be greater than zero";
    public static final String STATUS_REQUIRED_MESSAGE = "Status must not be null";

    // ---- Success / log messages ----
    public static final String ORDER_CREATED_LOG = "Order created successfully with id: {}";
    public static final String ORDER_STATUS_UPDATED_LOG = "Order id: {} status updated to: {}";
    public static final String ORDER_DELETED_LOG = "Order deleted successfully with id: {}";
}

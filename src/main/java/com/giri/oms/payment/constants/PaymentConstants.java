package com.giri.oms.payment.constants;

public final class PaymentConstants {

    private PaymentConstants() {
        // utility class — no instances
    }

    // ---- Exception messages (used with String.format) ----
    public static final String PAYMENT_NOT_FOUND_MESSAGE = "Payment not found with id: %d";
    public static final String INVALID_STATUS_TRANSITION_MESSAGE =
            "Cannot transition payment id %d from status %s to %s";
    public static final String PAYMENT_NOT_DELETABLE_MESSAGE =
            "Payment id %d cannot be deleted while in status %s — only PENDING or FAILED payments can be deleted";

    // ---- Bean Validation messages ----
    public static final String ORDER_ID_REQUIRED_MESSAGE = "Order ID must not be null";
    public static final String AMOUNT_REQUIRED_MESSAGE = "Amount must not be null";
    public static final String AMOUNT_POSITIVE_MESSAGE = "Amount must be greater than zero";
    public static final String METHOD_REQUIRED_MESSAGE = "Payment method must not be null";
    public static final String STATUS_REQUIRED_MESSAGE = "Status must not be null";

    // ---- Success / log messages ----
    public static final String PAYMENT_CREATED_LOG = "Payment created successfully with id: {}";
    public static final String PAYMENT_STATUS_UPDATED_LOG = "Payment id: {} status updated to: {}";
    public static final String PAYMENT_DELETED_LOG = "Payment deleted successfully with id: {}";
}

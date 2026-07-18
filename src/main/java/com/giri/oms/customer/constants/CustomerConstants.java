package com.giri.oms.customer.constants;

/**
 * Centralized message strings for the Customer module — exception messages,
 * validation messages, and log messages all live here instead of being
 * hardcoded inline where they're used.
 */
public final class CustomerConstants {

    private CustomerConstants() {
        // utility class — no instances
    }

    // ---- Exception messages ----
    // Not-found / already-exists messages now live in
    // com.giri.oms.common.exception.ErrorCode alongside their error codes.

    // ---- Bean Validation messages ----
    public static final String FIRST_NAME_REQUIRED_MESSAGE = "First name must not be blank";
    public static final String FIRST_NAME_SIZE_MESSAGE = "First name must be at most 100 characters";
    public static final String LAST_NAME_REQUIRED_MESSAGE = "Last name must not be blank";
    public static final String LAST_NAME_SIZE_MESSAGE = "Last name must be at most 100 characters";
    public static final String EMAIL_REQUIRED_MESSAGE = "Email must not be blank";
    public static final String EMAIL_INVALID_MESSAGE = "Email must be a valid email address";
    public static final String PHONE_INVALID_MESSAGE = "Phone number format is invalid";
    public static final String STATUS_REQUIRED_MESSAGE = "Customer status must not be null";

    // ---- Success / log messages ----
    public static final String CUSTOMER_CREATED_LOG = "Customer created successfully with id: {}";
    public static final String CUSTOMER_UPDATED_LOG = "Customer updated successfully with id: {}";
    public static final String CUSTOMER_DELETED_LOG = "Customer deleted successfully with id: {}";

}
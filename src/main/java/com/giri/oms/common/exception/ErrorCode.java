package com.giri.oms.common.exception;

import java.util.HashSet;
import java.util.Set;

/**
 * Single source of truth for every error this API can return: code, HTTP-adjacent
 * category, and message together in one place instead of spread across each module's
 * {@code *Constants.java} and {@code GlobalExceptionHandler}.
 * <p>
 * Wire format is a fixed 6 characters, parsed by the UI as
 * <pre>
 *     [PREFIX(1)][COMPONENT_ID(2)][ERROR_ID(3)]
 *
 *     e.g. E PR 100  ->  "EPR100"
 * </pre>
 * <b>Prefix</b> — message severity: {@code E} error (blocks the request), {@code W}
 * warning (completed with caveats), {@code I} informational (non-blocking).
 * <p>
 * <b>Component ID</b> (2 letters) — {@code CM} common/platform, {@code PR} Product,
 * {@code CU} Customer, {@code OR} Order, {@code PY} Payment, {@code SH} Shipment,
 * {@code IN} Inventory, {@code AU} Auth/identity.
 * <p>
 * <b>Error ID</b> (3 digits) — {@code 001}-{@code 099} client-side validation/bad input,
 * {@code 100}-{@code 199} resource state (not found/conflict/illegal transition),
 * {@code 500}-{@code 599} internal system dependency/third-party timeout.
 * <p>
 * <b>Message</b> — for exceptions that only ever have one message shape (a not-found
 * by id, an already-exists by a unique field, etc.), {@link #messageTemplate} IS the
 * message: the exception calls {@link #formatMessage} instead of keeping its own copy.
 * A few exceptions (illegal state transitions) are thrown from several call sites with
 * genuinely different wording under the same code — for those, {@link #messageTemplate}
 * is a representative description for this catalog, not the literal runtime text; the
 * actual message is still built where it's thrown.
 * <p>
 * This is the contract clients branch on — once published, a code is append-only: never
 * reassign a code to a different meaning, and never renumber an existing constant.
 */
public enum ErrorCode {

    // ---- Common / platform (CM) ----
    VALIDATION_FAILED("E", "CM", "001",
            "One or more fields failed validation"),
    INVALID_SORT_FIELD("E", "CM", "002",
            "Invalid sort field: %s"),
    UNAUTHENTICATED("E", "CM", "003",
            "A valid Bearer token is required to access this resource"),
    ACCESS_DENIED("E", "CM", "101",
            "You do not have permission to perform this action"),
    LOCK_ACQUISITION_FAILED("E", "CM", "102",
            "Could not acquire lock for '%s' — another update is in progress, please retry"),
    OPTIMISTIC_LOCK_CONFLICT("E", "CM", "103",
            "This record was modified by someone else in the meantime — please refresh and try again."),
    RATE_LIMIT_EXCEEDED("E", "CM", "104",
            "Too many login attempts — please try again in %d seconds"),
    INTERNAL_ERROR("E", "CM", "500",
            "An unexpected error occurred. Please try again later."),

    // ---- Product (PR) ----
    PRODUCT_NOT_FOUND("E", "PR", "100",
            "Product not found with id: %d"),

    // ---- Customer (CU) ----
    CUSTOMER_NOT_FOUND("E", "CU", "100",
            "Customer not found with id: %d"),
    CUSTOMER_EMAIL_ALREADY_EXISTS("E", "CU", "101",
            "A customer already exists with email: %s"),

    // ---- Order (OR) ----
    ORDER_NOT_FOUND("E", "OR", "100",
            "Order not found with id: %d"),
    // Thrown for several distinct scenarios (invalid status transition, deleting an
    // order past the deletable window) each with its own wording — see
    // OrderServiceImpl. This description is representative, not literal.
    ILLEGAL_ORDER_STATE("E", "OR", "101",
            "Operation conflicts with the order's current status"),

    // ---- Payment (PY) ----
    PAYMENT_NOT_FOUND("E", "PY", "100",
            "Payment not found with id: %d"),
    // See ILLEGAL_ORDER_STATE note — same situation for payments (invalid transition,
    // not deletable, order not awaiting payment). Representative description only.
    ILLEGAL_PAYMENT_STATE("E", "PY", "101",
            "Operation conflicts with the payment's current status"),

    // ---- Shipment (SH) ----
    SHIPMENT_NOT_FOUND("E", "SH", "100",
            "Shipment not found with id: %d"),
    // See ILLEGAL_ORDER_STATE note — same situation for shipments. Representative
    // description only.
    ILLEGAL_SHIPMENT_STATE("E", "SH", "101",
            "Operation conflicts with the shipment's current status"),

    // ---- Inventory (IN) ----
    INVENTORY_NOT_FOUND("E", "IN", "100",
            "Inventory record not found with id: %d"),
    INVENTORY_ALREADY_EXISTS("E", "IN", "101",
            "An inventory record already exists for product id %d at location: %s"),
    INSUFFICIENT_STOCK("E", "IN", "102",
            "Insufficient stock for product id %d: requested %d, available %d"),

    // ---- Auth / identity (AU) ----
    INVALID_CREDENTIALS("E", "AU", "001",
            "Invalid username or password"),
    USERNAME_ALREADY_EXISTS("E", "AU", "100",
            "Username already taken: %s"),
    EMAIL_ALREADY_EXISTS("E", "AU", "101",
            "Email already registered: %s");

    private final String prefix;
    private final String componentId;
    private final String errorId;
    private final String messageTemplate;

    ErrorCode(String prefix, String componentId, String errorId, String messageTemplate) {
        this.prefix = prefix;
        this.componentId = componentId;
        this.errorId = errorId;
        this.messageTemplate = messageTemplate;
    }

    /** The 6-character code sent to clients, e.g. {@code "EPR100"}. */
    public String code() {
        return prefix + componentId + errorId;
    }

    /**
     * Formats {@link #messageTemplate} with the given args via {@link String#format}.
     * Only meaningful for codes whose template is the literal runtime message — see the
     * class Javadoc for which ones those are.
     */
    public String formatMessage(Object... args) {
        return String.format(messageTemplate, args);
    }

    // Fail fast at class-load time if two constants were accidentally given the same
    // code — a silent collision here would mean two different failures are
    // indistinguishable to a client branching on errorCode.
    static {
        Set<String> seen = new HashSet<>();
        for (ErrorCode value : values()) {
            if (!seen.add(value.code())) {
                throw new ExceptionInInitializerError(
                        "Duplicate ErrorCode.code() value: " + value.code() + " (on " + value.name() + ")");
            }
        }
    }
}

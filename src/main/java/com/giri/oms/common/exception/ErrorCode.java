package com.giri.oms.common.exception;

import org.springframework.http.HttpStatus;

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
 * <p>
 * <b>HTTP status</b> — each code also carries the {@link HttpStatus} it's returned with,
 * so this enum is the single place that answers both "what code is this" and "what
 * status does it come back as" — {@link com.giri.oms.common.exception.GlobalExceptionHandler}
 * and the OpenAPI documentation generator (see {@code common.openapi}) both read it from
 * here rather than hardcoding it a second time. A code is {@code null} here only if it
 * never crosses the REST boundary at all — currently just {@link #INSUFFICIENT_STOCK},
 * which is thrown solely inside an async Kafka consumer (see
 * {@code OrderCreatedInventoryConsumer}) and never reaches a controller.
 */
public enum ErrorCode {

    // ---- Common / platform (CM) ----
    VALIDATION_FAILED("E", "CM", "001", HttpStatus.BAD_REQUEST,
            "One or more fields failed validation"),
    INVALID_SORT_FIELD("E", "CM", "002", HttpStatus.BAD_REQUEST,
            "Invalid sort field: %s"),
    UNAUTHENTICATED("E", "CM", "003", HttpStatus.UNAUTHORIZED,
            "A valid Bearer token is required to access this resource"),
    ACCESS_DENIED("E", "CM", "101", HttpStatus.FORBIDDEN,
            "You do not have permission to perform this action"),
    LOCK_ACQUISITION_FAILED("E", "CM", "102", HttpStatus.CONFLICT,
            "Could not acquire lock for '%s' — another update is in progress, please retry"),
    OPTIMISTIC_LOCK_CONFLICT("E", "CM", "103", HttpStatus.CONFLICT,
            "This record was modified by someone else in the meantime — please refresh and try again."),
    RATE_LIMIT_EXCEEDED("E", "CM", "104", HttpStatus.TOO_MANY_REQUESTS,
            "Too many login attempts — please try again in %d seconds"),
    // Catch-all for a DB uniqueness-constraint violation that reaches the handler as a
    // raw DataIntegrityViolationException instead of a domain-specific *AlreadyExists
    // exception — i.e. a check-then-act race (an "exists?" check followed by a save())
    // that a request-level lock didn't (or couldn't) fully close. Deliberately generic
    // since the violated constraint could belong to any table; see GlobalExceptionHandler.
    RESOURCE_CONFLICT("E", "CM", "105", HttpStatus.CONFLICT,
            "This request conflicts with an existing record — please check for a duplicate and try again."),
    INTERNAL_ERROR("E", "CM", "500", HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later."),

    // ---- Product (PR) ----
    PRODUCT_NOT_FOUND("E", "PR", "100", HttpStatus.NOT_FOUND,
            "Product not found with id: %d"),

    // ---- Customer (CU) ----
    CUSTOMER_NOT_FOUND("E", "CU", "100", HttpStatus.NOT_FOUND,
            "Customer not found with id: %d"),
    CUSTOMER_EMAIL_ALREADY_EXISTS("E", "CU", "101", HttpStatus.CONFLICT,
            "A customer already exists with email: %s"),

    // ---- Order (OR) ----
    ORDER_NOT_FOUND("E", "OR", "100", HttpStatus.NOT_FOUND,
            "Order not found with id: %d"),
    // Thrown for several distinct scenarios (invalid status transition, deleting an
    // order past the deletable window) each with its own wording — see
    // OrderServiceImpl. This description is representative, not literal.
    ILLEGAL_ORDER_STATE("E", "OR", "101", HttpStatus.CONFLICT,
            "Operation conflicts with the order's current status"),

    // ---- Payment (PY) ----
    PAYMENT_NOT_FOUND("E", "PY", "100", HttpStatus.NOT_FOUND,
            "Payment not found with id: %d"),
    // See ILLEGAL_ORDER_STATE note — same situation for payments (invalid transition,
    // not deletable, order not awaiting payment). Representative description only.
    ILLEGAL_PAYMENT_STATE("E", "PY", "101", HttpStatus.CONFLICT,
            "Operation conflicts with the payment's current status"),

    // ---- Shipment (SH) ----
    SHIPMENT_NOT_FOUND("E", "SH", "100", HttpStatus.NOT_FOUND,
            "Shipment not found with id: %d"),
    // See ILLEGAL_ORDER_STATE note — same situation for shipments. Representative
    // description only.
    ILLEGAL_SHIPMENT_STATE("E", "SH", "101", HttpStatus.CONFLICT,
            "Operation conflicts with the shipment's current status"),

    // ---- Inventory (IN) ----
    INVENTORY_NOT_FOUND("E", "IN", "100", HttpStatus.NOT_FOUND,
            "Inventory record not found with id: %d"),
    INVENTORY_ALREADY_EXISTS("E", "IN", "101", HttpStatus.CONFLICT,
            "An inventory record already exists for product id %d at location: %s"),
    // Never reaches a controller — see class Javadoc. No HTTP status.
    INSUFFICIENT_STOCK("E", "IN", "102", null,
            "Insufficient stock for product id %d: requested %d, available %d"),

    // ---- Auth / identity (AU) ----
    INVALID_CREDENTIALS("E", "AU", "001", HttpStatus.UNAUTHORIZED,
            "Invalid username or password"),
    USERNAME_ALREADY_EXISTS("E", "AU", "100", HttpStatus.CONFLICT,
            "Username already taken: %s"),
    EMAIL_ALREADY_EXISTS("E", "AU", "101", HttpStatus.CONFLICT,
            "Email already registered: %s");

    private final String prefix;
    private final String componentId;
    private final String errorId;
    private final HttpStatus httpStatus;
    private final String messageTemplate;

    ErrorCode(String prefix, String componentId, String errorId, HttpStatus httpStatus, String messageTemplate) {
        this.prefix = prefix;
        this.componentId = componentId;
        this.errorId = errorId;
        this.httpStatus = httpStatus;
        this.messageTemplate = messageTemplate;
    }

    /** The 6-character code sent to clients, e.g. {@code "EPR100"}. */
    public String code() {
        return prefix + componentId + errorId;
    }

    /**
     * The HTTP status this code is returned with, or {@code null} for codes that never
     * cross the REST boundary (see class Javadoc). {@link com.giri.oms.common.exception.GlobalExceptionHandler}
     * and the OpenAPI generator both rely on this instead of hardcoding status per handler.
     */
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    /**
     * Formats {@link #messageTemplate} with the given args via {@link String#format}.
     * Only meaningful for codes whose template is the literal runtime message — see the
     * class Javadoc for which ones those are.
     */
    public String formatMessage(Object... args) {
        return String.format(messageTemplate, args);
    }

    /**
     * A representative rendering of {@link #messageTemplate} for OpenAPI examples and
     * documentation only — placeholders are filled with generic stand-in values instead
     * of real request data, and this never throws regardless of how many placeholders
     * the template has. Never use this for an actual client-facing message; use
     * {@link #formatMessage} with real arguments for that.
     */
    public String sampleMessage() {
        return messageTemplate
                .replace("%d", "123")
                .replace("%s", "example");
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

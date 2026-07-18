package com.giri.oms.common.exception;

import com.giri.oms.auth.exception.EmailAlreadyExistsException;
import com.giri.oms.auth.exception.UsernameAlreadyExistsException;
import com.giri.oms.common.lock.LockAcquisitionException;
import com.giri.oms.customer.exception.CustomerEmailAlreadyExistsException;
import com.giri.oms.customer.exception.CustomerNotFoundException;
import com.giri.oms.inventory.exception.InventoryAlreadyExistsException;
import com.giri.oms.inventory.exception.InventoryNotFoundException;
import com.giri.oms.order.exception.IllegalOrderStateException;
import com.giri.oms.order.exception.OrderNotFoundException;
import com.giri.oms.payment.exception.IllegalPaymentStateException;
import com.giri.oms.payment.exception.PaymentNotFoundException;
import com.giri.oms.product.exception.ProductNotFoundException;
import com.giri.oms.shipment.exception.IllegalShipmentStateException;
import com.giri.oms.shipment.exception.ShipmentNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every handler here builds an {@link ErrorResponse} that carries both the human-readable
 * {@code message} and a stable {@code errorCode} (see {@link ErrorCode}) so clients can
 * branch on the code instead of parsing message text. For exceptions that implement
 * {@link ErrorCoded}, the code comes from the exception itself via {@link #codeOf}; for
 * framework-thrown exceptions that can't implement our interface, the code is fixed per
 * handler below.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(ProductNotFoundException ex, HttpServletRequest request) {
        log.warn("Product not found — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, codeOf(ex), ex.getMessage(), request);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCustomerNotFound(CustomerNotFoundException ex, HttpServletRequest request) {
        log.warn("Customer not found — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, codeOf(ex), ex.getMessage(), request);
    }

    @ExceptionHandler(CustomerEmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCustomerAlreadyExists(CustomerEmailAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("Customer already exists — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, codeOf(ex), ex.getMessage(), request);
    }

    @ExceptionHandler(InventoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInventoryNotFound(InventoryNotFoundException ex, HttpServletRequest request) {
        log.warn("Inventory record not found — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, codeOf(ex), ex.getMessage(), request);
    }

    @ExceptionHandler(InventoryAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleInventoryAlreadyExists(InventoryAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("Duplicate inventory record — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, codeOf(ex), ex.getMessage(), request);
    }

    @ExceptionHandler(LockAcquisitionException.class)
    public ResponseEntity<ErrorResponse> handleLockAcquisitionFailure(LockAcquisitionException ex, HttpServletRequest request) {
        log.warn("Distributed lock contention — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, codeOf(ex), ex.getMessage(), request);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex, HttpServletRequest request) {
        log.warn("Order not found — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, codeOf(ex), ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalOrderStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalOrderState(IllegalOrderStateException ex, HttpServletRequest request) {
        log.warn("Illegal order state transition — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, codeOf(ex), ex.getMessage(), request);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex, HttpServletRequest request) {
        log.warn("Payment not found — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, codeOf(ex), ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalPaymentStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalPaymentState(IllegalPaymentStateException ex, HttpServletRequest request) {
        log.warn("Illegal payment state transition — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, codeOf(ex), ex.getMessage(), request);
    }

    @ExceptionHandler(ShipmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleShipmentNotFound(ShipmentNotFoundException ex, HttpServletRequest request) {
        log.warn("Shipment not found — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, codeOf(ex), ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalShipmentStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalShipmentState(IllegalShipmentStateException ex, HttpServletRequest request) {
        log.warn("Illegal shipment state transition — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, codeOf(ex), ex.getMessage(), request);
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUsernameAlreadyExists(UsernameAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("Username already taken — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, codeOf(ex), ex.getMessage(), request);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("Email already registered — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, codeOf(ex), ex.getMessage(), request);
    }

    // Covers both wrong username/password and a disabled account attempting to log
    // in — AuthenticationManager.authenticate() throws one of these two, and both
    // map to the same generic 401 so a caller can't use the response to tell
    // "wrong password" apart from "account exists but disabled".
    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationFailure(Exception ex, HttpServletRequest request) {
        log.warn("Authentication failed — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS,
                ErrorCode.INVALID_CREDENTIALS.formatMessage(), request);
    }

    @ExceptionHandler(org.springframework.data.core.PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSortProperty(
            org.springframework.data.core.PropertyReferenceException ex, HttpServletRequest request) {
        log.warn("Invalid sort property in request — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_SORT_FIELD,
                ErrorCode.INVALID_SORT_FIELD.formatMessage(ex.getPropertyName()), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex,
                                                                          HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(fieldError -> {
            fieldErrors.computeIfAbsent(fieldError.getField(), k -> new ArrayList<>())
                    .add(fieldError.getDefaultMessage());
        });

        log.warn("Validation failed — path: {}, fields: {}", request.getRequestURI(), fieldErrors.keySet());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Validation Failed");
        response.put("errorCode", ErrorCode.VALIDATION_FAILED.code());
        response.put("path", request.getRequestURI());
        response.put("errors", fieldErrors);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidSortFieldException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSortField(InvalidSortFieldException ex, HttpServletRequest request) {
        log.warn("Invalid sort field — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, codeOf(ex), ex.getMessage(), request);
    }

    // @PreAuthorize rejections throw AccessDeniedException from inside the AOP proxy
    // around the controller method — that happens during normal Spring MVC dispatch,
    // so it's this @ControllerAdvice that sees it first, not Spring Security's
    // ExceptionTranslationFilter/JwtAccessDeniedHandler (those only catch exceptions
    // that reach back up to the servlet filter chain, which this never does). Without
    // this handler, the catch-all Exception.class handler below would turn every
    // @PreAuthorize rejection into a 500 instead of a 403.
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex,
                                                             HttpServletRequest request) {
        log.warn("Access denied — path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                ErrorCode.ACCESS_DENIED.formatMessage(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic locking conflict — path: {}, entity: {}", request.getRequestURI(), ex.getPersistentClassName());
        return build(HttpStatus.CONFLICT, ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                ErrorCode.OPTIMISTIC_LOCK_CONFLICT.formatMessage(), request);
    }

    // Catch-all safety net — anything not handled above lands here as a 500,
    // instead of leaking a raw stack trace to the client.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception — path: {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.formatMessage(), request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, ErrorCode errorCode, String message,
                                                 HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                errorCode.code(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(response);
    }

    private ErrorCode codeOf(ErrorCoded ex) {
        return ex.getErrorCode();
    }
}

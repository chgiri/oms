package com.giri.oms.common.exception;

import com.giri.oms.product.exception.InvalidSortFieldException;
import com.giri.oms.product.exception.ProductNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(ProductNotFoundException ex, HttpServletRequest request) {
        log.warn("Product not found — path: {}, message: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
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
        response.put("path", request.getRequestURI());
        response.put("errors", fieldErrors);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidSortFieldException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSortField(InvalidSortFieldException ex, HttpServletRequest request) {
        log.warn("Invalid sort field — path: {}, message: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = new ErrorResponse(
          LocalDateTime.now(),
          HttpStatus.BAD_REQUEST.value(),
          HttpStatus.BAD_REQUEST.getReasonPhrase(),
          ex.getMessage(), request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // Catch-all safety net — anything not handled above lands here as a 500,
    // instead of leaking a raw stack trace to the client.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception — path: {}", request.getRequestURI(), ex);

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}

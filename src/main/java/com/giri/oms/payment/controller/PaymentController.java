package com.giri.oms.payment.controller;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.payment.dto.PaymentRequest;
import com.giri.oms.payment.dto.PaymentResponse;
import com.giri.oms.payment.dto.PaymentStatusUpdateRequest;
import com.giri.oms.payment.entity.PaymentMethod;
import com.giri.oms.payment.entity.PaymentStatus;
import com.giri.oms.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Payment recording, status tracking, and refunds")
public class PaymentController {

    private final PaymentService paymentService;

    // Build Create Payment REST API
    @PostMapping
    @Operation(summary = "Record a new payment",
            description = "Records a payment attempt against an order. The order must already exist — "
                    + "an unknown orderId returns a 404. New payments always start in PENDING status; "
                    + "use PATCH /{id}/status to confirm, fail, or refund it.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(name = "Created payment", value = """
                                    {
                                      "id": 1,
                                      "orderId": 1,
                                      "amount": 77.97,
                                      "method": "CREDIT_CARD",
                                      "status": "PENDING",
                                      "transactionReference": null,
                                      "createdAt": "2026-07-01T10:15:30",
                                      "updatedAt": "2026-07-01T10:15:30"
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "Validation error — e.g. missing orderId, non-positive amount, missing method"),
            @ApiResponse(responseCode = "404", description = "No order exists with the given ID")
    })
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest paymentRequest) {
        log.info("POST /api/payments — creating payment for order id: {}", paymentRequest.getOrderId());
        PaymentResponse savedPayment = paymentService.createPayment(paymentRequest);
        return new ResponseEntity<>(savedPayment, HttpStatus.CREATED);
    }

    // Build Get Payment REST API
    @GetMapping("{id}")
    @Operation(summary = "Get a payment by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "404", description = "No payment exists with the given ID")
    })
    public ResponseEntity<PaymentResponse> getPaymentById(
            @Parameter(description = "ID of the payment to fetch", example = "1")
            @PathVariable("id") Long paymentId) {
        log.debug("GET /api/payments/{} — fetching payment", paymentId);
        PaymentResponse paymentResponse = paymentService.getPaymentById(paymentId);
        return ResponseEntity.ok(paymentResponse);
    }

    // Build Get All Payments REST API
    @GetMapping
    @Operation(summary = "Get all payments (paginated)",
            description = "Returns payments page by page. `sortBy` is restricted to an allow-list "
                    + "(id, amount, status, method, createdAt, updatedAt) — any other value returns a 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of payments returned"),
            @ApiResponse(responseCode = "400", description = "Invalid sortBy field")
    })
    public ResponseEntity<PagedResponse<PaymentResponse>> getAllPayments(
            @Parameter(description = "Page number, 0-indexed", example = "0")
            @RequestParam(value = "pageNo", defaultValue = "0") int pageNo,
            @Parameter(description = "Number of items per page (capped server-side)", example = "10")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @Parameter(description = "Field to sort by — id, amount, status, method, createdAt, or updatedAt", example = "id")
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @Parameter(description = "Sort direction", schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir) {

        log.debug("GET /api/payments — fetching all payments");
        PagedResponse<PaymentResponse> response = paymentService.getAllPayments(pageNo, pageSize, sortBy, sortDir);
        return ResponseEntity.ok(response);
    }

    // Build Update Payment Status REST API
    @PatchMapping("{id}/status")
    @Operation(summary = "Transition a payment's status",
            description = "Moves the payment to a new status. Allowed transitions: "
                    + "PENDING → COMPLETED or FAILED; COMPLETED → REFUNDED. "
                    + "FAILED and REFUNDED are terminal — any other transition returns a 409. "
                    + "An optional transactionReference in the request body is recorded alongside the transition "
                    + "(typically supplied when confirming a COMPLETED payment); omitting it leaves any existing "
                    + "reference on the payment unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment status updated"),
            @ApiResponse(responseCode = "400", description = "Validation error — e.g. missing status"),
            @ApiResponse(responseCode = "404", description = "No payment exists with the given ID"),
            @ApiResponse(responseCode = "409", description = "The requested transition is not allowed from the payment's current status")
    })
    public ResponseEntity<PaymentResponse> updatePaymentStatus(
            @Parameter(description = "ID of the payment to update", example = "1")
            @PathVariable("id") Long id,
            @Valid @RequestBody PaymentStatusUpdateRequest statusUpdateRequest) {

        log.info("PATCH /api/payments/{}/status — transitioning to {}", id, statusUpdateRequest.getStatus());
        PaymentResponse updatedPayment = paymentService.updatePaymentStatus(
                id, statusUpdateRequest.getStatus(), statusUpdateRequest.getTransactionReference());
        return ResponseEntity.ok(updatedPayment);
    }

    // Build Delete Payment REST API
    @DeleteMapping("{id}")
    @Operation(summary = "Delete a payment",
            description = "Only payments in PENDING or FAILED status can be deleted — "
                    + "once a payment has completed (or been refunded), its record is kept for the audit trail instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Payment deleted"),
            @ApiResponse(responseCode = "404", description = "No payment exists with the given ID"),
            @ApiResponse(responseCode = "409", description = "The payment's current status does not allow deletion")
    })
    public ResponseEntity<Void> deletePayment(
            @Parameter(description = "ID of the payment to delete", example = "1")
            @PathVariable("id") Long paymentId) {
        log.info("DELETE /api/payments/{} — deleting payment", paymentId);

        paymentService.deletePayment(paymentId);
        return ResponseEntity.noContent().build();
    }

    // Build Search Payments REST API — JPQL query approach
    @GetMapping("/search")
    @Operation(summary = "Search payments (JPQL query approach)",
            description = "Filters by orderId (exact), status (exact), method (exact), and/or amount range. "
                    + "All filters are optional — omitting all of them returns every payment, paginated. "
                    + "Functionally equivalent to /search/advanced; this variant is implemented with a hand-written JPQL @Query.")
    public ResponseEntity<Page<PaymentResponse>> searchPayments(
            @Parameter(description = "Exact match on order ID", example = "1")
            @RequestParam(required = false) Long orderId,
            @Parameter(description = "Exact match on payment status", example = "PENDING")
            @RequestParam(required = false) PaymentStatus status,
            @Parameter(description = "Exact match on payment method", example = "CREDIT_CARD")
            @RequestParam(required = false) PaymentMethod method,
            @Parameter(description = "Minimum amount (inclusive)", example = "50.00")
            @RequestParam(required = false) BigDecimal minAmount,
            @Parameter(description = "Maximum amount (inclusive)", example = "500.00")
            @RequestParam(required = false) BigDecimal maxAmount,
            @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        log.debug("GET /api/payments/search — orderId={}, status={}, method={}, minAmount={}, maxAmount={}, page={}, size={}",
                orderId, status, method, minAmount, maxAmount, pageable.getPageNumber(), pageable.getPageSize());

        Page<PaymentResponse> results = paymentService.searchPayments(orderId, status, method, minAmount, maxAmount, pageable);
        return ResponseEntity.ok(results);
    }

    // Build Search Payments REST API — JpaSpecificationExecutor approach
    @GetMapping("/search/advanced")
    @Operation(summary = "Search payments (JPA Specification approach)",
            description = "Same filters and behavior as /search, implemented with JpaSpecificationExecutor instead of JPQL. "
                    + "Kept alongside /search for comparison — see project docs for which is preferred going forward.")
    public ResponseEntity<Page<PaymentResponse>> searchPaymentsAdvanced(
            @Parameter(description = "Exact match on order ID", example = "1")
            @RequestParam(required = false) Long orderId,
            @Parameter(description = "Exact match on payment status", example = "PENDING")
            @RequestParam(required = false) PaymentStatus status,
            @Parameter(description = "Exact match on payment method", example = "CREDIT_CARD")
            @RequestParam(required = false) PaymentMethod method,
            @Parameter(description = "Minimum amount (inclusive)", example = "50.00")
            @RequestParam(required = false) BigDecimal minAmount,
            @Parameter(description = "Maximum amount (inclusive)", example = "500.00")
            @RequestParam(required = false) BigDecimal maxAmount,
            @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        log.debug("GET /api/payments/search/advanced — orderId={}, status={}, method={}, minAmount={}, maxAmount={}, page={}, size={}",
                orderId, status, method, minAmount, maxAmount, pageable.getPageNumber(), pageable.getPageSize());

        Page<PaymentResponse> results = paymentService.searchPaymentsBySpecification(orderId, status, method, minAmount, maxAmount, pageable);
        return ResponseEntity.ok(results);
    }

}

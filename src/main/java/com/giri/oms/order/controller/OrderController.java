package com.giri.oms.order.controller;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.order.dto.OrderRequest;
import com.giri.oms.order.dto.OrderResponse;
import com.giri.oms.order.dto.OrderStatusUpdateRequest;
import com.giri.oms.order.entity.OrderStatus;
import com.giri.oms.order.service.OrderService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order", description = "Order placement, status tracking, and fulfillment")
public class OrderController {

    private final OrderService orderService;

    // Build Create Order REST API
    @PostMapping
    @Operation(summary = "Place a new order",
            description = "Creates an order for a customer from one or more line items. "
                    + "The customer and every referenced product must already exist — an unknown "
                    + "customerId or productId returns a 404. Each line item's unit price is snapshotted "
                    + "from the product's current price at creation time, and the order's total is computed "
                    + "server-side. New orders always start in PENDING status.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(name = "Created order", value = """
                                    {
                                      "id": 1,
                                      "customerId": 1,
                                      "customerName": "Ada Lovelace",
                                      "status": "PENDING",
                                      "totalAmount": 77.97,
                                      "items": [
                                        {
                                          "id": 1,
                                          "productId": 1,
                                          "productName": "Wireless Mouse",
                                          "quantity": 3,
                                          "unitPrice": 25.99,
                                          "subtotal": 77.97
                                        }
                                      ],
                                      "createdAt": "2026-07-01T10:15:30",
                                      "updatedAt": "2026-07-01T10:15:30"
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "Validation error — e.g. missing customerId, empty items list, non-positive quantity"),
            @ApiResponse(responseCode = "404", description = "No customer or product exists with the given ID")
    })
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest orderRequest) {
        log.info("POST /api/orders — creating order for customer id: {}", orderRequest.getCustomerId());
        OrderResponse savedOrder = orderService.createOrder(orderRequest);
        return new ResponseEntity<>(savedOrder, HttpStatus.CREATED);
    }

    // Build Get Order REST API
    @GetMapping("{id}")
    @Operation(summary = "Get an order by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "No order exists with the given ID")
    })
    public ResponseEntity<OrderResponse> getOrderById(
            @Parameter(description = "ID of the order to fetch", example = "1")
            @PathVariable("id") Long orderId) {
        log.debug("GET /api/orders/{} — fetching order", orderId);
        OrderResponse orderResponse = orderService.getOrderById(orderId);
        return ResponseEntity.ok(orderResponse);
    }

    // Build Get All Orders REST API
    @GetMapping
    @Operation(summary = "Get all orders (paginated)",
            description = "Returns orders page by page. `sortBy` is restricted to an allow-list "
                    + "(id, status, totalAmount, createdAt, updatedAt) — any other value returns a 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of orders returned"),
            @ApiResponse(responseCode = "400", description = "Invalid sortBy field")
    })
    public ResponseEntity<PagedResponse<OrderResponse>> getAllOrders(
            @Parameter(description = "Page number, 0-indexed", example = "0")
            @RequestParam(value = "pageNo", defaultValue = "0") int pageNo,
            @Parameter(description = "Number of items per page (capped server-side)", example = "10")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @Parameter(description = "Field to sort by — id, status, totalAmount, createdAt, or updatedAt", example = "id")
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @Parameter(description = "Sort direction", schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir) {

        log.debug("GET /api/orders — fetching all orders");
        PagedResponse<OrderResponse> response = orderService.getAllOrders(pageNo, pageSize, sortBy, sortDir);
        return ResponseEntity.ok(response);
    }

    // Build Update Order Status REST API
    @PatchMapping("{id}/status")
    @Operation(summary = "Transition an order's status",
            description = "Moves the order to a new status. Allowed transitions: "
                    + "PENDING → CONFIRMED or CANCELLED; CONFIRMED → SHIPPED or CANCELLED; SHIPPED → DELIVERED. "
                    + "DELIVERED and CANCELLED are terminal — any other transition returns a 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order status updated"),
            @ApiResponse(responseCode = "400", description = "Validation error — e.g. missing status"),
            @ApiResponse(responseCode = "404", description = "No order exists with the given ID"),
            @ApiResponse(responseCode = "409", description = "The requested transition is not allowed from the order's current status")
    })
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @Parameter(description = "ID of the order to update", example = "1")
            @PathVariable("id") Long id,
            @Valid @RequestBody OrderStatusUpdateRequest statusUpdateRequest) {

        log.info("PATCH /api/orders/{}/status — transitioning to {}", id, statusUpdateRequest.getStatus());
        OrderResponse updatedOrder = orderService.updateOrderStatus(id, statusUpdateRequest.getStatus());
        return ResponseEntity.ok(updatedOrder);
    }

    // Build Delete Order REST API
    @DeleteMapping("{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete an order",
            description = "Restricted to ADMIN. Only orders in PENDING or CANCELLED status can be deleted — "
                    + "once an order has shipped, its record is kept for the audit trail instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Order deleted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Authenticated but not an ADMIN"),
            @ApiResponse(responseCode = "404", description = "No order exists with the given ID"),
            @ApiResponse(responseCode = "409", description = "The order's current status does not allow deletion")
    })
    public ResponseEntity<Void> deleteOrder(
            @Parameter(description = "ID of the order to delete", example = "1")
            @PathVariable("id") Long orderId) {
        log.info("DELETE /api/orders/{} — deleting order", orderId);

        orderService.deleteOrder(orderId);
        return ResponseEntity.noContent().build();
    }

    // Build Search Orders REST API — JPQL query approach
    @GetMapping("/search")
    @Operation(summary = "Search orders (JPQL query approach)",
            description = "Filters by customerId (exact), status (exact), and/or totalAmount range. "
                    + "All filters are optional — omitting all of them returns every order, paginated. "
                    + "Functionally equivalent to /search/advanced; this variant is implemented with a hand-written JPQL @Query.")
    public ResponseEntity<Page<OrderResponse>> searchOrders(
            @Parameter(description = "Exact match on customer ID", example = "1")
            @RequestParam(required = false) Long customerId,
            @Parameter(description = "Exact match on order status", example = "PENDING")
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "Minimum total amount (inclusive)", example = "50.00")
            @RequestParam(required = false) BigDecimal minTotal,
            @Parameter(description = "Maximum total amount (inclusive)", example = "500.00")
            @RequestParam(required = false) BigDecimal maxTotal,
            @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        log.debug("GET /api/orders/search — customerId={}, status={}, minTotal={}, maxTotal={}, page={}, size={}",
                customerId, status, minTotal, maxTotal, pageable.getPageNumber(), pageable.getPageSize());

        Page<OrderResponse> results = orderService.searchOrders(customerId, status, minTotal, maxTotal, pageable);
        return ResponseEntity.ok(results);
    }

    // Build Search Orders REST API — JpaSpecificationExecutor approach
    @GetMapping("/search/advanced")
    @Operation(summary = "Search orders (JPA Specification approach)",
            description = "Same filters and behavior as /search, implemented with JpaSpecificationExecutor instead of JPQL. "
                    + "Kept alongside /search for comparison — see project docs for which is preferred going forward.")
    public ResponseEntity<Page<OrderResponse>> searchOrdersAdvanced(
            @Parameter(description = "Exact match on customer ID", example = "1")
            @RequestParam(required = false) Long customerId,
            @Parameter(description = "Exact match on order status", example = "PENDING")
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "Minimum total amount (inclusive)", example = "50.00")
            @RequestParam(required = false) BigDecimal minTotal,
            @Parameter(description = "Maximum total amount (inclusive)", example = "500.00")
            @RequestParam(required = false) BigDecimal maxTotal,
            @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        log.debug("GET /api/orders/search/advanced — customerId={}, status={}, minTotal={}, maxTotal={}, page={}, size={}",
                customerId, status, minTotal, maxTotal, pageable.getPageNumber(), pageable.getPageSize());

        Page<OrderResponse> results = orderService.searchOrdersBySpecification(customerId, status, minTotal, maxTotal, pageable);
        return ResponseEntity.ok(results);
    }

}

package com.giri.oms.shipment.controller;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.shipment.dto.ShipmentRequest;
import com.giri.oms.shipment.dto.ShipmentResponse;
import com.giri.oms.shipment.dto.ShipmentStatusUpdateRequest;
import com.giri.oms.shipment.entity.ShipmentStatus;
import com.giri.oms.shipment.entity.ShippingCarrier;
import com.giri.oms.shipment.service.ShipmentService;
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

@Slf4j
@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
@Tag(name = "Shipment", description = "Shipment creation, carrier tracking, and delivery status")
public class ShipmentController {

    private final ShipmentService shipmentService;

    // Build Create Shipment REST API
    @PostMapping
    @Operation(summary = "Create a new shipment",
            description = "Creates a shipment for an order with a chosen carrier. The order must already exist — "
                    + "an unknown orderId returns a 404. New shipments always start in PENDING status; "
                    + "use PATCH /{id}/status to move it through SHIPPED, IN_TRANSIT, DELIVERED, or RETURNED.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Shipment created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(name = "Created shipment", value = """
                                    {
                                      "id": 1,
                                      "orderId": 1,
                                      "carrier": "UPS",
                                      "status": "PENDING",
                                      "trackingNumber": null,
                                      "shippedAt": null,
                                      "deliveredAt": null,
                                      "createdAt": "2026-07-01T10:15:30",
                                      "updatedAt": "2026-07-01T10:15:30"
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "Validation error — e.g. missing orderId, missing carrier"),
            @ApiResponse(responseCode = "404", description = "No order exists with the given ID")
    })
    public ResponseEntity<ShipmentResponse> createShipment(@Valid @RequestBody ShipmentRequest shipmentRequest) {
        log.info("POST /api/shipments — creating shipment for order id: {}", shipmentRequest.getOrderId());
        ShipmentResponse savedShipment = shipmentService.createShipment(shipmentRequest);
        return new ResponseEntity<>(savedShipment, HttpStatus.CREATED);
    }

    // Build Get Shipment REST API
    @GetMapping("{id}")
    @Operation(summary = "Get a shipment by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shipment found"),
            @ApiResponse(responseCode = "404", description = "No shipment exists with the given ID")
    })
    public ResponseEntity<ShipmentResponse> getShipmentById(
            @Parameter(description = "ID of the shipment to fetch", example = "1")
            @PathVariable("id") Long shipmentId) {
        log.debug("GET /api/shipments/{} — fetching shipment", shipmentId);
        ShipmentResponse shipmentResponse = shipmentService.getShipmentById(shipmentId);
        return ResponseEntity.ok(shipmentResponse);
    }

    // Build Get All Shipments REST API
    @GetMapping
    @Operation(summary = "Get all shipments (paginated)",
            description = "Returns shipments page by page. `sortBy` is restricted to an allow-list "
                    + "(id, status, carrier, shippedAt, deliveredAt, createdAt, updatedAt) — any other value returns a 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of shipments returned"),
            @ApiResponse(responseCode = "400", description = "Invalid sortBy field")
    })
    public ResponseEntity<PagedResponse<ShipmentResponse>> getAllShipments(
            @Parameter(description = "Page number, 0-indexed", example = "0")
            @RequestParam(value = "pageNo", defaultValue = "0") int pageNo,
            @Parameter(description = "Number of items per page (capped server-side)", example = "10")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @Parameter(description = "Field to sort by — id, status, carrier, shippedAt, deliveredAt, createdAt, or updatedAt", example = "id")
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @Parameter(description = "Sort direction", schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir) {

        log.debug("GET /api/shipments — fetching all shipments");
        PagedResponse<ShipmentResponse> response = shipmentService.getAllShipments(pageNo, pageSize, sortBy, sortDir);
        return ResponseEntity.ok(response);
    }

    // Build Update Shipment Status REST API
    @PatchMapping("{id}/status")
    @Operation(summary = "Transition a shipment's status",
            description = "Moves the shipment to a new status. Allowed transitions: "
                    + "PENDING → SHIPPED; SHIPPED → IN_TRANSIT or RETURNED; IN_TRANSIT → DELIVERED or RETURNED. "
                    + "DELIVERED and RETURNED are terminal — any other transition returns a 409. "
                    + "An optional trackingNumber in the request body is recorded alongside the transition "
                    + "(typically supplied when moving to SHIPPED); omitting it leaves any existing tracking "
                    + "number on the shipment unchanged. shippedAt and deliveredAt are stamped automatically "
                    + "the first time the shipment reaches SHIPPED or DELIVERED respectively.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shipment status updated"),
            @ApiResponse(responseCode = "400", description = "Validation error — e.g. missing status"),
            @ApiResponse(responseCode = "404", description = "No shipment exists with the given ID"),
            @ApiResponse(responseCode = "409", description = "The requested transition is not allowed from the shipment's current status")
    })
    public ResponseEntity<ShipmentResponse> updateShipmentStatus(
            @Parameter(description = "ID of the shipment to update", example = "1")
            @PathVariable("id") Long id,
            @Valid @RequestBody ShipmentStatusUpdateRequest statusUpdateRequest) {

        log.info("PATCH /api/shipments/{}/status — transitioning to {}", id, statusUpdateRequest.getStatus());
        ShipmentResponse updatedShipment = shipmentService.updateShipmentStatus(
                id, statusUpdateRequest.getStatus(), statusUpdateRequest.getTrackingNumber());
        return ResponseEntity.ok(updatedShipment);
    }

    // Build Delete Shipment REST API
    @DeleteMapping("{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a shipment",
            description = "Restricted to ADMIN. Only shipments in PENDING or RETURNED status can be deleted — "
                    + "once a shipment is in transit or delivered, its record is kept for the audit trail instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Shipment deleted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Authenticated but not an ADMIN"),
            @ApiResponse(responseCode = "404", description = "No shipment exists with the given ID"),
            @ApiResponse(responseCode = "409", description = "The shipment's current status does not allow deletion")
    })
    public ResponseEntity<Void> deleteShipment(
            @Parameter(description = "ID of the shipment to delete", example = "1")
            @PathVariable("id") Long shipmentId) {
        log.info("DELETE /api/shipments/{} — deleting shipment", shipmentId);

        shipmentService.deleteShipment(shipmentId);
        return ResponseEntity.noContent().build();
    }

    // Build Search Shipments REST API — JPQL query approach
    @GetMapping("/search")
    @Operation(summary = "Search shipments (JPQL query approach)",
            description = "Filters by orderId (exact), status (exact), and/or carrier (exact). "
                    + "All filters are optional — omitting all of them returns every shipment, paginated. "
                    + "Functionally equivalent to /search/advanced; this variant is implemented with a hand-written JPQL @Query.")
    public ResponseEntity<Page<ShipmentResponse>> searchShipments(
            @Parameter(description = "Exact match on order ID", example = "1")
            @RequestParam(required = false) Long orderId,
            @Parameter(description = "Exact match on shipment status", example = "PENDING")
            @RequestParam(required = false) ShipmentStatus status,
            @Parameter(description = "Exact match on carrier", example = "UPS")
            @RequestParam(required = false) ShippingCarrier carrier,
            @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        log.debug("GET /api/shipments/search — orderId={}, status={}, carrier={}, page={}, size={}",
                orderId, status, carrier, pageable.getPageNumber(), pageable.getPageSize());

        Page<ShipmentResponse> results = shipmentService.searchShipments(orderId, status, carrier, pageable);
        return ResponseEntity.ok(results);
    }

    // Build Search Shipments REST API — JpaSpecificationExecutor approach
    @GetMapping("/search/advanced")
    @Operation(summary = "Search shipments (JPA Specification approach)",
            description = "Same filters and behavior as /search, implemented with JpaSpecificationExecutor instead of JPQL. "
                    + "Kept alongside /search for comparison — see project docs for which is preferred going forward.")
    public ResponseEntity<Page<ShipmentResponse>> searchShipmentsAdvanced(
            @Parameter(description = "Exact match on order ID", example = "1")
            @RequestParam(required = false) Long orderId,
            @Parameter(description = "Exact match on shipment status", example = "PENDING")
            @RequestParam(required = false) ShipmentStatus status,
            @Parameter(description = "Exact match on carrier", example = "UPS")
            @RequestParam(required = false) ShippingCarrier carrier,
            @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        log.debug("GET /api/shipments/search/advanced — orderId={}, status={}, carrier={}, page={}, size={}",
                orderId, status, carrier, pageable.getPageNumber(), pageable.getPageSize());

        Page<ShipmentResponse> results = shipmentService.searchShipmentsBySpecification(orderId, status, carrier, pageable);
        return ResponseEntity.ok(results);
    }

}

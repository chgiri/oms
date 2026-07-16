package com.giri.oms.inventory.controller;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.inventory.dto.InventoryRequest;
import com.giri.oms.inventory.dto.InventoryResponse;
import com.giri.oms.inventory.service.InventoryService;
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

@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Per-product, per-warehouse stock management")
public class InventoryController {

    private final InventoryService inventoryService;

    // Build Add Inventory REST API
    @PostMapping
    @Operation(summary = "Create a new inventory record",
            description = "Creates a stock record for a product at a specific warehouse/location. "
                    + "The product must already exist — an unknown productId returns a 404. "
                    + "Each (product, location) pair must be unique — creating a duplicate returns a 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Inventory record created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(name = "Created inventory record", value = """
                                    {
                                      "id": 1,
                                      "productId": 1,
                                      "productName": "Wireless Mouse",
                                      "location": "WH-EAST-01",
                                      "quantityAvailable": 120,
                                      "quantityReserved": 15,
                                      "reorderLevel": 20,
                                      "createdAt": "2026-07-01T10:15:30",
                                      "updatedAt": "2026-07-01T10:15:30"
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "Validation error — e.g. missing productId, negative quantity"),
            @ApiResponse(responseCode = "404", description = "No product exists with the given productId"),
            @ApiResponse(responseCode = "409", description = "An inventory record already exists for this product at this location")
    })
    public ResponseEntity<InventoryResponse> createInventory(@Valid @RequestBody InventoryRequest inventoryRequest) {
        log.info("POST /api/inventory — creating inventory for product id: {} at location: {}",
                inventoryRequest.getProductId(), inventoryRequest.getLocation());
        InventoryResponse savedInventory = inventoryService.createInventory(inventoryRequest);
        return new ResponseEntity<>(savedInventory, HttpStatus.CREATED);
    }

    // Build Get Inventory REST API
    @GetMapping("{id}")
    @Operation(summary = "Get an inventory record by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory record found"),
            @ApiResponse(responseCode = "404", description = "No inventory record exists with the given ID")
    })
    public ResponseEntity<InventoryResponse> getInventoryById(
            @Parameter(description = "ID of the inventory record to fetch", example = "1")
            @PathVariable("id") Long inventoryId) {
        log.debug("GET /api/inventory/{} — fetching inventory record", inventoryId);
        InventoryResponse inventoryResponse = inventoryService.getInventoryById(inventoryId);
        return ResponseEntity.ok(inventoryResponse);
    }

    // Build Get All Inventory REST API
    @GetMapping
    @Operation(summary = "Get all inventory records (paginated)",
            description = "Returns inventory records page by page. `sortBy` is restricted to an allow-list "
                    + "(id, location, quantityAvailable, quantityReserved, reorderLevel, createdAt, updatedAt) "
                    + "— any other value returns a 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of inventory records returned"),
            @ApiResponse(responseCode = "400", description = "Invalid sortBy field")
    })
    public ResponseEntity<PagedResponse<InventoryResponse>> getAllInventory(
            @Parameter(description = "Page number, 0-indexed", example = "0")
            @RequestParam(value = "pageNo", defaultValue = "0") int pageNo,
            @Parameter(description = "Number of items per page (capped server-side)", example = "10")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @Parameter(description = "Field to sort by — id, location, quantityAvailable, quantityReserved, reorderLevel, createdAt, or updatedAt", example = "id")
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @Parameter(description = "Sort direction", schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir) {

        log.debug("GET /api/inventory — fetching all inventory records");
        PagedResponse<InventoryResponse> response = inventoryService.getAllInventory(pageNo, pageSize, sortBy, sortDir);
        return ResponseEntity.ok(response);
    }

    // Build Update Inventory REST API
    @PutMapping("{id}")
    @Operation(summary = "Update an inventory record",
            description = "Fully replaces the record's product, location, quantities, and reorder level. "
                    + "All fields are re-validated as on create. Moving the record to a (product, location) "
                    + "pair already used by another record returns a 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory record updated"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "No inventory record (or referenced product) exists with the given ID"),
            @ApiResponse(responseCode = "409", description = "Another record already exists for this product at this location")
    })
    public ResponseEntity<InventoryResponse> updateInventory(
            @Parameter(description = "ID of the inventory record to update", example = "1")
            @PathVariable("id") Long id,
            @Valid @RequestBody InventoryRequest inventoryRequest) {

        log.info("PUT /api/inventory/{} — updating inventory record", id);
        InventoryResponse updatedInventory = inventoryService.updateInventory(id, inventoryRequest);
        return ResponseEntity.ok(updatedInventory);
    }

    // Build Delete Inventory REST API
    @DeleteMapping("{id}")
    @Operation(summary = "Delete an inventory record")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Inventory record deleted"),
            @ApiResponse(responseCode = "404", description = "No inventory record exists with the given ID")
    })
    public ResponseEntity<Void> deleteInventory(
            @Parameter(description = "ID of the inventory record to delete", example = "1")
            @PathVariable("id") Long inventoryId) {
        log.info("DELETE /api/inventory/{} — deleting inventory record", inventoryId);

        inventoryService.deleteInventory(inventoryId);
        return ResponseEntity.noContent().build();
    }

    // Build Search Inventory REST API — JPQL query approach
    @GetMapping("/search")
    @Operation(summary = "Search inventory (JPQL query approach)",
            description = "Filters by productId (exact), location (partial), and/or low-stock status. "
                    + "lowStockOnly restricts results to records where quantityAvailable has dropped to or below reorderLevel. "
                    + "All filters are optional — omitting all of them returns every record, paginated. "
                    + "Functionally equivalent to /search/advanced; this variant is implemented with a hand-written JPQL @Query.")
    public ResponseEntity<Page<InventoryResponse>> searchInventory(
            @Parameter(description = "Exact match on product ID", example = "1")
            @RequestParam(required = false) Long productId,
            @Parameter(description = "Partial, case-insensitive match on location", example = "east")
            @RequestParam(required = false) String location,
            @Parameter(description = "If true, restricts to records at or below their reorder level")
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @PageableDefault(size = 10, sort = "location") Pageable pageable
    ) {
        log.debug("GET /api/inventory/search — productId={}, location={}, lowStockOnly={}, page={}, size={}",
                productId, location, lowStockOnly, pageable.getPageNumber(), pageable.getPageSize());

        Page<InventoryResponse> results = inventoryService.searchInventory(productId, location, lowStockOnly, pageable);
        return ResponseEntity.ok(results);
    }

    // Build Search Inventory REST API — JpaSpecificationExecutor approach
    @GetMapping("/search/advanced")
    @Operation(summary = "Search inventory (JPA Specification approach)",
            description = "Same filters and behavior as /search, implemented with JpaSpecificationExecutor instead of JPQL. "
                    + "Kept alongside /search for comparison — see project docs for which is preferred going forward.")
    public ResponseEntity<Page<InventoryResponse>> searchInventoryAdvanced(
            @Parameter(description = "Exact match on product ID", example = "1")
            @RequestParam(required = false) Long productId,
            @Parameter(description = "Partial, case-insensitive match on location", example = "east")
            @RequestParam(required = false) String location,
            @Parameter(description = "If true, restricts to records at or below their reorder level")
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @PageableDefault(size = 10, sort = "location") Pageable pageable
    ) {
        log.debug("GET /api/inventory/search/advanced — productId={}, location={}, lowStockOnly={}, page={}, size={}",
                productId, location, lowStockOnly, pageable.getPageNumber(), pageable.getPageSize());

        Page<InventoryResponse> results = inventoryService.searchInventoryBySpecification(productId, location, lowStockOnly, pageable);
        return ResponseEntity.ok(results);
    }

}

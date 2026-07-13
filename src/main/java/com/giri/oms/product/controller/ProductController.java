package com.giri.oms.product.controller;

import com.giri.oms.product.dto.PagedResponse;
import com.giri.oms.product.dto.ProductRequest;
import com.giri.oms.product.dto.ProductResponse;
import com.giri.oms.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog management")
public class ProductController {

    private final ProductService productService;

    // Build Add Product REST API
    @PostMapping
    @Operation(summary = "Create a new product")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        log.info("POST /api/products — creating product: {}", productRequest.getName());
        ProductResponse savedProduct = productService.createProduct(productRequest);
        return new ResponseEntity<>(savedProduct, HttpStatus.CREATED);
    }

    // Build Get Product REST API
    @GetMapping("{id}")
    @Operation(summary = "Get a product by ID")
    //@ApiResponse(responseCode = "404", description = "Product not found")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable("id") Long productId) {
        log.debug("GET /api/products/{} — fetching product", productId);
        ProductResponse productResponse = productService.getProductById(productId);
        return ResponseEntity.ok(productResponse);
    }

    // Build Get All Products REST API
    @GetMapping
    @Operation(summary = "Get all products (paginated)")
    public ResponseEntity<PagedResponse<ProductResponse>> getAllProducts(
            @RequestParam(value = "pageNo", defaultValue = "0") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir) {

        log.debug("GET /api/products — fetching all products");
        PagedResponse<ProductResponse> response = productService.getAllProducts(pageNo, pageSize, sortBy, sortDir);
        return ResponseEntity.ok(response);
    }

    // Build Update Product REST API
    @PutMapping("{id}")
    @Operation(summary = "Update a product")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable("id") Long id,
                                                         @Valid @RequestBody ProductRequest productRequest) {

        log.info("PUT /api/products/{} — updating product", id);
        ProductResponse updatedProduct = productService.updateProduct(id, productRequest);
        return ResponseEntity.ok(updatedProduct);
    }

    // Build Delete Product REST API
    @DeleteMapping("{id}")
    @Operation(summary = "Delete a product")
    @ApiResponse(responseCode = "204", description = "Product deleted")
    @ApiResponse(responseCode = "404", description = "Product not found")
    public ResponseEntity<Void> deleteProduct(@PathVariable("id") Long productId) {
        log.info("DELETE /api/products/{} — deleting product", productId);

        productService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    // Build Search Products REST API - @Query (JPQL) approach
    @GetMapping("/search")
    @Operation(summary = "Search products (JPQL query approach)")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStockOnly,
            @PageableDefault(size = 10, sort ="name") Pageable pageable
            ) {

        log.debug("GET /api/products/search — name={}, minPrice={}, maxPrice={}, inStockOnly={}, page={}, size={}",
                name, minPrice, maxPrice, inStockOnly, pageable.getPageNumber(), pageable.getPageSize());

        Page<ProductResponse> results = productService.searchProducts(name, minPrice, maxPrice, inStockOnly, pageable);
        return ResponseEntity.ok(results);
    }

    // Build Search Products REST API — JpaSpecificationExecutor approach
    @GetMapping("/search/advanced")
    @Operation(summary = "Search products (JPA Specification approach)")
    public ResponseEntity<Page<ProductResponse>> searchProductsAdvanced(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStockOnly,
            @PageableDefault(size = 10, sort = "name") Pageable pageable
    ) {
        Page<ProductResponse> results = productService.searchProductsBySpecification(
                name, minPrice, maxPrice, inStockOnly, pageable);

        log.debug("GET /api/products/search/advanced — name={}, minPrice={}, maxPrice={}, inStockOnly={}, page={}, size={}",
                name, minPrice, maxPrice, inStockOnly, pageable.getPageNumber(), pageable.getPageSize());

        return ResponseEntity.ok(results);
    }

}

package com.giri.oms.product.controller;

import com.giri.oms.product.dto.PagedResponse;
import com.giri.oms.product.dto.ProductRequest;
import com.giri.oms.product.dto.ProductResponse;
import com.giri.oms.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // Build Add Product REST API
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        ProductResponse savedProduct = productService.createProduct(productRequest);
        return new ResponseEntity<>(savedProduct, HttpStatus.CREATED);
    }

    // Build Get Product REST API
    @GetMapping("{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable("id") Long productId) {
        ProductResponse productResponse = productService.getProductById(productId);
        return ResponseEntity.ok(productResponse);
    }

    // Build Get All Products REST API
    @GetMapping
    public ResponseEntity<PagedResponse<ProductResponse>> getAllProducts(
            @RequestParam(value = "pageNo", defaultValue = "0") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir) {

        PagedResponse<ProductResponse> response = productService.getAllProducts(pageNo, pageSize, sortBy, sortDir);
        return ResponseEntity.ok(response);
    }

    // Build Update Product REST API
    @PutMapping("{id}")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable("id") Long id, @Valid @RequestBody ProductRequest productRequest) {
        ProductResponse updatedProduct = productService.updateProduct(id, productRequest);
        return ResponseEntity.ok(updatedProduct);
    }

    // Build Delete Product REST API
    @DeleteMapping("{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable("id") Long productId) {
        productService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    // Build Search Products REST API - @Query (JPQL) approach
    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStockOnly,
            @PageableDefault(size = 10, sort ="name") Pageable pageable
            ) {
        Page<ProductResponse> results = productService.searchProducts(name, minPrice, maxPrice, inStockOnly, pageable);
        return ResponseEntity.ok(results);
    }

    // Build Search Products REST API — JpaSpecificationExecutor approach
    @GetMapping("/search/advanced")
    public ResponseEntity<Page<ProductResponse>> searchProductsAdvanced(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStockOnly,
            @PageableDefault(size = 10, sort = "name") Pageable pageable
    ) {
        Page<ProductResponse> results = productService.searchProductsBySpecification(
                name, minPrice, maxPrice, inStockOnly, pageable);
        return ResponseEntity.ok(results);
    }

}

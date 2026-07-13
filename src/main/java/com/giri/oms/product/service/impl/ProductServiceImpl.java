package com.giri.oms.product.service.impl;

import com.giri.oms.product.constants.ProductConstants;
import com.giri.oms.product.dto.PagedResponse;
import com.giri.oms.product.dto.ProductRequest;
import com.giri.oms.product.dto.ProductResponse;
import com.giri.oms.product.entity.Product;
import com.giri.oms.product.exception.InvalidSortFieldException;
import com.giri.oms.product.exception.ProductNotFoundException;
import com.giri.oms.product.mapper.ProductMapper;
import com.giri.oms.product.repository.ProductRepository;
import com.giri.oms.product.service.ProductService;
import com.giri.oms.product.specification.ProductSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // class-level default: every method is read-only unless overridden below
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "name", "price", "stock", "createdAt", "updatedAt");

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    public ProductResponse createProduct(ProductRequest request) {
        log.debug("Creating product with name: {}", request.getName());

        Product product = productMapper.mapToProduct(request);
        Product savedProduct = productRepository.save(product);

        log.info(ProductConstants.PRODUCT_CREATED_LOG, savedProduct.getId());
        return productMapper.mapToProductResponse(savedProduct);
    }

    @Override
    public ProductResponse getProductById(Long productId) {
        log.debug("Fetching product with id: {}", productId);
        return productMapper.mapToProductResponse(getExistingProduct(productId));
    }

    @Override
    public PagedResponse<ProductResponse> getAllProducts(int pageNo, int pageSize, String sortBy, String sortDir) {
        log.debug("Fetching all products");

        validateSortField(sortBy);

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.DESC.name())
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);

        Page<Product> productPage = productRepository.findAll(pageable);

        Page<ProductResponse> responsePage = productPage.map(productMapper::mapToProductResponse);

        return PagedResponse.of(responsePage);
    }

    private void validateSortField(String sortBy) {
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new InvalidSortFieldException(sortBy, ALLOWED_SORT_FIELDS);
        }
    }

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    public ProductResponse updateProduct(Long productId, ProductRequest request) {
        log.debug("Updating product with id: {}", productId);

        Product product = getExistingProduct(productId);
        productMapper.mapToProduct(request, product);
        Product updatedProduct = productRepository.save(product);

        log.info(ProductConstants.PRODUCT_UPDATED_LOG, updatedProduct.getId());
        return productMapper.mapToProductResponse(updatedProduct);
    }

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    public void deleteProduct(Long productId) {
        log.debug("Deleting product with id: {}", productId);

        getExistingProduct(productId);
        productRepository.deleteById(productId);

        log.info(ProductConstants.PRODUCT_DELETED_LOG, productId);
    }

    @Override
    public Page<ProductResponse> searchProducts(String name, BigDecimal minPrice, BigDecimal maxPrice,
                                                boolean inStockOnly, Pageable pageable) {
        Page<Product> products = productRepository.searchProducts(name, minPrice, maxPrice, inStockOnly, pageable);
        return products.map(productMapper::mapToProductResponse);
    }

    @Override
    public Page<ProductResponse> searchProductsBySpecification(String name, BigDecimal minPrice, BigDecimal maxPrice,
                                                               boolean inStockOnly, Pageable pageable) {
        var spec = ProductSpecification.buildSearchSpec(name, minPrice, maxPrice, inStockOnly);
        Page<Product> products = productRepository.findAll(spec, pageable);
        return products.map(productMapper::mapToProductResponse);
    }

    private Product getExistingProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> {
                    log.warn("Product not found with id: {}", productId);
                    return new ProductNotFoundException(productId);
                });
    }

}

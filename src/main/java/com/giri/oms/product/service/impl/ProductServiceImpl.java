package com.giri.oms.product.service.impl;

import com.giri.oms.product.dto.PagedResponse;
import com.giri.oms.product.dto.ProductRequest;
import com.giri.oms.product.dto.ProductResponse;
import com.giri.oms.product.entity.Product;
import com.giri.oms.product.exception.InvalidSortFieldException;
import com.giri.oms.product.exception.ProductNotFoundException;
import com.giri.oms.product.mapper.ProductMapper;
import com.giri.oms.product.repository.ProductRepository;
import com.giri.oms.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "name", "price", "stock", "createdAt", "updatedAt");
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public ProductResponse createProduct(ProductRequest request) {
        Product product = ProductMapper.mapToProduct(request);
        Product savedProduct = productRepository.save(product);
        return ProductMapper.mapToProductResponse(savedProduct);
    }

    @Override
    public ProductResponse getProductById(Long productId) {
        return ProductMapper.mapToProductResponse(getExistingProduct(productId));
    }

    @Override
    public PagedResponse<ProductResponse> getAllProducts(int pageNo, int pageSize, String sortBy, String sortDir) {

        validateSortField(sortBy);

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.DESC.name())
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);

        Page<Product> productPage = productRepository.findAll(pageable);

        Page<ProductResponse> responsePage = productPage.map(ProductMapper::mapToProductResponse);

        return PagedResponse.of(responsePage);
    }

    private void validateSortField(String sortBy) {
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new InvalidSortFieldException(sortBy, ALLOWED_SORT_FIELDS);
        }
    }

    @Override
    public ProductResponse updateProduct(Long productId, ProductRequest request) {
        Product product = getExistingProduct(productId);
        ProductMapper.mapToProduct(request, product);
        Product updatedProduct = productRepository.save(product);

        return ProductMapper.mapToProductResponse(updatedProduct);
    }

    @Override
    public void deleteProduct(Long productId) {
        getExistingProduct(productId);
        productRepository.deleteById(productId);

    }

    private Product getExistingProduct(Long productId) {
        return productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
    }
}

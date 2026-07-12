package com.giri.oms.product.service.impl;

import com.giri.oms.product.dto.ProductRequest;
import com.giri.oms.product.dto.ProductResponse;
import com.giri.oms.product.entity.Product;
import com.giri.oms.product.exception.ProductNotFoundException;
import com.giri.oms.product.mapper.ProductMapper;
import com.giri.oms.product.repository.ProductRepository;
import com.giri.oms.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

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
    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRepository.findAll();
        return products.stream().map((product) -> ProductMapper.mapToProductResponse(product))
                .collect(Collectors.toList());
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

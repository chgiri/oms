package com.giri.oms.product.service.impl;

import com.giri.oms.product.dto.ProductDto;
import com.giri.oms.product.entity.Product;
import com.giri.oms.product.exception.ProductNotFoundException;
import com.giri.oms.product.mapper.ProductMapper;
import com.giri.oms.product.repository.ProductRepository;
import com.giri.oms.product.service.ProductService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ProductServiceImpl implements ProductService {

    private ProductRepository productRepository;

    @Override
    public ProductDto createProduct(ProductDto productDto) {
        productDto.setId(null);
        productDto.setCreatedAt(new Date());
        productDto.setUpdatedAt(new Date());
        Product product = ProductMapper.mapToProduct(productDto);
        Product savedProduct = productRepository.save(product);
        return ProductMapper.mapToProductDto(savedProduct);
    }

    @Override
    public ProductDto getProductById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return ProductMapper.mapToProductDto(product);
    }

    @Override
    public List<ProductDto> getAllProducts() {
        List<Product> products = productRepository.findAll();
        return products.stream().map((product) -> ProductMapper.mapToProductDto(product))
                .collect(Collectors.toList());
    }

    @Override
    public ProductDto updateProduct(Long productId, ProductDto productDto) {
        Product product = productRepository.findById(productId).orElseThrow(
                () -> new ProductNotFoundException(productId)
        );

        ProductMapper.mapToProduct(productDto, product);
        product.setUpdatedAt(new Date());
        Product updatedProduct = productRepository.save(product);

        return ProductMapper.mapToProductDto(updatedProduct);
    }

    @Override
    public void deleteProduct(Long productId) {
        Product product = productRepository.findById(productId).orElseThrow(
                () -> new ProductNotFoundException(productId)
        );

        productRepository.deleteById(productId);

    }
}

package com.giri.oms.product.service;

import com.giri.oms.product.dto.ProductDto;

import java.util.List;

public interface ProductService {

    ProductDto createProduct(ProductDto productDto);

    ProductDto getProductById(Long productId);

    List<ProductDto> getAllProducts();

    ProductDto updateProduct(Long productId, ProductDto productDto);

    void deleteProduct (Long productId);

}

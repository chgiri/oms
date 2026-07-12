package com.giri.oms.product.service;

import com.giri.oms.product.dto.ProductRequest;
import com.giri.oms.product.dto.ProductResponse;

import java.util.List;

public interface ProductService {

    ProductResponse createProduct(ProductRequest request);

    ProductResponse getProductById(Long productId);

    List<ProductResponse> getAllProducts();

    ProductResponse updateProduct(Long productId, ProductRequest request);

    void deleteProduct (Long productId);

}

package com.giri.oms.product.service;

import com.giri.oms.product.dto.PagedResponse;
import com.giri.oms.product.dto.ProductRequest;
import com.giri.oms.product.dto.ProductResponse;

public interface ProductService {

    ProductResponse createProduct(ProductRequest request);

    ProductResponse getProductById(Long productId);

    PagedResponse<ProductResponse> getAllProducts(int pageNo, int pageSize, String sortBy, String sortDir);

    ProductResponse updateProduct(Long productId, ProductRequest request);

    void deleteProduct (Long productId);

}

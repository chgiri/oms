package com.giri.oms.product.mapper;

import com.giri.oms.product.dto.ProductRequest;
import com.giri.oms.product.dto.ProductResponse;
import com.giri.oms.product.entity.Product;

@Deprecated
public class ProductMapperOld {

    public static ProductResponse mapToProductResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    public static Product mapToProduct(ProductRequest productRequest) {
        return new Product(
                null,
                productRequest.getName(),
                productRequest.getDescription(),
                productRequest.getPrice()
        );
    }

    public static void mapToProduct(ProductRequest productRequest, Product product) {
        product.setName(productRequest.getName());
        product.setDescription(productRequest.getDescription());
        product.setPrice(productRequest.getPrice());
    }


}

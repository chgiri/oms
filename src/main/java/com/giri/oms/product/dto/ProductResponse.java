package com.giri.oms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Product details returned by the API")
public class ProductResponse {

    @Schema(description = "Unique product ID", example = "1")
    private Long id;

    @Schema(description = "Product name", example = "Wireless Mouse")
    private String name;

    @Schema(description = "Product description", example = "Ergonomic wireless mouse with USB receiver")
    private String description;

    @Schema(description = "Unit price in USD", example = "29.99")
    private BigDecimal price;

    @Schema(description = "Units currently in stock", example = "50")
    private int stock;

    @Schema(description = "Timestamp the product was created", example = "2026-07-01T10:15:30")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp the product was last updated", example = "2026-07-10T08:42:11")
    private LocalDateTime updatedAt;
}

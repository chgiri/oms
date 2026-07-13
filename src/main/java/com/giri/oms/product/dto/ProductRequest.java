package com.giri.oms.product.dto;

import jakarta.validation.constraints.*;
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
public class ProductRequest {
    @NotBlank(message = "Name is required")
    private String name;
    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price should be a positive number")
    @Digits(integer = 5, fraction = 2, message = "Price must have up to 3 integer digits and 2 decimals")
    private BigDecimal price;

    @NotNull
    @PositiveOrZero(message = "Stock should be zero or positive number")
    private Integer stock;
}

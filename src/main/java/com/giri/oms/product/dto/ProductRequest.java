package com.giri.oms.product.dto;

import com.giri.oms.product.constants.ProductConstants;
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
    @NotBlank(message = ProductConstants.NAME_REQUIRED_MESSAGE)
    private String name;
    private String description;

    @NotNull(message = ProductConstants.PRICE_REQUIRED_MESSAGE)
    @Positive(message = ProductConstants.PRICE_POSITIVE_MESSAGE)
    @Digits(integer = 5, fraction = 2, message = ProductConstants.PRICE_DIGITS_MESSAGE)
    private BigDecimal price;

    @NotNull(message = ProductConstants.STOCK_REQUIRED_MESSAGE)
    @PositiveOrZero(message = ProductConstants.STOCK_POSITIVE_OR_ZERO_MESSAGE)
    private Integer stock;
}

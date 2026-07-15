package com.giri.oms.customer.dto;

import com.giri.oms.customer.entity.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Customer details returned by the API")
public class CustomerResponse {

    @Schema(description = "Unique customer ID", example = "1")
    private Long id;

    @Schema(description = "Customer's first name", example = "Ada")
    private String firstName;

    @Schema(description = "Customer's last name", example = "Lovelace")
    private String lastName;


    @Schema(description = "Unique email address", example = "alice@example.com")
    private String email;

    @Schema(description = "Phone number", example = "+1 555-0100")
    private String phone;

    @Schema(description = "Street address line", example = "123 Main St")
    private String street;

    @Schema(description = "City", example = "Springfield")
    private String city;

    @Schema(description = "State or province", example = "IL")
    private String state;

    @Schema(description = "Postal or ZIP code", example = "62701")
    private String postalCode;

    @Schema(description = "Country", example = "USA")
    private String country;

    @Schema(description = "Customer status", example = "ACTIVE")
    private CustomerStatus status;

    @Schema(description = "Timestamp the customer was created", example = "2026-07-01T10:15:30")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp the customer was last updated", example = "2026-07-10T08:42:11")
    private LocalDateTime updatedAt;
}
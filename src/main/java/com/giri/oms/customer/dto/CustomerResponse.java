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

    @Schema(description = "Unique customer ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "Customer's first name", example = "Ada", requiredMode = Schema.RequiredMode.REQUIRED)
    private String firstName;

    @Schema(description = "Customer's last name", example = "Lovelace", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lastName;

    @Schema(description = "Unique email address", example = "alice@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "Phone number — null if never provided", example = "+1 555-0100",
            requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String phone;

    @Schema(description = "Street address line — null if never provided", example = "123 Main St",
            requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String street;

    @Schema(description = "City — null if never provided", example = "Springfield",
            requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String city;

    @Schema(description = "State or province — null if never provided", example = "IL",
            requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String state;

    @Schema(description = "Postal or ZIP code — null if never provided", example = "62701",
            requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String postalCode;

    @Schema(description = "Country — null if never provided", example = "USA",
            requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String country;

    @Schema(description = "Customer status", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private CustomerStatus status;

    @Schema(description = "Timestamp the customer was created", example = "2026-07-01T10:15:30", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp the customer was last updated", example = "2026-07-10T08:42:11", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updatedAt;
}

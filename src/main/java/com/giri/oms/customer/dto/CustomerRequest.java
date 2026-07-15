package com.giri.oms.customer.dto;

import com.giri.oms.customer.constants.CustomerConstants;
import com.giri.oms.customer.entity.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request payload for creating or updating a customer")
public class CustomerRequest {

    @Schema(description = "Customer's first name", example = "Ada", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = CustomerConstants.FIRST_NAME_REQUIRED_MESSAGE)
    @Size(max = 100, message = CustomerConstants.FIRST_NAME_SIZE_MESSAGE)
    private String firstName;

    @Schema(description = "Customer's last name", example = "Lovelace", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = CustomerConstants.LAST_NAME_REQUIRED_MESSAGE)
    @Size(max = 100, message = CustomerConstants.LAST_NAME_SIZE_MESSAGE)
    private String lastName;


    @Schema(description = "Unique email address — used as the customer's identifier for lookups and conflict checks",
            example = "alice@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = CustomerConstants.EMAIL_REQUIRED_MESSAGE)
    @Email(message = CustomerConstants.EMAIL_INVALID_MESSAGE)
    private String email;

    @Schema(description = "Phone number — digits with optional +, spaces, hyphens, or parentheses",
            example = "+1 555-0100")
    @Pattern(regexp = "^$|^[+]?[0-9 ()-]{7,20}$", message = CustomerConstants.PHONE_INVALID_MESSAGE)
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

    @Schema(description = "Customer account status", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = CustomerConstants.STATUS_REQUIRED_MESSAGE)
    private CustomerStatus status;
}
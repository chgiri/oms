package com.giri.oms.auth.dto;

import com.giri.oms.auth.constants.AuthConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request payload for logging in")
public class LoginRequest {

    @Schema(description = "Login username", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = AuthConstants.USERNAME_REQUIRED_MESSAGE)
    private String username;

    @Schema(description = "Password", example = "Admin@123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = AuthConstants.PASSWORD_REQUIRED_MESSAGE)
    private String password;
}

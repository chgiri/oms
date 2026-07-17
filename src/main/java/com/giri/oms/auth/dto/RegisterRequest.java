package com.giri.oms.auth.dto;

import com.giri.oms.auth.constants.AuthConstants;
import com.giri.oms.auth.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request payload for registering a new user account")
public class RegisterRequest {

    @Schema(description = "Unique login username", example = "jane.doe", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = AuthConstants.USERNAME_REQUIRED_MESSAGE)
    private String username;

    @Schema(description = "Password — at least 8 characters", example = "S3curePass!", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = AuthConstants.PASSWORD_REQUIRED_MESSAGE)
    @Size(min = 8, message = AuthConstants.PASSWORD_TOO_SHORT_MESSAGE)
    private String password;

    @Schema(description = "Email address", example = "jane.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = AuthConstants.EMAIL_REQUIRED_MESSAGE)
    @Email(message = AuthConstants.EMAIL_INVALID_MESSAGE)
    private String email;

    @Schema(description = "Role to assign to the new account", example = "STAFF", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = AuthConstants.ROLE_REQUIRED_MESSAGE)
    private Role role;
}

package com.giri.oms.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Response returned on successful login, containing the bearer token to use on subsequent requests")
public class AuthResponse {

    @Schema(description = "JWT to send as 'Authorization: Bearer <token>' on subsequent requests",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.abc123")
    private String accessToken;

    @Schema(description = "Authorization header scheme this token uses", example = "Bearer")
    private String tokenType;

    @Schema(description = "Token lifetime in milliseconds from the time of login", example = "86400000")
    private long expiresInMs;

    @Schema(description = "Username of the authenticated user", example = "admin")
    private String username;

    @Schema(description = "Role of the authenticated user", example = "ADMIN")
    private String role;
}

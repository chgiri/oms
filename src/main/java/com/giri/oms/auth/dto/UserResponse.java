package com.giri.oms.auth.dto;

import com.giri.oms.auth.entity.Role;
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
@Schema(description = "User account details returned by the API — never includes the password")
public class UserResponse {

    @Schema(description = "Unique user ID", example = "1")
    private Long id;

    @Schema(description = "Login username", example = "jane.doe")
    private String username;

    @Schema(description = "Email address", example = "jane.doe@example.com")
    private String email;

    @Schema(description = "Assigned role", example = "STAFF")
    private Role role;

    @Schema(description = "Whether the account is active", example = "true")
    private boolean enabled;

    @Schema(description = "Timestamp the account was created", example = "2026-07-01T10:15:30")
    private LocalDateTime createdAt;
}

package com.giri.oms.auth.controller;

import com.giri.oms.auth.dto.AuthResponse;
import com.giri.oms.auth.dto.LoginRequest;
import com.giri.oms.auth.dto.RegisterRequest;
import com.giri.oms.auth.dto.UserResponse;
import com.giri.oms.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "User account provisioning and login")
public class AuthController {

    private final AuthService authService;

    // Build Register REST API
    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Register a new user account",
            description = "Restricted to ADMIN — this is account provisioning for staff, not public self-service "
                    + "signup. A default admin account is bootstrapped automatically on first startup "
                    + "(see application.properties: app.security.default-admin-username/-password) so there's "
                    + "always at least one ADMIN able to call this.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(name = "Created user", value = """
                                    {
                                      "id": 2,
                                      "username": "jane.doe",
                                      "email": "jane.doe@example.com",
                                      "role": "STAFF",
                                      "enabled": true,
                                      "createdAt": "2026-07-16T10:15:30"
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "Validation error — e.g. blank username, password under 8 characters"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Authenticated but not an ADMIN"),
            @ApiResponse(responseCode = "409", description = "Username or email already taken")
    })
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/auth/register — registering user: {}", request.getUsername());
        UserResponse response = authService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // Build Login REST API
    @PostMapping("/login")
    @Operation(summary = "Log in and obtain a JWT",
            description = "Public endpoint. On success, returns a bearer token to send as "
                    + "'Authorization: Bearer <token>' on every subsequent request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "400", description = "Validation error — e.g. blank username or password"),
            @ApiResponse(responseCode = "401", description = "Invalid username or password")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/auth/login — login attempt for: {}", request.getUsername());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

}

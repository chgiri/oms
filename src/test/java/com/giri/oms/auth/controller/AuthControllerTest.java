package com.giri.oms.auth.controller;

import tools.jackson.databind.json.JsonMapper;
import com.giri.oms.auth.dto.AuthResponse;
import com.giri.oms.auth.dto.LoginRequest;
import com.giri.oms.auth.dto.RegisterRequest;
import com.giri.oms.auth.dto.UserResponse;
import com.giri.oms.auth.entity.Role;
import com.giri.oms.auth.exception.EmailAlreadyExistsException;
import com.giri.oms.auth.exception.UsernameAlreadyExistsException;
import com.giri.oms.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.giri.oms.common.config.ClockConfig;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Like the other controller slices, this exercises controller/validation/exception-
 * handling logic with the security filter chain disabled (AuthService is mocked, so
 * there's nothing to authenticate against anyway). The actual permitAll-on-/login,
 * ADMIN-only-on-/register, and JWT validation behavior is covered end-to-end by
 * SecurityIntegrationTest instead, against the real filter chain.
 */
@Import(ClockConfig.class) // ClockConfig isn't auto-detected by the @WebMvcTest slice scan; GlobalExceptionHandler needs a Clock bean
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("jane.doe", "S3curePass!", "jane.doe@example.com", Role.STAFF);
        loginRequest = new LoginRequest("jane.doe", "S3curePass!");
    }

    @Nested
    class Register {

        @Test
        void returns201AndBody_whenRequestIsValid() throws Exception {
            UserResponse response = new UserResponse(1L, "jane.doe", "jane.doe@example.com", Role.STAFF, true, LocalDateTime.now());
            when(authService.register(any())).thenReturn(response);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("jane.doe"))
                    .andExpect(jsonPath("$.role").value("STAFF"));
        }

        @Test
        void returns400_whenPasswordTooShort() throws Exception {
            registerRequest.setPassword("short");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").exists());
        }

        @Test
        void returns400_whenEmailIsInvalid() throws Exception {
            registerRequest.setEmail("not-an-email");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").exists());
        }

        @Test
        void returns400_whenRoleIsMissing() throws Exception {
            registerRequest.setRole(null);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.role").exists());
        }

        @Test
        void returns409_whenUsernameAlreadyTaken() throws Exception {
            when(authService.register(any())).thenThrow(new UsernameAlreadyExistsException("jane.doe"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isConflict());
        }

        @Test
        void returns409_whenEmailAlreadyTaken() throws Exception {
            when(authService.register(any())).thenThrow(new EmailAlreadyExistsException("jane.doe@example.com"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class Login {

        @Test
        void returns200AndToken_whenCredentialsAreValid() throws Exception {
            AuthResponse response = new AuthResponse("signed.jwt.token", "Bearer", 86_400_000L, "jane.doe", "STAFF");
            when(authService.login(any())).thenReturn(response);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("signed.jwt.token"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"));
        }

        @Test
        void returns401_whenCredentialsAreInvalid() throws Exception {
            when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void returns400_whenUsernameIsBlank() throws Exception {
            loginRequest.setUsername("");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.username").exists());
        }
    }
}

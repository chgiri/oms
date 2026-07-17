package com.giri.oms.security;

import com.giri.oms.auth.dto.LoginRequest;
import com.giri.oms.auth.dto.RegisterRequest;
import com.giri.oms.auth.entity.Role;
import com.giri.oms.common.AbstractIntegrationTest;
import com.giri.oms.customer.dto.CustomerRequest;
import com.giri.oms.customer.entity.CustomerStatus;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one place the real, fully-assembled security setup is exercised end to
 * end: real SecurityFilterChain, real JwtAuthenticationFilter, real
 * AuthenticationManager/PasswordEncoder, real database. Every other test in
 * the project either mocks security away (@AutoConfigureMockMvc(addFilters =
 * false)) or mocks the service layer — this is what actually proves login,
 * token validation, and role-based access control work together.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    // The username/password AdminUserSeeder bootstraps on a fresh database —
    // see application.properties: app.security.default-admin-username/-password.
    @Value("${app.security.default-admin-username}")
    private String adminUsername;

    @Value("${app.security.default-admin-password}")
    private String adminPassword;

    private String loginAndGetToken(String username, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(username, password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Small, dependency-free extraction — avoids pulling in a JSON path
        // library just to pluck one field out of a response we already know the
        // shape of.
        int start = body.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
        int end = body.indexOf("\"", start);
        return body.substring(start, end);
    }

    @Test
    void protectedEndpoint_returns401_withNoToken() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void protectedEndpoint_returns401_withGarbageToken() throws Exception {
        mockMvc.perform(get("/api/customers").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_returns401_withWrongPassword() throws Exception {
        LoginRequest loginRequest = new LoginRequest(adminUsername, "definitely-wrong");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_returns200AndToken_withSeededAdminCredentials() throws Exception {
        LoginRequest loginRequest = new LoginRequest(adminUsername, adminPassword);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void protectedEndpoint_returns200_withValidToken() throws Exception {
        String token = loginAndGetToken(adminUsername, adminPassword);

        mockMvc.perform(get("/api/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void register_returns401_withoutAuthentication() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("no.auth.user", "S3curePass!", "no.auth.user@example.com", Role.STAFF);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_returns403_whenCallerIsNotAdmin() throws Exception {
        String adminToken = loginAndGetToken(adminUsername, adminPassword);

        // Provision a STAFF account as admin, then use ITS token to try registering someone else.
        RegisterRequest staffRequest = new RegisterRequest("staffer1", "S3curePass!", "staffer1@example.com", Role.STAFF);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(objectMapper.writeValueAsString(staffRequest)))
                .andExpect(status().isCreated());

        String staffToken = loginAndGetToken("staffer1", "S3curePass!");

        RegisterRequest anotherRequest = new RegisterRequest("staffer2", "S3curePass!", "staffer2@example.com", Role.STAFF);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + staffToken)
                        .content(objectMapper.writeValueAsString(anotherRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void deleteCustomer_returns403_forNonAdminRole_andReturns204_forAdmin() throws Exception {
        String adminToken = loginAndGetToken(adminUsername, adminPassword);

        // Provision a STAFF account to test the restriction from a non-admin's perspective.
        RegisterRequest staffRequest = new RegisterRequest("deleter.staff", "S3curePass!", "deleter.staff@example.com", Role.STAFF);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(objectMapper.writeValueAsString(staffRequest)))
                .andExpect(status().isCreated());
        String staffToken = loginAndGetToken("deleter.staff", "S3curePass!");

        // Any authenticated role can create — only delete is admin-restricted.
        CustomerRequest customerRequest = new CustomerRequest(
                "Ada", "Lovelace", "ada.security.test@example.com", null, null, null, null, null, null, CustomerStatus.ACTIVE);
        MvcResult createResult = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + staffToken)
                        .content(objectMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String createBody = createResult.getResponse().getContentAsString();
        int idStart = createBody.indexOf("\"id\":") + "\"id\":".length();
        int idEnd = createBody.indexOf(",", idStart);
        String customerId = createBody.substring(idStart, idEnd);

        mockMvc.perform(delete("/api/customers/{id}", customerId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/customers/{id}", customerId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }
}

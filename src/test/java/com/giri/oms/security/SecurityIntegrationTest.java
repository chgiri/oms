package com.giri.oms.security;

import com.giri.oms.auth.dto.LoginRequest;
import com.giri.oms.auth.dto.RegisterRequest;
import com.giri.oms.auth.entity.Role;
import com.giri.oms.common.AbstractIntegrationTest;
import com.giri.oms.inventory.dto.InventoryRequest;
import com.giri.oms.productclient.dto.ProductClientResponse;
import com.giri.oms.productclient.service.ProductClient;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
 *
 * @TestPropertySource raises the login rate limit for this class only. This is
 * the one test class that exercises the real LoginRateLimitFilter (it's a
 * @SpringBootTest with the full context, not a mocked-security slice), and it
 * calls the login endpoint many times across its test methods — comfortably
 * more than the production capacity of 5 attempts/minute. Without this
 * override, whichever login calls land after the 5th get a genuine 429 instead
 * of the 200 the test expects, since the underlying Redis bucket is real and
 * shared for the whole test JVM run (see AbstractIntegrationTest). Production
 * behavior and any future dedicated rate-limit test are unaffected — this only
 * applies within this class.
 *
 * Uses `/api/v1/inventory` as its stand-in "some authenticated endpoint" throughout —
 * `/api/v1/customers` was the original choice, until Customer's own Stage 5 (microservices-prep
 * plan) removed CustomerController from this codebase entirely. Inventory's create/delete
 * endpoints also happen to match the exact any-role-creates/admin-only-deletes shape the
 * original Customer-based test needed, which is why deleteInventory_... below still exercises
 * the same authorization behavior it always did, just through a different resource.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.ratelimit.login.capacity=1000",
        "app.ratelimit.login.refill-tokens=1000"
})
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    // InventoryServiceImpl.createInventory validates the product exists via
    // ProductClient (Stage 4 of the microservices-prep plan) — a real HTTP
    // call in production, which this @SpringBootTest context has no real
    // product-service to answer. Mocked here purely so
    // deleteInventory_returns403_forNonAdminRole_andReturns204_forAdmin can
    // exercise a real create+delete round trip without depending on one —
    // this class's actual concern is authorization, not product validation
    // (that's ProductClientImplTest's job). Same pattern
    // OrderCreatedOutboxIntegrationTest already established for the same reason.
    @MockitoBean
    private ProductClient productClient;

    // The username/password AdminUserSeeder bootstraps on a fresh database —
    // see application.properties: app.security.default-admin-username/-password.
    @Value("${app.security.default-admin-username}")
    private String adminUsername;

    @Value("${app.security.default-admin-password}")
    private String adminPassword;

    private String loginAndGetToken(String username, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(username, password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
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
        mockMvc.perform(get("/api/v1/inventory"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void protectedEndpoint_returns401_withGarbageToken() throws Exception {
        mockMvc.perform(get("/api/v1/inventory").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_returns401_withWrongPassword() throws Exception {
        LoginRequest loginRequest = new LoginRequest(adminUsername, "definitely-wrong");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_returns200AndToken_withSeededAdminCredentials() throws Exception {
        LoginRequest loginRequest = new LoginRequest(adminUsername, adminPassword);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void protectedEndpoint_returns200_withValidToken() throws Exception {
        String token = loginAndGetToken(adminUsername, adminPassword);

        mockMvc.perform(get("/api/v1/inventory").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_returns401_withTokenAfterLogout() throws Exception {
        String token = loginAndGetToken(adminUsername, adminPassword);

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Same token, same signature, still unexpired — but now blacklisted in Redis,
        // so it must be rejected exactly like an invalid one.
        mockMvc.perform(get("/api/v1/inventory").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_returns401_withoutAuthentication() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("no.auth.user", "S3curePass!", "no.auth.user@example.com", Role.STAFF);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_returns403_whenCallerIsNotAdmin() throws Exception {
        String adminToken = loginAndGetToken(adminUsername, adminPassword);

        // Provision a STAFF account as admin, then use ITS token to try registering someone else.
        RegisterRequest staffRequest = new RegisterRequest("staffer1", "S3curePass!", "staffer1@example.com", Role.STAFF);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(objectMapper.writeValueAsString(staffRequest)))
                .andExpect(status().isCreated());

        String staffToken = loginAndGetToken("staffer1", "S3curePass!");

        RegisterRequest anotherRequest = new RegisterRequest("staffer2", "S3curePass!", "staffer2@example.com", Role.STAFF);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + staffToken)
                        .content(objectMapper.writeValueAsString(anotherRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void deleteInventory_returns403_forNonAdminRole_andReturns204_forAdmin() throws Exception {
        String adminToken = loginAndGetToken(adminUsername, adminPassword);

        // Provision a STAFF account to test the restriction from a non-admin's perspective.
        RegisterRequest staffRequest = new RegisterRequest("deleter.staff", "S3curePass!", "deleter.staff@example.com", Role.STAFF);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(objectMapper.writeValueAsString(staffRequest)))
                .andExpect(status().isCreated());
        String staffToken = loginAndGetToken("deleter.staff", "S3curePass!");

        // See the mocked productClient field's Javadoc above — createInventory
        // validates the product exists via a real HTTP call in production;
        // this stub stands in for a real product-service response so the
        // create below succeeds on its own terms, independent of anything
        // this test is actually trying to verify (authorization, not
        // product validation).
        when(productClient.getProduct(any()))
                .thenReturn(new ProductClientResponse(1L, "Wireless Mouse", new BigDecimal("25.99")));

        // Any authenticated role can create — only delete is admin-restricted.
        InventoryRequest inventoryRequest = new InventoryRequest(1L, "WH-SECURITY-TEST-01", 10, 0, 5);
        MvcResult createResult = mockMvc.perform(post("/api/v1/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + staffToken)
                        .content(objectMapper.writeValueAsString(inventoryRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String createBody = createResult.getResponse().getContentAsString();
        int idStart = createBody.indexOf("\"id\":") + "\"id\":".length();
        int idEnd = createBody.indexOf(",", idStart);
        String inventoryId = createBody.substring(idStart, idEnd);

        mockMvc.perform(delete("/api/v1/inventory/{id}", inventoryId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/inventory/{id}", inventoryId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }
}
package com.paycore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.auth.dto.LoginRequest;
import com.paycore.auth.dto.RegisterRequest;
import com.paycore.merchant.dto.ApiKeyCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("End-to-end integration: Register, Login, Me, API Key generation, and RBAC authorization")
    void testEndToEndAuthAndMerchantLifecycle() throws Exception {
        String uniqueEmail = "merchant_" + System.currentTimeMillis() + "@teststore.com";

        // 1. Register new merchant user
        RegisterRequest registerRequest = new RegisterRequest(
                "Merchant One",
                uniqueEmail,
                "StrongPass123!",
                "+1234567890",
                "Merchant One Retail",
                "456 Commerce Blvd"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(uniqueEmail))
                .andExpect(jsonPath("$.data.merchantCode").isNotEmpty());

        // 2. Login to obtain JWT
        LoginRequest loginRequest = new LoginRequest(uniqueEmail, "StrongPass123!");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(uniqueEmail))
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String merchantToken = loginJson.get("data").get("accessToken").asText();

        // 3. Access /api/v1/auth/me with Bearer Token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(uniqueEmail));

        // 4. Access /api/v1/auth/me WITHOUT token -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // 5. Merchant attempts to access Admin endpoint -> 403 Forbidden
        mockMvc.perform(get("/api/v1/admin/merchants")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // 6. Login as seeded Platform Admin
        LoginRequest adminLogin = new LoginRequest("admin@paycore.internal", "AdminPassword123!");
        MvcResult adminResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode adminJson = objectMapper.readTree(adminResult.getResponse().getContentAsString());
        String adminToken = adminJson.get("data").get("accessToken").asText();

        // 7. Admin accesses /api/v1/admin/merchants -> 200 OK
        mockMvc.perform(get("/api/v1/admin/merchants")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());

        // 8. Merchant generates an API key
        ApiKeyCreateRequest keyReq = new ApiKeyCreateRequest("SECRET", "LIVE");
        MvcResult keyResult = mockMvc.perform(post("/api/v1/merchants/api-keys")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(keyReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.secretKey").isNotEmpty())
                .andExpect(jsonPath("$.data.keyPrefix").isNotEmpty())
                .andReturn();

        JsonNode keyJson = objectMapper.readTree(keyResult.getResponse().getContentAsString());
        Long keyId = keyJson.get("data").get("id").asLong();
        String secretKey = keyJson.get("data").get("secretKey").asText();
        assertThat(secretKey).startsWith("sk_live_");

        // 9. Merchant lists API keys -> secret is NOT exposed
        MvcResult listResult = mockMvc.perform(get("/api/v1/merchants/api-keys")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].keyPrefix").isNotEmpty())
                .andReturn();

        String listResponseStr = listResult.getResponse().getContentAsString();
        assertThat(listResponseStr).doesNotContain(secretKey); // Plain secret must NEVER appear again!

        // 10. Merchant revokes API key
        mockMvc.perform(delete("/api/v1/merchants/api-keys/" + keyId)
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }
}

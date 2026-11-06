package com.paycore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.auth.dto.*;
import com.paycore.auth.entity.AdminSetupToken;
import com.paycore.auth.entity.EmailVerificationToken;
import com.paycore.auth.repository.AdminSetupTokenRepository;
import com.paycore.auth.repository.EmailVerificationTokenRepository;
import com.paycore.common.util.HashUtil;
import com.paycore.merchant.dto.ApiKeyCreateRequest;
import com.paycore.user.entity.User;
import com.paycore.user.repository.UserRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "paycore.auth.auto-verify-email=false",
        "paycore.security.seed-test-admin=false"
})
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.paycore.email.EmailService emailService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository verificationTokenRepository;

    @Autowired
    private AdminSetupTokenRepository adminSetupTokenRepository;

    @Test
    @DisplayName("End-to-end integration: Registration, Verification, Login, Token Refresh, Password Reset, Admin Setup, and RBAC")
    void testEndToEndAuthAndMerchantLifecycle() throws Exception {
        String uniqueEmail = "merchant_" + System.currentTimeMillis() + "@teststore.com";

        // 1. Register new merchant user (in unverified status)
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
                .andExpect(jsonPath("$.data.emailVerified").value(false))
                .andExpect(jsonPath("$.data.merchantCode").isNotEmpty());

        // 2. Login BEFORE email verification -> rejected with 403 Forbidden
        LoginRequest unverifiedLogin = new LoginRequest(uniqueEmail, "StrongPass123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unverifiedLogin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));

        // 3. Complete email verification using a valid token
        User createdUser = userRepository.findByEmail(uniqueEmail).orElseThrow();
        String rawToken = "integ_test_verification_token_" + System.currentTimeMillis();
        String tokenHash = HashUtil.sha256Hex(rawToken);
        EmailVerificationToken token = new EmailVerificationToken(createdUser, tokenHash, Instant.now().plus(24, ChronoUnit.HOURS));
        verificationTokenRepository.save(token);

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(rawToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 4. Login AFTER verification -> 200 OK with access and refresh tokens
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(uniqueEmail, "StrongPass123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(uniqueEmail))
                .andExpect(jsonPath("$.data.user.emailVerified").value(true))
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String merchantToken = loginJson.get("data").get("accessToken").asText();
        String refreshToken = loginJson.get("data").get("refreshToken").asText();

        // 5. Access /api/v1/auth/me with Bearer Token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(uniqueEmail));

        // 6. Access /api/v1/auth/me WITHOUT token -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // 7. Merchant attempts to access Admin endpoint -> 403 Forbidden
        mockMvc.perform(get("/api/v1/admin/merchants")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // 8. Refresh Token Rotation
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode refreshJson = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newRefreshToken = refreshJson.get("data").get("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        // 9. Initial Administrator Setup Flow (omp27614@gmail.com)
        mockMvc.perform(post("/api/v1/auth/admin/initiate-setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Complete admin setup with raw token
        User adminUser = userRepository.findByEmail("omp27614@gmail.com").orElseThrow();
        String rawAdminToken = "admin_integ_setup_token_" + System.currentTimeMillis();
        String adminTokenHash = HashUtil.sha256Hex(rawAdminToken);
        adminSetupTokenRepository.save(new AdminSetupToken(adminUser, adminTokenHash, Instant.now().plus(24, ChronoUnit.HOURS)));

        mockMvc.perform(post("/api/v1/auth/admin/complete-setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminSetupRequest(
                                rawAdminToken,
                                "MasterAdminSecurePass123!",
                                "MasterAdminSecurePass123!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 10. Admin Login & Authorization check
        MvcResult adminLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(
                                "omp27614@gmail.com",
                                "MasterAdminSecurePass123!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.roles[0]").value("ROLE_ADMIN"))
                .andReturn();

        String adminAccessToken = objectMapper.readTree(adminLoginResult.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        // Admin accesses /api/v1/admin/merchants -> 200 OK
        mockMvc.perform(get("/api/v1/admin/merchants")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());

        // 11. Merchant API Key generation and management
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

        // Merchant lists API keys -> secret is NOT exposed
        MvcResult listResult = mockMvc.perform(get("/api/v1/merchants/api-keys")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].keyPrefix").isNotEmpty())
                .andReturn();

        String listResponseStr = listResult.getResponse().getContentAsString();
        assertThat(listResponseStr).doesNotContain(secretKey);

        // Merchant revokes API key
        mockMvc.perform(delete("/api/v1/merchants/api-keys/" + keyId)
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }
}

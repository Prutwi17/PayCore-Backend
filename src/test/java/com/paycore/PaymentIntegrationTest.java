package com.paycore;



import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.paycore.auth.dto.LoginRequest;

import com.paycore.auth.dto.RegisterRequest;

import com.paycore.payment.dto.PaymentCreateRequest;

import com.paycore.payment.entity.PaymentMethodType;

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



import java.math.BigDecimal;



import static org.junit.jupiter.api.Assertions.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;



@SpringBootTest

@AutoConfigureMockMvc

@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")

class PaymentIntegrationTest {



    @Autowired

    private MockMvc mockMvc;



    @Autowired

    private ObjectMapper objectMapper;



    @Test

    @DisplayName("Comprehensive Payment Processing Engine E2E Integration Test")

    void testFullPaymentLifecycle() throws Exception {

        // Step 1: Login as Default Admin

        String adminToken = loginUser("admin@paycore.internal", "AdminPassword123!");

        assertNotNull(adminToken);



        // Step 2: Register Merchant 1

        RegisterRequest m1Register = new RegisterRequest();

        m1Register.setFullName("Merchant One");

        m1Register.setEmail("m1@acmepay.com");

        m1Register.setPassword("Password123!");

        m1Register.setBusinessName("Acme Payments Ltd");

        m1Register.setBusinessAddress("100 FinTech Way");



        mockMvc.perform(post("/api/v1/auth/register")

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(m1Register)))

                .andExpect(status().isCreated());



        // Step 3: Admin approves Merchant 1

        MvcResult m1ListResult = mockMvc.perform(get("/api/v1/admin/merchants")

                        .header("Authorization", "Bearer " + adminToken)

                        .param("search", "Acme Payments"))

                .andExpect(status().isOk())

                .andReturn();



        JsonNode m1PageNode = objectMapper.readTree(m1ListResult.getResponse().getContentAsString());

        long m1Id = m1PageNode.get("data").get("content").get(0).get("id").asLong();



        mockMvc.perform(patch("/api/v1/admin/merchants/" + m1Id + "/approve")

                        .header("Authorization", "Bearer " + adminToken))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.data.status").value("ACTIVE"));



        // Step 4: Login as Merchant 1

        String m1Token = loginUser("m1@acmepay.com", "Password123!");

        assertNotNull(m1Token);



        // Step 5: Merchant 1 creates a SUCCESS payment

        PaymentCreateRequest successReq = new PaymentCreateRequest(

                new BigDecimal("1500.00"), "INR", "Electronics Order #1001", PaymentMethodType.CARD, "cust_alpha"

        );



        MvcResult createResult = mockMvc.perform(post("/api/v1/payments")

                        .header("Authorization", "Bearer " + m1Token)

                        .header("Idempotency-Key", "idemp-stage3-success-01")

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(successReq)))

                .andExpect(status().isCreated())

                .andExpect(jsonPath("$.data.status").value("SUCCESS"))

                .andExpect(jsonPath("$.data.amount").value(1500.00))

                .andExpect(jsonPath("$.data.currency").value("INR"))

                .andExpect(jsonPath("$.data.providerTransactionId").isNotEmpty())

                .andReturn();



        JsonNode successNode = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");

        String paymentRef = successNode.get("paymentReference").asText();



        // Step 6: Query payment details

        mockMvc.perform(get("/api/v1/payments/" + paymentRef)

                        .header("Authorization", "Bearer " + m1Token))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.data.paymentReference").value(paymentRef))

                .andExpect(jsonPath("$.data.status").value("SUCCESS"));



        // Step 7: Query payment attempt history

        mockMvc.perform(get("/api/v1/payments/" + paymentRef + "/attempts")

                        .header("Authorization", "Bearer " + m1Token))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.data[0].attemptNumber").value(1))

                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"))

                .andExpect(jsonPath("$.data[0].responseCode").value("200_APPROVED"));



        // Step 8: Query payment status

        mockMvc.perform(get("/api/v1/payments/" + paymentRef + "/status")

                        .header("Authorization", "Bearer " + m1Token))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.data.status").value("SUCCESS"));



        // Step 9: Register and approve Merchant 2 to verify cross-tenant data isolation

        RegisterRequest m2Register = new RegisterRequest();

        m2Register.setFullName("Merchant Two");

        m2Register.setEmail("m2@betapay.com");

        m2Register.setPassword("Password123!");

        m2Register.setBusinessName("Beta Store");



        mockMvc.perform(post("/api/v1/auth/register")

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(m2Register)))

                .andExpect(status().isCreated());



        MvcResult m2ListResult = mockMvc.perform(get("/api/v1/admin/merchants")

                        .header("Authorization", "Bearer " + adminToken)

                        .param("search", "Beta Store"))

                .andExpect(status().isOk())

                .andReturn();



        long m2Id = objectMapper.readTree(m2ListResult.getResponse().getContentAsString())

                .get("data").get("content").get(0).get("id").asLong();



        mockMvc.perform(patch("/api/v1/admin/merchants/" + m2Id + "/approve")

                        .header("Authorization", "Bearer " + adminToken))

                .andExpect(status().isOk());



        String m2Token = loginUser("m2@betapay.com", "Password123!");



        // Merchant 2 attempting to access Merchant 1's payment must receive 404 Not Found

        mockMvc.perform(get("/api/v1/payments/" + paymentRef)

                        .header("Authorization", "Bearer " + m2Token))

                .andExpect(status().isNotFound());



        // Step 10: Unauthenticated request rejected

        mockMvc.perform(post("/api/v1/payments")

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(successReq)))

                .andExpect(status().isUnauthorized());



        // Step 11: Merchant 1 creates a FAILED payment via simulation scenario

        PaymentCreateRequest failReq = new PaymentCreateRequest(

                new BigDecimal("250.00"), "USD", "Failed checkout", PaymentMethodType.CARD, "cust_beta"

        );

        failReq.setSimulationScenario("FAIL");



        mockMvc.perform(post("/api/v1/payments")

                        .header("Authorization", "Bearer " + m1Token)

                        .header("Idempotency-Key", "idemp-stage3-fail-01")

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(failReq)))

                .andExpect(status().isCreated())

                .andExpect(jsonPath("$.data.status").value("FAILED"))

                .andExpect(jsonPath("$.data.failureCode").value("DECLINED_INSUFFICIENT_FUNDS"));



        // Step 12: Merchant 1 creates a TIMEOUT payment -> status UNKNOWN -> verified to SUCCESS

        PaymentCreateRequest timeoutReq = new PaymentCreateRequest(

                new BigDecimal("500.00"), "EUR", "Timeout checkout", PaymentMethodType.CARD, "cust_gamma"

        );

        timeoutReq.setSimulationScenario("TIMEOUT");



        MvcResult timeoutResult = mockMvc.perform(post("/api/v1/payments")

                        .header("Authorization", "Bearer " + m1Token)

                        .header("Idempotency-Key", "idemp-stage3-timeout-01")

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(timeoutReq)))

                .andExpect(status().isCreated())

                .andExpect(jsonPath("$.data.status").value("UNKNOWN"))

                .andReturn();



        String timeoutRef = objectMapper.readTree(timeoutResult.getResponse().getContentAsString())

                .get("data").get("paymentReference").asText();



        // Query status with verify=true resolves UNKNOWN to SUCCESS

        mockMvc.perform(get("/api/v1/payments/" + timeoutRef + "/status")

                        .header("Authorization", "Bearer " + m1Token)

                        .param("verify", "true"))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.data.status").value("SUCCESS"));



        // Step 13: Merchant 1 creates a REQUIRES_ACTION payment -> cancels it

        PaymentCreateRequest actionReq = new PaymentCreateRequest(

                new BigDecimal("750.00"), "INR", "3DS checkout", PaymentMethodType.CARD, "cust_delta"

        );

        actionReq.setSimulationScenario("REQUIRES_ACTION");



        MvcResult actionResult = mockMvc.perform(post("/api/v1/payments")

                        .header("Authorization", "Bearer " + m1Token)

                        .header("Idempotency-Key", "idemp-stage3-action-01")

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(actionReq)))

                .andExpect(status().isCreated())

                .andExpect(jsonPath("$.data.status").value("REQUIRES_ACTION"))

                .andReturn();



        String actionRef = objectMapper.readTree(actionResult.getResponse().getContentAsString())

                .get("data").get("paymentReference").asText();



        mockMvc.perform(post("/api/v1/payments/" + actionRef + "/cancel")

                        .header("Authorization", "Bearer " + m1Token)

                        .param("reason", "Customer abandoned 3DS auth"))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.data.status").value("CANCELLED"));



        // Step 14: Admin monitors all system payments

        mockMvc.perform(get("/api/v1/admin/payments")

                        .header("Authorization", "Bearer " + adminToken))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.data.content").isArray())

                .andExpect(jsonPath("$.data.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));

    }



    private String loginUser(String email, String password) throws Exception {

        LoginRequest login = new LoginRequest(email, password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(login)))

                .andExpect(status().isOk())

                .andReturn();



        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());

        return root.get("data").get("accessToken").asText();

    }

}


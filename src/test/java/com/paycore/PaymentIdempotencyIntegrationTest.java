package com.paycore;



import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.paycore.auth.dto.LoginRequest;

import com.paycore.auth.dto.RegisterRequest;

import com.paycore.payment.dto.PaymentCreateRequest;

import com.paycore.payment.entity.PaymentMethodType;

import com.paycore.payment.idempotency.dto.IdempotencyResult;

import com.paycore.payment.idempotency.service.IdempotencyService;

import com.paycore.payment.repository.PaymentRepository;

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

import java.util.ArrayList;

import java.util.List;

import java.util.UUID;

import java.util.concurrent.*;



import static org.assertj.core.api.Assertions.assertThat;

import static org.junit.jupiter.api.Assertions.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;



@SpringBootTest

@AutoConfigureMockMvc

@ActiveProfiles("test")

@TestPropertySource(locations = "classpath:application-test.properties")

class PaymentIdempotencyIntegrationTest {



    @Autowired

    private MockMvc mockMvc;



    @Autowired

    private ObjectMapper objectMapper;



    @Autowired

    private PaymentRepository paymentRepository;



    @Autowired

    private IdempotencyService idempotencyService;



    @Test

    @DisplayName("Stage 4 E2E Integration: Idempotency, Concurrency, Fingerprinting & Timeout Reliability")

    void testIdempotencyAndReliabilityLifecycle() throws Exception {

        // Step 1: Admin login & Merchant setup

        String adminToken = loginUser("admin@paycore.internal", "AdminPassword123!");

        assertNotNull(adminToken);



        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        String merchantEmail = "merchant_" + uniqueSuffix + "@acmepay.com";



        RegisterRequest register = new RegisterRequest();

        register.setFullName("Idempotency Merchant");

        register.setEmail(merchantEmail);

        register.setPassword("Password123!");

        register.setBusinessName("Acme Corp " + uniqueSuffix);



        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(register)))

                .andExpect(status().isCreated())

                .andReturn();



        JsonNode regData = objectMapper.readTree(regResult.getResponse().getContentAsString()).get("data");

        long merchantId = regData.get("merchantId").asLong();



        // Admin approves merchant

        mockMvc.perform(patch("/api/v1/admin/merchants/" + merchantId + "/approve")

                        .header("Authorization", "Bearer " + adminToken))

                .andExpect(status().isOk());



        String merchantToken = loginUser(merchantEmail, "Password123!");

        assertNotNull(merchantToken);



        // Step 2: CASE 1 — First Request creates fresh payment

        String key1 = "idemp_test_fresh_" + UUID.randomUUID();

        PaymentCreateRequest req1 = new PaymentCreateRequest(

                new BigDecimal("250.00"), "USD", "Idempotent payment test", PaymentMethodType.CARD, "cust_alpha"

        );



        MvcResult result1 = mockMvc.perform(post("/api/v1/payments")

                        .header("Authorization", "Bearer " + merchantToken)

                        .header("Idempotency-Key", key1)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(req1)))

                .andExpect(status().isCreated())

                .andExpect(header().doesNotExist("Idempotent-Replay"))

                .andExpect(jsonPath("$.data.status").value("SUCCESS"))

                .andExpect(jsonPath("$.data.amount").value(250.00))

                .andReturn();



        JsonNode data1 = objectMapper.readTree(result1.getResponse().getContentAsString()).get("data");

        String paymentRef1 = data1.get("paymentReference").asText();

        assertNotNull(paymentRef1);



        long countAfterFresh = paymentRepository.count();



        // Step 3: CASE 2 — Identical request with same key returns cached 200 OK replay

        mockMvc.perform(post("/api/v1/payments")

                        .header("Authorization", "Bearer " + merchantToken)

                        .header("Idempotency-Key", key1)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(req1)))

                .andExpect(status().isOk())

                .andExpect(header().string("Idempotent-Replay", "true"))

                .andExpect(jsonPath("$.data.paymentReference").value(paymentRef1))

                .andExpect(jsonPath("$.data.status").value("SUCCESS"));



        long countAfterReplay = paymentRepository.count();

        assertEquals(countAfterFresh, countAfterReplay, "Duplicate request must not insert additional payments into DB");



        // Step 4: CASE 3 — Same key with altered payload rejected with 409 Conflict

        PaymentCreateRequest tamperedReq = new PaymentCreateRequest(

                new BigDecimal("300.00"), "USD", "Idempotent payment test", PaymentMethodType.CARD, "cust_alpha"

        );



        mockMvc.perform(post("/api/v1/payments")

                        .header("Authorization", "Bearer " + merchantToken)

                        .header("Idempotency-Key", key1)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(tamperedReq)))

                .andExpect(status().isConflict())

                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));



        // Step 5: Missing Idempotency-Key rejected with 400 Bad Request

        mockMvc.perform(post("/api/v1/payments")

                        .header("Authorization", "Bearer " + merchantToken)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(req1)))

                .andExpect(status().isBadRequest())

                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_INVALID"));



        // Step 6: Provider Timeout scenario -> UNKNOWN status -> safe verification resolves to SUCCESS

        String timeoutKey = "idemp_timeout_" + UUID.randomUUID();

        PaymentCreateRequest timeoutReq = new PaymentCreateRequest(

                new BigDecimal("800.00"), "GBP", "Timeout simulation test", PaymentMethodType.CARD, "cust_timeout"

        );

        timeoutReq.setSimulationScenario("TIMEOUT");



        MvcResult timeoutResult = mockMvc.perform(post("/api/v1/payments")

                        .header("Authorization", "Bearer " + merchantToken)

                        .header("Idempotency-Key", timeoutKey)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(timeoutReq)))

                .andExpect(status().isCreated())

                .andExpect(jsonPath("$.data.status").value("UNKNOWN"))

                .andReturn();



        String timeoutRef = objectMapper.readTree(timeoutResult.getResponse().getContentAsString())

                .get("data").get("paymentReference").asText();



        // Verify status endpoint with verify=true resolves to SUCCESS

        mockMvc.perform(get("/api/v1/payments/" + timeoutRef + "/status")

                        .header("Authorization", "Bearer " + merchantToken)

                        .param("verify", "true"))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.data.status").value("SUCCESS"));



        // Step 7: CASE 4 — Concurrent threads using same key safely serialize

        String sharedKey = "idemp_concurrent_race_" + UUID.randomUUID();

        PaymentCreateRequest concurrentReq = new PaymentCreateRequest(

                new BigDecimal("450.00"), "EUR", "Concurrent test", PaymentMethodType.CARD, "cust_concurrent"

        );



        long beforeConcurrentCount = paymentRepository.count();

        int threadCount = 4;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch latch = new CountDownLatch(1);

        List<Future<IdempotencyResult>> futures = new ArrayList<>();



        for (int i = 0; i < threadCount; i++) {

            futures.add(executor.submit(() -> {

                latch.await();

                return idempotencyService.processPaymentWithIdempotency(merchantId, sharedKey, concurrentReq);

            }));

        }



        latch.countDown();



        List<IdempotencyResult> results = new ArrayList<>();

        for (Future<IdempotencyResult> f : futures) {

            results.add(f.get(10, TimeUnit.SECONDS));

        }

        executor.shutdown();



        for (IdempotencyResult r : results) {

            assertThat(r.getHttpStatus()).isIn(201, 200, 202);

            assertNotNull(r.getResponse().getPaymentReference());

        }



        long createdCount = results.stream().filter(r -> r.getHttpStatus() == 201).count();

        assertEquals(1, createdCount, "Exactly 1 concurrent thread must have created the payment");

        long afterConcurrentCount = paymentRepository.count();

        assertEquals(beforeConcurrentCount + 1, afterConcurrentCount, "Database count must increment by exactly 1 for concurrent requests");

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


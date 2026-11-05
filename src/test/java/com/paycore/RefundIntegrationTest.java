package com.paycore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.auth.dto.LoginRequest;
import com.paycore.auth.dto.RegisterRequest;
import com.paycore.payment.dto.PaymentCreateRequest;
import com.paycore.payment.entity.PaymentMethodType;
import com.paycore.refund.dto.RefundCreateRequest;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class RefundIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Comprehensive Refund Lifecycle, Idempotency, Concurrency, and Transaction History E2E")
    void testCompleteRefundLifecycle() throws Exception {
        // Step 1: Admin Login
        String adminToken = loginUser("admin@paycore.internal", "AdminPassword123!");
        assertNotNull(adminToken);

        // Step 2: Register & Approve Merchant A
        String mEmailA = "merch_a_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        registerAndApproveMerchant(adminToken, mEmailA, "Merchant A Corp");
        String merchantAToken = loginUser(mEmailA, "Password123!");

        // Step 3: Register & Approve Merchant B
        String mEmailB = "merch_b_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        registerAndApproveMerchant(adminToken, mEmailB, "Merchant B Corp");
        String merchantBToken = loginUser(mEmailB, "Password123!");

        // Step 4: Merchant A creates a $100.00 USD Payment
        PaymentCreateRequest pRequest = new PaymentCreateRequest();
        pRequest.setAmount(new BigDecimal("100.00"));
        pRequest.setCurrency("USD");
        pRequest.setPaymentMethod(PaymentMethodType.CARD);
        pRequest.setDescription("Order #1001");
        pRequest.setCustomerReference("CUST-1001");

        String pIdempKey = "pay-key-" + UUID.randomUUID();
        MvcResult payResult = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .header("Idempotency-Key", pIdempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andReturn();

        JsonNode payNode = objectMapper.readTree(payResult.getResponse().getContentAsString());
        String paymentRef = payNode.path("data").path("paymentReference").asText();
        assertNotNull(paymentRef);

        // Step 5: Check Refundable Amount API (initially 100.00 available)
        mockMvc.perform(get("/api/v1/payments/" + paymentRef + "/refundable-amount")
                        .header("Authorization", "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalAmount").value(100.00))
                .andExpect(jsonPath("$.data.totalRefundedAmount").value(0.00))
                .andExpect(jsonPath("$.data.remainingRefundableAmount").value(100.00))
                .andExpect(jsonPath("$.data.refundable").value(true));

        // Step 6: Partial Refund 1 of $30.00 USD with Idempotency-Key
        String refIdempKey1 = "idemp-ref-001-" + UUID.randomUUID();
        RefundCreateRequest refund1 = new RefundCreateRequest(new BigDecimal("30.00"), "USD", "Partial return item 1");

        MvcResult r1Result = mockMvc.perform(post("/api/v1/payments/" + paymentRef + "/refunds")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .header("Idempotency-Key", refIdempKey1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refund1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.amount").value(30.00))
                .andExpect(jsonPath("$.data.idempotentReplay").value(false))
                .andReturn();

        JsonNode r1Node = objectMapper.readTree(r1Result.getResponse().getContentAsString());
        String refundRef1 = r1Node.path("data").path("refundReference").asText();

        // Step 7: Verify Payment transitioned to PARTIALLY_REFUNDED
        mockMvc.perform(get("/api/v1/payments/" + paymentRef)
                        .header("Authorization", "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.data.amountRefunded").value(30.00))
                .andExpect(jsonPath("$.data.remainingRefundableAmount").value(70.00));

        // Step 8: Idempotent Replay of Refund 1 (returns 200 OK, idempotentReplay = true)
        mockMvc.perform(post("/api/v1/payments/" + paymentRef + "/refunds")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .header("Idempotency-Key", refIdempKey1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refund1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundReference").value(refundRef1))
                .andExpect(jsonPath("$.data.idempotentReplay").value(true));

        // Step 9: Idempotency Payload Conflict (same key, modified amount $40.00 -> 409 Conflict)
        RefundCreateRequest conflictRefund = new RefundCreateRequest(new BigDecimal("40.00"), "USD", "Modified amount");
        mockMvc.perform(post("/api/v1/payments/" + paymentRef + "/refunds")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .header("Idempotency-Key", refIdempKey1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictRefund)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));

        // Step 10: Partial Refund 2 of $50.00 USD (leaving $20.00)
        RefundCreateRequest refund2 = new RefundCreateRequest(new BigDecimal("50.00"), "USD", "Partial return item 2");
        mockMvc.perform(post("/api/v1/payments/" + paymentRef + "/refunds")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refund2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.amount").value(50.00));

        // Verify remaining refundable is now 20.00
        mockMvc.perform(get("/api/v1/payments/" + paymentRef + "/refundable-amount")
                        .header("Authorization", "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRefundedAmount").value(80.00))
                .andExpect(jsonPath("$.data.remainingRefundableAmount").value(20.00));

        // Step 11: Exceeded Refundable Amount Rejection (attempting $25.00 when only $20.00 available)
        RefundCreateRequest exceededRefund = new RefundCreateRequest(new BigDecimal("25.00"), "USD", "Exceeding amount");
        mockMvc.perform(post("/api/v1/payments/" + paymentRef + "/refunds")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exceededRefund)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EXCEEDED_REFUNDABLE_AMOUNT"));

        // Step 12: Currency Mismatch Rejection (EUR on USD payment)
        RefundCreateRequest currencyMismatch = new RefundCreateRequest(new BigDecimal("10.00"), "EUR", "Currency mismatch");
        mockMvc.perform(post("/api/v1/payments/" + paymentRef + "/refunds")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(currencyMismatch)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REFUND_VALIDATION_ERROR"));

        // Step 13: Zero Amount Rejection
        RefundCreateRequest zeroAmount = new RefundCreateRequest(BigDecimal.ZERO, "USD", "Zero amount");
        mockMvc.perform(post("/api/v1/payments/" + paymentRef + "/refunds")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroAmount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // Step 14: Merchant Isolation (Merchant B attempts to refund Merchant A's payment)
        mockMvc.perform(post("/api/v1/payments/" + paymentRef + "/refunds")
                        .header("Authorization", "Bearer " + merchantBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundCreateRequest(new BigDecimal("10.00"), "USD", "Cross-merchant attack"))))
                .andExpect(status().isBadRequest());

        // Step 15: Final Partial Refund of remaining $20.00 -> Payment becomes REFUNDED
        RefundCreateRequest refund3 = new RefundCreateRequest(new BigDecimal("20.00"), "USD", "Final full refund");
        mockMvc.perform(post("/api/v1/payments/" + paymentRef + "/refunds")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refund3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        // Verify Payment is now REFUNDED and no longer refundable
        mockMvc.perform(get("/api/v1/payments/" + paymentRef)
                        .header("Authorization", "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.amountRefunded").value(100.00))
                .andExpect(jsonPath("$.data.remainingRefundableAmount").value(0.00));

        mockMvc.perform(get("/api/v1/payments/" + paymentRef + "/refundable-amount")
                        .header("Authorization", "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingRefundableAmount").value(0.00))
                .andExpect(jsonPath("$.data.refundable").value(false));

        // Step 16: Attempt refund on already fully refunded payment
        mockMvc.perform(post("/api/v1/payments/" + paymentRef + "/refunds")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundCreateRequest(new BigDecimal("5.00"), "USD", "Post refund"))))
                .andExpect(status().isBadRequest());

        // Step 17: List Refunds API for Payment
        mockMvc.perform(get("/api/v1/payments/" + paymentRef + "/refunds")
                        .header("Authorization", "Bearer " + merchantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        // Step 18: List Merchant Refunds with pagination
        mockMvc.perform(get("/api/v1/refunds")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.totalElements").value(3));

        // Step 19: Admin List Refunds across merchants
        mockMvc.perform(get("/api/v1/admin/refunds")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", paymentRef))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));

        // Step 20: Transaction History API (Unified ledger showing Payment + 3 Refunds)
        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4)); // 1 payment + 3 refunds

        // Filter transaction history by type = REFUND
        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .param("type", "REFUND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));

        // Filter transaction history by type = PAYMENT
        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + merchantAToken)
                        .param("type", "PAYMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // Admin Transaction History API
        mockMvc.perform(get("/api/v1/admin/transactions")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", paymentRef))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4));
    }

    @Test
    @DisplayName("Simulated Downstream Provider Failure and Timeout Reconciliation Flow")
    void testProviderFailureAndTimeoutFlow() throws Exception {
        String adminToken = loginUser("admin@paycore.internal", "AdminPassword123!");
        String mEmail = "merch_sim_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        registerAndApproveMerchant(adminToken, mEmail, "Simulated Merchant");
        String merchantToken = loginUser(mEmail, "Password123!");

        // Create Payment of $500.00
        PaymentCreateRequest pRequest = new PaymentCreateRequest();
        pRequest.setAmount(new BigDecimal("500.00"));
        pRequest.setCurrency("USD");
        pRequest.setPaymentMethod(PaymentMethodType.CARD);
        pRequest.setDescription("Provider Simulation Order");

        MvcResult payResult = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + merchantToken)
                        .header("Idempotency-Key", "sim-pay-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode payNode = objectMapper.readTree(payResult.getResponse().getContentAsString());
        String paymentRef = payNode.path("data").path("paymentReference").asText();

        // 1. Trigger simulated refund FAILURE via [SIM_FAIL] in reason
        RefundCreateRequest failRefund = new RefundCreateRequest(new BigDecimal("100.00"), "USD", "Damaged goods [SIM_FAIL]");
        MvcResult failResult = mockMvc.perform(post("/api/v1/payments/" + paymentRef + "/refunds")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failRefund)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureCode").value("REFUND_DECLINED"))
                .andReturn();

        // Verify reserved balance was released: remaining refundable is still $500.00
        mockMvc.perform(get("/api/v1/payments/" + paymentRef + "/refundable-amount")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingRefundableAmount").value(500.00))
                .andExpect(jsonPath("$.data.pendingRefundAmount").value(0.00));

        // 2. Trigger simulated refund TIMEOUT via [SIM_TIMEOUT] in reason
        RefundCreateRequest timeoutRefund = new RefundCreateRequest(new BigDecimal("150.00"), "USD", "Network delay [SIM_TIMEOUT]");
        MvcResult timeoutResult = mockMvc.perform(post("/api/v1/payments/" + paymentRef + "/refunds")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(timeoutRefund)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.failureCode").value("PROVIDER_TIMEOUT"))
                .andReturn();

        JsonNode timeoutNode = objectMapper.readTree(timeoutResult.getResponse().getContentAsString());
        String timeoutRefundRef = timeoutNode.path("data").path("refundReference").asText();

        // Verify reserved balance is HELD in pending ($150.00) so remaining is $350.00
        mockMvc.perform(get("/api/v1/payments/" + paymentRef + "/refundable-amount")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingRefundAmount").value(150.00))
                .andExpect(jsonPath("$.data.remainingRefundableAmount").value(350.00));

        // 3. Reconcile UNKNOWN refund via verify endpoint
        mockMvc.perform(post("/api/v1/refunds/" + timeoutRefundRef + "/verify")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        // Verify refund is now SUCCESS and deducted permanently from refundable amount
        mockMvc.perform(get("/api/v1/payments/" + paymentRef + "/refundable-amount")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingRefundAmount").value(0.00))
                .andExpect(jsonPath("$.data.totalRefundedAmount").value(150.00))
                .andExpect(jsonPath("$.data.remainingRefundableAmount").value(350.00));
    }

    private String loginUser(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    private void registerAndApproveMerchant(String adminToken, String email, String businessName) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Merchant Rep");
        req.setEmail(email);
        req.setPassword("Password123!");
        req.setBusinessName(businessName);
        req.setBusinessAddress("100 FinTech Way");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        MvcResult listResult = mockMvc.perform(get("/api/v1/admin/merchants")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode listNode = objectMapper.readTree(listResult.getResponse().getContentAsString());
        JsonNode content = listNode.path("data").path("content");
        long merchantId = -1;
        for (JsonNode m : content) {
            if (businessName.equals(m.path("businessName").asText())) {
                merchantId = m.path("id").asLong();
                break;
            }
        }
        assertTrue(merchantId > 0, "Registered merchant should be found in admin merchant list");

        mockMvc.perform(patch("/api/v1/admin/merchants/" + merchantId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}

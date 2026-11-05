package com.paycore;

import com.paycore.merchant.entity.Merchant;
import com.paycore.merchant.entity.MerchantStatus;
import com.paycore.merchant.repository.MerchantRepository;
import com.paycore.payment.entity.Payment;
import com.paycore.payment.entity.PaymentMethodType;
import com.paycore.payment.entity.PaymentStatus;
import com.paycore.payment.repository.PaymentRepository;
import com.paycore.refund.dto.RefundCreateRequest;
import com.paycore.refund.dto.RefundResponseDto;
import com.paycore.refund.exception.ExceededRefundableAmountException;
import com.paycore.refund.exception.PaymentNotRefundableException;
import com.paycore.refund.service.RefundService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class RefundConcurrencyIntegrationTest {

    @Autowired
    private RefundService refundService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Test
    @DisplayName("Pessimistic Locking & Reservation Concurrency Test: 10 threads race to refund $20 on a $100 payment")
    void testConcurrentRefundsPreventOverRefunding() throws Exception {
        // 1. Setup Merchant
        Merchant merchant = new Merchant();
        merchant.setMerchantCode("MCH_CONCUR_" + UUID.randomUUID().toString().substring(0, 6));
        merchant.setBusinessName("Concurrency Test Merchant");
        merchant.setBusinessEmail("concur_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com");
        merchant.setUserId(9999L);
        merchant.setStatus(MerchantStatus.ACTIVE);
        merchant = merchantRepository.saveAndFlush(merchant);

        // 2. Setup Payment of $100.00
        Payment payment = new Payment();
        String paymentRef = "pay_race_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        payment.setPaymentReference(paymentRef);
        payment.setMerchant(merchant);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setAmountRefunded(BigDecimal.ZERO);
        payment.setAmountRefundReserved(BigDecimal.ZERO);
        payment.setCurrency("USD");
        payment.setPaymentMethod(PaymentMethodType.CARD);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProvider("SIMULATED");
        payment.setProviderTransactionId("sim_race_tx_001");
        payment = paymentRepository.saveAndFlush(payment);

        final Long merchantId = merchant.getId();
        final int threadCount = 10;
        final BigDecimal partialAmount = new BigDecimal("20.00");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threadCount);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger exceededCounter = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    startGate.await(); // Align all threads at starting line
                    RefundCreateRequest request = new RefundCreateRequest(partialAmount, "USD", "Race attempt " + index);
                    RefundResponseDto response = refundService.executeRefund(paymentRef, merchantId, request);
                    if (response != null && response.getStatus().name().equals("SUCCESS")) {
                        successCounter.incrementAndGet();
                    }
                } catch (ExceededRefundableAmountException | PaymentNotRefundableException ex) {
                    exceededCounter.incrementAndGet();
                } catch (Exception ex) {
                    otherErrors.incrementAndGet();
                } finally {
                    finishGate.countDown();
                }
            }));
        }

        // Release all threads simultaneously
        startGate.countDown();
        finishGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // 3. Assertions
        assertEquals(5, successCounter.get(), "Exactly 5 partial refunds of $20 must succeed to reach the $100 limit");
        assertEquals(5, exceededCounter.get(), "Exactly 5 partial refunds must be rejected due to balance depletion");
        assertEquals(0, otherErrors.get(), "No unexpected internal or deadlock errors should occur");

        // 4. Verify Final Database State
        Payment finalPayment = paymentRepository.findByPaymentReference(paymentRef).orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(finalPayment.getAmountRefunded()),
                "Total refunded amount on payment must equal exactly $100.00");
        assertEquals(0, BigDecimal.ZERO.compareTo(finalPayment.getAmountRefundReserved()),
                "All temporary reservations must be cleanly settled (zero remaining reserved)");
        assertEquals(0, BigDecimal.ZERO.compareTo(finalPayment.getRemainingRefundableAmount()),
                "Remaining refundable balance must be zero");
        assertEquals(PaymentStatus.REFUNDED, finalPayment.getStatus(),
                "Payment must transition to REFUNDED once completely refunded");
    }
}

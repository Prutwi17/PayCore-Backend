package com.paycore;

import com.paycore.common.exception.ResourceNotFoundException;
import com.paycore.merchant.entity.Merchant;
import com.paycore.payment.entity.Payment;
import com.paycore.payment.entity.PaymentStatus;
import com.paycore.payment.exception.PaymentValidationException;
import com.paycore.payment.idempotency.service.IdempotencyService;
import com.paycore.payment.provider.PaymentProvider;
import com.paycore.payment.repository.PaymentRepository;
import com.paycore.refund.dto.RefundCreateRequest;
import com.paycore.refund.dto.RefundableAmountDto;
import com.paycore.refund.exception.ExceededRefundableAmountException;
import com.paycore.refund.exception.PaymentNotRefundableException;
import com.paycore.refund.exception.RefundValidationException;
import com.paycore.refund.repository.RefundRepository;
import com.paycore.refund.service.RefundService;
import com.paycore.refund.service.RefundTransactionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Refund Service Unit Tests")
class RefundServiceUnitTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private RefundTransactionHelper refundTransactionHelper;

    @Mock
    private PaymentProvider paymentProvider;

    @Mock
    private IdempotencyService idempotencyService;

    private RefundService refundService;

    private Merchant merchant;
    private Payment payment;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(
                paymentRepository,
                refundRepository,
                refundTransactionHelper,
                paymentProvider,
                idempotencyService
        );

        merchant = new Merchant();
        merchant.setId(10L);
        merchant.setMerchantCode("MCH_TEST");

        payment = new Payment();
        payment.setId(100L);
        payment.setPaymentReference("pay_test_100");
        payment.setMerchant(merchant);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setAmountRefunded(BigDecimal.ZERO);
        payment.setAmountRefundReserved(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Calculate accurate refundable balance for full and partial states")
    void testGetRefundableAmount() {
        when(paymentRepository.findByPaymentReferenceWithMerchant("pay_test_100"))
                .thenReturn(Optional.of(payment));

        // When no refunds exist
        RefundableAmountDto dto = refundService.getRefundableAmount("pay_test_100", 10L, false);
        assertEquals(new BigDecimal("100.00"), dto.getOriginalAmount());
        assertEquals(BigDecimal.ZERO, dto.getTotalRefundedAmount());
        assertEquals(new BigDecimal("100.00"), dto.getRemainingRefundableAmount());
        assertTrue(dto.isRefundable());

        // When partial refund exists
        payment.setAmountRefunded(new BigDecimal("40.00"));
        payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);

        dto = refundService.getRefundableAmount("pay_test_100", 10L, false);
        assertEquals(new BigDecimal("40.00"), dto.getTotalRefundedAmount());
        assertEquals(new BigDecimal("60.00"), dto.getRemainingRefundableAmount());
        assertTrue(dto.isRefundable());

        // When in-flight reserved amount exists
        payment.setAmountRefundReserved(new BigDecimal("25.00"));
        dto = refundService.getRefundableAmount("pay_test_100", 10L, false);
        assertEquals(new BigDecimal("25.00"), dto.getPendingRefundAmount());
        assertEquals(new BigDecimal("35.00"), dto.getRemainingRefundableAmount());
    }

    @Test
    @DisplayName("Enforce merchant ownership check on refundable balance calculation")
    void testRefundableAmountOwnershipValidation() {
        when(paymentRepository.findByPaymentReferenceWithMerchant("pay_test_100"))
                .thenReturn(Optional.of(payment));

        // Other merchant (ID 99) attempts to access Merchant 10's payment
        assertThrows(PaymentValidationException.class, () ->
                refundService.getRefundableAmount("pay_test_100", 99L, false)
        );

        // Admin bypasses merchant check
        assertDoesNotThrow(() ->
                refundService.getRefundableAmount("pay_test_100", 99L, true)
        );
    }

    @Test
    @DisplayName("Throw ResourceNotFoundException when payment does not exist")
    void testRefundableAmountPaymentNotFound() {
        when(paymentRepository.findByPaymentReferenceWithMerchant("pay_invalid"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                refundService.getRefundableAmount("pay_invalid", 10L, false)
        );
    }
}

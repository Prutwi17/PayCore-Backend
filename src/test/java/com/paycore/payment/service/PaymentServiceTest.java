package com.paycore.payment.service;

import com.paycore.common.exception.ResourceNotFoundException;
import com.paycore.merchant.entity.Merchant;
import com.paycore.merchant.entity.MerchantStatus;
import com.paycore.merchant.repository.MerchantRepository;
import com.paycore.payment.dto.PaymentCreateRequest;
import com.paycore.payment.dto.PaymentResponseDto;
import com.paycore.payment.entity.*;
import com.paycore.payment.exception.InvalidStateTransitionException;
import com.paycore.payment.exception.PaymentValidationException;
import com.paycore.payment.provider.*;
import com.paycore.payment.repository.PaymentAttemptRepository;
import com.paycore.payment.repository.PaymentRepository;
import com.paycore.payment.statemachine.PaymentStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private MerchantRepository merchantRepository;

    private PaymentStateMachine stateMachine;
    private PaymentTransactionHelper transactionHelper;
    private PaymentProviderFactory providerFactory;
    private SimulatedPaymentProvider simulatedProvider;
    private PaymentService paymentService;

    private Merchant activeMerchant;

    @BeforeEach
    void setUp() {
        stateMachine = new PaymentStateMachine();
        transactionHelper = new PaymentTransactionHelper(paymentRepository, paymentAttemptRepository, merchantRepository, stateMachine);
        simulatedProvider = new SimulatedPaymentProvider();
        providerFactory = new PaymentProviderFactory(List.of(simulatedProvider));
        paymentService = new PaymentService(paymentRepository, paymentAttemptRepository, transactionHelper, providerFactory, stateMachine);

        activeMerchant = new Merchant();
        activeMerchant.setId(10L);
        activeMerchant.setMerchantCode("MCH_ACTIVE_001");
        activeMerchant.setStatus(MerchantStatus.ACTIVE);
    }

    @Test
    @DisplayName("Successful payment creation, provider execution, and status completion")
    void testProcessPaymentSuccess() {
        when(merchantRepository.findById(10L)).thenReturn(Optional.of(activeMerchant));

        Payment mockSavedPayment = new Payment();
        mockSavedPayment.setId(100L);
        mockSavedPayment.setPaymentReference("pay_ref_test1");
        mockSavedPayment.setMerchant(activeMerchant);
        mockSavedPayment.setAmount(new BigDecimal("99.99"));
        mockSavedPayment.setCurrency("USD");
        mockSavedPayment.setStatus(PaymentStatus.CREATED);

        PaymentAttempt mockAttempt = new PaymentAttempt();
        mockAttempt.setId(500L);
        mockAttempt.setAttemptNumber(1);
        mockAttempt.setRequestReference("att_req_1");
        mockSavedPayment.addAttempt(mockAttempt);

        when(paymentRepository.save(any(Payment.class))).thenReturn(mockSavedPayment);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenReturn(mockSavedPayment);
        when(paymentRepository.findByIdWithMerchant(100L)).thenReturn(Optional.of(mockSavedPayment));
        when(paymentAttemptRepository.findById(500L)).thenReturn(Optional.of(mockAttempt));

        PaymentCreateRequest request = new PaymentCreateRequest(
                new BigDecimal("99.99"), "USD", "Test order", PaymentMethodType.CARD, "cust_001"
        );

        PaymentResponseDto response = paymentService.processPayment(10L, request);

        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertNotNull(response.getProviderTransactionId());
        verify(paymentRepository, atLeast(2)).saveAndFlush(any(Payment.class));
    }

    @Test
    @DisplayName("Amount zero or negative throws PaymentValidationException")
    void testInvalidAmountThrowsException() {
        when(merchantRepository.findById(10L)).thenReturn(Optional.of(activeMerchant));

        PaymentCreateRequest zeroRequest = new PaymentCreateRequest(
                BigDecimal.ZERO, "INR", "Zero order", PaymentMethodType.CARD, null
        );

        assertThrows(PaymentValidationException.class, () -> paymentService.processPayment(10L, zeroRequest));
    }

    @Test
    @DisplayName("Unsupported currency throws PaymentValidationException")
    void testUnsupportedCurrencyThrowsException() {
        when(merchantRepository.findById(10L)).thenReturn(Optional.of(activeMerchant));

        PaymentCreateRequest request = new PaymentCreateRequest(
                new BigDecimal("100.00"), "XYZ", "Invalid currency", PaymentMethodType.CARD, null
        );

        assertThrows(PaymentValidationException.class, () -> paymentService.processPayment(10L, request));
    }

    @Test
    @DisplayName("Inactive merchant cannot initiate payment")
    void testInactiveMerchantRejected() {
        Merchant pendingMerchant = new Merchant();
        pendingMerchant.setId(20L);
        pendingMerchant.setMerchantCode("MCH_PENDING");
        pendingMerchant.setStatus(MerchantStatus.PENDING);

        when(merchantRepository.findById(20L)).thenReturn(Optional.of(pendingMerchant));

        PaymentCreateRequest request = new PaymentCreateRequest(
                new BigDecimal("100.00"), "INR", "Order", PaymentMethodType.CARD, null
        );

        PaymentValidationException ex = assertThrows(PaymentValidationException.class,
                () -> paymentService.processPayment(20L, request));
        assertTrue(ex.getMessage().contains("not active"));
    }

    @Test
    @DisplayName("Provider timeout transitions payment to UNKNOWN")
    void testProviderTimeoutTransitionsToUnknown() {
        when(merchantRepository.findById(10L)).thenReturn(Optional.of(activeMerchant));

        Payment mockSavedPayment = new Payment();
        mockSavedPayment.setId(101L);
        mockSavedPayment.setPaymentReference("pay_ref_timeout");
        mockSavedPayment.setMerchant(activeMerchant);
        mockSavedPayment.setAmount(new BigDecimal("50.00"));
        mockSavedPayment.setCurrency("INR");
        mockSavedPayment.setStatus(PaymentStatus.CREATED);

        PaymentAttempt mockAttempt = new PaymentAttempt();
        mockAttempt.setId(501L);
        mockAttempt.setAttemptNumber(1);
        mockAttempt.setRequestReference("att_req_timeout");
        mockSavedPayment.addAttempt(mockAttempt);

        when(paymentRepository.save(any(Payment.class))).thenReturn(mockSavedPayment);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenReturn(mockSavedPayment);
        when(paymentRepository.findByIdWithMerchant(101L)).thenReturn(Optional.of(mockSavedPayment));
        when(paymentAttemptRepository.findById(501L)).thenReturn(Optional.of(mockAttempt));

        PaymentCreateRequest request = new PaymentCreateRequest(
                new BigDecimal("50.00"), "INR", "Order [SIM_TIMEOUT]", PaymentMethodType.CARD, null
        );
        request.setSimulationScenario("TIMEOUT");

        PaymentResponseDto response = paymentService.processPayment(10L, request);

        assertNotNull(response);
        assertEquals(PaymentStatus.UNKNOWN, response.getStatus());
        assertEquals("PROVIDER_TIMEOUT", response.getFailureCode());
    }

    @Test
    @DisplayName("Merchant ownership check prevents cross-merchant access")
    void testMerchantOwnershipEnforcement() {
        when(paymentRepository.findByPaymentReferenceAndMerchantIdWithMerchant("pay_123", 10L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.getPaymentByReference("pay_123", 10L, false));
    }

    @Test
    @DisplayName("Cancellable check permits cancelling CREATED/REQUIRES_ACTION and rejects SUCCESS")
    void testCancellationRules() {
        Payment activePayment = new Payment();
        activePayment.setId(102L);
        activePayment.setPaymentReference("pay_to_cancel");
        activePayment.setStatus(PaymentStatus.REQUIRES_ACTION);
        activePayment.setMerchant(activeMerchant);

        when(paymentRepository.findByPaymentReferenceAndMerchantIdWithMerchant("pay_to_cancel", 10L))
                .thenReturn(Optional.of(activePayment));
        when(paymentRepository.findByIdWithMerchant(102L)).thenReturn(Optional.of(activePayment));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenReturn(activePayment);

        PaymentResponseDto cancelled = paymentService.cancelPayment("pay_to_cancel", 10L, false, "Customer requested");
        assertNotNull(cancelled);
        assertEquals(PaymentStatus.CANCELLED, cancelled.getStatus());

        // Rejection when already terminal SUCCESS
        activePayment.setStatus(PaymentStatus.SUCCESS);
        assertThrows(InvalidStateTransitionException.class,
                () -> paymentService.cancelPayment("pay_to_cancel", 10L, false, "Cancel"));
    }
}

package com.paycore.payment.statemachine;

import com.paycore.payment.entity.PaymentStatus;
import com.paycore.payment.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStateMachineTest {

    private PaymentStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new PaymentStateMachine();
    }

    @Test
    @DisplayName("Valid transitions from CREATED succeed")
    void testValidTransitionsFromCreated() {
        assertDoesNotThrow(() -> stateMachine.validateTransition(PaymentStatus.CREATED, PaymentStatus.PROCESSING));
        assertDoesNotThrow(() -> stateMachine.validateTransition(PaymentStatus.CREATED, PaymentStatus.CANCELLED));
    }

    @Test
    @DisplayName("Valid transitions from PROCESSING succeed")
    void testValidTransitionsFromProcessing() {
        assertDoesNotThrow(() -> stateMachine.validateTransition(PaymentStatus.PROCESSING, PaymentStatus.SUCCESS));
        assertDoesNotThrow(() -> stateMachine.validateTransition(PaymentStatus.PROCESSING, PaymentStatus.FAILED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(PaymentStatus.PROCESSING, PaymentStatus.REQUIRES_ACTION));
        assertDoesNotThrow(() -> stateMachine.validateTransition(PaymentStatus.PROCESSING, PaymentStatus.AUTHORIZED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(PaymentStatus.PROCESSING, PaymentStatus.UNKNOWN));
    }

    @Test
    @DisplayName("Valid transitions from REQUIRES_ACTION and AUTHORIZED succeed")
    void testValidTransitionsFromRequiresActionAndAuthorized() {
        assertDoesNotThrow(() -> stateMachine.validateTransition(PaymentStatus.REQUIRES_ACTION, PaymentStatus.PROCESSING));
        assertDoesNotThrow(() -> stateMachine.validateTransition(PaymentStatus.REQUIRES_ACTION, PaymentStatus.CANCELLED));

        assertDoesNotThrow(() -> stateMachine.validateTransition(PaymentStatus.AUTHORIZED, PaymentStatus.SUCCESS));
        assertDoesNotThrow(() -> stateMachine.validateTransition(PaymentStatus.AUTHORIZED, PaymentStatus.CANCELLED));
    }

    @Test
    @DisplayName("UNKNOWN payment can transition to SUCCESS or FAILED upon verification")
    void testValidTransitionsFromUnknown() {
        assertDoesNotThrow(() -> stateMachine.validateTransition(PaymentStatus.UNKNOWN, PaymentStatus.SUCCESS));
        assertDoesNotThrow(() -> stateMachine.validateTransition(PaymentStatus.UNKNOWN, PaymentStatus.FAILED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(PaymentStatus.UNKNOWN, PaymentStatus.PROCESSING));
    }

    @Test
    @DisplayName("Prohibited transitions throw InvalidStateTransitionException")
    void testInvalidTransitions() {
        assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(PaymentStatus.CREATED, PaymentStatus.SUCCESS));

        assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(PaymentStatus.CREATED, PaymentStatus.REQUIRES_ACTION));

        assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(PaymentStatus.FAILED, PaymentStatus.SUCCESS));

        assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(PaymentStatus.SUCCESS, PaymentStatus.PROCESSING));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"SUCCESS", "FAILED", "CANCELLED"})
    @DisplayName("Terminal states cannot transition to any other status")
    void testTerminalStatesCannotTransition(PaymentStatus terminalStatus) {
        assertTrue(stateMachine.isTerminal(terminalStatus));

        for (PaymentStatus target : PaymentStatus.values()) {
            if (target != terminalStatus) {
                assertThrows(InvalidStateTransitionException.class,
                        () -> stateMachine.validateTransition(terminalStatus, target));
            }
        }
    }

    @Test
    @DisplayName("Cancellable check identifies correct states")
    void testIsCancellable() {
        assertTrue(stateMachine.isCancellable(PaymentStatus.CREATED));
        assertTrue(stateMachine.isCancellable(PaymentStatus.REQUIRES_ACTION));
        assertTrue(stateMachine.isCancellable(PaymentStatus.AUTHORIZED));

        assertFalse(stateMachine.isCancellable(PaymentStatus.PROCESSING));
        assertFalse(stateMachine.isCancellable(PaymentStatus.SUCCESS));
        assertFalse(stateMachine.isCancellable(PaymentStatus.FAILED));
        assertFalse(stateMachine.isCancellable(PaymentStatus.CANCELLED));
    }
}

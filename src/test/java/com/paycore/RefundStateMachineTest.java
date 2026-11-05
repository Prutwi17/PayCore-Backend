package com.paycore;

import com.paycore.refund.entity.Refund;
import com.paycore.refund.entity.RefundStatus;
import com.paycore.refund.exception.InvalidRefundStateTransitionException;
import com.paycore.refund.statemachine.RefundStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Refund State Machine Unit Tests")
class RefundStateMachineTest {

    private RefundStateMachine stateMachine;
    private Refund refund;

    @BeforeEach
    void setUp() {
        stateMachine = new RefundStateMachine();
        refund = new Refund();
        refund.setRefundReference("ref_test_001");
        refund.setStatus(RefundStatus.REQUESTED);
    }

    @Test
    @DisplayName("Permit REQUESTED -> PROCESSING transition")
    void testRequestedToProcessing() {
        assertTrue(stateMachine.canTransition(RefundStatus.REQUESTED, RefundStatus.PROCESSING));
        stateMachine.transition(refund, RefundStatus.PROCESSING);
        assertEquals(RefundStatus.PROCESSING, refund.getStatus());
    }

    @Test
    @DisplayName("Permit PROCESSING -> SUCCESS transition")
    void testProcessingToSuccess() {
        refund.setStatus(RefundStatus.PROCESSING);
        assertTrue(stateMachine.canTransition(RefundStatus.PROCESSING, RefundStatus.SUCCESS));
        stateMachine.transition(refund, RefundStatus.SUCCESS);
        assertEquals(RefundStatus.SUCCESS, refund.getStatus());
        assertTrue(stateMachine.isTerminal(RefundStatus.SUCCESS));
    }

    @Test
    @DisplayName("Permit PROCESSING -> FAILED transition")
    void testProcessingToFailed() {
        refund.setStatus(RefundStatus.PROCESSING);
        assertTrue(stateMachine.canTransition(RefundStatus.PROCESSING, RefundStatus.FAILED));
        stateMachine.transition(refund, RefundStatus.FAILED);
        assertEquals(RefundStatus.FAILED, refund.getStatus());
        assertTrue(stateMachine.isTerminal(RefundStatus.FAILED));
    }

    @Test
    @DisplayName("Permit PROCESSING -> UNKNOWN transition")
    void testProcessingToUnknown() {
        refund.setStatus(RefundStatus.PROCESSING);
        assertTrue(stateMachine.canTransition(RefundStatus.PROCESSING, RefundStatus.UNKNOWN));
        stateMachine.transition(refund, RefundStatus.UNKNOWN);
        assertEquals(RefundStatus.UNKNOWN, refund.getStatus());
    }

    @Test
    @DisplayName("Permit UNKNOWN -> PROCESSING and UNKNOWN -> SUCCESS transitions")
    void testUnknownTransitions() {
        refund.setStatus(RefundStatus.UNKNOWN);
        assertTrue(stateMachine.canTransition(RefundStatus.UNKNOWN, RefundStatus.PROCESSING));
        assertTrue(stateMachine.canTransition(RefundStatus.UNKNOWN, RefundStatus.SUCCESS));
        assertTrue(stateMachine.canTransition(RefundStatus.UNKNOWN, RefundStatus.FAILED));

        stateMachine.transition(refund, RefundStatus.PROCESSING);
        assertEquals(RefundStatus.PROCESSING, refund.getStatus());
    }

    @Test
    @DisplayName("Reject terminal SUCCESS -> FAILED or any other outbound transition")
    void testSuccessTerminalState() {
        refund.setStatus(RefundStatus.SUCCESS);
        assertFalse(stateMachine.canTransition(RefundStatus.SUCCESS, RefundStatus.FAILED));
        assertFalse(stateMachine.canTransition(RefundStatus.SUCCESS, RefundStatus.PROCESSING));

        assertThrows(InvalidRefundStateTransitionException.class, () ->
                stateMachine.transition(refund, RefundStatus.FAILED)
        );
    }

    @Test
    @DisplayName("Reject terminal FAILED -> SUCCESS or any other outbound transition")
    void testFailedTerminalState() {
        refund.setStatus(RefundStatus.FAILED);
        assertFalse(stateMachine.canTransition(RefundStatus.FAILED, RefundStatus.SUCCESS));
        assertFalse(stateMachine.canTransition(RefundStatus.FAILED, RefundStatus.PROCESSING));

        assertThrows(InvalidRefundStateTransitionException.class, () ->
                stateMachine.transition(refund, RefundStatus.SUCCESS)
        );
    }

    @Test
    @DisplayName("Reject illegal REQUESTED -> SUCCESS skipping PROCESSING")
    void testIllegalDirectSkip() {
        assertFalse(stateMachine.canTransition(RefundStatus.REQUESTED, RefundStatus.SUCCESS));
        assertThrows(InvalidRefundStateTransitionException.class, () ->
                stateMachine.transition(refund, RefundStatus.SUCCESS)
        );
    }

    @Test
    @DisplayName("Handle no-op transition gracefully")
    void testNoOpTransition() {
        refund.setStatus(RefundStatus.PROCESSING);
        stateMachine.transition(refund, RefundStatus.PROCESSING);
        assertEquals(RefundStatus.PROCESSING, refund.getStatus());
    }
}

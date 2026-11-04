package com.paycore.payment.statemachine;

import com.paycore.payment.entity.PaymentStatus;
import com.paycore.payment.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Centralized state machine governing valid payment lifecycle state transitions.
 * Enforces strict transition validation and terminal status protection.
 */
@Component
public class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> VALID_TRANSITIONS = new EnumMap<>(PaymentStatus.class);

    private static final Set<PaymentStatus> TERMINAL_STATES = EnumSet.of(
            PaymentStatus.SUCCESS,
            PaymentStatus.FAILED,
            PaymentStatus.CANCELLED
    );

    static {
        // CREATED: initial state when payment is persisted
        VALID_TRANSITIONS.put(PaymentStatus.CREATED, EnumSet.of(
                PaymentStatus.PROCESSING,
                PaymentStatus.CANCELLED
        ));

        // PROCESSING: payment attempt is in-flight with provider
        VALID_TRANSITIONS.put(PaymentStatus.PROCESSING, EnumSet.of(
                PaymentStatus.SUCCESS,
                PaymentStatus.FAILED,
                PaymentStatus.REQUIRES_ACTION,
                PaymentStatus.AUTHORIZED,
                PaymentStatus.UNKNOWN
        ));

        // REQUIRES_ACTION: requires 3DS / user authentication redirect
        VALID_TRANSITIONS.put(PaymentStatus.REQUIRES_ACTION, EnumSet.of(
                PaymentStatus.PROCESSING,
                PaymentStatus.CANCELLED
        ));

        // AUTHORIZED: payment funds held, awaiting capture or cancellation
        VALID_TRANSITIONS.put(PaymentStatus.AUTHORIZED, EnumSet.of(
                PaymentStatus.SUCCESS,
                PaymentStatus.CANCELLED
        ));

        // UNKNOWN: provider timeout or unconfirmed network response. Requires status verification
        VALID_TRANSITIONS.put(PaymentStatus.UNKNOWN, EnumSet.of(
                PaymentStatus.SUCCESS,
                PaymentStatus.FAILED,
                PaymentStatus.PROCESSING // Only allowed if re-verifying/re-querying
        ));

        // Terminal states cannot transition to anything
        VALID_TRANSITIONS.put(PaymentStatus.SUCCESS, Collections.emptySet());
        VALID_TRANSITIONS.put(PaymentStatus.FAILED, Collections.emptySet());
        VALID_TRANSITIONS.put(PaymentStatus.CANCELLED, Collections.emptySet());
    }

    /**
     * Validates if transition from currentStatus to targetStatus is allowed.
     * Throws InvalidStateTransitionException if transition is prohibited.
     */
    public void validateTransition(PaymentStatus currentStatus, PaymentStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            throw new InvalidStateTransitionException("Current and target payment status must not be null");
        }

        if (currentStatus == targetStatus) {
            return; // No-op idempotent transition
        }

        Set<PaymentStatus> allowedTargets = VALID_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
        if (!allowedTargets.contains(targetStatus)) {
            throw new InvalidStateTransitionException(currentStatus.name(), targetStatus.name());
        }
    }

    /**
     * Returns true if the status is a terminal state (SUCCESS, FAILED, CANCELLED).
     */
    public boolean isTerminal(PaymentStatus status) {
        return status != null && TERMINAL_STATES.contains(status);
    }

    /**
     * Returns true if the payment can be cancelled.
     */
    public boolean isCancellable(PaymentStatus status) {
        return status == PaymentStatus.CREATED ||
                status == PaymentStatus.REQUIRES_ACTION ||
                status == PaymentStatus.AUTHORIZED;
    }

    /**
     * Returns true if status requires verification before further action.
     */
    public boolean requiresVerification(PaymentStatus status) {
        return status == PaymentStatus.UNKNOWN;
    }
}

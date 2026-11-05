package com.paycore.refund.statemachine;

import com.paycore.refund.entity.Refund;
import com.paycore.refund.entity.RefundStatus;
import com.paycore.refund.exception.InvalidRefundStateTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Deterministic state machine governing the lifecycle transitions of a refund.
 * <p>
 * Valid transitions:
 * REQUESTED  -> PROCESSING
 * PROCESSING -> SUCCESS
 * PROCESSING -> FAILED
 * PROCESSING -> UNKNOWN
 * UNKNOWN    -> PROCESSING (via verified provider status check)
 * <p>
 * SUCCESS and FAILED are terminal states and cannot transition.
 */
@Component
public class RefundStateMachine {

    private static final Logger log = LoggerFactory.getLogger(RefundStateMachine.class);

    private static final Map<RefundStatus, Set<RefundStatus>> VALID_TRANSITIONS = new EnumMap<>(RefundStatus.class);

    static {
        VALID_TRANSITIONS.put(
                RefundStatus.REQUESTED,
                Collections.unmodifiableSet(EnumSet.of(RefundStatus.PROCESSING))
        );
        VALID_TRANSITIONS.put(
                RefundStatus.PROCESSING,
                Collections.unmodifiableSet(EnumSet.of(RefundStatus.SUCCESS, RefundStatus.FAILED, RefundStatus.UNKNOWN))
        );
        VALID_TRANSITIONS.put(
                RefundStatus.UNKNOWN,
                Collections.unmodifiableSet(EnumSet.of(RefundStatus.PROCESSING, RefundStatus.SUCCESS, RefundStatus.FAILED))
        );
        // Terminal states have no outbound transitions
        VALID_TRANSITIONS.put(RefundStatus.SUCCESS, Collections.emptySet());
        VALID_TRANSITIONS.put(RefundStatus.FAILED, Collections.emptySet());
    }

    /**
     * Validates whether transition from current status to target status is permitted.
     */
    public boolean canTransition(RefundStatus from, RefundStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true; // No-op transition
        }
        Set<RefundStatus> allowedTargets = VALID_TRANSITIONS.get(from);
        return allowedTargets != null && allowedTargets.contains(to);
    }

    /**
     * Executes the state transition on the target Refund entity or throws an exception.
     */
    public void transition(Refund refund, RefundStatus targetStatus) {
        if (refund == null) {
            throw new IllegalArgumentException("Refund entity must not be null");
        }
        RefundStatus current = refund.getStatus();
        if (current == targetStatus) {
            log.debug("Refund [{}] already in status [{}]. No-op transition.", refund.getRefundReference(), targetStatus);
            return;
        }

        if (!canTransition(current, targetStatus)) {
            String error = String.format(
                    "Illegal refund state transition from [%s] to [%s] for refund reference [%s]",
                    current, targetStatus, refund.getRefundReference()
            );
            log.warn(error);
            throw new InvalidRefundStateTransitionException(error);
        }

        log.info("Transitioning refund [{}] from [{}] to [{}]", refund.getRefundReference(), current, targetStatus);
        refund.setStatus(targetStatus);
    }

    public boolean isTerminal(RefundStatus status) {
        return status == RefundStatus.SUCCESS || status == RefundStatus.FAILED;
    }
}

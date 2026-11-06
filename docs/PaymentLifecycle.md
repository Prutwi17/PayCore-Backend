# PayCore — Payment & Transaction Lifecycle Specification

## 1. Complete Payment State Machine

PayCore enforces deterministic state transitions across payments. No entity can transition directly from terminal states (`SUCCESS`, `FAILED`, `CANCELLED`) to any other state.

```mermaid
stateDiagram-v2
    [*] --> CREATED: Merchant Initiates Charge
    CREATED --> PROCESSING: Pre-auth & Lock Acquired
    PROCESSING --> SUCCESS: Provider Confirmed Charge
    PROCESSING --> FAILED: Provider Rejected Card / Decline
    PROCESSING --> REQUIRES_ACTION: 3D-Secure / OTP Required
    PROCESSING --> UNKNOWN: Network Timeout / Dropped Connection
    
    REQUIRES_ACTION --> PROCESSING: Customer Completes Action
    REQUIRES_ACTION --> FAILED: Action Timed Out / Abandoned
    
    UNKNOWN --> PROCESSING: Self-Healing Status Query Triggered
    PROCESSING --> CANCELLED: Explicit Merchant Pre-Capture Cancel
    
    SUCCESS --> [*]: Terminal (Settled)
    FAILED --> [*]: Terminal (No Charge)
    CANCELLED --> [*]: Terminal (Voided)
```

### Valid Transition Matrix

| Current State | Target State | Trigger / Condition |
|---|---|---|
| `CREATED` | `PROCESSING` | Payment record initialized, idempotency validated, ready for provider dispatch. |
| `PROCESSING` | `SUCCESS` | Downstream provider returned successful capture confirmation. |
| `PROCESSING` | `FAILED` | Provider returned explicit decline or fraud rule violation. |
| `PROCESSING` | `REQUIRES_ACTION` | Downstream bank requires customer challenge (3DS redirect URL returned). |
| `PROCESSING` | `UNKNOWN` | Gateway socket dropped, connection timeout, or 504 Gateway Timeout. |
| `REQUIRES_ACTION` | `PROCESSING` | Webhook or customer return callback received indicating completion. |
| `REQUIRES_ACTION` | `FAILED` | Customer failed challenge or challenge expired. |
| `UNKNOWN` | `PROCESSING` | Scheduled reconciliation or merchant verify request polls provider status. |
| `CREATED` / `PROCESSING` | `CANCELLED` | Explicit cancellation requested prior to downstream settlement. |

---

## 2. Zero-DB-Transaction Network I/O Guarantee

In high-volume payment processing, holding an open database transaction while making an external network call causes connection pool exhaustion and database lock starvation.

PayCore guarantees **zero open database transactions during external provider network I/O**:

```mermaid
sequenceDiagram
    autonumber
    actor Merchant as Merchant API Client
    participant Controller as PaymentController
    participant TxHelper as PaymentTransactionHelper
    participant DB as MySQL Database
    participant Provider as PaymentProvider (Simulated / Gateway)
    participant Outbox as OutboxEvent

    Merchant->>Controller: POST /api/v1/payments (Idempotency-Key)
    
    rect rgb(230, 245, 255)
    Note over Controller,DB: Phase 1: Database Transaction (Short, <5ms)
    Controller->>TxHelper: createInitialPaymentRecord()
    TxHelper->>DB: Insert Payment(status=PROCESSING, version=0)
    TxHelper->>DB: Insert OutboxEvent(PAYMENT_CREATED)
    TxHelper-->>Controller: Commit DB Transaction & Release Connection
    end

    rect rgb(255, 245, 230)
    Note over Controller,Provider: Phase 2: External Network I/O (NO Open DB Transaction)
    Controller->>Provider: executePayment(request)
    Provider-->>Controller: ProviderResponse(SUCCESS, txnId="txn_12345")
    end

    rect rgb(230, 255, 235)
    Note over Controller,DB: Phase 3: Database Transaction (Short, <5ms)
    Controller->>TxHelper: completePaymentRecord(paymentId, response)
    TxHelper->>DB: Update Payment(status=SUCCESS, providerTxnId="txn_12345")
    TxHelper->>DB: Insert OutboxEvent(PAYMENT_SUCCESS)
    TxHelper-->>Controller: Commit DB Transaction & Release Connection
    end

    Controller-->>Merchant: 201 Created (PaymentResponseDto)
```

---

## 3. Distributed Idempotency Protocol

To prevent accidental double-charging when clients retry due to network hiccups, PayCore implements a 3-layer idempotency defense:

1. **Redis Fast-Lock Coordination:**
   - Atomic distributed lock on `lock:idempotency:{merchantId}:{idempotencyKey}` with a 30-second TTL.
   - Prevents simultaneous requests with the exact same key from racing in memory.
2. **Canonical Payload Fingerprinting:**
   - Generates SHA-256 digest of normalized payload: `amount + "|" + currency + "|" + paymentMethod + "|" + customerEmail + "|" + description`.
   - If an existing record is found with the same `(merchant_id, idempotency_key)`:
     - If payload fingerprint matches: returns cached result immediately with `Idempotent-Replay: true` header. No second provider charge occurs.
     - If payload fingerprint differs: rejects immediately with HTTP `409 Conflict` (`IDEMPOTENCY_CONFLICT`).
3. **Database Uniqueness Constraint:**
   - Composite unique constraint `uk_merchant_idempotency_key (merchant_id, idempotency_key)` in MySQL acts as the authoritative final barrier even if Redis is unavailable.

---

## 4. Refund Balance Reservations & Lifecycle

A payment can be refunded partially or fully. To prevent concurrent race conditions where multiple simultaneous refunds exceed the original settled amount:

```mermaid
sequenceDiagram
    autonumber
    actor Merchant
    participant RefundService
    participant DB as MySQL (Pessimistic Lock)
    participant Provider as PaymentProvider

    Merchant->>RefundService: POST /api/v1/refunds (paymentRef, amount=$50)
    
    rect rgb(255, 240, 240)
    Note over RefundService,DB: Phase 1: Atomic Balance Reservation (DB TX)
    RefundService->>DB: SELECT * FROM payments WHERE id=? FOR UPDATE
    Note right of DB: Checks: amount <= (originalAmount - amountRefunded - amountRefundReserved)
    RefundService->>DB: UPDATE payments SET amount_refund_reserved = amount_refund_reserved + $50
    RefundService->>DB: INSERT INTO refunds (status=PROCESSING, amount=$50)
    RefundService-->>DB: Commit Reservation Transaction
    end

    rect rgb(255, 245, 230)
    Note over RefundService,Provider: Phase 2: External Provider Refund Call (NO DB TX)
    RefundService->>Provider: executeRefund(...)
    Provider-->>RefundService: RefundResult(SUCCESS, refundTxnId="ref_987")
    end

    rect rgb(240, 255, 240)
    Note over RefundService,DB: Phase 3: Final Settlement (DB TX)
    RefundService->>DB: SELECT * FROM payments WHERE id=? FOR UPDATE
    RefundService->>DB: UPDATE payments SET amount_refund_reserved = amount_refund_reserved - $50, amount_refunded = amount_refunded + $50
    RefundService->>DB: UPDATE refunds SET status=SUCCESS, provider_refund_id="ref_987"
    RefundService-->>DB: Commit Final Transaction
    end

    RefundService-->>Merchant: 201 Created (RefundResponseDto)
```

**Timeout Protection:** If the external provider drops the connection during Phase 2, the `$50` remains in `amount_refund_reserved` and the refund is marked `UNKNOWN`. It cannot be double-refunded while in this unconfirmed state until reconciled or authoritatively verified.

---

## 5. Event-Driven Webhook Dispatching (Outbox + Kafka)

1. Business operations update payments/refunds and insert domain events into `outbox_events` in the **same database transaction**.
2. `OutboxPublisherScheduler` polls unpublished outbox records and publishes messages to partitioned Kafka topics (`paycore.payment.events`, `paycore.refund.events`).
3. Partition key is set to entity reference (`paymentReference`), guaranteeing per-entity message ordering.
4. Kafka consumers write events to `inbox_events` with unique constraint `(event_id, consumer_name)` to guarantee idempotent execution.
5. `WebhookDeliveryWorker` computes HMAC-SHA256 signatures (`X-PayCore-Signature: t=timestamp,v1=signature`) and delivers payloads with exponential backoff retries (`5s, 30s, 120s, 900s, 3600s`). Exhausted attempts route to dead-letter queues.

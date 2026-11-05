# PAYCORE — SYSTEM DESIGN & RELIABILITY SPECIFICATION

## 1. System Context & High-Level Architecture

PayCore is positioned as the central payment gateway between external merchant platforms (e.g. e-commerce checkouts) and downstream payment providers.

```
       [ E-Commerce Client / Merchant System ]
                          │
                   HTTPS / REST API
         (Header: X-Api-Key, Idempotency-Key)
                          │
                          ▼
            ┌────────────────────────────┐
            │        API Gateway         │
            │   (Rate Limiter / WAF)     │
            └─────────────┬──────────────┘
                          │
                          ▼
    ┌───────────────────────────────────────────────┐
    │          PAYCORE MODULAR MONOLITH             │
    │                                               │
    │  ┌─────────────┐ ┌─────────────┐ ┌──────────┐ │
    │  │ Auth & Sec  │ │ Merchant    │ │ Customer │ │
    │  └─────────────┘ └─────────────┘ └──────────┘ │
    │  ┌─────────────┐ ┌─────────────┐ ┌──────────┐ │
    │  │ Payment Eng │ │ Refund Eng  │ │ Admin    │ │
    │  └──────┬──────┘ └──────┬──────┘ └──────────┘ │
    │         │               │                     │
    │  ┌──────┴───────────────┴─────────┐           │
    │  │  Idempotency & Concurrency     │           │
    │  └────────────────────────────────┘           │
    │         │ Redis Lock & Deduplication          │
    └─────────┼─────────────────────────┼───────────┘
              │                         │
     State &  │ Persistence             │ Async Event Stream
              ▼                         ▼
      ┌───────────────┐         ┌───────────────┐
      │  MySQL 8.0    │         │ Apache Kafka  │
      │ Primary Store │         │ Message Bus   │
      └───────────────┘         └───────┬───────┘
                                        │
                         ┌──────────────┴──────────────┐
                         ▼                             ▼
             ┌───────────────────────┐     ┌───────────────────────┐
             │ Webhook Worker Engine │     │ Audit Log Consumer    │
             └───────────┬───────────┘     └───────────────────────┘
                         │
                  HTTP POST (HMAC Signed)
                         │
                         ▼
             [ Merchant Webhook URL ]
```

---

## 2. Low-Level Component Responsibilities

1. **API Gateway / Security Filters:**
   - Validates JWT tokens for dashboard users.
   - Validates and hashes `X-Api-Key` headers for merchant API clients.
   - Resolves tenant (`merchant_id`) and sets thread-local security context.
2. **Idempotency Interceptor & Engine:**
   - Evaluates incoming `Idempotency-Key` header on mutating POST operations (`/payments`, `/refunds`).
   - Aquires Redis in-flight lock to reject concurrent identical requests.
   - Queries `idempotency_records` table in MySQL.
   - Computes SHA-256 request fingerprint. Detects key tampering or payload mismatch.
3. **Payment Orchestration Engine:**
   - Enforces the payment state machine.
   - Pre-creates payment entity in `CREATED` status inside an atomic local transaction.
   - Dispatches provider request OUTSIDE database transaction.
   - Evaluates provider response or timeout, records attempt in `payment_attempts`, and updates payment status.
4. **Provider Adapter Layer:**
   - Maps internal payment models to provider-specific payloads.
   - Executes remote calls with circuit breakers and connect/read timeouts (e.g. 5000ms).
   - Handles network faults and provider status mapping.
5. **Event Publisher & Kafka Pipeline:**
   - Publishes domain events to Kafka topics (`paycore.payment.events`, `paycore.webhook.dispatch`).
   - Guarantees transaction outbox or commit-first consistency.
6. **Webhook Dispatcher Engine:**
   - Consumes dispatch events from Kafka.
   - Signs payload with merchant's webhook secret using `HMAC-SHA256` (`X-PayCore-Signature`).
   - Dispatches HTTP POST with exponential backoff retry (1s, 5s, 30s, 300s, 1800s).
   - Routes to Dead Letter Queue (DLQ) upon exceeding retry limits.
7. **Reconciliation Engine:**
   - Ingests settlement batches from providers.
   - Compares internal transaction IDs, amounts, and statuses against external ledger.
   - Identifies discrepancies: `MATCHED`, `MISSING_INTERNAL`, `MISSING_PROVIDER`, `AMOUNT_MISMATCH`, `STATUS_MISMATCH`.

---

## 3. Core Sequence Diagrams

### 3.1 Payment Processing Sequence (with Provider Timeout & Recovery)

```
Client             PayCore API           Redis / MySQL         PaymentProvider
  │                     │                      │                      │
  │ POST /v1/payments   │                      │                      │
  │ Idempotency-Key     │                      │                      │
  │────────────────────>│                      │                      │
  │                     │ Acquire Redis Lock   │                      │
  │                     │─────────────────────>│                      │
  │                     │ Lock Acquired        │                      │
  │                     │<─────────────────────│                      │
  │                     │ Check Idempotency    │                      │
  │                     │ Record in MySQL      │                      │
  │                     │─────────────────────>│                      │
  │                     │ Not Found (New Req)  │                      │
  │                     │<─────────────────────│                      │
  │                     │                      │                      │
  │                     │ [DB TX 1: Commit]    │                      │
  │                     │ INSERT Payment       │                      │
  │                     │ (status=CREATED)     │                      │
  │                     │─────────────────────>│                      │
  │                     │ Committed            │                      │
  │                     │<─────────────────────│                      │
  │                     │                      │                      │
  │                     │ Execute Remote Charge (OUTSIDE TX)          │
  │                     │────────────────────────────────────────────>│
  │                     │                                             │
  │                     │ [Case A: Provider OK]                       │
  │                     │   HTTP 200 { status: SUCCESS }              │
  │                     │<────────────────────────────────────────────│
  │                     │                                             │
  │                     │ [DB TX 2: Commit]                           │
  │                     │ UPDATE Payment -> SUCCESS                   │
  │                     │ INSERT Attempt (SUCCESS)                    │
  │                     │ INSERT IdempotencyRecord (with Response)    │
  │                     │─────────────────────>│                      │
  │                     │ Release Redis Lock   │                      │
  │                     │─────────────────────>│                      │
  │                     │ Produce Kafka Event  │                      │
  │                     │ (payment.success)    │                      │
  │                     │                      │                      │
  │ HTTP 201 Created    │                      │                      │
  │ { status: SUCCESS } │                      │                      │
  │<────────────────────│                      │                      │
  │                     │                      │                      │
  │                     │ [Case B: Provider Timeout]                  │
  │                     │   Timeout Exception (SocketTimeout)         │
  │                     │<────────────────────────────────────────────│
  │                     │                                             │
  │                     │ [DB TX 2: Commit]                           │
  │                     │ UPDATE Payment -> UNKNOWN                   │
  │                     │ INSERT Attempt (TIMEOUT)                    │
  │                     │ INSERT IdempotencyRecord                    │
  │                     │─────────────────────>│                      │
  │                     │ Release Redis Lock   │                      │
  │                     │─────────────────────>│                      │
  │                     │ Publish Verify Task  │                      │
  │ HTTP 202 Accepted   │                      │                      │
  │ { status: UNKNOWN } │                      │                      │
  │<────────────────────│                      │                      │
```

---


### 3.2 Refund Processing Sequence (Two-Phase Zero-DB Network I/O & Reservation)

```
Merchant Client       PayCore API          MySQL DB (Payments/Refunds)       PaymentProvider
      │                    │                            │                           │
      │ POST /v1/payments/ │                            │                           │
      │   {ref}/refunds    │                            │                           │
      │ Idempotency-Key    │                            │                           │
      │───────────────────>│                            │                           │
      │                    │ Check Idempotency          │                           │
      │                    │───────────────────────────>│                           │
      │                    │ New Request / Lock Acquired│                           │
      │                    │<───────────────────────────│                           │
      │                    │                            │                           │
      │                    │ [Phase 1: DB TX 1 - PESSIMISTIC LOCK & RESERVATION]    │
      │                    │ SELECT * FROM payments WHERE payment_ref=? FOR UPDATE  │
      │                    │ Check: amount <= (original - refunded - reserved)     │
      │                    │ UPDATE payments SET amount_refund_reserved += amount   │
      │                    │ INSERT INTO refunds (status=PROCESSING)                │
      │                    │ COMMIT TX 1                │                           │
      │                    │───────────────────────────>│                           │
      │                    │ DB TX 1 Committed          │                           │
      │                    │<───────────────────────────│                           │
      │                    │                            │                           │
      │                    │ [Phase 2: Remote Provider Refund (OUTSIDE ANY DB TX)]  │
      │                    │ POST /provider/refunds     │                           │
      │                    │───────────────────────────────────────────────────────>│
      │                    │                                                        │
      │                    │ [Case A: Provider Refund SUCCESS]                      │
      │                    │ HTTP 200 { status: SUCCESS, provider_refund_id }       │
      │                    │<───────────────────────────────────────────────────────│
      │                    │                                                        │
      │                    │ [Phase 3: DB TX 2 - SETTLEMENT FINALIZATION]           │
      │                    │ SELECT * FROM payments WHERE id=? FOR UPDATE           │
      │                    │ UPDATE payments SET                                    │
      │                    │   amount_refund_reserved -= amount,                    │
      │                    │   amount_refunded += amount,                           │
      │                    │   status = (remaining == 0 ? REFUNDED : PARTIAL)       │
      │                    │ UPDATE refunds SET status=SUCCESS, provider_ref        │
      │                    │ INSERT IdempotencyRecord (COMPLETED, response_body)    │
      │                    │ COMMIT TX 2                │                           │
      │                    │───────────────────────────>│                           │
      │                    │ DB TX 2 Committed          │                           │
      │                    │<───────────────────────────│                           │
      │ HTTP 201 Created   │                            │                           │
      │ { status: SUCCESS }│                            │                           │
      │<───────────────────│                            │                           │
      │                    │                                                        │
      │                    │ [Case B: Provider Network Timeout / UNKNOWN]           │
      │                    │ TimeoutException (SocketTimeout / Provider Hang)       │
      │                    │<───────────────────────────────────────────────────────│
      │                    │                                                        │
      │                    │ [Phase 3: DB TX 2 - PRESERVE RESERVATION SAFELY]       │
      │                    │ UPDATE refunds SET status=UNKNOWN                      │
      │                    │ Note: amount_refund_reserved REMAINS RESERVED          │
      │                    │ COMMIT TX 2                │                           │
      │                    │───────────────────────────>│                           │
      │ HTTP 202 Accepted  │                            │                           │
      │ { status: UNKNOWN }│                            │                           │
      │<───────────────────│                            │                           │
```

## 4. Failure Scenarios & Mitigations

### 4.1 Idempotency Architecture & 6 Critical Cases

Payment initiation requires a client-generated `Idempotency-Key` HTTP header scoped per merchant. The authoritative source of truth is the MySQL composite unique constraint `uk_merchant_idempotency_key` on `(merchant_id, idempotency_key)`. Redis acts strictly as an optional fast coordination lock and is never the sole protection against duplicates.

#### The 6 Idempotency Scenarios:
1. **CASE 1 — First Request (Fresh Key):**
   - Atomically insert `idempotency_records` row with status `PENDING` and canonical SHA-256 `request_fingerprint`.
   - Dispatch payment to provider adapter outside active database transaction.
   - Atomically update record status to `COMPLETED`, cache serialized HTTP 201 response payload.
   - Return HTTP `201 Created` with normal headers.
2. **CASE 2 — Duplicate Request (Identical Key & Payload):**
   - Find existing completed record matching `merchant_id` and `idempotency_key`.
   - Compute incoming request fingerprint and verify equality with stored fingerprint.
   - Return stored cached response with HTTP `200 OK` and response header `Idempotent-Replay: true`.
   - Downstream payment provider is **NEVER** contacted a second time.
3. **CASE 3 — Payload Conflict (Same Key, Altered Payload):**
   - Identify existing record with matching key but mismatching `request_fingerprint`.
   - Immediately reject with HTTP `409 Conflict` and error code `IDEMPOTENCY_CONFLICT`.
   - Prevents accidental reuse of keys across distinct financial transactions.
4. **CASE 4 — Concurrent Requests (Race Condition with Same Key):**
   - Multiple threads or API instances simultaneously receive the same idempotency key.
   - Serialized through Redis distributed lock (`SET lock:payment:idemp:{merchantId}:{key} {token} NX PX 15000`).
   - If Redis is unavailable or contested, the MySQL database unique constraint `uk_merchant_idempotency_key` guarantees that exactly one thread succeeds in inserting the `PENDING` record.
   - Concurrent threads catch `DataIntegrityViolationException` and enter a polling observation loop (up to 2.25s) awaiting primary completion.
   - Once completed, observing threads return the cached result with HTTP `200 OK` (`Idempotent-Replay: true`). Exactly one payment is created in the database.
5. **CASE 5 — Existing Request Currently In-Flight:**
   - Client queries or retries while original thread is still actively communicating with provider.
   - Service detects `processing_status = PENDING` and enters polling loop.
   - If request exceeds poll window without completion, returns HTTP `202 Accepted` (`IDEMPOTENCY_PROCESSING`) advising client to query status endpoint. Under no circumstances is a second charge initiated.
6. **CASE 6 — Expired Key Handling:**
   - Keys have a 24-hour expiration window (`expires_at`).
   - If key expires after payment completed: Service returns cached completed payment rather than initiating a duplicate charge.
   - If key expired while pending/failed: Rejects with HTTP `409 Conflict` instructing client to initiate with a fresh key.

#### Request Fingerprint Algorithm:
The fingerprint is a deterministic SHA-256 digest calculated over normalized canonical fields:
`SHA-256(amount.stripTrailingZeros() + "|" + currency.toUpperCase() + "|" + paymentMethod + "|" + customerReference.trim() + "|" + description.trim())`

#### Redis Coordination & Automated Circuit Breaker:
- Redis key: `lock:payment:idemp:{merchantId}:{idempotencyKey}` with 15-second TTL.
- Atomic release via Lua script matching ownership token.
- If Redis is unreachable, an automated circuit breaker trips (`CIRCUIT_BREAKER_RESET_MS = 30000`), instantly bypassing Redis and relying directly on the database unique constraint without latency spikes or duplicate charges.

### 4.2 Retry Strategy & Charge Safety Invariant
The `PaymentRetryExecutor` strictly segregates query and charge operations:
- **Safe Queries (Status Verification):** Retried up to 3 times with exponential backoff (100ms, 200ms, 400ms) on transient socket or network blips.
- **Unsafe Charges (Payment Dispatch):** **STRICTLY PROHIBITED FROM BLIND RETRIES.** If a charge call encounters a network timeout, retrying blindly risks double-charging the customer. Instead, the transaction status is marked `UNKNOWN` with attempt telemetry preserved, and resolved via idempotent status verification (`GET /payments/{ref}/status?verify=true`).

### 4.3 Concurrency Control & Optimistic Locking
- The `payments` table includes a JPA `@Version` column (`version BIGINT`).
- Optimistic locking prevents concurrent state machine updates and race conditions during simultaneous webhook callbacks, refunds, or cancellations.
- Conflicts trigger `OptimisticLockException` translated to HTTP `409 Conflict` (`CONCURRENCY_CONFLICT`).

### 4.3 Payment Provider Timeouts
- **Scenario:** The backend invokes the provider. The downstream network drops or provider hangs past 5000ms.
- **Handling:**
  1. **Strict Reliability Rule:** The backend MUST NEVER assume the transaction failed, nor blindly retry the charge. The provider may have debited the customer's card.
  2. The payment record is transitioned to `UNKNOWN` or `PROCESSING_PENDING_VERIFICATION`.
  3. The response returned to the client is HTTP `202 Accepted` with status `UNKNOWN`.
  4. An asynchronous reconciliation/verification job queries the provider API (`GET /provider/transactions/{id}`) to obtain the true authoritative settlement status before any further state update or retry is allowed.

### 4.4 Database Failures During Processing
- **Scenario:** MySQL connection is lost after the provider succeeds.
- **Handling:**
  - Database connection pool (HikariCP) retries connection.
  - Transactions are kept short.
  - The provider transaction ID is logged in persistent application logs and emergency audit store. The reconciliation engine detects this discrepancy as `MISSING_INTERNAL` or `STATUS_MISMATCH` during scheduled sync.

### 4.5 Kafka Processing Failures & Duplicate Events
- **Scenario:** Webhook worker consumes event from Kafka, dispatches to merchant, but crashes before committing Kafka consumer offset. Upon restart, event is redelivered.
- **Handling:**
  - Webhook dispatch is designed to be idempotent: The worker inspects `webhook_delivery_attempts` table.
  - Delivery attempts record event ID and timestamp.
  - Outbound webhook HTTP payload includes unique event ID `evt_...` and timestamp. The receiving merchant can deduplicate using the event ID.

### 4.6 Refund Failures & Over-Refund Prevention
- **Scenario:** Merchant requests a refund of ₹500 on a ₹1000 payment twice concurrently.
- **Handling:**
  - Service acquires a database row-level lock on the payment record: `SELECT * FROM payments WHERE id = ? FOR UPDATE`.
  - Service checks `payment.getAmountRefunded() + requestedRefund <= payment.getAmount()`.
  - Transaction 1 processes: `amount_refunded` becomes ₹500.
  - Transaction 2 evaluates updated state: `₹500 + ₹500 <= ₹1000`, succeeds.
  - Transaction 3 for ₹100: Fails with `InsufficientRefundableAmountException` (Refundable remaining: ₹0).

### 4.7 Reconciliation Mismatches
- **Scenario:** Provider settlement file contains a transaction marked SUCCESS for ₹1,000, while internal database marks it as UNKNOWN or ₹900.
- **Handling:**
  - Reconciliation batch compares records on `(provider_transaction_id, internal_payment_id)`.
  - Mismatch is recorded in `reconciliation_records` with flag `STATUS_MISMATCH` or `AMOUNT_MISMATCH`.
  - Admin dashboard surfaces the discrepancy for manual review or automated auto-heal workflow.

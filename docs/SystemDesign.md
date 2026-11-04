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

## 4. Failure Scenarios & Mitigations

### 4.1 Duplicate Payment Requests (Idempotency)
- **Scenario:** Client sends payment request with key `K1`. Network drops before client receives response. Client retries request with key `K1`.
- **Handling:**
  1. API checks `idempotency_records` table where `(merchant_id, idempotency_key)` is uniquely indexed.
  2. If found, the request fingerprint is calculated (`SHA-256(HTTP Method + Path + Body)`).
  3. If fingerprint matches: The stored HTTP status and response payload are returned immediately from the record without re-charging.
  4. If fingerprint differs: HTTP `422 Unprocessable Entity` is returned with code `IDEMPOTENCY_KEY_REUSE_WITH_DIFFERENT_PAYLOAD`.

### 4.2 Simultaneous Concurrent Requests
- **Scenario:** Client inadvertently issues two parallel HTTP POSTs with identical `Idempotency-Key` `K1` simultaneously.
- **Handling:**
  1. Both requests attempt to acquire a distributed Redis lock: `SET lock:idempotency:{merchant_id}:{key} {uuid} NX PX 10000`.
  2. Request 1 succeeds and proceeds with processing.
  3. Request 2 fails lock acquisition and immediately receives HTTP `409 Conflict` (`CONCURRENT_REQUEST_IN_FLIGHT`) or waits up to a tight timeout (500ms) before re-checking the idempotency record.
  4. Even if Redis is evicted or restarted, the MySQL database unique constraint `uq_idempotency_merchant_key` rejects duplicate inserts with a constraint violation.

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

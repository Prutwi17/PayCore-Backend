# PAYCORE — PRODUCT REQUIREMENTS DOCUMENT (PRD)

## 1. Document Overview
- **Document Version:** 1.0.0
- **Project Name:** PayCore — Payment Processing Engine
- **Target Audience:** Engineering, DevOps, Security, Product Architects
- **Status:** Draft / Approved Master Specification

---

## 2. Executive Summary
PayCore is an enterprise-grade, highly reliable, and modular payment processing backend designed to handle payment initiation, authorization, settlement simulation, failure mitigation, idempotency, partial and full refunds, asynchronous webhook dispatching via Apache Kafka, payment reconciliation, and granular audit logging. 

PayCore functions as an independent payment gateway infrastructure that external client applications (e.g., e-commerce platforms, SaaS billing systems, mobile apps) interact with via authenticated REST APIs using merchant credentials and API keys.

---

## 3. User Personas & Access Roles

### 3.1 Role Hierarchy
The system enforces strict Role-Based Access Control (RBAC) across three principal user personas:

| Role | Target Persona | Scope of Access | Primary Interface |
|---|---|---|---|
| **ADMIN** | System Operators, DevOps, Risk & Finance Ops | Global system view, all merchants, platform health, global reconciliation, audit logs, provider switch | Admin Portal (`/admin/*`) & Admin REST APIs |
| **MERCHANT** | Registered Business / Enterprise Sellers | Tenant-scoped view of own transactions, API keys, webhooks, refunds, settlement reports | Merchant Portal (`/merchant/*`) & Merchant API (`/api/v1/*`) |
| **CUSTOMER** | End-consumers executing checkout | Session-scoped payment completion, status check, 3DS action verification | Checkout Hosted Interface / Client SDK |

---

## 4. Functional Requirements by Module

### 4.1 Admin Module
- **Authentication & Security:** Secure credential login with MFA readiness, JWT token issuance, session tracking.
- **Platform Analytics:** Real-time metrics including total system transaction volume, platform success rates, payment velocity, failed transaction distribution, active merchants count.
- **Merchant Management:** Review new merchant registrations, approve/suspend merchant accounts, inspect API key usage, audit merchant activity.
- **Payment Monitoring:** Global search across all payments with filtering by merchant, status, payment method, date range, amount threshold, error codes.
- **Refund Monitoring:** Oversight of all full and partial refund requests across all merchants with anomaly detection (e.g., refund rate spikes).
- **Webhook Telemetry:** Inspection of dead-letter queues (DLQ), retry failure counts, webhook dispatch latency, endpoint health.
- **Reconciliation Engine:** Run automated or on-demand reconciliation jobs between internal ledger records and provider settlement files. Review discrepancy reports (missing internal, missing provider, amount mismatch, status mismatch).
- **Audit Logging:** Searchable, immutable timeline of administrative, financial, and security events with actor IP, timestamp, action type, before/after state diffs.
- **System Health:** Live Spring Boot Actuator monitoring (JVM memory, thread pools, Kafka broker health, Redis connection pool, database connection pool).

### 4.2 Merchant Module
- **Self-Service Registration & Onboarding:** Registration workflow with business verification status (PENDING, ACTIVE, SUSPENDED).
- **Authentication & Access:** JWT-based dashboard authentication, password reset workflows.
- **API Key Lifecycle Management:**
  - Generate dual keys: Publishable Key (`pk_live_...` or `pk_test_...`) and Secret API Key (`sk_live_...` or `sk_test_...`).
  - Secret keys displayed once upon generation and stored as salted SHA-256 hashes.
  - Revocation, roll-over, and expiration capabilities.
- **Merchant Dashboard:**
  - Real-time KPIs: Gross Payment Volume, Net Settlement, Success Rate, Refund Volume.
  - Payment volume trend charts (daily, weekly, monthly).
- **Payment Operations:**
  - Initiate payments via REST API with idempotency guarantee.
  - Manual payment creation for test and phone-order scenarios.
  - Comprehensive payment explorer with deep search and filtering.
  - Payment detail view with interactive state transition timeline, payment attempts, provider reference, customer info.
- **Refund Management:**
  - Issue full or partial refunds directly against settled or successful payments.
  - Enforce non-exceedance: Cumulative refunds cannot exceed original payment amount.
  - Real-time tracking of refund lifecycle (`REQUESTED` -> `PROCESSING` -> `SUCCESS` / `FAILED`).
- **Webhook Notifications:**
  - Configure webhook listener URLs with endpoint-specific signing secrets (`whsec_...`).
  - Select subscribed event types (e.g., `payment.success`, `payment.failed`, `refund.processed`).
  - Webhook delivery explorer: Inspect HTTP status codes, request payloads, response bodies, attempt history, and manual redelivery button.
- **Reconciliation & Reporting:**
  - View daily settlement batches, fee breakdowns, and discrepancy logs.

### 4.3 Customer / Checkout Module
- **Payment Initiation:** Accept payment intent with order reference, amount, currency, payment method details (tokenized card, UPI, NetBanking).
- **Lifecycle Progression:** Transition payment from `CREATED` -> `PROCESSING` -> `AUTHORIZED` -> `SUCCESS` or `FAILED`.
- **Action Handling:** Support `REQUIRES_ACTION` state for 3D Secure / OTP verification simulation.
- **Cancellation:** Allow customer or system cancellation prior to capture.
- **Receipt & Status:** Real-time query of payment state without exposing sensitive merchant internal data.

---

## 5. Non-Functional Requirements (NFR)

### 5.1 Reliability & Availability
- Zero duplicate payment execution under retry or network fluctuation conditions.
- Target 99.95% API uptime for core payment ingestion endpoints.
- Circuit breaker and rate-limiting patterns to isolate downstream provider outages.

### 5.2 Concurrency & Data Consistency
- Concurrent payment requests with the same idempotency key must execute strictly once.
- Concurrency control combining Redis distributed locks (fast rejection of concurrent duplicates) and MySQL database unique constraints (persistent ACID correctness).
- Isolation level: `READ_COMMITTED` for standard transactions with optimistic/pessimistic locking on financial updates.

### 5.3 Asynchronous Processing & Throughput
- Webhook dispatch and event streaming decoupled from the synchronous HTTP request path using Apache Kafka.
- Guaranteed at-least-once delivery semantics in Kafka with idempotent consumers to prevent duplicated side effects.
- Dead Letter Queue (DLQ) for events failing after 5 exponential backoff retries.

### 5.4 Security & Compliance
- Strictly NO storage of raw Primary Account Numbers (PAN), Card Verification Values (CVV), or card expiration dates.
- BCrypt password hashing (work factor 12).
- HMAC-SHA256 signature verification for webhook payloads.
- HTTPS/TLS termination and sanitized logging (masking all PII and auth tokens).

---

## 6. Success Metrics & Key Performance Indicators (KPIs)
1. **Idempotency Accuracy:** 100% duplicate charge prevention on repeated request tests.
2. **Provider Timeout Resilience:** 0% unverified double charges following simulated provider timeouts.
3. **P99 API Latency:** < 250ms for payment creation (excluding downstream simulated network latency).
4. **Reconciliation Discrepancy Detection:** 100% identification of mismatched amounts, statuses, and orphaned transactions.

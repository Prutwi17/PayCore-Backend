# PayCore — Payment Processing Engine (Backend)

[![Java 17](https://img.shields.io/badge/Java-17-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-orange.svg)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.x-red.svg)](https://redis.io/)
[![Kafka](https://img.shields.io/badge/Kafka-3.6-black.svg)](https://kafka.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

> **PayCore** is an enterprise-grade payment processing engine engineered as a modular monolith. It provides robust REST APIs for merchant integration, distributed idempotency guarantees, asynchronous webhook dispatching via Apache Kafka, resilient provider timeout handling, full and partial refunds, financial reconciliation, and immutable audit logging.

---

## 🚀 Key Architecture & Engineering Highlights

- **Modular Monolith:** Clean boundary separation across domain modules (`auth`, `merchant`, `payment`, `refund`, `webhook`, `reconciliation`, `audit`, `admin`).
- **Payment Lifecycle State Machine:** Strict enforcement of transitions (`CREATED`, `PROCESSING`, `REQUIRES_ACTION`, `AUTHORIZED`, `SUCCESS`, `FAILED`, `CANCELLED`, `UNKNOWN`).
- **Zero Double-Charge Guarantee:** 
  - Distributed `Idempotency-Key` evaluation.
  - Redis distributed locks for active requests.
  - Persistent composite unique constraint `(merchant_id, idempotency_key)` in MySQL.
  - Payload fingerprinting using SHA-256 to reject key tampering.
- **Provider Timeout Resilience:** Unconfirmed downstream provider timeouts transition to `UNKNOWN` pending verification; never blindly retries potentially successful charges.
- **Zero Active DB Transactions during Network I/O:** Database connections are never held open while waiting for external provider responses.
- **Asynchronous Webhook Engine:** Event-driven architecture powered by Apache Kafka, supporting HMAC-SHA256 signature verification, exponential backoff retries, and Dead Letter Queues (DLQ).
- **Financial Reconciliation:** Automated detection of matching transactions, amount discrepancies, status mismatches, and orphaned records.

---

## 📁 Project Documentation

Detailed technical documentation is maintained under the [`docs/`](./docs) directory:

| Document | Description |
|---|---|
| [**PRD.md**](./docs/PRD.md) | Product Requirements Document, user roles, functional & non-functional requirements |
| [**TechStack.md**](./docs/TechStack.md) | Technology choices, versions, libraries, and architectural rationale |
| [**Architecture.md**](./docs/Architecture.md) | Modular monolith architecture, package structure, module boundaries |
| [**SystemDesign.md**](./docs/SystemDesign.md) | High-level & low-level design, sequence diagrams, failure recovery |
| [**DatabaseDesign.md**](./docs/DatabaseDesign.md) | Normalized MySQL schema, table definitions, indexes, monetary precision |
| [**ERDiagram.md**](./docs/ERDiagram.md) | Visual Mermaid Entity-Relationship Diagram |
| [**AppFlow.md**](./docs/AppFlow.md) | Payment state machine, refund flow, webhook dispatching, reconciliation |
| [**API-Documentation.md**](./docs/API-Documentation.md) | Comprehensive REST API specification, request/response models |
| [**Security.md**](./docs/Security.md) | Spring Security, JWT, RBAC, API Key hashing, webhook HMAC signing |
| [**EmailIntegration.md**](./docs/EmailIntegration.md) | Real Gmail API architecture, OAuth 2.0 offline refresh, MIME templating |
| [**GoogleOAuthSetup.md**](./docs/GoogleOAuthSetup.md) | Step-by-step Google Cloud project setup, consent screen, refresh token retrieval |
| [**AuthenticationFlow.md**](./docs/AuthenticationFlow.md) | Email verification, JWT access, refresh token rotation, replay defense |
| [**PasswordRecovery.md**](./docs/PasswordRecovery.md) | Anti-enumeration forgot password, reset tokens, session invalidation alerts |
| [**AdminProvisioning.md**](./docs/AdminProvisioning.md) | Zero-trust administrator onboarding for omp27614@gmail.com |
| [**TestingGuide.md**](./docs/TestingGuide.md) | Comprehensive testing guide across unit, integration, and frontend suites |
| [**DeploymentGuide.md**](./docs/DeploymentGuide.md) | Production Docker, Nginx, MySQL, Redis, Kafka, and deployment runbooks |
| [**PaymentLifecycle.md**](./docs/PaymentLifecycle.md) | State machine, two-phase zero-DB network I/O, balance reservations, and idempotency |
| [**KnownLimitations.md**](./docs/KnownLimitations.md) | Simulated provider scope, PCI DSS disclosures, Gmail quotas, and future scope |
| [**CICD.md**](./docs/CICD.md) | GitHub Actions CI pipeline, Dockerfile, docker-compose.yml |
| [**GitWorkflow.md**](./docs/GitWorkflow.md) | Branching strategy, PR guidelines, conventional commits |
| [**DevelopmentRoadmap.md**](./docs/DevelopmentRoadmap.md) | 8-Milestone execution plan with acceptance criteria |

---

## 🛠 Local Development Setup

### Prerequisites
- Java 17 LTS
- Maven 3.9+ (or use `./mvnw` wrapper)
- Docker & Docker Compose
- Node.js 20+ (for frontend)

### Running Supporting Services via Docker Compose
```bash
# Start MySQL, Redis, and Kafka KRaft
docker-compose up -d mysql redis kafka
```

### Running Backend Locally
```bash
cd PayCore-Backend
./mvnw spring-boot:run
```

API Server starts at: `http://localhost:8080`  
Swagger UI Documentation: `http://localhost:8080/swagger-ui/index.html`  
Actuator Health: `http://localhost:8080/actuator/health`

---

## 🧪 Testing & Current Status (Stage 8 Complete — Project Finalized)

PayCore Stage 8 (Final Testing, Optimization, Security, Deployment & Project Completion) is fully implemented, verified, and operational:

- **143 Automated Tests Passing:** Complete backend test suite running across unit, repository, service, state machine, idempotency, concurrent race condition, and end-to-end integration tests (0 failures, 0 errors, 0 skipped).
- **Scheduled Token Lifecycle Cleanup:** Automatic daily purging (TokenCleanupScheduler) of expired verification, reset, and refresh tokens older than 48 hours to preserve database health and index performance.
- **Production-Ready Configuration:** Dedicated pplication-prod.properties with strict DDL validation (ddl-auto=validate), HikariCP connection pool optimization, and comprehensive schema-production.sql MySQL migration script.
- **Full Docker & Container Orchestration:** Production multi-stage Dockerfiles and container-healthcheck verified docker-compose.yml supporting MySQL, Redis, Kafka, Backend, and Frontend.
- **Postman API Test Suite:** Complete collection and environment (postman/PayCore.postman_collection.json) covering all 8 major functional modules with automated token extraction.
- **Zero Double-Charge & Timeout Resilience:** Authoritative MySQL composite unique constraints, Redis distributed locks, and two-phase balance reservations.
- **Real Gmail API Integration:** Google OAuth 2.0 offline authorization with fail-fast diagnostics (no fake delivery).

## 🧪 Previous Stage 7.5 Details

PayCore Stage 7.5 (Real Gmail Email Integration, Registration, Login, Logout & Password Recovery) is fully implemented, verified, and operational:

- **141 Automated Tests Passing:** Complete test suite running across unit, repository, service, and end-to-end integration tests with 0 failures, 0 errors, and 0 skipped.
- **Real Gmail API Integration:** Dispatches real RFC 2822 multipart HTML/text emails through Google OAuth 2.0 and Gmail REST API (Users.Messages.Send). No simulated email delivery.
- **Fail-Fast Error Diagnostics:** If OAuth credentials or refresh tokens are missing, the API rejects requests immediately with 503 Service Unavailable (EMAIL_DELIVERY_UNAVAILABLE). No fake delivery claims.
- **Mandatory Email Verification:** Merchant accounts are registered with email_verified=false and cannot authenticate until verifying ownership via a single-use SHA-256 hashed token with 24-hour expiration.
- **Dual-Token & Refresh Token Rotation:** Issues 15-minute JWT access tokens and persistent 7-day refresh tokens. Using a refresh token rotates both tokens and invalidates the previous one, with automated replay attack detection.
- **Anti-Enumeration Password Recovery:** Generic 200 OK responses prevent account enumeration. Reset tokens are single-use, expire in 60 minutes, invalidate all active sessions upon consumption, and trigger immediate security alerts.
- **Zero-Trust Admin Onboarding:** System owner email omp27614@gmail.com is onboarded via a cryptographic setup link sent to their Gmail inbox. No hardcoded default administrator credentials exist in production.

## 🧪 Previous Stage 6 Details

PayCore Stage 6 (Webhooks, Kafka & Asynchronous Processing) is fully implemented, verified, and operational:

- **Transactional Outbox Pattern:**
  - Events are saved into `outbox_events` in the **exact same ACID database transaction** as payments and refunds (`PaymentTransactionHelper`, `RefundTransactionHelper`).
  - No dual-write failure: even if Kafka or the application crashes, no event is ever lost.
  - Background `OutboxPublisherScheduler` polls pending records and dispatches to Kafka outside any DB transaction, recording broker ACKs.
- **Kafka Messaging & Topic Design:**
  - 6 Kafka topics: `paycore.payment.events`, `paycore.refund.events`, `paycore.webhook.events`, and corresponding `.dlq` dead-letter queues.
  - Partition key set to aggregate ID (`paymentReference` / `refundReference`) to guarantee strict per-entity ordering.
- **Idempotent Inbox Consumers:**
  - `InboxEvent` table with unique constraint `(event_id, consumer_name)` acts as an atomic deduplication barrier.
  - Duplicate deliveries resulting from broker re-balances or network retries are detected and skipped without executing duplicate side effects.
- **Multi-Layer SSRF Defense:**
  - `WebhookSsrfValidator` verifies destination URLs before any HTTP socket connection.
  - Blocks loopback (`127.0.0.1`, `localhost`, `::1`), RFC-1918 private subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), link-local metadata (`169.254.0.0/16`), multicast, and HTTP redirects.
- **Canonical HMAC-SHA256 Signatures:**
  - Secrets generated with `whsec_` prefix and 32 bytes cryptographically secure entropy.
  - Outbound requests include `X-PayCore-Signature: t={timestamp},v1={hash}` and `X-PayCore-Event: {type}`.
  - Constant-time verification prevents timing attacks; 5-minute replay tolerance window.
  - Single-exposure secret policy: secrets are displayed only once upon generation; database stores reversible encryption for workers and SHA-256 for hashes.
- **Webhook Delivery Engine & Exponential Backoff:**
  - Non-blocking HTTP POST worker with execution latency tracking.
  - Failure classification: retryable (5xx, 429, timeouts) vs non-retryable (4xx client errors, SSRF rejections).
  - Exponential backoff schedule across 5 attempts: `5s, 30s, 120s, 900s, 3600s`.
  - Quarantine: exhausted attempts routed to `DEAD_LETTER` with manual admin replay.
- **Admin Governance & Telemetry:**
  - REST endpoints for metrics (`/api/v1/admin/webhooks/metrics`), Outbox monitoring and force republishing, Inbox consumer logs, and Dead-Letter recovery.

## 🧪 Previous Stage 5 Details

PayCore Stage 5 (Refund Management & Transaction History) is fully implemented, verified, and operational:

- **Automated Tests:** **64 passing tests** across unit, service, state machine, idempotency, concurrent load, and MockMvc integration tests (0 failures, 0 errors).
- **Refund State Machine & Strict Validation:** Full transition lifecycle (`REQUESTED` -> `PROCESSING` -> `SUCCESS`, `FAILED`, `UNKNOWN`) with non-positive amount rejection, currency mismatch rejection, and terminal state immutability.
- **Two-Phase Zero-DB Network I/O & Balance Reservation:**
  - *Phase 1 (DB TX):* Row-level pessimistic write lock on `Payment` (`@Lock(LockModeType.PESSIMISTIC_WRITE)`), checks `amount <= (original - refunded - reserved)`, atomically increments `amount_refund_reserved`, persists `Refund(PROCESSING)`, and commits transaction immediately.
  - *Network I/O (No DB TX):* Dispatches refund to payment provider completely outside any database transaction or connection lock.
  - *Phase 2 (DB TX):* Acquires lock, decrements `amount_refund_reserved`, increments `amount_refunded`, updates payment status to `PARTIALLY_REFUNDED` or `REFUNDED`, records provider refund ID, and commits.
  - *Network Timeout Protection:* On timeout or socket drop, the reserved balance **remains safely reserved** to prevent concurrent over-refunding while marked `UNKNOWN`, awaiting authoritative verification.
- **Refund Concurrency Proof:** Verified via `RefundConcurrencyIntegrationTest` with 10 simultaneous threads racing on a $100 payment attempting $20 refunds each: exactly 5 succeed ($100 settled), and 5 are safely rejected with zero over-refund.
- **Refund Idempotency:** Full `Idempotency-Key` support for refunds with SHA-256 canonical payload fingerprinting, returning cached results on duplicate submissions (`Idempotent-Replay: true`) and rejecting altered payloads with HTTP 409 Conflict.
- **Unified Transaction Ledger:** High-performance database-level `v_transactions` view combining payments and refunds into a single searchable, filterable, and paginated ledger via Spring Data JPA `@Entity @Immutable @Subselect`.
- **Indeterminate Refund Status Verification:** Active provider status queries (`POST /api/v1/refunds/{ref}/verify`) to reconcile `UNKNOWN` refunds without double-crediting or manual database edits.

## 🧪 Previous Stage 4 Details

PayCore Stage 4 (Idempotency, Concurrency & Payment Reliability) is fully implemented, verified, and operational:

- **Automated Tests:** 49 passing tests (Unit, Service, State Machine, Fingerprinting, Retry Executor, and MockMvc/H2 End-to-End Idempotency & Concurrency Integration tests).
- **Authoritative Idempotency Barrier:** Persistent MySQL composite unique constraint uk_merchant_idempotency_key on (merchant_id, idempotency_key) guarantees zero duplicate payments even under severe network replay or process crashes. Redis is strictly a fast-coordination layer and never the sole protection against duplicates.
- **6 Idempotency Scenarios Handled:**
  1. *First Request:* Fresh payment created, HTTP 201 Created.
  2. *Idempotent Replay:* Duplicate request with matching key & payload returns cached response with HTTP 200 OK and Idempotent-Replay: true header. No second provider charge.
  3. *Payload Conflict:* Same key with altered request payload rejected with HTTP 409 Conflict (IDEMPOTENCY_CONFLICT).
  4. *Concurrent Requests:* Parallel threads safely serialize via DB constraint + Redis lock; observing threads poll and return completed payment with HTTP 200 OK.
  5. *In-Flight Processing:* Active operations return HTTP 202 Accepted (IDEMPOTENCY_PROCESSING) without initiating duplicate provider calls.
  6. *24-Hour Expiration:* Expired keys for completed payments return the original transaction safely rather than double-charging.
- **Request Fingerprinting:** Deterministic SHA-256 canonical hashing across normalized monetary amounts, currency codes, payment methods, customer references, and descriptions.
- **Optimistic Locking:** JPA @Version column on Payment entity detects and rejects concurrent entity mutations with HTTP 409 Conflict (CONCURRENCY_CONFLICT).
- **Safe Query Retries vs. Charge Safety Invariant:** Strict distinction between safe read queries (status verification with exponential backoff) and unsafe charges (blind retries strictly prohibited on network timeouts to prevent duplicate charges).
- **Redis Coordination with Automated Circuit Breaker:** Safe Lua atomic lock release script with 30-second circuit breaker that trips automatically on Redis outage, falling back directly to database-level synchronization with zero duplicate risk.

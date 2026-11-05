# PAYCORE — 8-MILESTONE DEVELOPMENT ROADMAP

## 1. Roadmap Architecture
To build PayCore with uncompromising engineering quality, development proceeds through exactly eight incremental milestones. Each milestone delivers an independently verified, production-grade slice of the system with atomic testing, Git branches, PRs, and merges.

---

## 2. Milestone Breakdown

### Milestone 1: Project Foundation, Architecture, Repository Structure, Documentation, & CI/CD Setup
- **Objective:** Establish the clean project skeleton, package structure, build dependencies, database migrations/schema definitions, shared exceptions, standardized response envelopes, and automated CI pipelines for both repositories.
- **Backend Deliverables:**
  - Maven `pom.xml` configured with Java 17, Spring Boot 3.2.x, JPA, Hibernate, MySQL, Redis, Kafka, Actuator, SpringDoc OpenAPI.
  - Package structure: `config`, `security`, `common (exception, response, validation, util)`.
  - Base domain exception hierarchy and `GlobalExceptionHandler`.
  - `ApiResponse<T>`, `ErrorResponse`, and `PagedResponse<T>` envelopes.
  - GitHub Actions backend CI pipeline (`.github/workflows/backend-ci.yml`).
  - Dockerfile and `docker-compose.yml` for local service orchestration (MySQL, Redis, Kafka).
- **Frontend Deliverables:**
  - React + TypeScript + Vite project initialized with Tailwind CSS.
  - Shared layout, navbar, sidebar, route configuration (`/`, `/login`, `/register`, `/merchant/*`, `/admin/*`).
  - GitHub Actions frontend CI pipeline (`.github/workflows/frontend-ci.yml`).
  - Dockerfile for frontend production container.
- **Verification Criteria:**
  - `mvn clean compile test` executes successfully.
  - `npm run build` succeeds without TypeScript or linting errors.
  - GitHub Actions runs green on PR.

---

### Milestone 2: Authentication, Authorization, & Merchant Management
- **Objective:** Implement tenant identity, user registration, JWT authentication, RBAC (`ADMIN`, `MERCHANT`), and merchant API key generation and hashing.
- **Backend Deliverables:**
  - JPA entities: `User`, `Role`, `UserRole`, `Merchant`, `MerchantApiKey`.
  - Spring Security 6 filter chain with `JwtAuthenticationFilter` and `ApiKeyAuthenticationFilter`.
  - BCrypt password encoder.
  - Endpoints: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/merchants/profile`, `GET / POST /api/v1/merchants/api-keys`.
  - API Key generator producing `sk_live_...` with salted SHA-256 hash storage.
- **Frontend Deliverables:**
  - Public pages: Landing/Home, Login, Register, Forgot Password, Reset Password matching the visual direction from `paycore-ui-reference.png` (white background, bottom blue wave, circular blue gradient glow).
  - Auth context and Axios interceptors appending Bearer tokens.
  - Merchant portal shell with API Keys management view.
- **Verification Criteria:**
  - JWT issued upon valid credentials; 401 Unauthorized upon invalid credentials.
  - Protected endpoints reject unauthenticated requests.
  - API keys correctly authenticate `X-Api-Key` requests.

---

### Milestone 3: Core Payment Processing Engine & Provider Abstraction
- **Objective:** Build the core payment processing state machine, payment intent creation, provider SPI, and simulated payment provider with realistic failure modes.
- **Backend Deliverables:**
  - JPA entities: `Customer`, `Payment`, `PaymentAttempt`.
  - Finite State Machine enforcing valid transitions: `CREATED`, `PROCESSING`, `REQUIRES_ACTION`, `AUTHORIZED`, `SUCCESS`, `FAILED`, `CANCELLED`, `UNKNOWN`.
  - `PaymentProvider` SPI and `SimulatedPaymentProvider` with configurable latency and error codes.
  - Transaction boundary design: Short-lived DB transactions, remote HTTP calls executed outside `@Transactional`.
  - Endpoints: `POST /api/v1/payments`, `GET /api/v1/payments/{paymentId}`, `GET /api/v1/payments` (with filtering).
- **Frontend Deliverables:**
  - Merchant Dashboard: Metrics cards (Total Payments, Successful, Failed, Refunded), Payment Volume chart.
  - Create Payment modal/page (Order ID, Amount, Currency, Payment Method).
  - Payments list table with status badges (`SUCCESS` green, `PROCESSING` blue, `FAILED` red).
  - Payment Details view with interactive step-by-step state transition timeline.
- **Verification Criteria:**
  - Unit tests validating each valid and invalid state machine transition.
  - Simulated provider returns structured success/failure responses.
  - Non-transactional boundary test ensuring DB connections are not tied during provider latency.

---

### Milestone 4: Idempotency, Concurrency, & Payment Reliability
- **Objective:** Implement production-grade duplicate charge prevention using `Idempotency-Key`, Redis distributed locks, MySQL unique constraints, and provider timeout recovery.
- **Backend Deliverables:**
  - JPA entity: `IdempotencyRecord` with composite unique constraint `(merchant_id, idempotency_key)`.
  - Request fingerprinting: SHA-256 hash of HTTP method, URI, and body payload.
  - Two-tier concurrency control: Redis distributed lock (`SET resource_name uuid NX PX 10000`) + MySQL database constraint fallback.
  - Replay handling: Identical key with identical payload returns cached original response; identical key with differing payload returns HTTP 422 Unprocessable Entity.
  - Provider timeout handling: Network timeouts transition payment to `UNKNOWN` (HTTP 202 Accepted) instead of blindly retrying charges.
- **Frontend Deliverables:**
  - Auto-generated UUID idempotency keys on payment initiation forms.
  - Idempotency collision and timeout notification banners.
- **Verification Criteria:**
  - Multi-threaded concurrency test firing 50 simultaneous identical payment requests: Exactly 1 executes, 49 are safely deduplicated or rejected with 409 Conflict.
  - Key reuse test with altered amount returns 422 Unprocessable Entity.

---

### Milestone 5: Refund Management & Transaction History [COMPLETED]
- **Objective:** Build an enterprise-grade refund processing engine featuring full and partial refunds, remaining refundable balance calculations, pessimistic row locking on parent payments, in-flight balance reservation, Zero-DB Network I/O, refund idempotency with request fingerprinting, indeterminate status verification, and a unified immutable transaction history view.
- **Backend Deliverables:**
  - JPA entity: `Refund` with `refund_reference`, parent payment foreign key, `status` (`REQUESTED`, `PROCESSING`, `SUCCESS`, `FAILED`, `UNKNOWN`), optimistic locking `@Version`, and audit timestamps.
  - Parent `Payment` fields: `amountRefunded`, `amountRefundReserved`, and state transitions to `PARTIALLY_REFUNDED` and `REFUNDED`.
  - Two-Phase Zero-DB Network I/O & Balance Reservation: Phase 1 reserves funds and commits before external gateway call; Phase 2 finalizes state after provider returns; on timeouts, reserved funds stay locked to prevent race conditions.
  - Refund Idempotency: `Idempotency-Key` support with SHA-256 fingerprinting, cached replay (`Idempotent-Replay: true`), and conflict detection (HTTP 409).
  - Provider Abstraction: Added `initiateRefund` and `queryRefundStatus` to `PaymentProvider` and `SimulatedPaymentProvider` with deterministic triggers (`[SIM_FAIL]`, `[SIM_TIMEOUT]`).
  - Unified Transaction Ledger: `v_transactions` database-level view combining payments and refunds with `TransactionHistoryController` and `AdminTransactionHistoryController`.
  - Endpoints: `POST /api/v1/payments/{ref}/refunds`, `GET /api/v1/refunds`, `GET /api/v1/refunds/{ref}`, `GET /api/v1/payments/{ref}/refunds`, `GET /api/v1/payments/{ref}/refundable-amount`, `POST /api/v1/refunds/{ref}/verify`, `GET /api/v1/transactions`, and corresponding `/admin/*` endpoints.
- **Frontend Deliverables:**
  - Merchant Refunds page (`/merchant/refunds`): Metrics cards, filters, search, paginated table, details modal, and verify action.
  - Merchant Transactions page (`/merchant/transactions`): Unified ledger with transaction type pills, status badges, date range filter, and pagination.
  - In Payment Details: Live refundable balance widget, quick full/partial refund fill, full/partial warnings, and issued refund history table.
  - Admin Refunds (`/admin/refunds`) & Admin Transactions (`/admin/transactions`) pages.
  - Sidebar and router integration with clean active route styling.
- **Verification Criteria:**
  - Full test suite: 64 passing automated tests (0 failures, 0 errors).
  - 10-thread concurrency test on $100 payment attempting $20 refunds: exactly 5 succeed ($100 settled), 5 safely rejected.
  - TypeScript build (`tsc && vite build`) and ESLint checks pass with 0 errors, 0 warnings.

---

### Milestone 6: Webhooks, Kafka, & Asynchronous Processing [COMPLETED]
- **Objective:** Decouple event publishing from request handling using Apache Kafka and the Transactional Outbox pattern, implement merchant webhook subscription management with SSRF protections, canonical HMAC-SHA256 payload signing, idempotent consumer inboxes, exponential backoff retries, and dead-letter quarantine (DLQ) with admin governance.
- **Backend Deliverables:**
  - Transactional Outbox: `OutboxEvent` entity and `OutboxPublisherScheduler` guaranteeing atomic event persistence alongside entity mutations and at-least-once Kafka publication.
  - Apache Kafka topics: `paycore.payment.events`, `paycore.refund.events`, `paycore.webhook.events`, plus dedicated `.dlq` topics (`.dlq`).
  - Idempotent Inboxes: `inbox_events` table with unique constraint `(event_id, consumer_name)` preventing duplicate execution across `PaymentEventConsumer` and `RefundEventConsumer`.
  - Multi-Layer SSRF Defense: `WebhookSsrfValidator` rejecting loopback, RFC-1918, link-local metadata, multicast, and redirects.
  - Canonical HMAC-SHA256 Signatures: `X-PayCore-Signature: t={timestamp},v1={hash}` and `X-PayCore-Event` with constant-time verification.
  - Webhook Delivery Worker: HTTP POST worker with latency tracking, retry classification (retryable 5xx, 429 vs non-retryable 4xx, SSRF), exponential backoff (`5s, 30s, 120s, 900s, 3600s`), and dead-letter quarantine.
  - Endpoints: `POST /api/v1/webhooks/endpoints`, `GET /api/v1/webhooks/endpoints`, `PUT /api/v1/webhooks/endpoints/{ref}`, `PATCH /api/v1/webhooks/endpoints/{ref}/status`, `POST /api/v1/webhooks/endpoints/{ref}/rotate-secret`, `DELETE /api/v1/webhooks/endpoints/{ref}`, `GET /api/v1/webhooks/deliveries`, `POST /api/v1/webhooks/deliveries/{ref}/retry`, and comprehensive `/admin/webhooks/*` governance endpoints.
- **Frontend Deliverables:**
  - Merchant Webhooks page (`/merchant/webhooks`): Dual-tab interface for Endpoints (CRUD, active toggle, single-exposure secret reveal modal, secret rotation, copy guide) and Delivery History (attempt logs, status badges, inspection modal, manual retry).
  - Admin Webhook Pipeline Governance (`/admin/webhooks`): KPI telemetry cards, All Deliveries multi-tenant explorer, Dead-Letter Queue quarantine with Replay, Outbox monitor with Force Republish, and Inbox Consumer duplicate detection telemetry.
  - Fully wired routes, sidebar navigation, and 0-warning production bundle.
- **Verification Criteria:**
  - Unit and integration tests for signing, SSRF validation, endpoint management, delivery retries, and inbox idempotency.
  - TypeScript compilation and ESLint pass with 0 errors, 0 warnings.
  - Vite production bundle builds cleanly.

---

### Milestone 7: Reconciliation, Audit Logs, & Admin Dashboard
- **Objective:** Implement financial reconciliation between internal ledgers and provider records, comprehensive audit logging, and administrative oversight portals.
- **Backend Deliverables:**
  - JPA entities: `ReconciliationBatch`, `ReconciliationRecord`, `AuditLog`.
  - Reconciliation engine classifying: `MATCHED`, `AMOUNT_MISMATCH`, `STATUS_MISMATCH`, `MISSING_INTERNAL`, `MISSING_PROVIDER`.
  - Immutable audit logging interceptor capturing actor, action, IP address, and payload diffs.
  - Endpoints: `POST /api/v1/reconciliation/run`, `GET /api/v1/reconciliation/batches/{id}`, `GET /api/v1/admin/audit-logs`, `GET /api/v1/admin/dashboard/overview`.
- **Frontend Deliverables:**
  - Admin Portal: Dashboard with platform-wide KPIs, daily volume charts, active merchants.
  - Admin Reconciliation page: Matched / Mismatched metric cards, discrepancy explorer.
  - Admin Webhook Events explorer.
  - Admin Audit Logs view with searchable filter.
- **Verification Criteria:**
  - Reconciliation batch correctly flags intentionally introduced mismatch records.
  - Audit logs record all mutating payment, refund, and authentication actions.

---

### Milestone 8: Testing, Optimization, Deployment, & Final Documentation
- **Objective:** Comprehensive end-to-end integration testing, Testcontainers test suites, performance optimization (query indexing, connection pooling), Docker Compose packaging, and production documentation.
- **Backend Deliverables:**
  - End-to-end integration test suites with Testcontainers (MySQL, Redis, Kafka).
  - Actuator metrics verification and Prometheus scraper tuning.
  - Index tuning and query execution plan analysis (`EXPLAIN`).
- **Frontend Deliverables:**
  - Production build optimization (chunk splitting, bundle analysis).
  - Responsive layout validation across mobile and desktop viewports.
- **Documentation Deliverables:**
  - Finalized, verified documentation across all 13 project documents.
  - Complete Postman collection with example request payloads.
- **Verification Criteria:**
  - Full test suite passes cleanly in CI pipeline.
  - Clean local startup via `docker-compose up`.

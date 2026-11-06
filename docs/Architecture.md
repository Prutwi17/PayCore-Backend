# PAYCORE — BACKEND ARCHITECTURE SPECIFICATION

## 1. Architectural Style: Modular Monolith
PayCore is engineered as a **Modular Monolith**. Rather than introducing microservices prematurely—which brings operational overhead, distributed network failures, complex distributed transactions (Saga/2PC), and deployment complexity—PayCore enforces strict modular boundaries within a single deployable artifact.

Each domain module is designed with its own controllers, services, repositories, domain entities, DTOs, and event publishers. Cross-module communication is handled through defined internal service interfaces or Kafka domain events, allowing future decomposition into microservices without rewriting core business logic.

---

## 2. Package Structure

The backend source tree resides under `com.paycore`:

```
com.paycore
├── PayCoreApplication.java               # Application bootstrap entry point
├── config                                # Global configuration beans
│   ├── AppConfig.java                    # General application properties & beans
│   ├── JpaConfig.java                    # Auditing, transaction manager config
│   ├── RedisConfig.java                  # RedisTemplate, cache managers, lock beans
│   ├── KafkaConfig.java                  # Topic definitions, producer/consumer factories
│   ├── OpenApiConfig.java                # Swagger UI and OpenAPI 3.0 metadata
│   └── WebMvcConfig.java                 # CORS configuration, interceptors
├── security                              # Security infrastructure
│   ├── SecurityConfig.java               # Spring Security filter chain configuration
│   ├── JwtTokenProvider.java             # Token generation, claims validation
│   ├── JwtAuthenticationFilter.java      # Dashboard Bearer token interceptor
│   ├── ApiKeyAuthenticationFilter.java   # Merchant X-Api-Key interceptor
│   ├── CustomUserDetailsService.java     # User loading for authentication
│   ├── SecurityUserPrincipal.java        # Authenticated principal wrapper
│   └── RestAuthenticationEntryPoint.java # Unified 401/403 JSON error responder
├── common                                # Shared infrastructure components
│   ├── exception                         # Global exception handlers and domain exceptions
│   │   ├── GlobalExceptionHandler.java   # @RestControllerAdvice handling standard errors
│   │   ├── BaseException.java            # Abstract domain exception
│   │   ├── ResourceNotFoundException.java
│   │   ├── DuplicateResourceException.java
│   │   ├── IdempotencyViolationException.java
│   │   ├── InvalidPaymentStateException.java
│   │   ├── InsufficientRefundableAmountException.java
│   │   └── UnauthorizedException.java
│   ├── response                          # Standardized JSON response envelope
│   │   ├── ApiResponse.java              # Success wrapper: { success, message, data, timestamp }
│   │   ├── ErrorResponse.java            # Error wrapper: { success: false, error: { code, message, details } }
│   │   └── PagedResponse.java            # Pagination wrapper: { content, page, size, totalElements, totalPages }
│   ├── validation                        # Custom validation annotations and validators
│   │   └── ValidCurrency.java
│   └── util                              # Utility classes
│       ├── HashUtil.java                 # SHA-256 hashing for API keys and request fingerprints
│       ├── HmacUtil.java                 # HMAC-SHA256 signature calculations
│       └── IdGenerator.java              # Prefixed ID generation (e.g. PAY_..., REF_..., evt_...)
├── auth                                  # Authentication & registration module
│   ├── controller                        # /api/v1/auth/login, /api/v1/auth/register, /refresh
│   ├── service                           # AuthService, PasswordResetService
│   ├── dto                               # AuthRequest, AuthResponse, RegisterRequest, PasswordResetDto
│   └── repository                        # Token verification tokens (if stateful)
├── user                                  # System user identity
│   ├── entity                            # User, Role, UserRole
│   ├── repository                        # UserRepository, RoleRepository
│   ├── service                           # UserService
│   └── dto                               # UserDto, UserProfileDto
├── merchant                              # Merchant tenancy & credentials
│   ├── controller                        # /api/v1/merchants (registration, profile, settings)
│   │   └── MerchantApiKeyController.java # /api/v1/merchants/api-keys
│   ├── entity                            # Merchant, MerchantApiKey
│   ├── repository                        # MerchantRepository, MerchantApiKeyRepository
│   ├── service                           # MerchantService, MerchantApiKeyService
│   ├── dto                               # MerchantCreateDto, MerchantResponseDto, ApiKeyResponseDto
│   └── mapper                            # MerchantMapper
├── customer                              # End-customer profile
│   ├── entity                            # Customer
│   ├── repository                        # CustomerRepository
│   ├── service                           # CustomerService
│   └── dto                               # CustomerDto
├── payment                               # Core payment processing engine
│   ├── controller                        # /api/v1/payments (initiate, capture, get, list)
│   ├── service                           # PaymentService, PaymentProcessingService, PaymentAttemptService
│   ├── repository                        # PaymentRepository, PaymentAttemptRepository, IdempotencyRecordRepository
│   ├── entity                            # Payment, PaymentAttempt, IdempotencyRecord
│   ├── dto                               # CreatePaymentRequest, PaymentResponseDto, PaymentDetailDto
│   ├── mapper                            # PaymentMapper
│   └── provider                          # Downstream payment gateway abstraction
│       ├── PaymentProvider.java          # Provider interface: charge, verify, refund
│       ├── PaymentProviderResponse.java  # Normalized provider outcome
│       ├── SimulatedPaymentProvider.java # Default simulated provider with configurable latency & failure modes
│       └── ProviderFactory.java          # Factory resolving active provider instance
├── refund                                # Refund management module
│   ├── controller                        # /api/v1/refunds (initiate refund, query, list)
│   ├── service                           # RefundService, RefundValidationService
│   ├── repository                        # RefundRepository
│   ├── entity                            # Refund
│   ├── dto                               # CreateRefundRequest, RefundResponseDto
│   └── mapper                            # RefundMapper
├── webhook                               # Webhook ingestion and outbound dispatching
│   ├── controller                        # /api/v1/webhooks (endpoints config, delivery history)
│   ├── entity                            # WebhookEndpoint, WebhookEvent, WebhookDeliveryAttempt
│   ├── repository                        # WebhookEndpointRepository, WebhookEventRepository, WebhookDeliveryAttemptRepository
│   ├── service                           # WebhookEventPublisher, WebhookDispatcherService
│   ├── kafka                             # KafkaProducer, KafkaConsumer, WebhookRetryConsumer
│   └── dto                               # WebhookEndpointCreateDto, WebhookEventDto, WebhookDeliveryDto
├── reconciliation                        # Ledger & settlement reconciliation
│   ├── controller                        # /api/v1/reconciliation (trigger job, view reports)
│   ├── service                           # ReconciliationService, DiscrepancyClassifier
│   ├── repository                        # ReconciliationRecordRepository, ReconciliationBatchRepository
│   ├── entity                            # ReconciliationBatch, ReconciliationRecord
│   └── dto                               # ReconciliationReportDto, ReconciliationFilterDto
├── notification                          # Internal system alert notifications
│   ├── entity                            # Notification
│   ├── repository                        # NotificationRepository
│   └── service                           # NotificationService
├── audit                                 # System-wide immutable audit trail
│   ├── entity                            # AuditLog
│   ├── repository                        # AuditLogRepository
│   ├── service                           # AuditService, AuditEventListener
│   └── dto                               # AuditLogDto, AuditFilterDto
└── admin                                 # Platform administrative operations
    ├── controller                        # /api/v1/admin (overview metrics, merchants, global payments)
    ├── service                           # AdminAnalyticsService, PlatformMonitoringService
    └── dto                               # PlatformStatsDto, DailyVolumeDto
```

---

## 3. Layer Separation and Architectural Invariants

### 3.1 Controller Layer
- Responsibilities: HTTP protocol negotiation, query parameter extraction, HTTP status mapping, invoking validation annotations (`@Valid`), delegating to services.
- **Invariants:**
  - Controllers must NEVER contain business logic, calculations, or direct JPA repository references.
  - Controllers must return standardized response wrappers (`ApiResponse<T>`, `PagedResponse<T>`).
  - No database transactions open in the controller layer.

### 3.2 Service Layer
- Responsibilities: Business rules, state transitions, validation against domain rules, orchestrating provider interactions, managing transaction boundaries (`@Transactional`), firing domain events.
- **Invariants:**
  - Services must encapsulate all domain logic.
  - Transactions must be granular and strictly scoped.
  - External network calls (e.g. payment provider HTTP requests) must NEVER execute inside an active database transaction.

### 3.3 Persistence (Repository) Layer
- Responsibilities: CRUD operations, query specification (`JpaSpecificationExecutor`), custom native queries for reports, row locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`).
- **Invariants:**
  - Repositories return domain entities or query projections.
  - Entities must never be leaked across the controller boundary; DTO mappers must always be invoked.

---

## 4. Provider Abstraction Architecture

To ensure PayCore can integrate with any payment provider (e.g., Stripe, Adyen, Razorpay, or simulated gateways) without altering core payment orchestration, all provider interactions implement the `PaymentProvider` SPI:

```java
public interface PaymentProvider {
    String getProviderName();
    ProviderChargeResult initiateCharge(ProviderChargeRequest request);
    ProviderVerifyResult verifyPayment(String providerTransactionId);
    ProviderRefundResult processRefund(ProviderRefundRequest request);
}
```

The `SimulatedPaymentProvider` implements this interface with configurable behavior parameters:
- Success rate (default 95%)
- Configurable synthetic latency (default 300ms)
- Simulated timeout responses (testing the `UNKNOWN` state machine path)
- Error code mapping (e.g., `INSUFFICIENT_FUNDS`, `EXPIRED_CARD`, `DO_NOT_HONOR`).

---

## 5. Event-Driven Architecture & Webhook Engine (Stage 6)

### 5.1 Transactional Outbox Pattern
To guarantee **at-least-once** event publishing without dual-write inconsistencies between MySQL and Apache Kafka:
1. When a business state transition occurs (e.g., `PAYMENT_CREATED`, `PAYMENT_SUCCESS`, `REFUND_SUCCESS`), a canonical `OutboxEvent` record is inserted inside the **exact same ACID database transaction** as the entity update.
2. Kafka publication is completely isolated from user-facing HTTP request threads.
3. An `OutboxPublisherScheduler` polls for `PENDING` outbox records, publishes them to Kafka with the aggregate ID (`paymentReference` / `refundReference`) as the message key to maintain partition-level ordering, and marks them `PUBLISHED` upon broker acknowledgment.
4. If Kafka is unavailable, records remain safely in the `outbox_events` table and retry with exponential backoff.

### 5.2 Idempotent Inbox Consumer Pattern
Because distributed brokers guarantee **at-least-once** delivery, duplicate messages can arrive. To ensure zero duplicate side effects:
1. Every consumer (`PaymentEventConsumer`, `RefundEventConsumer`) records event reception into the `inbox_events` table under a strict composite unique constraint: `(event_id, consumer_name)`.
2. When a duplicate message is received, the atomic insertion detects the duplicate and short-circuits execution before applying any business action.
3. Once business processing succeeds, the record is transitioned to `PROCESSED`. If an unrecoverable error occurs, it is routed to the corresponding `.dlq` topic.

### 5.3 Webhook Dispatcher & Delivery Engine
1. **SSRF Guard:** Prior to making any outbound HTTP POST, `WebhookSsrfValidator` resolves the target hostname and blocks loopback addresses (`127.0.0.1`, `localhost`, `::1`), RFC-1918 private subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), link-local IPs, multicast, and redirects.
2. **HMAC-SHA256 Signatures:** Requests include standard signature headers:
   `X-PayCore-Signature: t={timestamp},v1={hash}` and `X-PayCore-Event: {eventType}`.
3. **Exponential Backoff & Dead-Letter Quarantine:** Failed deliveries (5xx, 429, timeouts) are scheduled across 5 retry intervals: `5s, 30s, 120s, 900s, 3600s`. After 5 failed attempts, deliveries are transitioned to `DEAD_LETTER` for admin recovery.

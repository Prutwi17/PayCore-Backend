# PAYCORE — DATABASE DESIGN & SCHEMA SPECIFICATION

## 1. Relational Database Overview
- **Database Engine:** MySQL 8.0+
- **Storage Engine:** InnoDB (Row-level locking, ACID transactions, Foreign key constraints)
- **Character Set & Collation:** `utf8mb4` / `utf8mb4_unicode_ci`
- **Monetary Policy:** All monetary amounts MUST use `DECIMAL(19,4)` or `DECIMAL(12,2)`. Under no circumstances will floating-point (`FLOAT`, `DOUBLE`) types be utilized for financial values to prevent precision loss.

---

## 2. Table Specifications

### 2.1 Table: `users`
Stores system accounts for Admins and Merchant portal operators.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | PRIMARY KEY | Unique user ID |
| `username` | VARCHAR(100) | NOT NULL, UNIQUE | User login handle |
| `email` | VARCHAR(255) | NOT NULL, UNIQUE | Unique user email |
| `password_hash` | VARCHAR(255) | NOT NULL | BCrypt hashed password |
| `full_name` | VARCHAR(150) | NOT NULL | Full name of user |
| `merchant_id` | BIGINT | NULL, FK(`merchants.id`) | Tenant ID (NULL for ADMIN) |
| `status` | VARCHAR(30) | NOT NULL DEFAULT 'ACTIVE' | `ACTIVE`, `SUSPENDED`, `LOCKED` |
| `created_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Audit creation time |
| `updated_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | Audit update time |

*Indexes:*
- `idx_users_email` ON `users(email)`
- `idx_users_merchant_id` ON `users(merchant_id)`

---

### 2.2 Table: `roles` & `user_roles`
Defines RBAC permission groups.

| Table | Column | Type | Constraints |
|---|---|---|---|
| `roles` | `id` | BIGINT AUTO_INCREMENT | PRIMARY KEY |
| `roles` | `name` | VARCHAR(50) | NOT NULL, UNIQUE (`ROLE_ADMIN`, `ROLE_MERCHANT`, `ROLE_CUSTOMER`) |
| `user_roles` | `user_id` | BIGINT | NOT NULL, FK(`users.id`) ON DELETE CASCADE |
| `user_roles` | `role_id` | BIGINT | NOT NULL, FK(`roles.id`) ON DELETE CASCADE |
| `user_roles` | - | - | PRIMARY KEY (`user_id`, `role_id`) |

---

### 2.3 Table: `merchants`
Tenant profile representing registered businesses.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | PRIMARY KEY | Internal merchant ID |
| `merchant_code` | VARCHAR(64) | NOT NULL, UNIQUE | Business reference (e.g. `mer_8f2301`) |
| `business_name` | VARCHAR(200) | NOT NULL | Registered company / brand name |
| `business_email` | VARCHAR(255) | NOT NULL | Contact email for billing |
| `status` | VARCHAR(30) | NOT NULL DEFAULT 'ACTIVE' | `PENDING`, `ACTIVE`, `SUSPENDED` |
| `webhook_secret` | VARCHAR(128) | NULL | Default signing key for webhooks |
| `created_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Creation timestamp |
| `updated_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | Update timestamp |

*Indexes:*
- `idx_merchants_code` ON `merchants(merchant_code)`
- `idx_merchants_status` ON `merchants(status)`

---

### 2.4 Table: `merchant_api_keys`
API credentials for merchant backend integration.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | PRIMARY KEY | Unique API key record ID |
| `merchant_id` | BIGINT | NOT NULL, FK(`merchants.id`) | Owning merchant |
| `key_prefix` | VARCHAR(16) | NOT NULL | Prefix for identification (e.g. `sk_live_...` or `pk_live_...`) |
| `key_hash` | VARCHAR(255) | NOT NULL, UNIQUE | Salted SHA-256 hash of API secret |
| `key_type` | VARCHAR(30) | NOT NULL | `SECRET`, `PUBLISHABLE` |
| `environment` | VARCHAR(20) | NOT NULL DEFAULT 'TEST' | `TEST`, `LIVE` |
| `status` | VARCHAR(30) | NOT NULL DEFAULT 'ACTIVE' | `ACTIVE`, `REVOKED`, `EXPIRED` |
| `expires_at` | DATETIME(6) | NULL | Optional key expiry date |
| `last_used_at` | DATETIME(6) | NULL | Timestamp of last API invocation |
| `created_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Creation timestamp |

*Indexes:*
- `idx_api_keys_hash` ON `merchant_api_keys(key_hash)`
- `idx_api_keys_merchant` ON `merchant_api_keys(merchant_id)`

---

### 2.5 Table: `customers`
Customer entity representing payment payers.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | PRIMARY KEY | Internal customer ID |
| `customer_code` | VARCHAR(64) | NOT NULL, UNIQUE | Customer public ID (`cus_...`) |
| `merchant_id` | BIGINT | NOT NULL, FK(`merchants.id`) | Tenant owning customer relationship |
| `email` | VARCHAR(255) | NOT NULL | Customer email |
| `phone` | VARCHAR(30) | NULL | Contact phone number |
| `name` | VARCHAR(150) | NULL | Customer full name |
| `created_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Creation timestamp |
| `updated_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | Update timestamp |

*Indexes:*
- `uq_merchant_customer_email` UNIQUE (`merchant_id`, `email`)
- `idx_customers_code` ON `customers(customer_code)`

---

### 2.6 Table: `payments`
Core ledger representing a payment request and its authoritative lifecycle state.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | PRIMARY KEY | Internal DB surrogate key |
| `payment_id` | VARCHAR(64) | NOT NULL, UNIQUE | Public external ID (e.g. `PAY_8F2301`) |
| `merchant_id` | BIGINT | NOT NULL, FK(`merchants.id`) | Merchant owning this transaction |
| `customer_id` | BIGINT | NULL, FK(`customers.id`) | Payer customer record |
| `order_id` | VARCHAR(128) | NOT NULL | Merchant order reference (e.g. `ORD-1001`) |
| `amount` | DECIMAL(19,4) | NOT NULL | Transaction principal amount |
| `amount_refunded` | DECIMAL(19,4) | NOT NULL DEFAULT 0.0000 | Cumulative settled refunded amount |
| `amount_refund_reserved` | DECIMAL(19,4) | NOT NULL DEFAULT 0.0000 | In-flight active reserved refund balance (concurrency protection) |
| `currency` | VARCHAR(3) | NOT NULL | ISO 4217 Currency Code (`INR`, `USD`, `EUR`) |
| `status` | VARCHAR(32) | NOT NULL | State machine status: `CREATED`, `PROCESSING`, `REQUIRES_ACTION`, `AUTHORIZED`, `SUCCESS`, `PARTIALLY_REFUNDED`, `REFUNDED`, `FAILED`, `CANCELLED`, `UNKNOWN` |
| `payment_method` | VARCHAR(32) | NOT NULL | `CARD`, `UPI`, `NET_BANKING`, `WALLET` |
| `payment_provider` | VARCHAR(64) | NOT NULL DEFAULT 'SIMULATED' | Provider adapter name |
| `provider_transaction_id`| VARCHAR(128) | NULL | External provider reference code |
| `error_code` | VARCHAR(64) | NULL | Failure categorization code |
| `error_message` | VARCHAR(500) | NULL | Failure description |
| `version` | BIGINT | NOT NULL DEFAULT 0 | Optimistic concurrency locking |
| `created_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Payment creation timestamp |
| `updated_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | Status change timestamp |

*Indexes:*
- `idx_payments_payment_id` ON `payments(payment_id)`
- `idx_payments_merchant_created` ON `payments(merchant_id, created_at DESC)`
- `idx_payments_merchant_order` ON `payments(merchant_id, order_id)`
- `idx_payments_status` ON `payments(status)`
- `idx_payments_provider_tx` ON `payments(provider_transaction_id)`

---

### 2.7 Table: `payment_attempts`
Captures every individual authorization/charge attempt against downstream providers.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | PRIMARY KEY | Internal attempt ID |
| `attempt_id` | VARCHAR(64) | NOT NULL, UNIQUE | External attempt ID (`att_...`) |
| `payment_id` | BIGINT | NOT NULL, FK(`payments.id`) | Parent payment |
| `attempt_number` | INT | NOT NULL | Sequential attempt counter (1, 2, 3...) |
| `status` | VARCHAR(32) | NOT NULL | `INITIATED`, `SUCCESS`, `FAILED`, `TIMEOUT` |
| `provider_transaction_id`| VARCHAR(128) | NULL | Downstream gateway reference |
| `raw_request_log` | TEXT | NULL | Sanitized provider request |
| `raw_response_log`| TEXT | NULL | Sanitized provider response |
| `response_code` | VARCHAR(64) | NULL | Gateway response code |
| `created_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Attempt timestamp |

*Indexes:*
- `idx_payment_attempts_payment` ON `payment_attempts(payment_id)`

---

### 2.8 Table: idempotency_records
Persistent authoritative storage for duplicate payment prevention, request fingerprinting, and cached response replaying.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | BIGINT AUTO_INCREMENT | PRIMARY KEY | Record surrogate ID |
| merchant_id | BIGINT | NOT NULL, FK(merchants.id) | Merchant tenancy boundary |
| idempotency_key | VARCHAR(128) | NOT NULL | Client-supplied key (1-128 chars) |
| 
equest_fingerprint | VARCHAR(64) | NOT NULL | SHA-256 canonical hash of normalized request payload |
| payment_reference | VARCHAR(64) | NULL | Associated payment reference (pay_...) |
| 
esponse_status | INT | NULL | HTTP response status code (e.g. 201 Created, 200 OK) |
| 
esponse_body | TEXT | NULL | Serialized JSON response payload returned to client |
| processing_status | VARCHAR(20) | NOT NULL | Lifecycle: PENDING, COMPLETED, FAILED |
| created_at | DATETIME(6) | NOT NULL | Initial request receipt timestamp |
| updated_at | DATETIME(6) | NOT NULL | State transition timestamp |
| expires_at | DATETIME(6) | NOT NULL | Key expiration timestamp (default 24h retention) |

*Constraints & Indexes:*
- uk_merchant_idempotency_key UNIQUE (merchant_id, idempotency_key) — Primary concurrency barrier enforcing zero duplicate transactions
- idx_idempotency_expires_at ON idempotency_records(expires_at) — Index for background TTL expiration cleanup
- idx_idempotency_payment_ref ON idempotency_records(payment_reference) — Fast lookup by payment reference
---

### 2.9 Table: `refunds`
Authoritative ledger for full and partial refund transactions, lifecycle state machine tracking, and provider settlement reference recording.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | PRIMARY KEY | Internal DB surrogate key |
| `refund_reference` | VARCHAR(64) | NOT NULL, UNIQUE | Public external refund reference (`ref_...`) |
| `payment_id` | BIGINT | NOT NULL, FK(`payments.id`) | Associated parent payment |
| `merchant_id` | BIGINT | NOT NULL, FK(`merchants.id`) | Tenant owning this refund transaction |
| `amount` | DECIMAL(19,4) | NOT NULL | Refund principal amount requested |
| `currency` | VARCHAR(3) | NOT NULL | ISO 4217 currency code (strictly matches parent payment) |
| `reason` | VARCHAR(255) | NULL | Refund motivation / merchant memo |
| `status` | VARCHAR(32) | NOT NULL | State machine status: `REQUESTED`, `PROCESSING`, `SUCCESS`, `FAILED`, `UNKNOWN` |
| `provider_refund_id`| VARCHAR(128) | NULL | External payment provider refund identifier |
| `failure_code` | VARCHAR(64) | NULL | Standardized error categorization code |
| `failure_message` | VARCHAR(500) | NULL | Provider / validation failure explanation |
| `requested_at` | DATETIME(6) | NOT NULL | Initial submission timestamp |
| `processed_at` | DATETIME(6) | NULL | Final terminal settlement timestamp |
| `version` | BIGINT | NOT NULL DEFAULT 0 | Optimistic concurrency control version |
| `created_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Creation timestamp |
| `updated_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | State transition timestamp |

*Indexes & Constraints:*
- `uk_refunds_refund_ref` UNIQUE (`refund_reference`)
- `idx_refunds_payment_id` ON `refunds(payment_id)`
- `idx_refunds_merchant_created` ON `refunds(merchant_id, created_at DESC)`
- `idx_refunds_status` ON `refunds(status)`
- `idx_refunds_provider_ref` ON `refunds(provider_refund_id)`

---

### 2.10 View: `v_transactions` (Unified Transaction Ledger)
An immutable database-level view (`UNION ALL` across `payments` and `refunds`) providing high-performance query execution, pagination, sorting, and multi-field filtering for merchant and administrative transaction ledgers without requiring memory-heavy application joins.

```sql
CREATE OR REPLACE VIEW v_transactions AS
SELECT
    CONCAT('PAY_', p.id) AS transaction_id,
    p.payment_reference AS transaction_reference,
    p.payment_reference AS parent_reference,
    p.merchant_id AS merchant_id,
    'PAYMENT' AS transaction_type,
    p.amount AS amount,
    p.currency AS currency,
    p.status AS status,
    p.description AS description,
    p.created_at AS created_at,
    p.processed_at AS processed_at
FROM payments p
UNION ALL
SELECT
    CONCAT('REF_', r.id) AS transaction_id,
    r.refund_reference AS transaction_reference,
    p.payment_reference AS parent_reference,
    r.merchant_id AS merchant_id,
    'REFUND' AS transaction_type,
    r.amount AS amount,
    r.currency AS currency,
    r.status AS status,
    r.reason AS description,
    r.created_at AS created_at,
    r.processed_at AS processed_at
FROM refunds r
JOIN payments p ON r.payment_id = p.id;
```

*Subselect Mapping:* Mapped in Spring Data JPA as an `@Entity` annotated with `@Immutable` and `@Subselect("SELECT ... FROM payments UNION ALL SELECT ... FROM refunds")` for fast pageable queries.

---

### 2.10 Table: `webhook_endpoints`
Stores merchant webhook listener configurations.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | PRIMARY KEY | Endpoint ID |
| `merchant_id` | BIGINT | NOT NULL, FK(`merchants.id`) | Owning merchant |
| `url` | VARCHAR(1024) | NOT NULL | Target HTTPS callback URL |
| `secret_key` | VARCHAR(128) | NOT NULL | HMAC signing secret (`whsec_...`) |
| `subscribed_events` | JSON | NOT NULL | Array of subscribed event names |
| `status` | VARCHAR(30) | NOT NULL DEFAULT 'ACTIVE' | `ACTIVE`, `DISABLED` |
| `created_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Creation timestamp |

---

### 2.11 Table: `webhook_events`
Immutable record of all published domain events.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | PRIMARY KEY | Internal event ID |
| `event_id` | VARCHAR(64) | NOT NULL, UNIQUE | Public event ID (`evt_1F3D20`) |
| `merchant_id` | BIGINT | NOT NULL, FK(`merchants.id`) | Target merchant |
| `event_type` | VARCHAR(64) | NOT NULL | e.g. `payment.success`, `refund.processed` |
| `payload` | LONGTEXT | NOT NULL | Full serialized event JSON |
| `created_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Event creation timestamp |

*Indexes:*
- `idx_webhook_events_merchant` ON `webhook_events(merchant_id, created_at DESC)`
- `idx_webhook_events_type` ON `webhook_events(event_type)`

---

### 2.12 Table: `webhook_delivery_attempts`
Tracks individual HTTP POST attempts to merchant endpoints.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | PRIMARY KEY | Attempt ID |
| `event_id` | BIGINT | NOT NULL, FK(`webhook_events.id`) | Associated event |
| `endpoint_id` | BIGINT | NOT NULL, FK(`webhook_endpoints.id`) | Destination URL config |
| `attempt_number` | INT | NOT NULL | Attempt counter (1 to 5) |
| `response_status` | INT | NULL | HTTP response code (e.g. 200, 500) |
| `response_body` | TEXT | NULL | Truncated response snippet |
| `status` | VARCHAR(32) | NOT NULL | `PENDING`, `SUCCESS`, `RETRYING`, `FAILED_DLQ` |
| `next_retry_at` | DATETIME(6) | NULL | Timestamp for scheduled retry |
| `created_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Dispatch timestamp |

---

### 2.13 Tables: `reconciliation_batches` & `reconciliation_records`
Manages daily settlement reconciliation audits.

| Table | Column | Type | Constraints |
|---|---|---|---|
| `reconciliation_batches` | `id` | BIGINT AUTO_INCREMENT | PRIMARY KEY |
| `reconciliation_batches` | `batch_id` | VARCHAR(64) | NOT NULL, UNIQUE (`rec_...`) |
| `reconciliation_batches` | `provider_name` | VARCHAR(64) | NOT NULL |
| `reconciliation_batches` | `total_records` | INT | NOT NULL DEFAULT 0 |
| `reconciliation_batches` | `matched_count` | INT | NOT NULL DEFAULT 0 |
| `reconciliation_batches` | `mismatched_count` | INT | NOT NULL DEFAULT 0 |
| `reconciliation_batches` | `status` | VARCHAR(32) | NOT NULL (`RUNNING`, `COMPLETED`, `FAILED`) |
| `reconciliation_batches` | `created_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) |
| `reconciliation_records` | `id` | BIGINT AUTO_INCREMENT | PRIMARY KEY |
| `reconciliation_records` | `batch_id` | BIGINT | NOT NULL, FK(`reconciliation_batches.id`) |
| `reconciliation_records` | `payment_id` | VARCHAR(64) | NULL |
| `reconciliation_records` | `provider_tx_id`| VARCHAR(128) | NULL |
| `reconciliation_records` | `discrepancy_type`| VARCHAR(64) | `MATCHED`, `AMOUNT_MISMATCH`, `STATUS_MISMATCH`, `MISSING_INTERNAL`, `MISSING_PROVIDER` |
| `reconciliation_records` | `internal_amount` | DECIMAL(19,4) | NULL |
| `reconciliation_records` | `provider_amount` | DECIMAL(19,4) | NULL |
| `reconciliation_records` | `internal_status` | VARCHAR(32) | NULL |
| `reconciliation_records` | `provider_status` | VARCHAR(32) | NULL |

---

### 2.14 Table: `audit_logs`
Immutable system audit trail.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | PRIMARY KEY | Log ID |
| `actor_id` | VARCHAR(64) | NOT NULL | User ID or API Key identifier |
| `actor_type` | VARCHAR(32) | NOT NULL | `USER`, `MERCHANT_API`, `SYSTEM` |
| `action` | VARCHAR(64) | NOT NULL | e.g. `PAYMENT_INITIATED`, `REFUND_ISSUED`, `MERCHANT_UPDATED` |
| `resource_type` | VARCHAR(64) | NOT NULL | `PAYMENT`, `REFUND`, `MERCHANT`, `API_KEY` |
| `resource_id` | VARCHAR(64) | NOT NULL | Affected resource ID |
| `ip_address` | VARCHAR(45) | NULL | Client IP address |
| `changes_json` | JSON | NULL | State changes (before & after) |
| `created_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Event timestamp |

---

### 2.15 Table: `notifications`
In-app notifications for portal operators.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | PRIMARY KEY | Notification ID |
| `recipient_id` | BIGINT | NOT NULL | User ID receiving notification |
| `title` | VARCHAR(200) | NOT NULL | Short subject |
| `message` | TEXT | NOT NULL | Detail text |
| `type` | VARCHAR(32) | NOT NULL | `INFO`, `WARNING`, `CRITICAL` |
| `is_read` | BOOLEAN | NOT NULL DEFAULT FALSE | Read indicator |
| `created_at` | DATETIME(6) | NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | Timestamp |

---

## 3. Transaction Boundaries & Concurrency Rules

1. **Short-Lived Transactions:** Database transactions must NEVER remain open during network I/O with external payment providers or Kafka producers.
2. **Refund Isolation:** Issuing a refund requires pessimistic row locking (`SELECT * FROM payments WHERE id = ? FOR UPDATE`) to prevent race conditions during concurrent refund calculations.
3. **Idempotency Insertion:** The idempotency record must be stored in the same transaction that finalizes the payment or refund state, guaranteeing consistent replay of responses.

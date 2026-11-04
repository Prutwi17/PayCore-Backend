# PAYCORE — SECURITY DESIGN & THREAT MITIGATION SPECIFICATION

## 1. Security Architecture Principles
PayCore operates under the **Defense-in-Depth** and **Least-Privilege** security paradigms. Because payment infrastructure represents a high-value target, security is treated as a foundational non-functional requirement rather than an afterthought.

---

## 2. Authentication & Credential Management

### 2.1 User Authentication (Dashboard Portal)
- **Mechanism:** Stateless JSON Web Tokens (JWT) signed using HMAC-SHA256 (`HS256`).
- **Token Structure:**
  - `sub`: User ID
  - `roles`: Assigned authority array (e.g. `["ROLE_MERCHANT"]`)
  - `merchantId`: Tenant identifier (for merchant users)
  - `iat`, `exp`: Issued-at and expiration (Access token lifetime: 60 minutes)
- **Password Security:**
  - Hashing: `BCryptPasswordEncoder` with strength factor 12.
  - Password complexity: Minimum 8 characters, at least 1 uppercase letter, 1 lowercase letter, 1 digit, and 1 special symbol.

### 2.2 Merchant API Key Authentication (Programmatic Integration)
- **Key Architecture:**
  - **Publishable Key (`pk_live_...`, `pk_test_...`):** For client-side checkout initiation.
  - **Secret Key (`sk_live_...`, `sk_test_...`):** For backend server-to-server calls (`POST /payments`, `POST /refunds`).
- **Key Generation & Storage:**
  - Generated using `java.security.SecureRandom` (256-bit cryptographically secure entropy).
  - Secret keys are displayed to the merchant **EXACTLY ONCE** upon generation.
  - PayCore database stores only the salted **SHA-256 hash** (`key_hash`) and an un-hashed prefix (`key_prefix`, e.g. `sk_live_8f23`).
  - Upon incoming HTTP request, the filter extracts `X-Api-Key`, hashes it via SHA-256, and queries `merchant_api_keys WHERE key_hash = ? AND status = 'ACTIVE'`.

---

## 3. Authorization & Tenant Isolation (RBAC)

1. **Role Enforcement:**
   - Evaluated using Spring Security method security annotations (`@PreAuthorize("hasRole('ADMIN')")`).
   - Admin routes (`/api/v1/admin/**`) strictly accessible only to `ROLE_ADMIN`.
2. **Tenant Scoping (Data Isolation):**
   - Every merchant request is strictly scoped by `merchant_id`.
   - Repositories enforce tenant filtering:
     `paymentRepository.findByPaymentIdAndMerchantId(paymentId, authenticatedMerchantId)`
   - Cross-tenant data leakage is structurally impossible: Even if a merchant guesses another merchant's `payment_id`, the database query returns 404/Empty.

---

## 4. Webhook Security (Outbound HMAC-SHA256 Signatures)

To prevent spoofing or tampering of outbound webhooks, PayCore signs every delivery:
- **Header:** `X-PayCore-Signature: t={timestamp},v1={signature}`
- **Signing Algorithm:**
  ```
  signature = HMAC_SHA256(secretKey, timestamp + "." + payloadJson)
  ```
- **Replay Attack Prevention:** Receiving merchants verify that `|currentTime - timestamp| < 300 seconds` (5 minutes tolerance) to discard replayed transmissions.

---

## 5. Sensitive Data & PCI DSS Scope Minimization

- **Explicit Statement:** PayCore does NOT store raw Primary Account Numbers (PAN), CVVs, or expiration dates.
- In simulated mode and future integrations, tokenization or dummy tokens (`tok_card_visa_4242`) are used.
- Logging filters sanitize all HTTP request/response payloads before writing to disk or console. Sensitive keywords (`password`, `token`, `secret`, `cvv`, `cardNumber`) are masked as `[REDACTED]`.

---

## 6. Secrets & Environment Configuration

Zero hardcoded credentials in source code. All secrets are injected through OS environment variables or Docker secret stores:

| Variable | Purpose | Default / Dev Fallback |
|---|---|---|
| `PAYCORE_DB_URL` | MySQL Connection URL | `jdbc:mysql://localhost:3306/paycore_db` |
| `PAYCORE_DB_USERNAME` | Database User | `paycore_user` |
| `PAYCORE_DB_PASSWORD` | Database Password | Must be injected via environment |
| `PAYCORE_JWT_SECRET` | 256-bit signing key | Must be injected via environment |
| `PAYCORE_REDIS_HOST` | Redis host | `localhost` |
| `PAYCORE_REDIS_PORT` | Redis port | `6379` |
| `PAYCORE_KAFKA_BROKERS`| Kafka bootstrap | `localhost:9092` |

---

## 7. Cross-Origin Resource Sharing (CORS)

- Specific allowed origins configured via application properties (e.g. `http://localhost:5173` for Vite frontend).
- Allowed methods: `GET, POST, PUT, DELETE, OPTIONS`.
- Allowed headers: `Authorization, Content-Type, X-Api-Key, Idempotency-Key, X-Requested-With`.
- Credentials allowed (`allowCredentials: true`).

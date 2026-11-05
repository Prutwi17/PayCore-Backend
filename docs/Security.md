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

---

## 8. Multi-Tenant Tenancy Isolation & Financial Race Condition Prevention

### 8.1 Tenant Scoping & Resource Ownership
- **Authorization Enforcement:** Every merchant request operates under an authenticated security context. The active `merchantId` is extracted securely from the verified JWT claims or API key record.
- **Enumeration Prevention:** Attempting to query, refund, or reconcile a payment or refund belonging to another merchant returns `404 Not Found` (rather than 403 Forbidden) to eliminate resource ID enumeration.
- **Administrative Governance:** System-wide queries and actions (`/api/v1/admin/*`) require authenticated users holding explicit `ROLE_ADMIN` authorities verified via Spring Security method and route guards.

### 8.2 Concurrency Exploits & Over-Refund Prevention
- **Race Condition Immunity:** Concurrent refund requests attempting to collectively drain more than the payment's refundable balance are serialized using pessimistic write locks (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on the parent payment row.
- **In-Flight Balance Reservation:** Available balance is decremented immediately via `payment.amountRefundReserved` during Phase 1 before calling external providers, eliminating race windows.

---

## 9. Server-Side Request Forgery (SSRF) & Webhook Security Architecture (Stage 6)

### 9.1 Multi-Layer SSRF Defense
Because merchants register arbitrary HTTP/HTTPS callback URLs, PayCore implements strict multi-layer SSRF validation in `WebhookSsrfValidator`:
1. **Protocol Restriction:** Only `https` (and optionally `http` for local testing via configuration) schemes are permitted. File, FTP, Gopher, and raw IP protocols are strictly rejected.
2. **DNS Resolution & Rebinding Mitigation:** Target hostnames are resolved to all bound `InetAddress` entries immediately prior to request execution.
3. **Blacklisted IP Ranges:** Outbound requests are blocked if destination addresses fall into:
   - **Loopback:** `127.0.0.0/8`, `::1`, `localhost`
   - **RFC-1918 Private Ranges:** `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`
   - **Link-Local & Cloud Metadata:** `169.254.0.0/16` (AWS/GCP/Azure IMDS metadata endpoints)
   - **Multicast / Anycast / Carrier-Grade NAT:** `224.0.0.0/4`, `100.64.0.0/10`
4. **Disabled Redirects:** `HttpClient` is configured with `Redirect.NEVER` to prevent open-redirect SSRF bypasses.

### 9.2 Webhook Signing Secret Governance
- **Generation:** Secrets are generated using cryptographically secure random bytes: `whsec_` + 64 hex characters (32 bytes entropy).
- **Single-Exposure Policy:** Plaintext secrets are revealed **only once** upon creation or secret rotation.
- **Storage:** Database stores a SHA-256 hash for integrity validation and reversible encryption for delivery worker HMAC computation. In all GET APIs, secrets are masked (`whsec_****************`).
- **Signature Verification:** PayCore computes canonical signatures:
  `X-PayCore-Signature: t={timestamp},v1={HMAC-SHA256(secret, timestamp + "." + body)}`.
  Constant-time string comparison (`MessageDigest.isEqual`) is enforced to prevent timing attacks.
- **Replay & Tampering Protection:** Idempotency keys are bound to canonical SHA-256 payload fingerprints. Key reuse with modified amounts or parameters is actively rejected with HTTP 409 Conflict.

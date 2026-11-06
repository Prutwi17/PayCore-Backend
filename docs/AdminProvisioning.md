# PayCore — Initial Administrator Onboarding & Provisioning Architecture

## 1. Zero-Trust Admin Provisioning Overview

In production payment systems, administrative accounts possess high-privilege access: inspecting merchant ledgers, resolving reconciliation discrepancies, managing webhook endpoints, and auditing audit trails.

To ensure zero-trust security:
1. **No Hardcoded Admin Passwords:** Production code and seed scripts contain no pre-set administrative passwords.
2. **Strict Email Identity Binding:** Initial administrator setup is strictly bound to the verified system owner email: `omp27614@gmail.com`.
3. **No Public Admin Registration:** The public `/api/v1/auth/register` endpoint strictly assigns `ROLE_MERCHANT`. Administrative roles cannot be registered through public forms.
4. **Email-Proof Required:** An admin account can only be established after proving control over `omp27614@gmail.com` via a cryptographic token sent through the Gmail API.
5. **One-Time Provisioning Lockout:** Once an administrator is provisioned, the initial onboarding flow is permanently locked out. Subsequent administrators can only be created by an authenticated administrator.

---

## 2. Admin Setup Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor Admin as System Owner (omp27614@gmail.com)
    participant Frontend as PayCore React UI (/admin-setup)
    participant Backend as AuthController / AuthService
    participant DB as MySQL Database
    participant Gmail as Gmail REST API
    participant Audit as AuditLogService

    Admin->>Frontend: Visit /admin-setup
    Admin->>Frontend: Click "Request Admin Onboarding Link" (email: omp27614@gmail.com)
    Frontend->>Backend: POST /api/v1/auth/admin/initiate-setup {email: "omp27614@gmail.com"}
    Backend->>DB: Check if any user with ROLE_ADMIN exists
    alt Admin already exists
        Backend-->>Frontend: 400 Bad Request (ADMIN_ALREADY_PROVISIONED)
    else Email does not match omp27614@gmail.com
        Backend-->>Frontend: 400 Bad Request (INVALID_ADMIN_EMAIL)
    else Allowed to initiate
        Backend->>Backend: Generate Secure UUID Setup Token
        Backend->>DB: Save AdminSetupToken (token_hash=SHA256(token), expires_at=+24h)
        Backend->>Gmail: Send "Administrator Setup Invitation" Email
        Backend-->>Frontend: 200 OK ("Setup link dispatched to omp27614@gmail.com")
        Frontend->>Admin: Show confirmation banner
    end

    Note over Admin,Gmail: Admin checks Gmail inbox and clicks setup link
    Admin->>Frontend: Open /admin-setup?token=RAW_SETUP_TOKEN
    Frontend->>Admin: Show "Set Master Password" Form
    Admin->>Frontend: Enter Master Password & Confirm
    Frontend->>Backend: POST /api/v1/auth/admin/complete-setup {token, password, confirmPassword}
    Backend->>Backend: Compute SHA-256(token)
    Backend->>DB: Lookup AdminSetupToken by token_hash
    alt Token invalid, expired, or already consumed
        Backend-->>Frontend: 400 Bad Request (INVALID_OR_EXPIRED_TOKEN)
    else Token valid
        Backend->>DB: Create or activate User (email="omp27614@gmail.com", role=ROLE_ADMIN, email_verified=true)
        Backend->>DB: Mark token consumed_at = NOW()
        Backend->>Audit: Log ADMIN_PROVISIONED event
        Backend-->>Frontend: 200 OK ("Administrator successfully provisioned.")
        Frontend->>Admin: Redirect to /login
    end
```

---

## 3. Database Schema: `admin_setup_tokens`

```sql
CREATE TABLE admin_setup_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_admin_setup_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_admin_setup_hash (token_hash)
);
```

---

## 4. Test Environment Isolation

In local and CI automated tests:
- Property `paycore.security.seed-test-admin` is set to `true` **only** in `application-test.properties` so existing integration tests (such as `ReconciliationIntegrationTest` and `AuditIntegrationTest`) can execute against a dummy admin (`admin@paycore.internal`).
- In `application.properties` (production), `paycore.security.seed-test-admin` defaults to `false`.
- In `AuthIntegrationTest`, the flag is explicitly tested as `false` to verify that real admin onboarding operates properly without test artifacts.

# PayCore — Authentication Flow & Token Lifecycle Architecture

## 1. Overview

In Stage 7.5, PayCore enhances its identity and access layer from simple JWT generation to a production-grade, zero-trust authentication architecture featuring:
1. **Mandatory Email Verification:** Newly registered users cannot access merchant APIs until email ownership is cryptographically verified.
2. **Cryptographic Token Hashing at Rest:** Tokens (verification, password reset, refresh tokens) are hashed using SHA-256 before database persistence.
3. **Dual-Token Architecture:** Short-lived JWT access tokens paired with rotatable, persistent refresh tokens.
4. **Token Rotation & Replay Attack Defense:** Refresh tokens are single-use; reusing an old token triggers automatic session invalidation.
5. **Secure Revocation on Logout:** Server-side refresh token invalidation ensures session termination across distributed systems.

---

## 2. Registration & Email Verification Sequence

```mermaid
sequenceDiagram
    autonumber
    actor User as Merchant / User
    participant Frontend as PayCore React UI
    participant Backend as AuthController / AuthService
    participant DB as MySQL Database
    participant Gmail as Gmail REST API

    User->>Frontend: Fill Registration Form (email, password, businessName)
    Frontend->>Backend: POST /api/v1/auth/register
    Backend->>DB: Check if email exists
    alt Email already registered
        Backend-->>Frontend: 409 Conflict (EMAIL_ALREADY_EXISTS)
    else Email is new
        Backend->>DB: Insert User (email_verified=false, status=ACTIVE)
        Backend->>Backend: Generate Secure Token (UUID) & SHA-256 hash
        Backend->>DB: Insert EmailVerificationToken (token_hash, expires_at=+24h)
        Backend->>Gmail: Send MIME Verification Email
        alt Gmail API Failure
            Backend-->>Frontend: 502/503 (Email delivery failed / unavailable)
        else Delivery Accepted
            Backend-->>Frontend: 201 Created (emailVerified=false)
            Frontend->>User: Display "Verification Link Sent" screen
        end
    end

    Note over User,Gmail: User opens email and clicks link
    User->>Frontend: Open /verify-email?token=RAW_TOKEN
    Frontend->>Backend: POST /api/v1/auth/verify-email {token}
    Backend->>Backend: Compute SHA-256(token)
    Backend->>DB: Query EmailVerificationToken by token_hash
    alt Token invalid, expired, or already consumed
        Backend-->>Frontend: 400 Bad Request (INVALID_OR_EXPIRED_TOKEN)
        Frontend->>User: Display verification error & offer resend
    else Token valid
        Backend->>DB: Mark token consumed_at=NOW()
        Backend->>DB: Update User email_verified=true, email_verified_at=NOW()
        Backend-->>Frontend: 200 OK (Email verified successfully)
        Frontend->>User: Show success & redirect to /login
    end
```

---

## 3. Login & Verified-Email Enforcement Sequence

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Frontend as PayCore UI
    participant Backend as AuthService
    participant DB as MySQL

    User->>Frontend: Enter Email & Password
    Frontend->>Backend: POST /api/v1/auth/login
    Backend->>DB: Find user by email
    alt User not found or password incorrect
        Backend-->>Frontend: 401 Unauthorized (INVALID_CREDENTIALS)
    else Password matches
        Backend->>Backend: Check user.isEmailVerified()
        alt email_verified == false
            Backend-->>Frontend: 403 Forbidden (EMAIL_NOT_VERIFIED)
            Frontend->>User: Display Alert: "Email verification required" with Resend link
        else email_verified == true
            Backend->>Backend: Generate 15-minute JWT Access Token
            Backend->>Backend: Generate 7-day Refresh Token & SHA-256 hash
            Backend->>DB: Insert RefreshToken (token_hash, expires_at=+7d, revoked=false)
            Backend->>DB: Update user.lastLoginAt = NOW()
            Backend-->>Frontend: 200 OK {accessToken, refreshToken, user}
            Frontend->>Frontend: Store tokens & redirect to dashboard
        end
    end
```

---

## 4. Refresh Token Rotation & Replay Protection

To guard against token theft while maintaining seamless user sessions, PayCore employs **single-use refresh token rotation**:

1. **Client Request:** When the 15-minute access token expires, the client sends:
   ```http
   POST /api/v1/auth/refresh
   Content-Type: application/json

   {
     "refreshToken": "<raw_refresh_token_string>"
   }
   ```
2. **Hash Lookup:** The backend hashes the raw token (`SHA-256`) and checks `refresh_tokens`.
3. **Replay Detection:**
   - If the token was **already revoked** (`revoked = true`), it indicates a potential token compromise or replay attack. The server immediately logs a security incident and revokes all active refresh tokens for the user.
4. **Normal Rotation:**
   - The presented token is marked `revoked = true`, `revoked_at = NOW()`.
   - A new raw refresh token is generated, hashed, and stored:
     - `replaced_by_token_hash = SHA256(newRefreshToken)`
   - A fresh 15-minute JWT and the new refresh token are returned to the client:
   ```json
   {
     "status": "success",
     "data": {
       "accessToken": "eyJhbG...",
       "refreshToken": "new_random_refresh_token...",
       "tokenType": "Bearer",
       "expiresIn": 900
     }
   }
   ```

---

## 5. Logout & Session Termination

When a user initiates logout:
1. The frontend invokes:
   ```http
   POST /api/v1/auth/logout
   Content-Type: application/json

   {
     "refreshToken": "<active_refresh_token>"
   }
   ```
2. The server marks the token as revoked in the database:
   - `revoked = true`
   - `revoked_at = NOW()`
3. The frontend clears all tokens from local storage (`paycore_token`, `paycore_refresh_token`) and redirects to `/login`.

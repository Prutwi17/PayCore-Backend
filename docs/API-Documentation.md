# PAYCORE — REST API SPECIFICATION

## 1. Global API Standards

- **Base Path:** `/api/v1`
- **Content-Type:** `application/json`
- **Authentication Headers:**
  - **Portal/Dashboard Users:** `Authorization: Bearer <jwt_access_token>`
  - **Merchant API Integration:** `X-Api-Key: sk_live_...` or `sk_test_...`
- **Idempotency Header:** `Idempotency-Key: <unique-uuid-or-string>` (Required on `POST /payments`, `POST /refunds`)
- **Standard Success Response Format:**
  ```json
  {
    "success": true,
    "message": "Operation completed successfully",
    "data": { ... },
    "timestamp": "2026-11-05T00:00:00.000Z"
  }
  ```
- **Standard Error Response Format:**
  ```json
  {
    "success": false,
    "error": {
      "code": "INSUFFICIENT_FUNDS",
      "message": "The payment provider reported insufficient funds on the card.",
      "details": [
        {
          "field": "amount",
          "issue": "Amount exceeds card spending limit"
        }
      ]
    },
    "timestamp": "2026-11-05T00:00:00.000Z"
  }
  ```

---

## 2. Authentication Endpoints (`/api/v1/auth`)

### 2.1 User Login
- **Endpoint:** `POST /api/v1/auth/login`
- **Access:** Public
- **Request Body:**
  ```json
  {
    "email": "merchant@store.com",
    "password": "Password123!"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Login successful",
    "data": {
      "accessToken": "eyJhbGciOiJIUzI1Ni...",
      "tokenType": "Bearer",
      "expiresIn": 86400,
      "user": {
        "id": 1,
        "username": "acme_store",
        "email": "merchant@store.com",
        "fullName": "Acme Admin",
        "role": "ROLE_MERCHANT",
        "merchantId": 101,
        "merchantCode": "mer_8f2301"
      }
    }
  }
  ```

### 2.2 Merchant Registration
- **Endpoint:** `POST /api/v1/auth/register`
- **Access:** Public
- **Request Body:**
  ```json
  {
    "fullName": "Jane Doe",
    "email": "jane@business.com",
    "businessName": "Jane Creations Ltd",
    "password": "StrongPassword123!"
  }
  ```
- **Response (201 Created):** Returns user and merchant profile with default test API keys.

---

## 3. Merchant API Key Management (`/api/v1/merchants/api-keys`)

### 3.1 List API Keys
- **Endpoint:** `GET /api/v1/merchants/api-keys`
- **Access:** Merchant (JWT)
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "data": [
      {
        "id": 1,
        "keyPrefix": "sk_test_8f23",
        "keyType": "SECRET",
        "environment": "TEST",
        "status": "ACTIVE",
        "lastUsedAt": "2026-11-04T18:30:00.000Z",
        "createdAt": "2026-11-01T10:00:00.000Z"
      }
    ]
  }
  ```

### 3.2 Generate New API Key
- **Endpoint:** `POST /api/v1/merchants/api-keys`
- **Access:** Merchant (JWT)
- **Request Body:**
  ```json
  {
    "keyType": "SECRET",
    "environment": "LIVE"
  }
  ```
- **Response (201 Created):** Returns full key once (e.g. `sk_live_a1b2c3d4e5f6...`). Subsequent GET requests display only `keyPrefix`.

---

## 4. Payment Processing Endpoints (`/api/v1/payments`)

### 4.1 Create / Initiate Payment
- **Endpoint:** `POST /api/v1/payments`
- **Access:** Merchant (`X-Api-Key`)
- **Headers:**
  - `X-Api-Key: sk_live_...` (Required)
  - `Idempotency-Key: 6f3a9d2e-4b8c-4f4e-a1b2-c3d4e5f6a7b8` (Required)
- **Request Body:**
  ```json
  {
    "orderId": "ORD-1001",
    "amount": 1990.00,
    "currency": "INR",
    "paymentMethod": "CARD",
    "customer": {
      "email": "customer@example.com",
      "name": "Rajesh Kumar",
      "phone": "+919876543210"
    },
    "metadata": {
      "cartItemsCount": 3
    }
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "success": true,
    "message": "Payment processed successfully",
    "data": {
      "paymentId": "PAY_8F2301",
      "orderId": "ORD-1001",
      "amount": 1990.00,
      "amountRefunded": 0.00,
      "currency": "INR",
      "status": "SUCCESS",
      "paymentMethod": "CARD",
      "providerTransactionId": "sim_tx_99887766",
      "createdAt": "2026-11-05T00:15:30.000Z"
    }
  }
  ```

### 4.2 Get Payment Details
- **Endpoint:** `GET /api/v1/payments/{paymentId}`
- **Access:** Merchant (`X-Api-Key` or JWT), Admin (JWT)
- **Response (200 OK):** Returns payment object, attempt timeline, associated refunds, and webhook events.

### 4.3 Search & Filter Payments
- **Endpoint:** `GET /api/v1/payments?page=0&size=10&status=SUCCESS&orderId=ORD-1001&startDate=2026-11-01&endDate=2026-11-05`
- **Access:** Merchant / Admin
- **Response (200 OK):** Paged list of payment summaries.

---

## 5. Refund Endpoints (`/api/v1/refunds`)

### 5.1 Create Refund
- **Endpoint:** `POST /api/v1/refunds`
- **Access:** Merchant (`X-Api-Key` or JWT)
- **Headers:**
  - `Idempotency-Key: ref-idemp-uuid-1234`
- **Request Body:**
  ```json
  {
    "paymentId": "PAY_8F2301",
    "amount": 500.00,
    "reason": "Customer returned 1 item from order"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "success": true,
    "message": "Refund processed successfully",
    "data": {
      "refundId": "REF_7712A",
      "paymentId": "PAY_8F2301",
      "amount": 500.00,
      "currency": "INR",
      "status": "SUCCESS",
      "providerRefundId": "sim_ref_112233",
      "createdAt": "2026-11-05T00:20:00.000Z"
    }
  }
  ```

### 5.2 List Refunds
- **Endpoint:** `GET /api/v1/refunds?page=0&size=10&paymentId=PAY_8F2301`
- **Access:** Merchant / Admin

---

## 6. Webhook Endpoints (`/api/v1/webhooks`)

### 6.1 Register Webhook Endpoint
- **Endpoint:** `POST /api/v1/webhooks/endpoints`
- **Access:** Merchant (JWT)
- **Request Body:**
  ```json
  {
    "url": "https://merchant-store.com/api/webhooks/paycore",
    "subscribedEvents": ["payment.success", "payment.failed", "refund.processed"]
  }
  ```
- **Response (201 Created):** Returns endpoint ID and `secretKey` (`whsec_...`) used to verify signatures.

### 6.2 Outbound Webhook Payload Format (Sent to Merchant)
- **Header:** `X-PayCore-Signature: t=1730764800,v1=9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08`
- **Body:**
  ```json
  {
    "eventId": "evt_1F3D20",
    "eventType": "payment.success",
    "createdAt": "2026-11-05T00:15:32.000Z",
    "data": {
      "paymentId": "PAY_8F2301",
      "orderId": "ORD-1001",
      "amount": 1990.00,
      "currency": "INR",
      "status": "SUCCESS"
    }
  }
  ```

---

## 7. Reconciliation Endpoints (`/api/v1/reconciliation`)

### 7.1 Trigger Reconciliation Run
- **Endpoint:** `POST /api/v1/reconciliation/run`
- **Access:** Admin (JWT)
- **Request Body:**
  ```json
  {
    "providerName": "SIMULATED",
    "date": "2026-11-04"
  }
  ```
- **Response (202 Accepted):** Batch initialized with `batchId: "rec_20261104_01"`.

### 7.2 Get Reconciliation Batch Report
- **Endpoint:** `GET /api/v1/reconciliation/batches/{batchId}`
- **Access:** Admin / Merchant
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "data": {
      "batchId": "rec_20261104_01",
      "providerName": "SIMULATED",
      "status": "COMPLETED",
      "metrics": {
        "totalRecords": 48148,
        "matched": 48120,
        "mismatched": 28,
        "missingProvider": 12,
        "missingInternal": 14
      },
      "discrepancies": [ ... ]
    }
  }
  ```

---

## 8. Admin Dashboard Endpoints (`/api/v1/admin`)

- `GET /api/v1/admin/dashboard/overview`: Platform-wide stats (total volume, payment count, refund count, success rate, daily volume time series).
- `GET /api/v1/admin/merchants`: List, search, and manage registered merchants.
- `GET /api/v1/admin/audit-logs`: Search immutable audit logs by actor, action, date, or resource ID.
- `GET /api/v1/admin/system/health`: Aggregated health metrics from Redis, Kafka, and Database.

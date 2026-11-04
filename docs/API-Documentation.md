# PAYCORE — REST API SPECIFICATION

## 1. Global API Standards

- **Base Path:** `/api/v1`
- **Content-Type:** `application/json`
- **Authentication Headers:**
  - **Portal/Dashboard Users:** `Authorization: Bearer <jwt_access_token>`
  - **Merchant API Integration:** `Authorization: Bearer <jwt_access_token>` or `X-Api-Key: sk_live_...`
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
        "username": "merchant@store.com",
        "email": "merchant@store.com",
        "fullName": "Jane Doe",
        "roles": ["ROLE_MERCHANT"],
        "status": "ACTIVE"
      }
    }
  }
  ```

### 2.2 User Registration
- **Endpoint:** `POST /api/v1/auth/register`
- **Access:** Public
- **Request Body:**
  ```json
  {
    "fullName": "Jane Doe",
    "email": "jane@acme.com",
    "password": "Password123!",
    "phoneNumber": "+15550192834",
    "businessName": "Acme Payments LLC",
    "businessAddress": "100 Enterprise Way"
  }
  ```

### 2.3 Current User Profile
- **Endpoint:** `GET /api/v1/auth/me`
- **Access:** Authenticated

---

## 3. Merchant & API Key Endpoints (`/api/v1/merchants`)

### 3.1 Get Merchant Profile
- **Endpoint:** `GET /api/v1/merchants/profile`
- **Access:** Merchant (`ROLE_MERCHANT`, `ROLE_ADMIN`)

### 3.2 List Merchant API Keys
- **Endpoint:** `GET /api/v1/merchants/api-keys`
- **Access:** Merchant

### 3.3 Generate New API Key
- **Endpoint:** `POST /api/v1/merchants/api-keys`
- **Access:** Merchant
- **Request Body:**
  ```json
  {
    "keyType": "SECRET",
    "environment": "LIVE",
    "description": "Primary Checkout Service"
  }
  ```
- **Response (201 Created):** Returns the plaintext secret key once (e.g. `sk_live_...`).

### 3.4 Revoke API Key
- **Endpoint:** `DELETE /api/v1/merchants/api-keys/{id}`
- **Access:** Merchant

---

## 4. Payment Processing Endpoints (`/api/v1/payments`)

### 4.1 Create / Initiate Payment
- **Endpoint:** `POST /api/v1/payments`
- **Access:** Merchant (`ROLE_MERCHANT`, `ROLE_ADMIN`)
- **Headers:** `Authorization: Bearer <jwt>`
- **Request Body:**
  ```json
  {
    "amount": 1499.50,
    "currency": "INR",
    "description": "Order #9021 payment",
    "paymentMethod": "CARD",
    "customerReference": "cust_user_42@example.com",
    "simulationScenario": "SUCCESS"
  }
  ```
  *Note on `simulationScenario`: Optional parameter supporting deterministic outcomes (`SUCCESS`, `FAIL`, `TIMEOUT`, `REQUIRES_ACTION`).*
- **Response (201 Created):**
  ```json
  {
    "success": true,
    "message": "Payment processed successfully",
    "data": {
      "id": 1,
      "paymentReference": "pay_64557f2597dd49b99de4",
      "merchantId": 10,
      "merchantCode": "MCH_ALPHA_01",
      "customerReference": "cust_user_42@example.com",
      "amount": 1499.5000,
      "currency": "INR",
      "description": "Order #9021 payment",
      "paymentMethod": "CARD",
      "status": "SUCCESS",
      "provider": "SIMULATED",
      "providerTransactionId": "sim_tx_2ea36e503b5f4ad2",
      "failureCode": null,
      "failureMessage": null,
      "createdAt": "2026-11-05T02:00:00.000Z",
      "updatedAt": "2026-11-05T02:00:00.050Z",
      "processedAt": "2026-11-05T02:00:00.050Z"
    }
  }
  ```

### 4.2 List Merchant Payments
- **Endpoint:** `GET /api/v1/payments?status=SUCCESS&search=cust_user&page=0&size=10&sortBy=createdAt&sortDirection=desc`
- **Access:** Merchant (`ROLE_MERCHANT`, `ROLE_ADMIN`)
- **Response (200 OK):** Paged list of merchant payments scoped strictly by tenant.

### 4.3 Get Payment Details
- **Endpoint:** `GET /api/v1/payments/{paymentReference}`
- **Access:** Merchant (Scoped by merchant ownership) or Admin
- **Response (200 OK):** Complete payment record. Returns 404 if accessed by unauthorized merchant.

### 4.4 Get Payment Attempt History
- **Endpoint:** `GET /api/v1/payments/{paymentReference}/attempts`
- **Access:** Merchant / Admin
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Payment attempts retrieved",
    "data": [
      {
        "id": 1,
        "attemptNumber": 1,
        "provider": "SIMULATED",
        "providerTransactionId": "sim_tx_2ea36e503b5f4ad2",
        "status": "SUCCESS",
        "requestReference": "att_req_8f11293a",
        "responseCode": "200_APPROVED",
        "responseMessage": "Payment authorized and settled successfully by simulated provider.",
        "startedAt": "2026-11-05T02:00:00.000Z",
        "completedAt": "2026-11-05T02:00:00.045Z",
        "createdAt": "2026-11-05T02:00:00.000Z"
      }
    ]
  }
  ```

### 4.5 Query Payment Status & Verification
- **Endpoint:** `GET /api/v1/payments/{paymentReference}/status?verify=true`
- **Access:** Merchant / Admin
- **Description:** Returns current payment status. If status is `UNKNOWN` and `verify=true`, actively queries downstream provider to reconcile indeterminate outcome without duplicate charging.

### 4.6 Cancel Payment
- **Endpoint:** `POST /api/v1/payments/{paymentReference}/cancel?reason=Abandoned+checkout`
- **Access:** Merchant / Admin
- **Description:** Cancels an eligible payment (in `CREATED`, `REQUIRES_ACTION`, or `AUTHORIZED` state).

---

## 5. Administrative Payments Governance (`/api/v1/admin/payments`)

### 5.1 List All System Payments
- **Endpoint:** `GET /api/v1/admin/payments?status=FAILED&merchantId=10&page=0&size=20`
- **Access:** Admin (`ROLE_ADMIN` only)

### 5.2 Admin Get Payment Audit & Telemetry
- **Endpoint:** `GET /api/v1/admin/payments/{paymentReference}`
- **Endpoint:** `GET /api/v1/admin/payments/{paymentReference}/attempts`
- **Access:** Admin (`ROLE_ADMIN` only)

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

### 4.1 Create / Initiate Payment with Idempotency
- **Endpoint:** `POST /api/v1/payments`
- **Access:** Merchant (`ROLE_MERCHANT`, `ROLE_ADMIN`)
- **Mandatory Headers:**
  - `Authorization: Bearer <jwt>`
  - `Idempotency-Key: <unique-client-generated-key>` (1-128 characters, e.g. UUID v4)
- **Response Headers:**
  - `Idempotent-Replay: true` (Included when returning a cached idempotent response)
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
- **Status Codes & Responses:**
  - **HTTP 201 Created:** Fresh payment processed successfully.
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
        "idempotentReplay": false,
        "createdAt": "2026-11-05T02:00:00.000Z",
        "updatedAt": "2026-11-05T02:00:00.050Z",
        "processedAt": "2026-11-05T02:00:00.050Z"
      }
    }
    ```
  - **HTTP 200 OK (Idempotent Replay):** Duplicate request sharing the same key and payload. Returns cached payment with header `Idempotent-Replay: true` and `"idempotentReplay": true`. No duplicate provider charge occurs.
  - **HTTP 202 Accepted:** Request with same key is currently in-flight concurrently (`IDEMPOTENCY_PROCESSING`).
  - **HTTP 400 Bad Request:** Missing `Idempotency-Key` header (`IDEMPOTENCY_KEY_INVALID`).
  - **HTTP 409 Conflict:**
    - `IDEMPOTENCY_CONFLICT`: Same key reused with an altered request payload.
    - `CONCURRENCY_CONFLICT`: Optimistic locking collision on concurrent resource modification.

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

---

## 6. Refund Processing Endpoints (`/api/v1/refunds` & `/api/v1/payments/{ref}/refunds`)

### 6.1 Initiate Refund with Idempotency & Balance Reservation
- **Endpoint:** `POST /api/v1/payments/{paymentReference}/refunds`
- **Access:** Merchant (`ROLE_MERCHANT`, `ROLE_ADMIN`)
- **Headers:**
  - `Authorization: Bearer <jwt>`
  - `Idempotency-Key: <unique-key>` (Optional but highly recommended)
- **Request Body:**
  ```json
  {
    "amount": 500.00,
    "currency": "INR",
    "reason": "Customer item returned in good condition"
  }
  ```
  *Simulation triggers supported in reason:*
  - `[SIM_FAIL]`: Forces simulated gateway refund failure.
  - `[SIM_TIMEOUT]`: Forces simulated network timeout / `UNKNOWN` state.
- **Response Headers:**
  - `Idempotent-Replay: true` (Present on idempotent replay)
- **Status Codes & Responses:**
  - **HTTP 201 Created:** Refund initiated and settled successfully.
    ```json
    {
      "success": true,
      "message": "Refund processed successfully",
      "data": {
        "id": 1,
        "refundReference": "ref_9a87bc12d34e56",
        "paymentReference": "pay_64557f2597dd49b99de4",
        "merchantId": 10,
        "merchantCode": "MCH_ALPHA_01",
        "amount": 500.0000,
        "currency": "INR",
        "reason": "Customer item returned in good condition",
        "status": "SUCCESS",
        "providerRefundId": "sim_ref_9a87bc12d34e56",
        "failureCode": null,
        "failureMessage": null,
        "requestedAt": "2026-11-05T03:00:00.000Z",
        "processedAt": "2026-11-05T03:00:00.040Z",
        "createdAt": "2026-11-05T03:00:00.000Z",
        "updatedAt": "2026-11-05T03:00:00.040Z",
        "idempotentReplay": false
      }
    }
    ```
  - **HTTP 200 OK (Idempotent Replay):** Exact duplicate refund request replayed without re-charging or double-decrementing balances.
  - **HTTP 202 Accepted:** In-flight concurrent request with identical key currently processing.
  - **HTTP 400 Bad Request:** 
    - `REFUND_VALIDATION_ERROR`: Missing or non-positive amount.
    - `CURRENCY_MISMATCH`: Currency does not match parent payment currency.
    - `PAYMENT_NOT_REFUNDABLE`: Payment is not in `SUCCESS` or `PARTIALLY_REFUNDED` state.
    - `EXCEEDED_REFUNDABLE_AMOUNT`: Requested amount exceeds remaining available refundable balance (`Original - SettledRefunds - ReservedPending`).
  - **HTTP 409 Conflict:** `IDEMPOTENCY_CONFLICT` (reusing key with different refund payload).

### 6.2 Get Refundable Balance for Payment
- **Endpoint:** `GET /api/v1/payments/{paymentReference}/refundable-amount`
- **Access:** Merchant (`ROLE_MERCHANT`, `ROLE_ADMIN`)
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Refundable amount calculated",
    "data": {
      "paymentReference": "pay_64557f2597dd49b99de4",
      "originalAmount": 1499.5000,
      "totalRefundedAmount": 500.0000,
      "pendingRefundAmount": 0.0000,
      "remainingRefundableAmount": 999.5000,
      "currency": "INR",
      "isRefundable": true
    }
  }
  ```

### 6.3 List Merchant Refunds
- **Endpoint:** `GET /api/v1/refunds?status=SUCCESS&search=ref_9a&page=0&size=10&sortBy=createdAt&sortDirection=desc`
- **Access:** Merchant (`ROLE_MERCHANT`, `ROLE_ADMIN`)
- **Response (200 OK):** Paged collection of merchant refunds.

### 6.4 Get Refund Details
- **Endpoint:** `GET /api/v1/refunds/{refundReference}`
- **Access:** Merchant / Admin
- **Response (200 OK):** Detailed refund record with parent payment references.

### 6.5 List Refunds for a Specific Payment
- **Endpoint:** `GET /api/v1/payments/{paymentReference}/refunds`
- **Access:** Merchant / Admin
- **Response (200 OK):** Array of all refunds issued against the specified payment.

### 6.6 Verify / Reconcile Indeterminate Refund
- **Endpoint:** `POST /api/v1/refunds/{refundReference}/verify`
- **Access:** Merchant / Admin
- **Description:** Actively queries downstream provider for a refund in `UNKNOWN` state. If provider confirms success, balance is decremented from reserved to settled; if provider confirms failure, reserved amount is unlocked.

### 6.7 Admin Refund Governance Endpoints
- **List All System Refunds:** `GET /api/v1/admin/refunds?merchantId=10&status=UNKNOWN&page=0&size=20`
- **Admin Get Refund:** `GET /api/v1/admin/refunds/{refundReference}`
- **Admin Force Reconcile:** `POST /api/v1/admin/refunds/{refundReference}/verify`

---

## 7. Unified Transaction History Endpoints (`/api/v1/transactions`)

### 7.1 Merchant Transaction History Ledger
- **Endpoint:** `GET /api/v1/transactions`
- **Access:** Merchant (`ROLE_MERCHANT`, `ROLE_ADMIN`)
- **Query Parameters:**
  - `type`: Filter by `PAYMENT` or `REFUND`
  - `status`: Filter by state (e.g. `SUCCESS`, `PARTIALLY_REFUNDED`, `REFUNDED`, `FAILED`)
  - `startDate` / `endDate`: ISO-8601 date filters (e.g. `2026-11-01T00:00:00Z`)
  - `search`: Search substring against references and memos
  - `page`: 0-indexed page number (default: 0)
  - `size`: Page size limit (default: 20)
  - `sortBy`: Field to sort on (e.g. `createdAt`, `amount`)
  - `sortDirection`: `ASC` or `DESC` (default: `DESC`)
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Transactions retrieved successfully",
    "data": {
      "content": [
        {
          "transactionId": "REF_1",
          "transactionReference": "ref_9a87bc12d34e56",
          "parentReference": "pay_64557f2597dd49b99de4",
          "merchantId": 10,
          "type": "REFUND",
          "amount": 500.0000,
          "currency": "INR",
          "status": "SUCCESS",
          "description": "Customer item returned in good condition",
          "createdAt": "2026-11-05T03:00:00.000Z",
          "processedAt": "2026-11-05T03:00:00.040Z"
        },
        {
          "transactionId": "PAY_1",
          "transactionReference": "pay_64557f2597dd49b99de4",
          "parentReference": "pay_64557f2597dd49b99de4",
          "merchantId": 10,
          "type": "PAYMENT",
          "amount": 1499.5000,
          "currency": "INR",
          "status": "PARTIALLY_REFUNDED",
          "description": "Order #9021 payment",
          "createdAt": "2026-11-05T02:00:00.000Z",
          "processedAt": "2026-11-05T02:00:00.050Z"
        }
      ],
      "pageNumber": 0,
      "pageSize": 20,
      "totalElements": 2,
      "totalPages": 1,
      "first": true,
      "last": true
    }
  }
  ```

### 7.2 Admin Global Transaction History Ledger
- **Endpoint:** `GET /api/v1/admin/transactions`
- **Access:** Admin (`ROLE_ADMIN` only)
- **Query Parameters:** Supports all merchant parameters plus `merchantId` filtering across all tenants in the platform.

---

## 8. Webhook & Asynchronous Event Endpoints (`/api/v1/webhooks`, `/api/v1/admin/webhooks`)

### 8.1 Register Webhook Endpoint
- **Endpoint:** `POST /api/v1/webhooks/endpoints`
- **Access:** Merchant (`ROLE_MERCHANT`, `ROLE_ADMIN`)
- **Request Body:**
  ```json
  {
    "url": "https://api.store.com/webhooks/paycore",
    "subscribedEvents": "PAYMENT_SUCCESS,REFUND_SUCCESS",
    "description": "Production Webhook Receiver"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "success": true,
    "message": "Webhook endpoint registered successfully",
    "data": {
      "id": 1,
      "endpointReference": "wh_4f9a0c1e82bc",
      "merchantId": 10,
      "url": "https://api.store.com/webhooks/paycore",
      "maskedSecret": "whsec_****************",
      "secret": "whsec_example_mock_signing_secret_do_not_use_in_prod",
      "subscribedEvents": "PAYMENT_SUCCESS,REFUND_SUCCESS",
      "status": "ACTIVE",
      "description": "Production Webhook Receiver",
      "failureCount": 0,
      "createdAt": "2026-11-05T06:00:00.000Z",
      "updatedAt": "2026-11-05T06:00:00.000Z"
    }
  }
  ```
  *(Note: The `secret` field is revealed only once upon registration or secret rotation).*

### 8.2 List Merchant Webhook Endpoints
- **Endpoint:** `GET /api/v1/webhooks/endpoints`
- **Access:** Merchant (`ROLE_MERCHANT`, `ROLE_ADMIN`)

### 8.3 Rotate Endpoint Secret
- **Endpoint:** `POST /api/v1/webhooks/endpoints/{endpointReference}/rotate-secret`
- **Access:** Merchant (`ROLE_MERCHANT`, `ROLE_ADMIN`)

### 8.4 List Webhook Delivery History
- **Endpoint:** `GET /api/v1/webhooks/deliveries`
- **Access:** Merchant (`ROLE_MERCHANT`, `ROLE_ADMIN`)
- **Query Parameters:** `status` (`SUCCESS`, `RETRYING`, `FAILED`, `DEAD_LETTER`), `page`, `size`

### 8.5 Retry Webhook Delivery
- **Endpoint:** `POST /api/v1/webhooks/deliveries/{attemptReference}/retry`
- **Access:** Merchant (`ROLE_MERCHANT`, `ROLE_ADMIN`)

### 8.6 Admin Webhook Pipeline Metrics
- **Endpoint:** `GET /api/v1/admin/webhooks/metrics`
- **Access:** Admin (`ROLE_ADMIN` only)
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Pipeline metrics retrieved successfully",
    "data": {
      "pendingOutboxCount": 0,
      "failedOutboxCount": 0,
      "totalDeliveries": 120,
      "successfulDeliveries": 118,
      "failedDeliveries": 2,
      "deadLetterDeliveries": 1,
      "deliverySuccessRate": 98.33
    }
  }
  ```

### 8.7 Admin Outbox Inspection & Republish
- **Endpoint:** `GET /api/v1/admin/webhooks/outbox` (List outbox entries by status)
- **Endpoint:** `POST /api/v1/admin/webhooks/outbox/{eventId}/republish` (Force republish to Kafka)
- **Access:** Admin (`ROLE_ADMIN` only)

### 8.8 Admin Inbox Consumers & Dead-Letter Replay
- **Endpoint:** `GET /api/v1/admin/webhooks/inbox` (List inbox consumer processing records)
- **Endpoint:** `GET /api/v1/admin/webhooks/deliveries/dead-letter` (List dead-letter deliveries)
- **Endpoint:** `POST /api/v1/admin/webhooks/deliveries/{attemptReference}/replay` (Force redelivery attempt)
- **Access:** Admin (`ROLE_ADMIN` only)


---

## 9. Financial Reconciliation Endpoints (`/api/v1/admin/reconciliation`)

### 9.1 Trigger Reconciliation Run
- **Endpoint:** `POST /api/v1/admin/reconciliation/runs`
- **Access:** Admin (`ROLE_ADMIN` only)
- **Request Body:**
  ```json
  {
    "runType": "FULL",
    "startDate": "2026-11-01T00:00:00Z",
    "endDate": "2026-11-06T23:59:59Z",
    "notes": "Daily automated clearing settlement reconciliation"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Reconciliation run executed successfully",
    "data": {
      "id": 1,
      "runReference": "rec_run_9a2f7c01b34e4a91",
      "runType": "FULL",
      "status": "COMPLETED",
      "totalRecords": 150,
      "matchedRecords": 148,
      "discrepancyRecords": 2,
      "failedRecords": 0,
      "initiatedBy": "admin@paycore.internal",
      "createdAt": "2026-11-06T14:00:00Z"
    }
  }
  ```

### 9.2 List Reconciliation Runs
- **Endpoint:** `GET /api/v1/admin/reconciliation/runs`
- **Access:** Admin (`ROLE_ADMIN` only)
- **Query Parameters:** `status` (`PENDING`, `RUNNING`, `COMPLETED`, `COMPLETED_WITH_ERRORS`, `FAILED`), `page`, `size`

### 9.3 Get Reconciliation Run Details
- **Endpoint:** `GET /api/v1/admin/reconciliation/runs/{runReference}`
- **Access:** Admin (`ROLE_ADMIN` only)

### 9.4 List Records in Run
- **Endpoint:** `GET /api/v1/admin/reconciliation/runs/{runReference}/records`
- **Access:** Admin (`ROLE_ADMIN` only)
- **Query Parameters:** `status` (`MATCHED`, `UNMATCHED`, `REVIEW_REQUIRED`, `RESOLVED`), `discrepancyType`, `recordType` (`PAYMENT`, `REFUND`), `page`, `size`

### 9.5 List Global Active Discrepancies
- **Endpoint:** `GET /api/v1/admin/reconciliation/discrepancies`
- **Access:** Admin (`ROLE_ADMIN` only)
- **Query Parameters:** `status`, `discrepancyType`, `recordType`, `page`, `size`

### 9.6 Manually Resolve Discrepancy
- **Endpoint:** `POST /api/v1/admin/reconciliation/records/{recordId}/resolve`
- **Access:** Admin (`ROLE_ADMIN` only)
- **Request Body:**
  ```json
  {
    "reason": "FEE_ADJUSTMENT",
    "notes": "Verified provider discount deduction with billing team; cleared as valid gateway surcharge."
  }
  ```
- **Response (200 OK):** Standardized response with updated status `RESOLVED`, `resolvedBy`, and `resolvedAt`.

---

## 10. Immutable Audit Log Endpoints (`/api/v1/admin/audit-logs`)

### 10.1 Query Audit Logs
- **Endpoint:** `GET /api/v1/admin/audit-logs`
- **Access:** Admin (`ROLE_ADMIN` only)
- **Query Parameters:** `actorId`, `action`, `resourceType`, `resourceId`, `merchantId`, `startDate`, `endDate`, `page`, `size`

### 10.2 Get Audit Log Entry by ID
- **Endpoint:** `GET /api/v1/admin/audit-logs/{id}`
- **Access:** Admin (`ROLE_ADMIN` only)

### 10.3 Recent System Activity
- **Endpoint:** `GET /api/v1/admin/audit-logs/recent`
- **Access:** Admin (`ROLE_ADMIN` only)

---

## 11. Admin Operational Dashboard Endpoints (`/api/v1/admin/dashboard`)

### 11.1 High-Level KPI Summary
- **Endpoint:** `GET /api/v1/admin/dashboard/summary`
- **Access:** Admin (`ROLE_ADMIN` only)
- **Query Parameters:** `startDate`, `endDate` (ISO-8601 strings, optional)
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Dashboard summary metrics retrieved successfully",
    "data": {
      "totalPayments": 1250,
      "successfulPayments": 1180,
      "failedPayments": 65,
      "pendingProcessingPayments": 4,
      "unknownPayments": 1,
      "totalPaymentVolume": 142500.50,
      "paymentSuccessRate": 94.4,
      "totalRefunds": 24,
      "successfulRefunds": 22,
      "successfulRefundVolume": 3150.00,
      "pendingRefunds": 2,
      "webhookDeliveryFailures": 3,
      "pendingWebhookRetries": 1,
      "latestReconciliationStatus": "COMPLETED",
      "latestReconciliationAt": "2026-11-06T12:00:00Z",
      "unresolvedDiscrepanciesCount": 2
    }
  }
  ```

### 11.2 Payment Daily Trends
- **Endpoint:** `GET /api/v1/admin/dashboard/trends/payments`
- **Access:** Admin (`ROLE_ADMIN` only)

### 11.3 Refund Daily Trends
- **Endpoint:** `GET /api/v1/admin/dashboard/trends/refunds`
- **Access:** Admin (`ROLE_ADMIN` only)

### 11.4 Payment Status Distribution Breakdown
- **Endpoint:** `GET /api/v1/admin/dashboard/status-distribution`
- **Access:** Admin (`ROLE_ADMIN` only)

### 11.5 Reconciliation Health Summary
- **Endpoint:** `GET /api/v1/admin/dashboard/reconciliation-summary`
- **Access:** Admin (`ROLE_ADMIN` only)

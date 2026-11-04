# PayCore — Payment Processing Engine (Backend)

[![Java 17](https://img.shields.io/badge/Java-17-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-orange.svg)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.x-red.svg)](https://redis.io/)
[![Kafka](https://img.shields.io/badge/Kafka-3.6-black.svg)](https://kafka.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

> **PayCore** is an enterprise-grade payment processing engine engineered as a modular monolith. It provides robust REST APIs for merchant integration, distributed idempotency guarantees, asynchronous webhook dispatching via Apache Kafka, resilient provider timeout handling, full and partial refunds, financial reconciliation, and immutable audit logging.

---

## 🚀 Key Architecture & Engineering Highlights

- **Modular Monolith:** Clean boundary separation across domain modules (`auth`, `merchant`, `payment`, `refund`, `webhook`, `reconciliation`, `audit`, `admin`).
- **Payment Lifecycle State Machine:** Strict enforcement of transitions (`CREATED`, `PROCESSING`, `REQUIRES_ACTION`, `AUTHORIZED`, `SUCCESS`, `FAILED`, `CANCELLED`, `UNKNOWN`).
- **Zero Double-Charge Guarantee:** 
  - Distributed `Idempotency-Key` evaluation.
  - Redis distributed locks for active requests.
  - Persistent composite unique constraint `(merchant_id, idempotency_key)` in MySQL.
  - Payload fingerprinting using SHA-256 to reject key tampering.
- **Provider Timeout Resilience:** Unconfirmed downstream provider timeouts transition to `UNKNOWN` pending verification; never blindly retries potentially successful charges.
- **Zero Active DB Transactions during Network I/O:** Database connections are never held open while waiting for external provider responses.
- **Asynchronous Webhook Engine:** Event-driven architecture powered by Apache Kafka, supporting HMAC-SHA256 signature verification, exponential backoff retries, and Dead Letter Queues (DLQ).
- **Financial Reconciliation:** Automated detection of matching transactions, amount discrepancies, status mismatches, and orphaned records.

---

## 📁 Project Documentation

Detailed technical documentation is maintained under the [`docs/`](./docs) directory:

| Document | Description |
|---|---|
| [**PRD.md**](./docs/PRD.md) | Product Requirements Document, user roles, functional & non-functional requirements |
| [**TechStack.md**](./docs/TechStack.md) | Technology choices, versions, libraries, and architectural rationale |
| [**Architecture.md**](./docs/Architecture.md) | Modular monolith architecture, package structure, module boundaries |
| [**SystemDesign.md**](./docs/SystemDesign.md) | High-level & low-level design, sequence diagrams, failure recovery |
| [**DatabaseDesign.md**](./docs/DatabaseDesign.md) | Normalized MySQL schema, table definitions, indexes, monetary precision |
| [**ERDiagram.md**](./docs/ERDiagram.md) | Visual Mermaid Entity-Relationship Diagram |
| [**AppFlow.md**](./docs/AppFlow.md) | Payment state machine, refund flow, webhook dispatching, reconciliation |
| [**API-Documentation.md**](./docs/API-Documentation.md) | Comprehensive REST API specification, request/response models |
| [**Security.md**](./docs/Security.md) | Spring Security, JWT, RBAC, API Key hashing, webhook HMAC signing |
| [**CICD.md**](./docs/CICD.md) | GitHub Actions CI pipeline, Dockerfile, docker-compose.yml |
| [**GitWorkflow.md**](./docs/GitWorkflow.md) | Branching strategy, PR guidelines, conventional commits |
| [**DevelopmentRoadmap.md**](./docs/DevelopmentRoadmap.md) | 8-Milestone execution plan with acceptance criteria |

---

## 🛠 Local Development Setup

### Prerequisites
- Java 17 LTS
- Maven 3.9+ (or use `./mvnw` wrapper)
- Docker & Docker Compose
- Node.js 20+ (for frontend)

### Running Supporting Services via Docker Compose
```bash
# Start MySQL, Redis, and Kafka KRaft
docker-compose up -d mysql redis kafka
```

### Running Backend Locally
```bash
cd PayCore-Backend
./mvnw spring-boot:run
```

API Server starts at: `http://localhost:8080`  
Swagger UI Documentation: `http://localhost:8080/swagger-ui/index.html`  
Actuator Health: `http://localhost:8080/actuator/health`

---

## 🧪 Testing & Current Status (Stage 4 Complete)

PayCore Stage 4 (Idempotency, Concurrency & Payment Reliability) is fully implemented, verified, and operational:

- **Automated Tests:** 49 passing tests (Unit, Service, State Machine, Fingerprinting, Retry Executor, and MockMvc/H2 End-to-End Idempotency & Concurrency Integration tests).
- **Authoritative Idempotency Barrier:** Persistent MySQL composite unique constraint uk_merchant_idempotency_key on (merchant_id, idempotency_key) guarantees zero duplicate payments even under severe network replay or process crashes. Redis is strictly a fast-coordination layer and never the sole protection against duplicates.
- **6 Idempotency Scenarios Handled:**
  1. *First Request:* Fresh payment created, HTTP 201 Created.
  2. *Idempotent Replay:* Duplicate request with matching key & payload returns cached response with HTTP 200 OK and Idempotent-Replay: true header. No second provider charge.
  3. *Payload Conflict:* Same key with altered request payload rejected with HTTP 409 Conflict (IDEMPOTENCY_CONFLICT).
  4. *Concurrent Requests:* Parallel threads safely serialize via DB constraint + Redis lock; observing threads poll and return completed payment with HTTP 200 OK.
  5. *In-Flight Processing:* Active operations return HTTP 202 Accepted (IDEMPOTENCY_PROCESSING) without initiating duplicate provider calls.
  6. *24-Hour Expiration:* Expired keys for completed payments return the original transaction safely rather than double-charging.
- **Request Fingerprinting:** Deterministic SHA-256 canonical hashing across normalized monetary amounts, currency codes, payment methods, customer references, and descriptions.
- **Optimistic Locking:** JPA @Version column on Payment entity detects and rejects concurrent entity mutations with HTTP 409 Conflict (CONCURRENCY_CONFLICT).
- **Safe Query Retries vs. Charge Safety Invariant:** Strict distinction between safe read queries (status verification with exponential backoff) and unsafe charges (blind retries strictly prohibited on network timeouts to prevent duplicate charges).
- **Redis Coordination with Automated Circuit Breaker:** Safe Lua atomic lock release script with 30-second circuit breaker that trips automatically on Redis outage, falling back directly to database-level synchronization with zero duplicate risk.

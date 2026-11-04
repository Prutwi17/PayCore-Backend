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
- Maven 3.9+
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
mvn clean spring-boot:run
```

API Server starts at: `http://localhost:8080`  
Swagger UI Documentation: `http://localhost:8080/swagger-ui/index.html`  
Actuator Health: `http://localhost:8080/actuator/health`

---

## 🧪 Testing
```bash
# Run unit & integration test suites
mvn clean verify
```

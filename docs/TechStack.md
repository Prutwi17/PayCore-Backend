# PAYCORE — TECHNOLOGY STACK SPECIFICATION

## 1. Overview
PayCore adopts a high-performance, industry-proven enterprise stack emphasizing strong typing, ACID persistence, distributed streaming, in-memory caching, and robust security.

---

## 2. Core Backend Technologies

| Technology | Version | Purpose | Architectural Rationale |
|---|---|---|---|
| **Java** | 17 (LTS) | Core Programming Language | Enhanced records, pattern matching, sealed classes, robust concurrency, long-term enterprise stability. |
| **Spring Boot** | 3.2.x | Application Framework | Rapid enterprise bootstrapping, auto-configured production metrics, modular dependency injection, native JPA/Security integration. |
| **Spring Web** | 3.2.x (Spring MVC) | RESTful Web Layer | Mature, thread-per-request model with strong filter chain integration and bean validation. |
| **Spring Data JPA / Hibernate 6** | Latest with Spring Boot 3.2 | ORM & Persistence | Declarative repositories, query optimization, first and second-level caching, transaction synchronization. |
| **Spring Security 6** | Latest with Spring Boot 3.2 | Security & Authorization | Stateless security filters, custom API key filters, BCrypt password encoder, role-based authorization rules. |
| **JWT (io.jsonwebtoken / jjwt)** | 0.12.x | Authentication Tokens | Compact, stateless cryptographically signed tokens (HMAC-SHA256) for Admin and Merchant dashboard sessions. |
| **Spring Validation (Hibernate Validator)** | 8.x | Input Validation | JSR-380 bean validation (`@NotNull`, `@DecimalMin`, `@Pattern`, `@Size`) ensuring invalid inputs never reach domain services. |
| **Spring Boot Actuator** | 3.2.x | Telemetry & Observability | Health checks (`/actuator/health`), metrics (`/actuator/metrics`), info endpoints, Prometheus-ready scrapers. |
| **Apache Kafka (Spring Kafka)** | 3.6.x (Kafka Client 3.6.x) | Asynchronous Messaging & Webhooks | High-throughput distributed event streaming for non-blocking webhook delivery, audit publishing, and asynchronous retry workflows. |
| **Redis (Spring Data Redis / Jedis or Lettuce)** | 7.x | Caching & Distributed Locking | Sub-millisecond distributed lock acquisition for in-flight idempotency locks, temporary token blacklisting, and rate limiting. |
| **MySQL** | 8.0 / 8.4 | Primary Relational Database | ACID guarantees, row-level locking (`SELECT ... FOR UPDATE`), transactional integrity for ledger and monetary state transitions. |
| **Maven** | 3.9+ | Build & Dependency Management | Standardized build lifecycle, multi-phase testing, reproducible dependency resolution. |

---

## 3. Database & Caching Architecture
- **MySQL 8.0:**
  - Storage Engine: `InnoDB`
  - Default Charset: `utf8mb4` with collation `utf8mb4_unicode_ci`
  - Transaction Isolation Level: `READ COMMITTED`
  - Connection Pool: `HikariCP` (configured with `maximum-pool-size: 20`, `connection-timeout: 30000ms`, `leak-detection-threshold: 2000ms`)
- **Redis 7.0:**
  - Memory Policy: `volatile-lru`
  - Distributed Lock Mechanism: Redisson or Spring Data Redis `SET resource_name my_random_value NX PX 10000` with Lua script unlock to ensure atomic release.

---

## 4. Asynchronous Messaging Architecture (Kafka)
- **Brokers:** Apache Kafka running with KRaft mode (or ZooKeeper in dev docker-compose)
- **Core Topics:**
  - `paycore.payment.events`: Emitted upon payment state transitions (`CREATED`, `SUCCESS`, `FAILED`, `UNKNOWN`).
  - `paycore.refund.events`: Emitted upon refund requests and completions.
  - `paycore.webhook.dispatch`: Consumed by webhook workers to deliver HTTP notifications to merchant endpoints.
  - `paycore.webhook.dispatch.retry`: Topic with exponential backoff delay for failed deliveries.
  - `paycore.webhook.dispatch.dlq`: Dead Letter Queue for permanently failed deliveries after max retry threshold.
  - `paycore.audit.events`: Async ingestion stream for centralized audit logging.

---

## 5. Testing Framework & Quality Engineering
- **JUnit 5 (Jupiter):** Modern testing engine for unit and integration suites.
- **Mockito 5:** Mocking framework for unit testing service layers without database overhead.
- **Spring Boot Test:** Context slicing (`@WebMvcTest`, `@DataJpaTest`) for targeted testing.
- **Testcontainers:** Dockerized container management for spinning up disposable MySQL, Redis, and Kafka instances during automated integration testing (`@Testcontainers`).
- **AssertJ:** Fluent assertions for readable, expressive test validations.

---

## 6. API Documentation & Schema
- **SpringDoc OpenAPI 2.x (Swagger UI 5.x):**
  - Generates OpenAPI 3.0 specification dynamically from annotated controllers.
  - Live interactive testing UI hosted at `/swagger-ui/index.html`.
  - Comprehensive parameter annotations (`@Parameter`, `@Schema`, `@ApiResponse`).

---

## 7. Version Compatibility Matrix

```
Java 17 (OpenJDK / Oracle JDK)
├── Spring Boot 3.2.4
│   ├── Spring Framework 6.1.5
│   ├── Spring Security 6.2.3
│   ├── Hibernate 6.4.4.Final
│   ├── Spring Data JPA 3.2.4
│   ├── Spring Kafka 3.1.3
│   └── Spring Data Redis 3.2.4
├── MySQL Connector/J 8.3.0
├── JJWT 0.12.5
└── SpringDoc OpenAPI 2.5.0
```

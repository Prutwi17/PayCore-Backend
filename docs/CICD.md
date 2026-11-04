# PAYCORE — CI/CD & DEVOPS SPECIFICATION

## 1. DevOps Architecture
PayCore utilizes automated CI/CD pipelines driven by GitHub Actions and containerized infrastructure orchestrated via Docker and Docker Compose. No paid CI/CD tools or proprietary services are required.

---

## 2. GitHub Actions Workflow: Backend Pipeline

File: `.github/workflows/backend-ci.yml`

```yaml
name: PayCore Backend CI

on:
  push:
    branches: [ main, 'feature/**' ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-test:
    name: Build & Test (Java 17 / Maven)
    runs-on: ubuntu-latest

    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_DATABASE: paycore_test_db
          MYSQL_ROOT_PASSWORD: rootpassword
        ports:
          - 3306:3306
        options: --health-cmd="mysqladmin ping" --health-interval=10s --health-timeout=5s --health-retries=3

      redis:
        image: redis:7-alpine
        ports:
          - 6379:6379
        options: --health-cmd="redis-cli ping" --health-interval=10s --health-timeout=5s --health-retries=3

    steps:
      - name: Checkout Source Code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Compile and Run Unit & Integration Tests
        run: mvn clean verify -B
        env:
          SPRING_PROFILES_ACTIVE: test
          PAYCORE_DB_URL: jdbc:mysql://localhost:3306/paycore_test_db
          PAYCORE_DB_USERNAME: root
          PAYCORE_DB_PASSWORD: rootpassword
          PAYCORE_REDIS_HOST: localhost
          PAYCORE_REDIS_PORT: 6379

      - name: Generate Test Report
        uses: dorny/test-reporter@v1
        if: always()
        with:
          name: Backend JUnit Test Results
          path: target/surefire-reports/*.xml
          reporter: java-junit
```

---

## 3. Dockerfile (Production Multi-Stage Build)

```dockerfile
# Stage 1: Build JAR with Maven
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal Distroless / Temurin JRE Runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Non-root user for security
RUN groupadd -r paycore && useradd -r -g paycore paycore
USER paycore

COPY --from=builder /app/target/paycore-backend-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

---

## 4. Local Development Docker Compose Setup

File: `docker-compose.yml`

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: paycore-mysql
    restart: unless-stopped
    environment:
      MYSQL_DATABASE: paycore_db
      MYSQL_USER: paycore_user
      MYSQL_PASSWORD: paycore_password
      MYSQL_ROOT_PASSWORD: root_password
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    container_name: paycore-redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: paycore-kafka
    restart: unless-stopped
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092'
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka:29093'
      KAFKA_LISTENERS: 'PLAINTEXT://0.0.0.0:29092,CONTROLLER://0.0.0.0:29093,PLAINTEXT_HOST://0.0.0.0:9092'
      KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT'
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
      KAFKA_LOG_DIRS: '/tmp/kraft-combined-logs'
      CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qk'

  backend:
    build:
      context: ./PayCore-Backend
      dockerfile: Dockerfile
    container_name: paycore-backend
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      PAYCORE_DB_URL: jdbc:mysql://mysql:3306/paycore_db?useSSL=false&serverTimezone=UTC
      PAYCORE_DB_USERNAME: paycore_user
      PAYCORE_DB_PASSWORD: paycore_password
      PAYCORE_REDIS_HOST: redis
      PAYCORE_REDIS_PORT: 6379
      PAYCORE_KAFKA_BROKERS: kafka:29092
    depends_on:
      - mysql
      - redis
      - kafka

volumes:
  mysql_data:
  redis_data:
```

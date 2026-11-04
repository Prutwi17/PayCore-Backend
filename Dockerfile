# ===================================================================
# PayCore Backend — Multi-Stage Production Dockerfile
# Stage 1: Build Application with Maven and Java 17
# Stage 2: Minimal Distroless / Temurin JRE 17 Runtime
# ===================================================================

FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime Stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Create unprivileged runtime user
RUN groupadd -r paycore && useradd -r -g paycore paycore
USER paycore

COPY --from=builder /app/target/paycore-backend-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

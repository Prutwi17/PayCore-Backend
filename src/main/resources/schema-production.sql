-- ===================================================================
-- PayCore Payment Processing Engine — Production Database Schema
-- Target: MySQL 8.0+
-- Monetary Precision: DECIMAL(19, 4) — No float / double
-- ===================================================================

CREATE DATABASE IF NOT EXISTS paycore_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE paycore_db;

-- -------------------------------------------------------------------
-- 1. Roles and Users
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at TIMESTAMP NULL,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_email (email),
    INDEX idx_users_email_verified (email_verified)
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
);

-- Seed core system roles
INSERT IGNORE INTO roles (name) VALUES ('ROLE_ADMIN'), ('ROLE_MERCHANT'), ('ROLE_CUSTOMER');

-- -------------------------------------------------------------------
-- 2. Merchants & API Credentials
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS merchants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    merchant_code VARCHAR(32) NOT NULL UNIQUE,
    business_name VARCHAR(255) NOT NULL,
    business_type VARCHAR(100) NULL,
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(50) NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_merchants_code (merchant_code),
    INDEX idx_merchants_status (status)
);

CREATE TABLE IF NOT EXISTS merchant_api_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    environment VARCHAR(20) NOT NULL DEFAULT 'TEST',
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP NULL,
    CONSTRAINT fk_api_key_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE CASCADE,
    INDEX idx_api_key_hash (key_hash),
    INDEX idx_api_key_merchant_active (merchant_id, active)
);

-- -------------------------------------------------------------------
-- 3. Authentication Tokens (SHA-256 Hashed at Rest)
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_evt_token_hash (token_hash),
    INDEX idx_evt_expires (expires_at)
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_prt_token_hash (token_hash),
    INDEX idx_prt_expires (expires_at)
);


-- ===================================================================
-- External OAuth / Identity Provider Mappings
-- ===================================================================
CREATE TABLE IF NOT EXISTS user_external_identities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(50) NOT NULL DEFAULT 'GOOGLE',
    provider_subject VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_external_identity_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT fk_external_identity_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_external_identities_user_id (user_id),
    INDEX idx_external_identities_email (email)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP NULL,
    replaced_by_token_hash VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_rt_token_hash (token_hash),
    INDEX idx_rt_user_revoked (user_id, revoked),
    INDEX idx_rt_expires (expires_at)
);

CREATE TABLE IF NOT EXISTS admin_setup_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ast_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_ast_token_hash (token_hash),
    INDEX idx_ast_expires (expires_at)
);

-- -------------------------------------------------------------------
-- 4. Payments & Payment Attempts
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_reference VARCHAR(64) NOT NULL UNIQUE,
    merchant_id BIGINT NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    customer_email VARCHAR(255) NULL,
    customer_reference VARCHAR(100) NULL,
    description VARCHAR(255) NULL,
    idempotency_key VARCHAR(128) NULL,
    provider VARCHAR(50) NULL,
    provider_transaction_id VARCHAR(128) NULL,
    failure_code VARCHAR(50) NULL,
    failure_message TEXT NULL,
    action_url TEXT NULL,
    amount_refund_reserved DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    amount_refunded DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE RESTRICT,
    CONSTRAINT uk_merchant_idempotency_key UNIQUE (merchant_id, idempotency_key),
    INDEX idx_payments_merchant_status (merchant_id, status),
    INDEX idx_payments_reference (payment_reference),
    INDEX idx_payments_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS payment_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_transaction_id VARCHAR(128) NULL,
    status VARCHAR(50) NOT NULL,
    failure_code VARCHAR(50) NULL,
    failure_message TEXT NULL,
    latency_ms BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attempt_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE,
    INDEX idx_attempts_payment (payment_id)
);

-- -------------------------------------------------------------------
-- 5. Refunds
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refunds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_reference VARCHAR(64) NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reason VARCHAR(255) NULL,
    status VARCHAR(50) NOT NULL,
    provider_refund_id VARCHAR(128) NULL,
    failure_code VARCHAR(50) NULL,
    failure_message TEXT NULL,
    idempotency_key VARCHAR(128) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
    CONSTRAINT fk_refund_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE RESTRICT,
    CONSTRAINT uk_merchant_refund_idempotency UNIQUE (merchant_id, idempotency_key),
    INDEX idx_refunds_payment (payment_id),
    INDEX idx_refunds_merchant_status (merchant_id, status),
    INDEX idx_refunds_reference (refund_reference)
);

-- -------------------------------------------------------------------
-- 6. Transactional Outbox & Inbox (Kafka Event Architecture)
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP NULL,
    last_error TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    INDEX idx_outbox_status_retry (status, next_retry_at, retry_count),
    INDEX idx_outbox_event_id (event_id)
);

CREATE TABLE IF NOT EXISTS inbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inbox_event_consumer UNIQUE (event_id, consumer_name),
    INDEX idx_inbox_event_consumer (event_id, consumer_name)
);

-- -------------------------------------------------------------------
-- 7. Webhook Endpoints & Deliveries
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS webhook_endpoints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    endpoint_reference VARCHAR(64) NOT NULL UNIQUE,
    merchant_id BIGINT NOT NULL,
    url VARCHAR(1024) NOT NULL,
    secret_key_encrypted TEXT NOT NULL,
    secret_key_hash VARCHAR(64) NOT NULL,
    subscribed_events VARCHAR(1024) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE CASCADE,
    INDEX idx_webhooks_merchant_active (merchant_id, active),
    INDEX idx_webhooks_reference (endpoint_reference)
);

CREATE TABLE IF NOT EXISTS webhook_delivery_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    attempt_reference VARCHAR(64) NOT NULL UNIQUE,
    endpoint_id BIGINT NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    http_status_code INT NULL,
    response_body TEXT NULL,
    attempt_number INT NOT NULL DEFAULT 1,
    next_retry_at TIMESTAMP NULL,
    execution_time_ms BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_delivery_endpoint FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    INDEX idx_delivery_status_retry (status, next_retry_at),
    INDEX idx_delivery_event (event_id)
);

-- -------------------------------------------------------------------
-- 8. Reconciliation Runs & Discrepancies
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reconciliation_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_reference VARCHAR(64) NOT NULL UNIQUE,
    provider VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NULL,
    total_records_processed INT NOT NULL DEFAULT 0,
    matched_count INT NOT NULL DEFAULT 0,
    discrepancy_count INT NOT NULL DEFAULT 0,
    discrepancy_summary TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_reconciliation_status (status),
    INDEX idx_reconciliation_created (created_at)
);

CREATE TABLE IF NOT EXISTS reconciliation_discrepancies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    discrepancy_reference VARCHAR(64) NOT NULL UNIQUE,
    run_id BIGINT NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    discrepancy_type VARCHAR(50) NOT NULL,
    internal_record_snapshot LONGTEXT NULL,
    provider_record_snapshot LONGTEXT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    resolution_notes TEXT NULL,
    resolved_by VARCHAR(255) NULL,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_discrepancy_run FOREIGN KEY (run_id) REFERENCES reconciliation_runs(id) ON DELETE CASCADE,
    INDEX idx_discrepancies_run_status (run_id, status),
    INDEX idx_discrepancies_aggregate (aggregate_type, aggregate_id)
);

-- -------------------------------------------------------------------
-- 9. Immutable Audit Logs
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    details LONGTEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_created_at (created_at),
    INDEX idx_audit_actor (actor),
    INDEX idx_audit_resource (resource_type, resource_id)
);

-- -------------------------------------------------------------------
-- 10. Unified Transaction Ledger View (v_transactions)
-- -------------------------------------------------------------------
CREATE OR REPLACE VIEW v_transactions AS
SELECT
    CONCAT('PAY_', p.payment_reference) AS transaction_id,
    p.payment_reference AS reference,
    'PAYMENT' AS type,
    p.merchant_id AS merchant_id,
    p.amount AS amount,
    p.currency AS currency,
    p.status AS status,
    p.payment_method AS payment_method,
    p.customer_email AS customer_email,
    p.customer_reference AS customer_reference,
    p.description AS description,
    p.created_at AS created_at
FROM payments p
UNION ALL
SELECT
    CONCAT('REF_', r.refund_reference) AS transaction_id,
    r.refund_reference AS reference,
    'REFUND' AS type,
    r.merchant_id AS merchant_id,
    r.amount AS amount,
    r.currency AS currency,
    r.status AS status,
    'REFUND' AS payment_method,
    p.customer_email AS customer_email,
    p.customer_reference AS customer_reference,
    r.reason AS description,
    r.created_at AS created_at
FROM refunds r
JOIN payments p ON r.payment_id = p.id;

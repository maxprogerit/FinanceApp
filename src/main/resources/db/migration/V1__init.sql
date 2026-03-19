-- ============================================================
-- V1 — Initial schema for Smart Finance Dashboard (PostgreSQL)
-- Run automatically by Flyway on first startup with prod profile.
-- ============================================================

-- Users
CREATE TABLE IF NOT EXISTS users (
    id                         BIGSERIAL PRIMARY KEY,
    username                   VARCHAR(255) NOT NULL UNIQUE,
    email                      VARCHAR(255) NOT NULL UNIQUE,
    password                   VARCHAR(255) NOT NULL,
    role                       VARCHAR(50)  NOT NULL DEFAULT 'USER',
    base_currency              VARCHAR(10)  NOT NULL DEFAULT 'USD',
    theme                      VARCHAR(50)  NOT NULL DEFAULT 'light',
    provider                   VARCHAR(50),
    provider_id                VARCHAR(255),
    email_verified             BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verification_token   VARCHAR(255),
    password_reset_token       VARCHAR(255),
    password_reset_expiry      TIMESTAMP,
    created_at                 TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Transactions
CREATE TABLE IF NOT EXISTS transactions (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type                 VARCHAR(20)  NOT NULL,          -- INCOME | EXPENSE
    amount               NUMERIC(19,2) NOT NULL,
    currency             VARCHAR(10)  NOT NULL,
    category             VARCHAR(255) NOT NULL,
    description          VARCHAR(500),
    transaction_date     TIMESTAMP    NOT NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP,
    is_recurring         BOOLEAN      NOT NULL DEFAULT FALSE,
    recurring_frequency  VARCHAR(50),                    -- DAILY | WEEKLY | MONTHLY | YEARLY
    tags                 TEXT,
    storage_type         VARCHAR(255),
    income_source        VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_transactions_user_id ON transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_type    ON transactions(type);
CREATE INDEX IF NOT EXISTS idx_transactions_date    ON transactions(transaction_date);

-- Budgets
CREATE TABLE IF NOT EXISTS budgets (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category     VARCHAR(255)  NOT NULL,
    limit_amount NUMERIC(19,2) NOT NULL,
    spent_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    period       VARCHAR(50)   NOT NULL,  -- MONTHLY | QUARTERLY | YEARLY
    start_date   DATE          NOT NULL,
    end_date     DATE          NOT NULL,
    currency     VARCHAR(10),
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP,
    is_active    BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_budgets_user_id ON budgets(user_id);

-- Investments
CREATE TABLE IF NOT EXISTS investments (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    asset_type     VARCHAR(50)   NOT NULL,  -- STOCK | CRYPTO | BOND | MUTUAL_FUND | ETF
    symbol         VARCHAR(50)   NOT NULL,
    asset_name     VARCHAR(255)  NOT NULL,
    quantity       NUMERIC(19,8) NOT NULL,
    purchase_price NUMERIC(19,2) NOT NULL,
    current_price  NUMERIC(19,2),
    currency       VARCHAR(10)   NOT NULL,
    purchase_date  TIMESTAMP     NOT NULL,
    notes          TEXT,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_investments_user_id ON investments(user_id);

-- Financial Goals
CREATE TABLE IF NOT EXISTS financial_goals (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_name      VARCHAR(255)  NOT NULL,
    description    TEXT,
    target_amount  NUMERIC(19,2) NOT NULL,
    current_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    currency       VARCHAR(10)   NOT NULL,
    target_date    DATE          NOT NULL,
    start_date     DATE          NOT NULL DEFAULT CURRENT_DATE,
    category       VARCHAR(100),
    status         VARCHAR(50)   NOT NULL DEFAULT 'IN_PROGRESS',
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_goals_user_id ON financial_goals(user_id);

-- Debts
CREATE TABLE IF NOT EXISTS debts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(255)  NOT NULL,
    amount      NUMERIC(19,2) NOT NULL,
    currency    VARCHAR(10)   NOT NULL,
    due_date    DATE,
    direction   VARCHAR(20)   NOT NULL,  -- I_OWE | THEY_OWE
    description TEXT,
    status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SETTLED
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    settled_at  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_debts_user_id ON debts(user_id);

-- Alerts
CREATE TABLE IF NOT EXISTS alerts (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT   REFERENCES users(id) ON DELETE CASCADE,
    type                VARCHAR(50)   NOT NULL,
    severity            VARCHAR(20)   NOT NULL,
    title               VARCHAR(255)  NOT NULL,
    message             VARCHAR(1000) NOT NULL,
    related_entity_id   BIGINT,
    related_entity_type VARCHAR(50),
    is_read             BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    read_at             TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alerts_user_id ON alerts(user_id);

-- Categories (global defaults + user-custom)
CREATE TABLE IF NOT EXISTS categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    type        VARCHAR(20)  NOT NULL,  -- INCOME | EXPENSE
    icon        VARCHAR(50),
    color       VARCHAR(20),
    description TEXT,
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Storage Types (builtIn or user-created)
CREATE TABLE IF NOT EXISTS storage_types (
    id       BIGSERIAL PRIMARY KEY,
    name     VARCHAR(255) NOT NULL UNIQUE,
    icon     VARCHAR(50),
    built_in BOOLEAN NOT NULL DEFAULT FALSE
);

-- Income Sources (builtIn or user-created)
CREATE TABLE IF NOT EXISTS income_sources (
    id       BIGSERIAL PRIMARY KEY,
    name     VARCHAR(255) NOT NULL UNIQUE,
    icon     VARCHAR(50),
    built_in BOOLEAN NOT NULL DEFAULT FALSE
);

-- Categorization Rules
CREATE TABLE IF NOT EXISTS categorization_rules (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pattern          VARCHAR(255) NOT NULL,
    category         VARCHAR(255) NOT NULL,
    transaction_type VARCHAR(20),
    priority         INTEGER      NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cat_rules_user_id ON categorization_rules(user_id);

-- User Subscriptions (Stripe)
CREATE TABLE IF NOT EXISTS user_subscriptions (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT      NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    stripe_customer_id     VARCHAR(255) NOT NULL,
    stripe_subscription_id VARCHAR(255),
    status                 VARCHAR(50)  NOT NULL DEFAULT 'trialing',
    plan                   VARCHAR(50)  NOT NULL DEFAULT 'FREE',
    current_period_end     TIMESTAMP,
    trial_end              TIMESTAMP,
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP
);

-- ── Seed built-in storage types ───────────────────────────────────────────────
INSERT INTO storage_types (name, icon, built_in) VALUES
    ('Cash',         '💵', TRUE),
    ('Bank Card',    '💳', TRUE),
    ('Bank Account', '🏦', TRUE),
    ('Savings',      '💰', TRUE),
    ('Crypto',       '₿',  TRUE)
ON CONFLICT (name) DO NOTHING;

-- ── Seed built-in income sources ─────────────────────────────────────────────
INSERT INTO income_sources (name, icon, built_in) VALUES
    ('Salary',      '💼', TRUE),
    ('Freelance',   '🖥️', TRUE),
    ('Reselling',   '🛍️', TRUE),
    ('Investments', '📈', TRUE)
ON CONFLICT (name) DO NOTHING;

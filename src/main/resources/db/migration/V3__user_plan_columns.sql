-- ============================================================
-- V3 — Add plan / Stripe fields directly to users table.
-- Enables plan-based feature gating without joining user_subscriptions.
-- ============================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS plan                   VARCHAR(50)  NOT NULL DEFAULT 'FREE',
    ADD COLUMN IF NOT EXISTS stripe_customer_id     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS stripe_subscription_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS premium_active         BOOLEAN      NOT NULL DEFAULT FALSE;

-- Backfill plan data from existing user_subscriptions records
UPDATE users u
SET plan                   = us.plan,
    stripe_customer_id     = us.stripe_customer_id,
    stripe_subscription_id = us.stripe_subscription_id,
    premium_active         = (us.status IN ('active', 'trialing') AND us.plan != 'FREE')
FROM user_subscriptions us
WHERE u.id = us.user_id;

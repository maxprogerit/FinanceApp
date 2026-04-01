-- ============================================================
-- V2 — Fix base_currency default: USD → EUR
--
-- Root cause: V1 defined base_currency DEFAULT 'USD', and the
-- Java entity field also defaulted to "USD". All users created
-- before this migration have base_currency = 'USD' because no
-- currency was ever set during registration.
--
-- This migration:
--   1. Changes the column default so new users get EUR.
--   2. Backfills all existing rows that still carry the wrong
--      default value to EUR.
-- ============================================================

-- Step 1: update the column default for future inserts
ALTER TABLE users
    ALTER COLUMN base_currency SET DEFAULT 'EUR';

-- Step 2: backfill every user whose currency was never explicitly
-- changed (still holds the old hardcoded default value 'USD').
UPDATE users
SET    base_currency = 'EUR'
WHERE  base_currency = 'USD';

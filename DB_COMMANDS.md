# Database Command Reference — Smart Finance Dashboard

## Connecting to PostgreSQL

```bash
# Connect to the finance database
psql -U postgres -d finance

# One-liner: run a single query without entering the shell
psql -U postgres -d finance -c "SELECT COUNT(*) FROM users;"
```

**psql quick reference**

| Command | What it does |
|---|---|
| `\dt` | List all tables |
| `\d users` | Show columns + types for a table |
| `\di` | List all indexes |
| `\q` | Quit |
| `\x` | Toggle expanded (vertical) row display |

---

## Reset the Database (dev only)

```bash
# Drop everything and start fresh — Flyway will recreate schema on next app start
psql -U postgres -c "DROP DATABASE IF EXISTS finance;"
psql -U postgres -c "CREATE DATABASE finance;"
```

---

## Flyway

```bash
# Check which migrations have been applied
psql -U postgres -d finance -c "SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank;"

# If you edited an existing migration and get a checksum mismatch in dev,
# delete the history and wipe the DB instead of using repair.
# In prod, use: mvn flyway:repair
```

---

## View Users

```sql
-- All users (safe fields only)
SELECT id, username, email, role, plan, premium_active, email_verified, provider, created_at
FROM users
ORDER BY created_at DESC;

-- One user by email
SELECT * FROM users WHERE email = 'someone@example.com';

-- One user by username
SELECT * FROM users WHERE username = 'john';

-- Users who haven't verified their email
SELECT id, username, email, created_at
FROM users
WHERE email_verified = FALSE;

-- OAuth users (Google login)
SELECT id, username, email, provider, provider_id
FROM users
WHERE provider IS NOT NULL;

-- Users with a pending password reset
SELECT id, username, email, password_reset_expiry
FROM users
WHERE password_reset_token IS NOT NULL;

-- Users by plan
SELECT id, username, email, plan, premium_active
FROM users
WHERE plan = 'PRO';   -- or 'FREE' / 'PREMIUM'
```

---

## Edit a User

```sql
-- Change username
UPDATE users SET username = 'new_username' WHERE email = 'someone@example.com';

-- Change email
UPDATE users SET email = 'newemail@example.com' WHERE username = 'john';

-- Change role (e.g. USER → ADMIN)
UPDATE users SET role = 'ADMIN' WHERE email = 'someone@example.com';

-- Change plan
UPDATE users SET plan = 'PRO', premium_active = TRUE WHERE email = 'someone@example.com';

-- Downgrade to free
UPDATE users SET plan = 'FREE', premium_active = FALSE WHERE email = 'someone@example.com';

-- Change base currency
UPDATE users SET base_currency = 'EUR' WHERE email = 'someone@example.com';

-- Change theme
UPDATE users SET theme = 'dark' WHERE email = 'someone@example.com';
```

---

## Email Verification

```sql
-- Manually verify a user's email (use when email link isn't working)
UPDATE users
SET email_verified = TRUE,
    email_verification_token = NULL,
    email_verification_token_expiry = NULL
WHERE email = 'someone@example.com';

-- Unverify a user (force them to re-verify)
UPDATE users SET email_verified = FALSE WHERE email = 'someone@example.com';

-- Check verification token for a user
SELECT username, email, email_verification_token, email_verification_token_expiry, email_verified
FROM users
WHERE email = 'someone@example.com';

-- Clear a stale verification token
UPDATE users
SET email_verification_token = NULL, email_verification_token_expiry = NULL
WHERE email = 'someone@example.com';
```

---

## Password Reset

```sql
-- Check if a reset token exists and when it expires
SELECT username, email, password_reset_token, password_reset_expiry
FROM users
WHERE email = 'someone@example.com';

-- Clear a stuck/expired reset token manually
UPDATE users
SET password_reset_token = NULL, password_reset_expiry = NULL
WHERE email = 'someone@example.com';
```

> **Note:** You cannot set a plain-text password via SQL — passwords are BCrypt hashed.
> Use the Forgot Password flow in the app, or generate a hash at bcrypt-generator.com
> (cost factor 10) and update the column directly:
>
> ```sql
> UPDATE users
> SET password = '$2a$10$HASH_GOES_HERE'
> WHERE email = 'someone@example.com';
> ```

---

## Delete a User

> All child tables have `ON DELETE CASCADE`, so deleting the user row removes everything.

```sql
-- Quick delete (CASCADE handles the rest)
DELETE FROM users WHERE email = 'someone@example.com';
```

```sql
-- Manual delete in order if you need to inspect/archive data first
DELETE FROM alerts              WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
DELETE FROM categorization_rules WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
DELETE FROM user_subscriptions  WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
DELETE FROM debts               WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
DELETE FROM investments         WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
DELETE FROM financial_goals     WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
DELETE FROM budgets             WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
DELETE FROM transactions        WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
DELETE FROM users WHERE email = 'someone@example.com';
```

---

## Useful Debug Queries

```sql
-- Count users
SELECT COUNT(*) AS total_users FROM users;

-- Count transactions per user
SELECT u.username, u.email, COUNT(t.id) AS tx_count
FROM users u
LEFT JOIN transactions t ON t.user_id = u.id
GROUP BY u.id, u.username, u.email
ORDER BY tx_count DESC;

-- List all tables in the database
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;

-- Show columns for a table
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'users'
ORDER BY ordinal_position;

-- Find duplicate emails (should be zero)
SELECT email, COUNT(*) FROM users GROUP BY email HAVING COUNT(*) > 1;

-- Registrations in the last 7 days
SELECT username, email, created_at
FROM users
WHERE created_at >= NOW() - INTERVAL '7 days'
ORDER BY created_at DESC;

-- Users linked to Google
SELECT username, email, provider
FROM users
WHERE provider = 'google';

-- Transactions summary per user
SELECT u.username, t.type, COUNT(*) AS count, SUM(t.amount) AS total
FROM transactions t
JOIN users u ON u.id = t.user_id
GROUP BY u.username, t.type
ORDER BY u.username, t.type;

-- Active budgets
SELECT u.username, b.category, b.limit_amount, b.spent_amount, b.period
FROM budgets b
JOIN users u ON u.id = b.user_id
WHERE b.is_active = TRUE
ORDER BY u.username;

-- Database size
SELECT pg_size_pretty(pg_database_size('finance')) AS db_size;

-- Table sizes
SELECT relname AS table, pg_size_pretty(pg_total_relation_size(relid)) AS size
FROM pg_catalog.pg_statio_user_tables
ORDER BY pg_total_relation_size(relid) DESC;
```

---

## PostgreSQL Syntax Reference

| Task | PostgreSQL syntax |
|---|---|
| Current timestamp | `NOW()` |
| Subtract N days | `NOW() - INTERVAL '7 days'` |
| String contains | `LIKE '%text%'` |
| Case-insensitive search | `ILIKE '%text%'` |
| List tables (psql shell) | `\dt` |
| Show columns (psql shell) | `\d table_name` |
| Database size | `pg_size_pretty(pg_database_size('finance'))` |
| Cast to text | `value::text` |
| Boolean true/false | `TRUE` / `FALSE` |

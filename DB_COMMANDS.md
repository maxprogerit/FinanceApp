# Database Command Reference — Smart Finance Dashboard

## Connecting to the H2 Console (dev only)

1. Start the app: `mvn spring-boot:run`
2. Open: http://localhost:8081/h2-console
3. Use these settings:
   - **JDBC URL**: `jdbc:h2:file:./data/financedb`
   - **Username**: `sa`
   - **Password**: *(leave empty)*

---

## View Users

```sql
-- All users
SELECT id, username, email, role, email_verified, provider, created_at
FROM users
ORDER BY created_at DESC;

-- One user by email
SELECT * FROM users WHERE email = 'someone@example.com';

-- One user by username
SELECT * FROM users WHERE username = 'john';

-- Users who haven't verified their email
SELECT id, username, email, created_at
FROM users
WHERE email_verified = FALSE OR email_verified IS NULL;

-- OAuth users (Google login)
SELECT id, username, email, provider, provider_id
FROM users
WHERE provider IS NOT NULL;

-- Users with a pending password reset
SELECT id, username, email, password_reset_expiry
FROM users
WHERE password_reset_token IS NOT NULL;
```

---

## Edit a User

```sql
-- Change username
UPDATE users SET username = 'new_username' WHERE email = 'someone@example.com';

-- Change email
UPDATE users SET email = 'newemail@example.com' WHERE username = 'john';

-- Change role  (e.g. USER → ADMIN)
UPDATE users SET role = 'ADMIN' WHERE email = 'someone@example.com';

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
SET email_verified = TRUE, email_verification_token = NULL
WHERE email = 'someone@example.com';

-- Unverify a user (force them to re-verify)
UPDATE users
SET email_verified = FALSE
WHERE email = 'someone@example.com';

-- Check verification token for a user
SELECT username, email, email_verification_token, email_verified
FROM users
WHERE email = 'someone@example.com';

-- Clear a stale verification token
UPDATE users
SET email_verification_token = NULL
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

> **Note:** You cannot set a plain-text password directly via SQL — passwords are BCrypt hashed.
> To reset a password, use the Forgot Password flow in the app, or generate a BCrypt hash externally
> (e.g. https://bcrypt-generator.com, cost factor 10) and update the `password` column:
>
> ```sql
> UPDATE users
> SET password = '$2a$10$HASH_GOES_HERE'
> WHERE email = 'someone@example.com';
> ```

---

## Delete a User

> **Warning:** Deleting a user also deletes all their linked data if you have CASCADE deletes set,
> otherwise delete related records first.

```sql
-- Delete all user data in the right order (avoids FK constraint errors)
DELETE FROM transactions        WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
DELETE FROM budgets             WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
DELETE FROM financial_goals     WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
DELETE FROM investments         WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
DELETE FROM debts               WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
DELETE FROM categorization_rules WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
DELETE FROM user_subscriptions  WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
DELETE FROM alerts              WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');

-- Finally delete the user record
DELETE FROM users WHERE email = 'someone@example.com';
```

```sql
-- Quick delete if you know CASCADE is set up (or in dev where you don't care about data)
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

-- See all tables in the database
SHOW TABLES;

-- See columns of a table
SHOW COLUMNS FROM users;

-- Find duplicate emails (should be zero)
SELECT email, COUNT(*) FROM users GROUP BY email HAVING COUNT(*) > 1;

-- Recent logins / registrations (last 7 days)
SELECT username, email, created_at
FROM users
WHERE created_at >= DATEADD('DAY', -7, NOW())
ORDER BY created_at DESC;

-- Users linked to Google but also have a local password
SELECT username, email, provider
FROM users
WHERE provider = 'google' AND password IS NOT NULL;
```

---

## H2-Specific Syntax Tips

| Task | H2 syntax |
|---|---|
| Current timestamp | `NOW()` |
| Add N days | `DATEADD('DAY', N, date)` |
| String contains | `LIKE '%text%'` |
| Case-insensitive search | `LOWER(email) LIKE LOWER('%query%')` |
| Show all tables | `SHOW TABLES` |
| Show columns | `SHOW COLUMNS FROM table_name` |

> H2 SQL is mostly standard SQL. Most queries here will also work unchanged on PostgreSQL (prod).

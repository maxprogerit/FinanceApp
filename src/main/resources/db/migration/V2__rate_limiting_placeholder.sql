-- ============================================================
-- V2 — Add rate limiting support table (optional, Nginx-based is preferred)
-- Only needed if you implement DB-backed rate limiting
-- ============================================================

-- This migration is intentionally minimal.
-- Rate limiting is handled by Nginx (see deploy/nginx.conf).
-- This placeholder keeps the migration chain intact.

SELECT 1;

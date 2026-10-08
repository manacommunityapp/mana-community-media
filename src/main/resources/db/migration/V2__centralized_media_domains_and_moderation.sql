-- V2__centralized_media_domains_and_moderation.sql
-- Flyway migration: Support centralized domain owners, moderation workflow, and retention tiers

ALTER TABLE media_files
    ADD COLUMN IF NOT EXISTS moderation_status   VARCHAR(30) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS moderation_score    DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS moderation_flags    VARCHAR(500),
    ADD COLUMN IF NOT EXISTS moderated_by        BIGINT,
    ADD COLUMN IF NOT EXISTS moderated_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS retention_tier      VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS retention_expires_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_media_moderation ON media_files (moderation_status);
CREATE INDEX IF NOT EXISTS idx_media_retention  ON media_files (retention_tier, retention_expires_at);
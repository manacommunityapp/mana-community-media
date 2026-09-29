-- V1__create_media_files_table.sql
-- Flyway migration: initial schema for mana-community-media service

CREATE TYPE owner_type_enum   AS ENUM ('USER','COMMUNITY','TOURNAMENT','EVENT','POST','ANNOUNCEMENT','TEAM');
CREATE TYPE media_type_enum   AS ENUM ('AVATAR','BANNER','GALLERY_IMAGE','DOCUMENT','VIDEO','ATTACHMENT','LOGO','THUMBNAIL');
CREATE TYPE access_level_enum AS ENUM ('PUBLIC','PRIVATE');
CREATE TYPE file_status_enum  AS ENUM ('PENDING_UPLOAD','ACTIVE','SOFT_DELETED','HARD_DELETED');

CREATE TABLE media_files
(
    id                 BIGSERIAL PRIMARY KEY,
    community_id       BIGINT                  NOT NULL,
    owner_type         VARCHAR(50)             NOT NULL,
    owner_id           BIGINT,
    original_name      VARCHAR(512)            NOT NULL,
    s3_key             VARCHAR(1024)           NOT NULL UNIQUE,
    mime_type          VARCHAR(100),
    size_bytes         BIGINT,
    media_type         VARCHAR(30)             NOT NULL,
    access_level       VARCHAR(20)             NOT NULL DEFAULT 'PUBLIC',
    cdn_url            VARCHAR(1024),
    width_px           INT,
    height_px          INT,
    thumbnail_s3_key   VARCHAR(1024),
    thumbnail_cdn_url  VARCHAR(1024),
    status             VARCHAR(20)             NOT NULL DEFAULT 'ACTIVE',
    s3_version_id      VARCHAR(256),
    etag               VARCHAR(256),
    created_at         TIMESTAMPTZ             NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ,
    deleted_at         TIMESTAMPTZ
);

-- Indexes for common query patterns
CREATE INDEX idx_media_owner        ON media_files (owner_type, owner_id);
CREATE INDEX idx_media_community    ON media_files (community_id);
CREATE INDEX idx_media_access_level ON media_files (access_level);
CREATE INDEX idx_media_media_type   ON media_files (media_type);
CREATE INDEX idx_media_status       ON media_files (status);
CREATE INDEX idx_media_deleted_at   ON media_files (deleted_at) WHERE deleted_at IS NOT NULL;

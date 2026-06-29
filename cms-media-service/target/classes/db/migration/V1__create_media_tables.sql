CREATE SCHEMA IF NOT EXISTS media;

CREATE TABLE media.media_assets (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    original_name    VARCHAR(500) NOT NULL,
    stored_name      VARCHAR(500) NOT NULL,
    storage_path     TEXT NOT NULL,
    storage_backend  VARCHAR(50) NOT NULL DEFAULT 'LOCAL'
                     CHECK (storage_backend IN ('LOCAL','S3','GCS','AZURE_BLOB')),
    mime_type        VARCHAR(100) NOT NULL,
    file_size_bytes  BIGINT NOT NULL,
    checksum         VARCHAR(64),
    alt_text         TEXT,
    caption          TEXT,
    width            INT,
    height           INT,
    duration_ms      INT,
    is_public        BOOLEAN NOT NULL DEFAULT false,
    folder_path      TEXT NOT NULL DEFAULT '/',
    tags             TEXT[] DEFAULT '{}',
    metadata         JSONB NOT NULL DEFAULT '{}',
    cdn_url          TEXT,
    thumbnail_url    TEXT,
    uploaded_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    uploaded_by      UUID,
    deleted_at       TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_media_checksum_tenant
    ON media.media_assets(tenant_id, checksum)
    WHERE deleted_at IS NULL AND checksum IS NOT NULL;

CREATE INDEX idx_media_tenant ON media.media_assets(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_folder ON media.media_assets(folder_path text_pattern_ops) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_tags ON media.media_assets USING GIN(tags);

CREATE TABLE media.outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(200) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0,
    error_message   TEXT
);

CREATE INDEX idx_media_outbox_unprocessed
    ON media.outbox_events(created_at) WHERE processed_at IS NULL;

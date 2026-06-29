-- ============================================================
-- contentdb migration: V1__create_content_tables.sql
-- ============================================================

-- Create schema if not exists
CREATE SCHEMA IF NOT EXISTS content;

-- ============================================================
-- CONTENT ITEMS (universal content store)
-- ============================================================
CREATE TABLE content.content_items (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    content_type_id     UUID NOT NULL,
    content_type_code   VARCHAR(100) NOT NULL,
    title               VARCHAR(500) NOT NULL,
    slug                VARCHAR(500),
    body                JSONB NOT NULL DEFAULT '{}',
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN (
                            'DRAFT','IN_REVIEW','APPROVED','PUBLISHED',
                            'SCHEDULED','ARCHIVED','TRASHED','REJECTED'
                        )),
    category_id         UUID,
    category_code       VARCHAR(100),
    parent_id           UUID REFERENCES content.content_items(id),
    folder_path         TEXT,
    site_id             UUID NOT NULL,
    site_code           VARCHAR(100) NOT NULL,
    persona             VARCHAR(100),
    publish_at          TIMESTAMPTZ,
    expire_at           TIMESTAMPTZ,
    published_at        TIMESTAMPTZ,
    current_version     INT NOT NULL DEFAULT 1,
    workflow_instance_id UUID,
    media_ids           UUID[] DEFAULT '{}',
    tags                TEXT[] DEFAULT '{}',
    external_link       TEXT,
    order_index         INT NOT NULL DEFAULT 0,
    is_featured         BOOLEAN NOT NULL DEFAULT false,
    view_count          BIGINT NOT NULL DEFAULT 0,
    metadata            JSONB NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ,
    deleted_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0
);

-- ============================================================
-- CONTENT VERSIONS (immutable history)
-- ============================================================
CREATE TABLE content.content_versions (
    id              UUID PRIMARY KEY,
    content_item_id UUID NOT NULL REFERENCES content.content_items(id) ON DELETE CASCADE,
    version_number  INT NOT NULL,
    title           VARCHAR(500) NOT NULL,
    body            JSONB NOT NULL,
    status          VARCHAR(30) NOT NULL,
    change_summary  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    UNIQUE(content_item_id, version_number)
);

-- ============================================================
-- INDEXES FOR PERFORMANCE AND TENANT SECURITY
-- ============================================================
CREATE UNIQUE INDEX idx_content_slug_unique
    ON content.content_items(tenant_id, content_type_code, site_id, slug)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_content_type_status
    ON content.content_items(content_type_code, status)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_content_tenant
    ON content.content_items(tenant_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_content_category
    ON content.content_items(category_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_content_site_code
    ON content.content_items(site_code)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_content_publish_at
    ON content.content_items(publish_at)
    WHERE status = 'SCHEDULED';

CREATE INDEX idx_content_parent
    ON content.content_items(parent_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_content_body
    ON content.content_items USING GIN(body);

CREATE INDEX idx_content_tags
    ON content.content_items USING GIN(tags);

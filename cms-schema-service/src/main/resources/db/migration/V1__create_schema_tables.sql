-- ============================================================
-- schemadb migration: V1__create_schema_tables.sql
-- ============================================================

-- Create schema if not exists
CREATE SCHEMA IF NOT EXISTS schema;

-- ============================================================
-- CONTENT TYPES
-- ============================================================
CREATE TABLE schema.content_types (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    code            VARCHAR(100) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    description     TEXT,
    icon            VARCHAR(100),
    is_hierarchical BOOLEAN NOT NULL DEFAULT false,
    is_versionable  BOOLEAN NOT NULL DEFAULT true,
    is_schedulable  BOOLEAN NOT NULL DEFAULT true,
    has_workflow    BOOLEAN NOT NULL DEFAULT false,
    workflow_id     UUID,
    workflow_code   VARCHAR(100),
    default_persona VARCHAR(100),
    metadata        JSONB NOT NULL DEFAULT '{}',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','DRAFT','DEPRECATED')),
    version         INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    UNIQUE(tenant_id, code)
);

-- ============================================================
-- FIELD DEFINITIONS
-- ============================================================
CREATE TABLE schema.field_definitions (
    id               UUID PRIMARY KEY,
    content_type_id  UUID NOT NULL REFERENCES schema.content_types(id) ON DELETE CASCADE,
    field_key        VARCHAR(100) NOT NULL,
    display_label    VARCHAR(255) NOT NULL,
    field_type       VARCHAR(50) NOT NULL
                     CHECK (field_type IN (
                         'TEXT','NUMBER','DATE','DATETIME','BOOLEAN',
                         'FILE','RICHTEXT','JSON','RELATION',
                         'SELECT','MULTISELECT'
                     )),
    is_required      BOOLEAN NOT NULL DEFAULT false,
    is_searchable    BOOLEAN NOT NULL DEFAULT false,
    is_filterable    BOOLEAN NOT NULL DEFAULT false,
    is_listable      BOOLEAN NOT NULL DEFAULT true,
    display_order    INT NOT NULL DEFAULT 0,
    default_value    TEXT,
    validation_rules JSONB NOT NULL DEFAULT '{}',
    ui_config        JSONB NOT NULL DEFAULT '{}',
    relation_config  JSONB,
    group_name       VARCHAR(100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(content_type_id, field_key)
);

-- ============================================================
-- CATEGORIES
-- ============================================================
CREATE TABLE schema.categories (
    id              UUID PRIMARY KEY,
    content_type_id UUID NOT NULL REFERENCES schema.content_types(id) ON DELETE CASCADE,
    code            VARCHAR(100) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    parent_id       UUID REFERENCES schema.categories(id),
    description     TEXT,
    icon            VARCHAR(100),
    metadata        JSONB NOT NULL DEFAULT '{}',
    display_order   INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(content_type_id, code)
);

-- ============================================================
-- SITES
-- ============================================================
CREATE TABLE schema.sites (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    code         VARCHAR(100) NOT NULL,
    name         VARCHAR(255) NOT NULL,
    domain       VARCHAR(255),
    locale       VARCHAR(10) NOT NULL DEFAULT 'en',
    theme_config JSONB NOT NULL DEFAULT '{}',
    is_active    BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, code)
);

-- ============================================================
-- INDEXES FOR PERFORMANCE AND TENANT SECURITY
-- ============================================================
CREATE INDEX idx_schema_content_types_tenant ON schema.content_types(tenant_id);
CREATE INDEX idx_schema_field_definitions_ct ON schema.field_definitions(content_type_id);
CREATE INDEX idx_schema_categories_ct ON schema.categories(content_type_id);
CREATE INDEX idx_schema_sites_tenant ON schema.sites(tenant_id);

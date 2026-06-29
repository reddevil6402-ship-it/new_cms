# Database Structure Reference
## NextGen Enterprise CMS — Database-per-Service Design

> **Document Type:** Canonical Database Design Reference  
> **Project:** NextGen Enterprise CMS  
> **Version:** 2.0 (database-per-service)  
> **Supersedes:** Single-cluster 8-schema design  
> **See also:** `cms-architecture.md` (system architecture), `cms-diagrams.md` (ERD and container diagrams)

---

## Table of Contents

1. [Schema Strategy](#1-schema-strategy)
2. [Database Topology — 9 Separate Databases](#2-database-topology--9-separate-databases)
3. [Cross-Database Reference Rule](#3-cross-database-reference-rule)
4. [iamdb — IAM Service](#4-iamdb--iam-service)
5. [schemadb — Schema Service](#5-schemadb--schema-service)
6. [contentdb — Content Service](#6-contentdb--content-service)
7. [workflowdb — Workflow Service](#7-workflowdb--workflow-service)
8. [mediadb — Media Service](#8-mediadb--media-service)
9. [formdb — Form Service](#9-formdb--form-service)
10. [searchstore — Search Service (OpenSearch)](#10-searchstore--search-service-opensearch)
11. [notificationdb — Notification Service](#11-notificationdb--notification-service)
12. [auditdb — Audit Service](#12-auditdb--audit-service)
13. [Indexing Strategy](#13-indexing-strategy)
14. [Row-Level Security & Tenant Validation](#14-row-level-security--tenant-validation)
15. [Migration Strategy — 9 Independent Flyway Histories](#15-migration-strategy--9-independent-flyway-histories)
16. [Key Query Patterns](#16-key-query-patterns)
17. [ERD Summary — Per-Database Mini-ERDs](#17-erd-summary--per-database-mini-erds)
18. [Operational Tradeoffs](#18-operational-tradeoffs)

---

## 1. Schema Strategy

### 1.1 JSONB-Typed Content Body (Not EAV)

Traditional EAV (Entity-Attribute-Value) is avoided. The system uses a **JSONB `body` column** per content item with:

- **Flexibility:** Any field can be stored without schema migration
- **Performance:** PostgreSQL GIN index on `body` enables JSONB path queries at millisecond speeds
- **Integrity:** Content body is validated at write time against `field_definitions` (via API call to schema-service)
- **Queryability:** `body->>'tender_value'` or `body @> '{"status": "open"}'` with GIN index

### 1.2 Standard Audit Columns (All Tables)

Every table carries:

```sql
created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
created_by   UUID   -- logical ref to iamdb.users, no FK (separate database)
deleted_at   TIMESTAMPTZ      -- null = active, soft delete pattern
version      BIGINT NOT NULL DEFAULT 0  -- optimistic locking
```

### 1.3 UUID Primary Keys

All PKs use `UUID DEFAULT gen_random_uuid()`. UUIDv7 (time-ordered) is recommended for production to reduce B-tree index fragmentation.

### 1.4 Soft Delete Convention

No hard deletes on content. `deleted_at IS NULL` = active record. Application-level Hibernate filters or DB views filter out deleted records by default.

### 1.5 Outbox Pattern — Per-Database Local Tables

Every database that produces domain events contains its **own local `outbox_events` table**. Events are written in the same transaction as the business write. A Debezium CDC connector per database reads from its local outbox and publishes to Kafka. There is **no centralized `cms_core.outbox_events` table** — that pattern is incompatible with physically separate databases.

---

## 2. Database Topology — 9 Separate Databases

| # | Service | Database | Engine | Owns |
|---|---------|----------|--------|------|
| 1 | iam-service | **iamdb** | PostgreSQL 16 | `tenants`, `users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `api_keys`, `refresh_tokens`, local `outbox_events` |
| 2 | schema-service | **schemadb** | PostgreSQL 16 | `content_types`, `field_definitions`, `categories`, `sites`, `menus`, `menu_items`, local `outbox_events` |
| 3 | content-service | **contentdb** | PostgreSQL 16 | `content_items`, `content_versions`, local `outbox_events` |
| 4 | workflow-service | **workflowdb** | PostgreSQL 16 | `workflow_definitions`, `workflow_instances`, `workflow_history`, local `outbox_events` |
| 5 | media-service | **mediadb** | PostgreSQL 16 | `media_assets`, local `outbox_events` |
| 6 | form-service | **formdb** | PostgreSQL 16 | `form_definitions`, `form_submissions`, local `outbox_events` |
| 7 | search-service | **searchstore** | **OpenSearch 2.x** | Denormalized search documents — no PostgreSQL schema |
| 8 | notification-service | **notificationdb** | PostgreSQL 16 | `notification_templates`, `notification_log`, optional local `outbox_events` |
| 9 | audit-service | **auditdb** | PostgreSQL 16 (partitioned) | `audit_events` — pure Kafka consumer, no outbox needed |

**PostgreSQL instance count:** 7 instances (iamdb, schemadb, contentdb, workflowdb, mediadb, formdb, notificationdb) + auditdb (can share a host with any of these but logically separate). Each runs the **same PostgreSQL 16 version** for uniform tooling.

---

## 3. Cross-Database Reference Rule

> [!IMPORTANT]
> **No foreign key constraint may cross a database boundary.** This is the single most important rule governing every DDL file in this document.

### 3.1 The Rule

When column `A.x` in database `adb` would naturally reference `B.id` in database `bdb`, the column becomes a plain UUID column with:

```sql
some_column  UUID NOT NULL  -- logical ref to bdb.table_name, no FK (separate database)
```

Validation is enforced at the **application layer** in two complementary ways:

1. **Synchronous API call at write time** — the writing service calls the owning service's API to verify existence and status before persisting. Example: `content-service` calls `schema-service` to verify `content_type_id` exists before inserting a content item.
2. **Local read-model cache** (recommended for high-frequency lookups) — the service maintains a local projection table populated from Kafka events emitted by the owning service. Example: `content-service` subscribes to `cms.schema.changed` events and maintains a local `content_type_cache` table. This eliminates the synchronous call on reads.

### 3.2 Affected References — Complete Cross-Reference Map

| Column | In Database | Logical Target | Owning Database | Validation Method |
|--------|-------------|----------------|-----------------|-------------------|
| `content_items.content_type_id` | contentdb | `schemadb.content_types.id` | schemadb | Sync API at write + local cache |
| `content_items.category_id` | contentdb | `schemadb.categories.id` | schemadb | Sync API at write + local cache |
| `content_items.site_id` | contentdb | `schemadb.sites.id` | schemadb | Sync API at write + local cache |
| `content_items.created_by/updated_by/deleted_by` | contentdb | `iamdb.users.id` | iamdb | JWT claim at write time |
| `content_versions.created_by` | contentdb | `iamdb.users.id` | iamdb | JWT claim at write time |
| `workflow_history.actor_id` | workflowdb | `iamdb.users.id` | iamdb | JWT claim at write time |
| `workflow_history.actor_email` | workflowdb | (denormalized from JWT) | iamdb | JWT claim — no lookup needed |
| `media_assets.uploaded_by` | mediadb | `iamdb.users.id` | iamdb | JWT claim at write time |
| `form_submissions.submitted_by` | formdb | `iamdb.users.id` | iamdb | JWT claim (null = anonymous) |
| `content_types.workflow_id` | schemadb | `workflowdb.workflow_definitions.id` | workflowdb | Sync API at write |
| `menu_items.content_item_id` | schemadb | `contentdb.content_items.id` | contentdb | Sync API at write + no local cache needed (optional link) |
| `users.tenant_id` | iamdb | `iamdb.tenants.id` | **same DB** | **Real FK** — both in iamdb |
| `roles.tenant_id` | iamdb | `iamdb.tenants.id` | **same DB** | **Real FK** — both in iamdb |
| `sites.tenant_id` | schemadb | `iamdb.tenants.id` | iamdb | JWT claim + local tenant cache |
| `content_items.tenant_id` | contentdb | `iamdb.tenants.id` | iamdb | JWT claim (verified at Gateway) |
| `workflow_instances.entity_id` | workflowdb | `contentdb.content_items.id` (or other) | contentdb | No FK — entity_type+entity_id is a polymorphic reference |

### 3.3 What Keeps Real FKs

Within a single database, real FK constraints are kept wherever they were before:
- All `workflowdb` tables reference each other with real FKs
- All `schemadb` tables reference each other with real FKs (`menus.site_id → sites.id`, `menu_items.menu_id → menus.id`, `field_definitions.content_type_id → content_types.id`, etc.)
- `iamdb.users.tenant_id → iamdb.tenants.id` is a **real FK** (same database)
- `iamdb.role_permissions`, `iamdb.user_roles` junction tables keep real FKs within iamdb

---

## 4. iamdb — IAM Service

**Connection string (example):** `jdbc:postgresql://iam-postgres:5432/iamdb`  
**Flyway history:** independent — schema `flyway_schema_history` inside iamdb  
**Debezium connector:** `debezium-connector-iamdb` → Kafka topic prefix `iamdb.iam.*`

```sql
-- ============================================================
-- iamdb schema: iam
-- ============================================================
CREATE SCHEMA iam;

-- ============================================================
-- TENANTS — root of the multi-tenant model
-- ============================================================
CREATE TABLE iam.tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(100) NOT NULL UNIQUE,    -- e.g. 'acme-corp'
    name        VARCHAR(255) NOT NULL,
    domain      VARCHAR(255) UNIQUE,             -- e.g. 'cms.acme.com'
    plan        VARCHAR(30) NOT NULL DEFAULT 'STANDARD'
                CHECK (plan IN ('TRIAL','STANDARD','ENTERPRISE')),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE','SUSPENDED','TRIAL','CANCELLED')),
    settings    JSONB NOT NULL DEFAULT '{}',     -- UI config, feature flags per tenant
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE iam.users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES iam.tenants(id), -- real FK, same DB
    username        VARCHAR(100) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,           -- bcrypt, cost 12+
    full_name       VARCHAR(255),
    avatar_url      TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','INACTIVE','LOCKED','PENDING')),
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,                            -- logical ref to iam.users(id), no FK (self-ref allowed)
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE(tenant_id, username),
    UNIQUE(tenant_id, email)
);

-- ============================================================
-- ROLES
-- ============================================================
CREATE TABLE iam.roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES iam.tenants(id), -- real FK, same DB
    name            VARCHAR(100) NOT NULL,
    code            VARCHAR(50) NOT NULL,
    description     TEXT,
    is_system_role  BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, code)
);

-- ============================================================
-- PERMISSIONS
-- ============================================================
CREATE TABLE iam.permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource    VARCHAR(100) NOT NULL,  -- 'content', 'tender', 'media', 'schema'
    action      VARCHAR(50) NOT NULL,   -- 'CREATE', 'READ', 'UPDATE', 'DELETE', 'PUBLISH'
    scope       VARCHAR(50),            -- 'OWN', 'DEPT', 'ALL'
    description TEXT,
    UNIQUE(resource, action, scope)
);

-- ============================================================
-- ROLE-PERMISSION JUNCTION
-- ============================================================
CREATE TABLE iam.role_permissions (
    role_id         UUID NOT NULL REFERENCES iam.roles(id) ON DELETE CASCADE,
    permission_id   UUID NOT NULL REFERENCES iam.permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ============================================================
-- USER-ROLE JUNCTION
-- ============================================================
CREATE TABLE iam.user_roles (
    user_id         UUID NOT NULL REFERENCES iam.users(id) ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES iam.roles(id) ON DELETE CASCADE,
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by      UUID,   -- logical ref to iam.users(id), no FK to avoid cycles
    expires_at      TIMESTAMPTZ,
    PRIMARY KEY (user_id, role_id)
);

-- ============================================================
-- API KEYS (for external consumers)
-- ============================================================
CREATE TABLE iam.api_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES iam.tenants(id),
    name            VARCHAR(100) NOT NULL,
    key_hash        VARCHAR(255) NOT NULL UNIQUE,  -- SHA-256 of raw key
    prefix          VARCHAR(10) NOT NULL,
    scopes          TEXT[] NOT NULL DEFAULT '{}',
    rate_limit_rpm  INT NOT NULL DEFAULT 60,
    last_used_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID   -- logical ref to iam.users(id)
);

-- ============================================================
-- REFRESH TOKENS
-- ============================================================
CREATE TABLE iam.refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES iam.users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at  TIMESTAMPTZ,
    ip_address  INET,
    user_agent  TEXT
);

-- ============================================================
-- LOCAL OUTBOX (Debezium CDC → Kafka)
-- ============================================================
CREATE TABLE iam.outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,  -- 'user', 'role', 'tenant'
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(200) NOT NULL,  -- 'cms.user.login', 'cms.user.role.changed'
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,            -- set by Debezium after publish
    retry_count     INT NOT NULL DEFAULT 0,
    error_message   TEXT
);

-- Indexes
CREATE INDEX idx_iam_users_tenant ON iam.users(tenant_id);
CREATE INDEX idx_iam_users_email ON iam.users(tenant_id, email);
CREATE INDEX idx_iam_refresh_tokens_user ON iam.refresh_tokens(user_id);
CREATE INDEX idx_iam_api_keys_tenant ON iam.api_keys(tenant_id) WHERE is_active = true;
CREATE INDEX idx_iam_outbox_unprocessed ON iam.outbox_events(created_at) WHERE processed_at IS NULL;
```

---

## 5. schemadb — Schema Service

**Connection string:** `jdbc:postgresql://schema-postgres:5432/schemadb`  
**Flyway history:** independent inside schemadb  
**Debezium connector:** `debezium-connector-schemadb` → Kafka topic prefix `schemadb.schema.*`

```sql
-- ============================================================
-- schemadb schema: schema
-- ============================================================
CREATE SCHEMA schema;

-- ============================================================
-- CONTENT TYPES (dynamic content type registry)
-- ============================================================
CREATE TABLE schema.content_types (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,  -- logical ref to iamdb.tenants, no FK (separate database)
    code            VARCHAR(100) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    description     TEXT,
    icon            VARCHAR(100),
    is_hierarchical BOOLEAN NOT NULL DEFAULT false,
    is_versionable  BOOLEAN NOT NULL DEFAULT true,
    is_schedulable  BOOLEAN NOT NULL DEFAULT true,
    has_workflow    BOOLEAN NOT NULL DEFAULT false,
    workflow_id     UUID,           -- logical ref to workflowdb.workflow_definitions, no FK
    workflow_code   VARCHAR(100),   -- denormalized for cache hits without a lookup
    default_persona VARCHAR(100),
    metadata        JSONB NOT NULL DEFAULT '{}',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','DRAFT','DEPRECATED')),
    version         INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,           -- logical ref to iamdb.users, no FK (separate database)
    UNIQUE(tenant_id, code)
);

-- ============================================================
-- FIELD DEFINITIONS
-- ============================================================
CREATE TABLE schema.field_definitions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type_id  UUID NOT NULL REFERENCES schema.content_types(id) ON DELETE CASCADE,  -- real FK, same DB
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
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type_id UUID NOT NULL REFERENCES schema.content_types(id) ON DELETE CASCADE,  -- real FK, same DB
    code            VARCHAR(100) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    parent_id       UUID REFERENCES schema.categories(id),  -- real FK, same DB
    description     TEXT,
    icon            VARCHAR(100),
    metadata        JSONB NOT NULL DEFAULT '{}',
    display_order   INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(content_type_id, code)
);

-- ============================================================
-- SITES (replaces free-text website_location)
-- ============================================================
CREATE TABLE schema.sites (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL,  -- logical ref to iamdb.tenants, no FK (separate database)
    code         VARCHAR(100) NOT NULL,       -- e.g. 'main-site', 'mobile-portal'
    name         VARCHAR(255) NOT NULL,
    domain       VARCHAR(255),               -- e.g. 'www.acme.com'
    locale       VARCHAR(10) NOT NULL DEFAULT 'en',
    theme_config JSONB NOT NULL DEFAULT '{}',
    is_active    BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, code)
);

-- ============================================================
-- MENUS
-- ============================================================
CREATE TABLE schema.menus (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    site_id   UUID NOT NULL REFERENCES schema.sites(id) ON DELETE CASCADE,  -- real FK, same DB
    code      VARCHAR(100) NOT NULL,
    name      VARCHAR(255) NOT NULL,
    location  VARCHAR(50) NOT NULL           -- 'header' | 'footer' | 'sidebar'
              CHECK (location IN ('header','footer','sidebar','custom')),
    UNIQUE(site_id, code)
);

-- ============================================================
-- MENU ITEMS
-- ============================================================
CREATE TABLE schema.menu_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_id           UUID NOT NULL REFERENCES schema.menus(id) ON DELETE CASCADE,    -- real FK, same DB
    parent_id         UUID REFERENCES schema.menu_items(id),                          -- real FK, same DB
    label             VARCHAR(255) NOT NULL,
    link_type         VARCHAR(20) NOT NULL
                      CHECK (link_type IN ('CONTENT','EXTERNAL','CATEGORY')),
    content_item_id   UUID,    -- logical ref to contentdb.content_items, no FK (separate database)
    external_url      TEXT,
    category_id       UUID REFERENCES schema.categories(id),    -- real FK, same DB
    display_order     INT NOT NULL DEFAULT 0,
    is_active         BOOLEAN NOT NULL DEFAULT true
);

-- ============================================================
-- LOCAL OUTBOX (Debezium CDC → Kafka)
-- ============================================================
CREATE TABLE schema.outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,   -- 'content_type', 'site', 'menu'
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(200) NOT NULL,   -- 'cms.schema.changed', 'cms.site.created'
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0,
    error_message   TEXT
);

-- Indexes
CREATE INDEX idx_schema_content_types_tenant ON schema.content_types(tenant_id);
CREATE INDEX idx_schema_sites_tenant ON schema.sites(tenant_id);
CREATE INDEX idx_schema_sites_code ON schema.sites(tenant_id, code);
CREATE INDEX idx_schema_menus_site ON schema.menus(site_id);
CREATE INDEX idx_schema_menu_items_menu ON schema.menu_items(menu_id, display_order);
CREATE INDEX idx_schema_outbox_unprocessed ON schema.outbox_events(created_at) WHERE processed_at IS NULL;
```

---

## 6. contentdb — Content Service

**Connection string:** `jdbc:postgresql://content-postgres:5432/contentdb`  
**Flyway history:** independent inside contentdb  
**Debezium connector:** `debezium-connector-contentdb` → Kafka topic prefix `contentdb.content.*`

> **Key change from prior design:** `website_location VARCHAR(100)` is replaced by `site_id UUID` (logical ref to schemadb.sites) + `site_code VARCHAR(100)` (denormalized for fast filtering without a cross-DB join). The Redis cache key `menu:{location}` becomes `menu:{siteCode}:{location}`.

```sql
-- ============================================================
-- contentdb schema: content
-- ============================================================
CREATE SCHEMA content;

-- ============================================================
-- CONTENT ITEMS (universal content store)
-- ============================================================
CREATE TABLE content.content_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    -- logical ref to schemadb.content_types, no FK (separate database)
    content_type_id     UUID NOT NULL,
    -- denormalized from content type for fast filtering without join
    content_type_code   VARCHAR(100) NOT NULL,
    title               VARCHAR(500) NOT NULL,
    slug                VARCHAR(500),
    body                JSONB NOT NULL DEFAULT '{}',
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN (
                            'DRAFT','IN_REVIEW','APPROVED','PUBLISHED',
                            'SCHEDULED','ARCHIVED','TRASHED','REJECTED'
                        )),
    -- logical ref to schemadb.categories, no FK (separate database)
    category_id         UUID,
    -- denormalized for fast display/filtering without sync API call
    category_code       VARCHAR(100),
    -- hierarchical content (self-referential — real FK, same DB)
    parent_id           UUID REFERENCES content.content_items(id),
    folder_path         TEXT,
    -- replaces website_location VARCHAR — real site identity
    site_id             UUID NOT NULL,         -- logical ref to schemadb.sites, no FK (separate database)
    site_code           VARCHAR(100) NOT NULL, -- denormalized for filtering/caching
    persona             VARCHAR(100),
    publish_at          TIMESTAMPTZ,
    expire_at           TIMESTAMPTZ,
    published_at        TIMESTAMPTZ,
    current_version     INT NOT NULL DEFAULT 1,
    -- logical ref to workflowdb.workflow_instances, no FK (separate database)
    workflow_instance_id UUID,
    media_ids           UUID[] DEFAULT '{}',
    tags                TEXT[] DEFAULT '{}',
    external_link       TEXT,
    order_index         INT NOT NULL DEFAULT 0,
    is_featured         BOOLEAN NOT NULL DEFAULT false,
    view_count          BIGINT NOT NULL DEFAULT 0,
    metadata            JSONB NOT NULL DEFAULT '{}',
    -- audit columns — user identity from JWT, no FK to iamdb (separate database)
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,   -- logical ref to iamdb.users, no FK (separate database)
    updated_by          UUID,   -- logical ref to iamdb.users, no FK (separate database)
    deleted_at          TIMESTAMPTZ,
    deleted_by          UUID,   -- logical ref to iamdb.users, no FK (separate database)
    version             BIGINT NOT NULL DEFAULT 0
);

-- Unique slug per type+site+tenant (partial index excludes deleted)
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

-- site_code used for fast filtering and Redis cache key construction
CREATE INDEX idx_content_site_code
    ON content.content_items(site_code)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_content_publish_at
    ON content.content_items(publish_at)
    WHERE status = 'SCHEDULED';

CREATE INDEX idx_content_expire_at
    ON content.content_items(expire_at)
    WHERE deleted_at IS NULL AND status = 'PUBLISHED';

CREATE INDEX idx_content_parent
    ON content.content_items(parent_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_content_body
    ON content.content_items USING GIN(body);

CREATE INDEX idx_content_tags
    ON content.content_items USING GIN(tags);

CREATE INDEX idx_content_folder_path
    ON content.content_items(folder_path text_pattern_ops);

CREATE INDEX idx_content_featured
    ON content.content_items(content_type_code, site_code, order_index)
    WHERE is_featured = true AND status = 'PUBLISHED' AND deleted_at IS NULL;

-- ============================================================
-- CONTENT VERSIONS (immutable history)
-- ============================================================
CREATE TABLE content.content_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_item_id UUID NOT NULL REFERENCES content.content_items(id) ON DELETE CASCADE,  -- real FK, same DB
    version_number  INT NOT NULL,
    title           VARCHAR(500) NOT NULL,
    body            JSONB NOT NULL,
    status          VARCHAR(30) NOT NULL,
    change_summary  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,   -- logical ref to iamdb.users, no FK (separate database)
    UNIQUE(content_item_id, version_number)
);

-- ============================================================
-- LOCAL OUTBOX (Debezium CDC → Kafka)
-- ============================================================
CREATE TABLE content.outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,   -- 'content_item'
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(200) NOT NULL,   -- 'cms.content.created', 'cms.content.published'
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0,
    error_message   TEXT
);

CREATE INDEX idx_content_outbox_unprocessed
    ON content.outbox_events(created_at)
    WHERE processed_at IS NULL;
```

---

## 7. workflowdb — Workflow Service

**Connection string:** `jdbc:postgresql://workflow-postgres:5432/workflowdb`  
**Flyway history:** independent inside workflowdb  
**Debezium connector:** `debezium-connector-workflowdb` → Kafka topic prefix `workflowdb.workflow.*`

```sql
CREATE SCHEMA workflow;

-- ============================================================
-- WORKFLOW DEFINITIONS (JSONB state machine configs)
-- ============================================================
CREATE TABLE workflow.workflow_definitions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,  -- logical ref to iamdb.tenants, no FK (separate database)
    code        VARCHAR(100) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    definition  JSONB NOT NULL,  -- states, transitions, guards, actions
    version     INT NOT NULL DEFAULT 1,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, code, version)
);

-- ============================================================
-- WORKFLOW INSTANCES
-- ============================================================
CREATE TABLE workflow.workflow_instances (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_def_id      UUID NOT NULL REFERENCES workflow.workflow_definitions(id),  -- real FK, same DB
    workflow_def_version INT NOT NULL,  -- pinned at creation time
    entity_type          VARCHAR(100) NOT NULL,  -- 'content_item', 'form_submission'
    entity_id            UUID NOT NULL,           -- logical ref — polymorphic, no FK
    current_state        VARCHAR(50) NOT NULL,
    started_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at         TIMESTAMPTZ,
    metadata             JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_workflow_entity
    ON workflow.workflow_instances(entity_type, entity_id);

-- ============================================================
-- WORKFLOW HISTORY (append-only transition log)
-- ============================================================
CREATE TABLE workflow.workflow_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id UUID NOT NULL REFERENCES workflow.workflow_instances(id),  -- real FK, same DB
    from_state  VARCHAR(50) NOT NULL,
    to_state    VARCHAR(50) NOT NULL,
    trigger     VARCHAR(100) NOT NULL,
    actor_id    UUID,           -- logical ref to iamdb.users, no FK (separate database)
    actor_email VARCHAR(255),   -- denormalized from JWT for audit durability
    comment     TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata    JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_workflow_history_instance ON workflow.workflow_history(instance_id);
CREATE INDEX idx_workflow_history_actor ON workflow.workflow_history(actor_id);
CREATE INDEX idx_workflow_history_time ON workflow.workflow_history(occurred_at DESC);

-- ============================================================
-- LOCAL OUTBOX
-- ============================================================
CREATE TABLE workflow.outbox_events (
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

CREATE INDEX idx_workflow_outbox_unprocessed
    ON workflow.outbox_events(created_at) WHERE processed_at IS NULL;
```

---

## 8. mediadb — Media Service

**Connection string:** `jdbc:postgresql://media-postgres:5432/mediadb`  
**Flyway history:** independent inside mediadb

```sql
CREATE SCHEMA media;

CREATE TABLE media.media_assets (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,   -- logical ref to iamdb.tenants, no FK
    original_name    VARCHAR(500) NOT NULL,
    stored_name      VARCHAR(500) NOT NULL,
    storage_path     TEXT NOT NULL,
    storage_backend  VARCHAR(50) NOT NULL DEFAULT 'LOCAL'
                     CHECK (storage_backend IN ('LOCAL','S3','GCS','AZURE_BLOB')),
    mime_type        VARCHAR(100) NOT NULL,
    file_size_bytes  BIGINT NOT NULL,
    checksum         VARCHAR(64),    -- SHA-256 for deduplication
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
    uploaded_by      UUID,   -- logical ref to iamdb.users, no FK (separate database)
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
    event_type      VARCHAR(200) NOT NULL,   -- 'cms.media.uploaded'
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0,
    error_message   TEXT
);

CREATE INDEX idx_media_outbox_unprocessed
    ON media.outbox_events(created_at) WHERE processed_at IS NULL;
```

---

## 9. formdb — Form Service

**Connection string:** `jdbc:postgresql://form-postgres:5432/formdb`  
**Flyway history:** independent inside formdb

```sql
CREATE SCHEMA form;

CREATE TABLE form.form_definitions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,  -- logical ref to iamdb.tenants, no FK
    site_id       UUID,           -- logical ref to schemadb.sites, no FK (optional per-site forms)
    code          VARCHAR(100) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    schema        JSONB NOT NULL,       -- JSON Schema for validation
    ui_schema     JSONB NOT NULL,       -- react-jsonschema-form UI schema
    submit_action VARCHAR(100),
    is_active     BOOLEAN NOT NULL DEFAULT true,
    version       INT NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, code)
);

CREATE TABLE form.form_submissions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    form_id        UUID NOT NULL REFERENCES form.form_definitions(id),  -- real FK, same DB
    submitted_data JSONB NOT NULL,
    submitted_by   UUID,   -- logical ref to iamdb.users, no FK (null = anonymous)
    submitted_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address     INET,
    user_agent     TEXT,
    status         VARCHAR(30) NOT NULL DEFAULT 'RECEIVED'
                   CHECK (status IN ('RECEIVED','PROCESSING','PROCESSED','REJECTED'))
);

CREATE INDEX idx_form_submissions_form ON form.form_submissions(form_id, submitted_at DESC);
CREATE INDEX idx_form_submissions_status ON form.form_submissions(status);

CREATE TABLE form.outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(200) NOT NULL,   -- 'cms.form.submitted'
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0,
    error_message   TEXT
);

CREATE INDEX idx_form_outbox_unprocessed
    ON form.outbox_events(created_at) WHERE processed_at IS NULL;
```

---

## 10. searchstore — Search Service (OpenSearch)

**No PostgreSQL database.** search-service uses **OpenSearch 2.x** as its sole data store.

### 10.1 Why OpenSearch, Not PostgreSQL

The search index must be denormalized, fully self-contained, and optimized for faceted, full-text queries that previously required cross-schema JOINs. With database-per-service, those JOINs are impossible in SQL. OpenSearch's document model naturally represents the denormalized form.

### 10.2 Index Document Structure

The search-service consumes Kafka events from `cms.content.published`, `cms.content.updated`, `cms.content.archived`, and other services, and maintains a fully denormalized document per content item. **Search queries never call back to content-service.** If the UI needs full content details after a hit, that is a separate follow-up `GET /api/v1/content/{type}/{id}` call to content-service.

```json
// Index: cms-content-{tenantCode} (one index per tenant)
// Document ID: content_item UUID
{
  "id": "uuid",
  "tenant_id": "uuid",
  "content_type_code": "tender_notice",
  "content_type_name": "Tender Notice",
  "title": "Tender for Bridge Construction 2026",
  "slug": "tender-bridge-2026",
  "body_text": "flattened text from all TEXT/RICHTEXT fields",
  "tags": ["infrastructure", "bridge"],
  "category_code": "open_tender",
  "category_name": "Open Tender",
  "site_id": "uuid",
  "site_code": "main-site",
  "persona": "vendors",
  "status": "PUBLISHED",
  "published_at": "2026-06-19T10:00:00Z",
  "created_by_email": "author@acme.com",
  "selected_fields": {
    "tender_value": 5000000,
    "bid_submission_end": "2026-07-31T23:59:00Z",
    "organisation_code": "CG57006"
  },
  "indexed_at": "2026-06-19T10:00:05Z"
}
```

### 10.3 OpenSearch Index Settings

```json
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "cms_analyzer": {
          "type": "standard",
          "stopwords": "_english_"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "title":        { "type": "text", "boost": 3, "analyzer": "cms_analyzer" },
      "body_text":    { "type": "text", "boost": 1, "analyzer": "cms_analyzer" },
      "tags":         { "type": "keyword" },
      "status":       { "type": "keyword" },
      "site_code":    { "type": "keyword" },
      "content_type_code": { "type": "keyword" },
      "category_code":     { "type": "keyword" },
      "published_at": { "type": "date" },
      "selected_fields": { "type": "object", "dynamic": true }
    }
  }
}
```

---

## 11. notificationdb — Notification Service

**Connection string:** `jdbc:postgresql://notification-postgres:5432/notificationdb`  
**Flyway history:** independent inside notificationdb

```sql
CREATE SCHEMA notification;

-- ============================================================
-- NOTIFICATION TEMPLATES (configurable by Admin)
-- ============================================================
CREATE TABLE notification.notification_templates (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,   -- logical ref to iamdb.tenants, no FK
    code          VARCHAR(100) NOT NULL,     -- e.g. 'review_requested', 'content_approved'
    name          VARCHAR(255) NOT NULL,
    channel       VARCHAR(20) NOT NULL
                  CHECK (channel IN ('EMAIL','SMS','IN_APP')),
    subject       TEXT,                      -- email subject (supports template vars)
    body_template TEXT NOT NULL,             -- Mustache/Thymeleaf template
    is_active     BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, code, channel)
);

-- ============================================================
-- NOTIFICATION LOG (delivery tracking, 90-day retention)
-- ============================================================
CREATE TABLE notification.notification_log (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,   -- logical ref to iamdb.tenants, no FK
    notification_type VARCHAR(50) NOT NULL CHECK (notification_type IN ('EMAIL','SMS','IN_APP')),
    recipient         VARCHAR(255) NOT NULL,
    template_code     VARCHAR(100) NOT NULL,
    payload           JSONB NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','SENT','FAILED','RETRYING')),
    sent_at           TIMESTAMPTZ,
    error_message     TEXT,
    retry_count       INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notif_log_status ON notification.notification_log(status);
CREATE INDEX idx_notif_log_tenant ON notification.notification_log(tenant_id, created_at DESC);

-- ============================================================
-- LOCAL OUTBOX (optional — for 'notification.sent' events)
-- If notification-service emits its own domain events (e.g. for analytics),
-- add this table. If it is a pure consumer, omit it.
-- ============================================================
CREATE TABLE notification.outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(200) NOT NULL,   -- 'cms.notification.sent'
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0,
    error_message   TEXT
);
-- Include the outbox connector only if notification-service is configured as a producer.
```

---

## 12. auditdb — Audit Service

**Connection string:** `jdbc:postgresql://audit-postgres:5432/auditdb`  
**Flyway history:** independent inside auditdb  
**Role:** Pure Kafka consumer. Consumes events from **all** other services' Debezium outbox streams. Does **not** produce domain events and therefore does **not** need its own `outbox_events` table.

```sql
CREATE SCHEMA audit;

-- APPEND-ONLY: No UPDATE or DELETE permitted on audit_events.
-- Partitioned by month for query performance and archival.

CREATE TABLE audit.audit_events (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    event_type      VARCHAR(100) NOT NULL,       -- 'cms.content.published', 'cms.user.login'
    actor_id        UUID,    -- logical ref to iamdb.users — denormalized, stored as-is
    actor_email     VARCHAR(255),                -- denormalized from JWT/event payload
    entity_type     VARCHAR(100),                -- 'content_item', 'user', 'workflow_instance'
    entity_id       UUID,
    tenant_id       UUID,
    source_service  VARCHAR(100),                -- 'content-service', 'workflow-service', etc.
    old_value       JSONB,
    new_value       JSONB,
    ip_address      INET,
    user_agent      TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    correlation_id  UUID                         -- trace ID for cross-service correlation
) PARTITION BY RANGE (occurred_at);

-- Monthly partitions (automate with pg_partman in production):
CREATE TABLE audit.audit_events_2026_01 PARTITION OF audit.audit_events
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE audit.audit_events_2026_02 PARTITION OF audit.audit_events
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
-- ... continue per month. pg_partman automates future partition creation.

CREATE INDEX idx_audit_entity ON audit.audit_events(entity_type, entity_id);
CREATE INDEX idx_audit_actor ON audit.audit_events(actor_id);
CREATE INDEX idx_audit_type ON audit.audit_events(event_type);
CREATE INDEX idx_audit_tenant ON audit.audit_events(tenant_id);
CREATE INDEX idx_audit_time ON audit.audit_events(occurred_at DESC);

-- Enforce append-only: application role may only INSERT
ALTER TABLE audit.audit_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY audit_append_only ON audit.audit_events
    TO cms_audit_app_role
    FOR INSERT WITH CHECK (true);
-- No SELECT/UPDATE/DELETE policy for cms_audit_app_role.
-- A separate read-only role (cms_audit_read_role) is used for queries.
```

---

## 13. Indexing Strategy

### Summary Table

| Index Type | Use Case | Databases |
|-----------|---------|-----------|
| B-tree (default) | Equality, range on typed columns | All PostgreSQL databases |
| Partial B-tree | Filtered queries with fixed WHERE clause | `WHERE deleted_at IS NULL`, `WHERE status = 'SCHEDULED'`, `WHERE processed_at IS NULL` |
| GIN on JSONB | JSONB containment and path queries | contentdb: `content_items.body`, workflowdb: `workflow_definitions.definition` |
| GIN on arrays | Array containment | contentdb: `content_items.tags`, mediadb: `media_assets.tags` |
| text_pattern_ops | LIKE prefix queries | contentdb: `folder_path`, mediadb: `folder_path` |
| Unique partial | Slug uniqueness excluding soft-deleted | contentdb: `slug` unique index |
| Composite | Multi-column filtering | contentdb: `(content_type_code, status)`, `(tenant_id, content_type_code, site_id, slug)` |

### Index Maintenance

- All indexes monitored via `pg_stat_user_indexes` per database
- VACUUM ANALYZE scheduled nightly via pg_cron per database
- Index bloat monitored per database via `pgstattuple`
- `CREATE INDEX CONCURRENTLY` used for all production index additions to avoid table locks

---

## 14. Row-Level Security & Tenant Validation

### 14.1 RLS Remains Per-Database

PostgreSQL RLS policies are configured in each database individually. The mechanism is identical to the prior design — application sets session variables before every query:

```sql
SET app.current_tenant_id = '<uuid>';
SET app.current_user_id = '<uuid>';
SET app.user_role = 'AUTHOR';
```

Example policy (contentdb):

```sql
ALTER TABLE content.content_items ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON content.content_items
    TO cms_content_app_role
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);
```

Each of the 6 PostgreSQL producer databases (iamdb, schemadb, contentdb, workflowdb, mediadb, formdb) has its own application role and its own RLS policies.

### 14.2 Tenant Validation Without Cross-DB FK

> [!IMPORTANT]
> **Tenant existence and active status can no longer be enforced by a PostgreSQL FK in any database except `iamdb`.** This is an architectural consequence of database-per-service.

**New invariant:** Tenant identity and status are verified **once at the API Gateway** via the JWT claim issued by iam-service. The JWT contains:

```json
{
  "sub": "<user-uuid>",
  "tenantId": "<tenant-uuid>",
  "tenantStatus": "ACTIVE",
  "roles": ["AUTHOR"],
  "email": "user@acme.com",
  "exp": 1750000000
}
```

The API Gateway validates the JWT signature and rejects requests where `tenantStatus != 'ACTIVE'` before the request reaches any microservice. Downstream services trust the JWT claim and do not re-validate tenant existence via a DB query.

**What this means in practice:**
- If a tenant is suspended in `iamdb.tenants`, iam-service stops issuing valid JWTs for that tenant and blacklists existing tokens via Redis.
- All downstream service databases (contentdb, workflowdb, etc.) do not need to know tenant status — they rely on the Gateway having already enforced it.
- The `tenant_id` column in each database is populated from the JWT claim and used for RLS filtering only.

### 14.3 When to Re-Validate Tenant at Application Layer

Batch jobs, scheduled tasks (e.g., the scheduled-publish cron), and Kafka consumers that run outside the request/response cycle must call iam-service to verify tenant status before acting. They do not have a JWT from the API Gateway and must use a service-to-service token.

---

## 15. Migration Strategy — 9 Independent Flyway Histories

### 15.1 One Flyway Project Per Service

Each service contains its own Flyway migration scripts under `src/main/resources/db/migration/`. Flyway stores its history in a `flyway_schema_history` table inside that service's database. There is no shared migration history.

```
content-service/
  src/main/resources/db/migration/
    V1__create_content_schema.sql
    V2__create_content_items.sql
    V3__create_content_versions.sql
    V4__create_outbox_events.sql
    V5__add_site_id_to_content_items.sql   ← replaces website_location
    V6__create_content_indexes.sql
    V7__enable_rls_content_items.sql
    V8__seed_default_site.sql              ← seed if needed

schema-service/
  src/main/resources/db/migration/
    V1__create_schema_schema.sql
    V2__create_content_types.sql
    V3__create_field_definitions.sql
    V4__create_categories.sql
    V5__create_sites.sql                   ← new
    V6__create_menus.sql                   ← new
    V7__create_menu_items.sql              ← new
    V8__create_outbox_events.sql
    V9__create_indexes.sql

iam-service/
  src/main/resources/db/migration/
    V1__create_iam_schema.sql
    V2__create_tenants.sql                 ← new
    V3__create_users.sql
    V4__create_roles_permissions.sql
    V5__create_user_roles.sql
    V6__create_api_keys.sql
    V7__create_refresh_tokens.sql
    V8__create_outbox_events.sql
    V9__create_indexes.sql
    V10__seed_system_roles.sql
    V11__seed_default_permissions.sql
    V12__seed_default_tenant.sql           ← seed default tenant for dev
... (similar structure for workflowdb, mediadb, formdb, notificationdb, auditdb)
```

### 15.2 Migration Rules (Unchanged)

1. Never modify an existing migration. Only add new ones.
2. Backward-compatible only: new column = nullable or with DEFAULT. Never drop a column in the same sprint it was added.
3. Rename in 3 steps across sprints.
4. Large data migrations run as background jobs, not inside Flyway scripts.
5. Index creation uses `CREATE INDEX CONCURRENTLY` in repair scripts to avoid table locks.

---

## 16. Key Query Patterns

> [!WARNING]
> No query pattern in this system may JOIN across databases. Any pattern that previously did so is rewritten below as **API composition** or **denormalized field access**.

### 16.1 List Content by Type + Status (Admin — most common)

```sql
-- contentdb only — no cross-DB join needed
SELECT id, title, slug, status, category_id, category_code,
       site_id, site_code, published_at, created_by, created_at
FROM content.content_items
WHERE content_type_code = 'tender_notice'
  AND tenant_id = :tenantId
  AND status IN ('DRAFT', 'IN_REVIEW')
  AND deleted_at IS NULL
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;
-- Uses: idx_content_type_status composite index
-- category_code and site_code are denormalized — no JOIN to schemadb needed
```

### 16.2 Full-Text Search (via search-service / OpenSearch)

```
// This is an OpenSearch query — NOT a SQL query
// search-service executes against searchstore (OpenSearch), never against contentdb SQL

POST /cms-content-{tenantCode}/_search
{
  "query": {
    "bool": {
      "must": [
        { "multi_match": { "query": "bridge construction", "fields": ["title^3", "body_text"] } }
      ],
      "filter": [
        { "term": { "status": "PUBLISHED" } },
        { "term": { "site_code": "main-site" } }
      ]
    }
  },
  "aggs": {
    "by_type": { "terms": { "field": "content_type_code" } },
    "by_category": { "terms": { "field": "category_code" } }
  }
}
```

**After a search hit:** The UI receives a list of `{ id, title, slug, content_type_code, ... }` from search-service. If full content body is needed, the UI makes a separate `GET /api/v1/content/{type}/{id}` call to content-service. Search never joins back to contentdb.

### 16.3 JSONB Field Query — Tenders Above a Value (contentdb only)

```sql
SELECT id, title, (body->>'tender_value')::numeric AS tender_value
FROM content.content_items
WHERE content_type_code = 'tender_notice'
  AND tenant_id = :tenantId
  AND site_code = :siteCode
  AND status = 'PUBLISHED'
  AND deleted_at IS NULL
  AND (body->>'tender_value')::numeric > 1000000
ORDER BY (body->>'bid_submission_end')::timestamptz DESC;
-- Uses: idx_content_body GIN for containment; site_code filters on idx_content_site_code
```

### 16.4 Scheduled Publish Cron (contentdb — every minute)

```sql
UPDATE content.content_items
SET status = 'PUBLISHED', published_at = NOW(), updated_at = NOW()
WHERE status = 'SCHEDULED'
  AND publish_at <= NOW()
  AND deleted_at IS NULL;
-- Uses: idx_content_publish_at partial index (covers SCHEDULED rows only)
-- Post-update: insert rows into content.outbox_events for Debezium → cms.content.published Kafka event
```

### 16.5 Workflow History for a Content Item (API composition)

```
// NOT a SQL join across contentdb + workflowdb
// Client calls two APIs:
1. GET /api/v1/content/tender_notice/{id}
   → content-service returns content item (includes workflow_instance_id from contentdb)

2. GET /api/v1/workflow-instances/{workflow_instance_id}/history
   → workflow-service queries workflowdb.workflow.workflow_history
   → returns transition history
```

### 16.6 Audit Log for an Entity (auditdb only)

```sql
SELECT event_type, actor_email, source_service, old_value, new_value, occurred_at, correlation_id
FROM audit.audit_events
WHERE entity_type = 'content_item'
  AND entity_id = :contentItemId
  AND tenant_id = :tenantId
ORDER BY occurred_at DESC
LIMIT 50;
-- Uses: idx_audit_entity + partition pruning (if date filter added)
-- All fields are denormalized — no join to contentdb, iamdb, or workflowdb
```

### 16.7 Menu Retrieval for a Site (schemadb + Redis cache)

```sql
-- schemadb only (all tables in same DB — real FKs used)
SELECT mi.id, mi.label, mi.link_type, mi.content_item_id,
       mi.external_url, mi.category_id, mi.display_order
FROM schema.menu_items mi
JOIN schema.menus m ON m.id = mi.menu_id
JOIN schema.sites s ON s.id = m.site_id
WHERE s.code = :siteCode
  AND m.location = :location
  AND s.tenant_id = :tenantId
  AND mi.is_active = true
ORDER BY mi.display_order;
-- Redis cache key: menu:{siteCode}:{location}   TTL: 15 min
-- Evicted on: cms.schema.changed event (menu updated)
```

---

## 17. ERD Summary — Per-Database Mini-ERDs

### 17.1 Logical Database Topology

```
┌─────────────────────────────────────────────────────────────────┐
│              DATABASE TOPOLOGY — LOGICAL REFERENCES             │
│           Solid line = real FK   Dashed line = logical ref      │
└─────────────────────────────────────────────────────────────────┘

         ┌──────────────────┐
         │     iamdb        │
         │ tenants          │◄─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐
         │ users            │◄ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐    │
         │ roles            │                               :    :
         │ permissions      │                               :    :
         │ user_roles       │                               :    :
         │ api_keys         │                               :    :
         │ refresh_tokens   │                               :    :
         └──────────────────┘                               :    :
                                                            :    :
         ┌──────────────────┐                               :    :
         │   schemadb       │◄ ─ (tenant_id)─ ─ ─ ─ ─ ─ ─ ┘    :
         │ content_types  ──┼──► workflow_id ─ ─ ► workflowdb   :
         │ field_definitions│                                     :
         │ categories       │                                     :
         │ sites          ──┼──► tenant_id ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
         │ menus            │                         (iamdb)
         │ menu_items     ──┼──► content_item_id ─ ─ ► contentdb
         └──────────────────┘
                  ▲
                  : (content_type_id, category_id, site_id)
         ┌────────┴─────────┐
         │   contentdb      │
         │ content_items    │◄──────────────┐ (self-ref parent_id)
         │ content_versions │               │
         └──────────────────┘               │
                  │                         │
                  : workflow_instance_id     │
                  ▼                         │
         ┌──────────────────┐               │
         │   workflowdb     │               │
         │ workflow_defs    │               │
         │ workflow_inst  ──┼──► entity_id──┘ (polymorphic, no FK)
         │ workflow_history │
         └──────────────────┘

         ┌──────────────────┐   ┌──────────────────┐
         │   mediadb        │   │   formdb          │
         │ media_assets     │   │ form_definitions  │
         └──────────────────┘   │ form_submissions  │
                                └──────────────────┘

         ┌──────────────────┐   ┌──────────────────┐
         │  notificationdb  │   │   auditdb         │
         │ notif_templates  │   │ audit_events      │
         │ notification_log │   │ (partitioned)     │
         └──────────────────┘   └──────────────────┘

         ┌──────────────────┐
         │  searchstore     │
         │ (OpenSearch)     │
         │ cms-content-*    │
         │ (denormalized)   │
         └──────────────────┘
```

### 17.2 iamdb Internal ERD

```
tenants ──────────────────────────────┐
    │ (real FK)                        │ (real FK)
    ▼                                  ▼
users ──── user_roles ──── roles ──── role_permissions ──── permissions
    │
    └── refresh_tokens
    └── api_keys
    └── outbox_events
```

### 17.3 schemadb Internal ERD

```
sites ──────────────── menus ──────────────── menu_items
  │                                               │
  └── (tenant_id: logical ref to iamdb)          └── category_id (real FK → categories)
                                                  └── content_item_id (logical ref to contentdb)

content_types ──── field_definitions
      │
      └── categories (tree: parent_id self-ref)
      └── (workflow_id: logical ref to workflowdb)

outbox_events
```

### 17.4 contentdb Internal ERD

```
content_items (self-ref: parent_id)
    └── content_versions
    └── outbox_events

content_items
  .content_type_id  → logical ref to schemadb.content_types
  .category_id      → logical ref to schemadb.categories
  .site_id          → logical ref to schemadb.sites
  .site_code        → denormalized
  .created_by       → logical ref to iamdb.users
  .workflow_instance_id → logical ref to workflowdb.workflow_instances
```

### 17.5 workflowdb Internal ERD

```
workflow_definitions ──── workflow_instances ──── workflow_history
                              │
                              └── entity_id (polymorphic logical ref)
                              └── actor_id (logical ref to iamdb.users)
```

---

## 18. Operational Tradeoffs

Moving to database-per-service introduces real operational complexity. This section documents it honestly alongside the mitigations.

| Tradeoff | Impact | Mitigation |
|----------|--------|------------|
| **9x backup surfaces** | Each database needs its own backup schedule, retention policy, and restore test | Use the same PostgreSQL version and `pg_basebackup` / pgBackRest config across all 7 PG instances. Kubernetes CronJob runs backup for each. One playbook covers all. |
| **9x HA setup** | Each PG instance needs a replica for availability | Use a PostgreSQL operator (CloudNativePG or Zalando Postgres Operator) — one Helm chart parameterizes all 7 instances identically |
| **9x monitoring** | Each database has its own `pg_stat_*` views, slow query logs, connection pools | Centralize via `postgres_exporter` per instance + single Grafana dashboard with datasource selector |
| **9x PgBouncer** | Each service needs its own connection pool | Deploy PgBouncer as a sidecar per service pod — standard Helm chart, same config |
| **9 independent Flyway histories** | Migration failures in one service don't block others; but coordinating cross-DB data migrations (e.g. legacy → new) requires a separate migration orchestrator | Wrap legacy migration in a one-shot Kubernetes Job that calls each service's migration API endpoint in dependency order |
| **No cross-DB SQL joins** | Queries that previously joined across schemas now require API composition or denormalized fields | Denormalize aggressively (site_code, category_code, content_type_code, actor_email). Use local read-model caches for frequent lookups. Accept eventual consistency for non-critical cross-service data. |
| **Eventual consistency** | A content item references a category that may not yet exist in contentdb's local cache | Apply Kafka consumer idempotency + retry: if a referenced entity is not found in local cache, wait for the Kafka event that syncs it, then retry |
| **No cross-DB transactions** | Creating a content item + starting a workflow instance cannot be in one atomic DB transaction | Use the Saga pattern: content-service creates the item (local transaction), emits `cms.content.created`, workflow-service listens and starts the instance. Compensating events handle failures. |
| **Distributed tracing required** | A user action spans 3–5 services; debugging without traces is painful | OpenTelemetry auto-instrumentation on every Spring Boot service. W3C Trace Context propagated in all HTTP and Kafka message headers. Jaeger/Tempo as backend. |

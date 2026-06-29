# NextGen Enterprise CMS — Complete Architecture Blueprint

> **Audience:** Principal Architects, Senior Engineers, Technical Leadership  
> **Scope:** Full architectural redesign — analysis → design → implementation strategy  
> **Technology:** Java 21 · Spring Boot 3 · Next.js 14 · **PostgreSQL 16 (9 separate databases, one per service)** · OpenSearch 2.x (search) · Kafka · Redis · Docker · Kubernetes  
> **Database Design:** See [`db-structure.md`](db-structure.md) for the canonical database-per-service schema reference.

---

## PART 1: DIAGNOSIS — What's Actually Broken

### 1.1 The Root Disease (Not Just Symptoms)

After analyzing 351 Java source files across 28 modules, the core problem is not "large controllers" or "enums in business logic." Those are symptoms. The root disease is:

> **The system has no abstraction layer between "content concept" and "database table."**  
> Every content type is permanently encoded in Java classes, ENUMs, and DDL. Adding a category means a code deployment.

This is the _Single Responsibility Violation at the Architecture Level_ — the architecture itself is responsible for what should be a runtime configuration concern.

### 1.2 The 7 Structural Anti-Patterns Found

**Pattern 1: The ENUM-as-Category Anti-Pattern**  
Every module has its own `XxxCategoryEnum` and `XxxSubCategoryEnum` with a paired `XxxEnumConvertor`. Across 28 modules: **25 ENUM files + 22 Convertor files = 47 files that exist only because categories are not database-driven.**

Every time a new content category is needed:
1. Add value to ENUM → recompile
2. Update convertor → recompile  
3. Write migration → deploy
4. Restart server

This alone causes an estimated 60–70% of "feature additions requiring code changes."

**Pattern 2: Fat Controller Anti-Pattern**  
The largest controller (`PublicCmsDocumentController.java`) is 4,571 lines. Average: ~580 lines per controller.  
Controllers are doing: authentication checks, business logic, file I/O, path construction, date parsing, enum conversion, workflow triggering, and view rendering — all in one class.

**Pattern 3: Repository-as-Service Anti-Pattern**  
Controllers directly inject repositories (`@Autowired CmsAboutUsRepository`). The service layer is bypassed for reads. Query logic proliferates in repositories with 80+ character method names like:  
`findAllByWebsiteLocationAndIsDeleteFalseAndIsPublishTrueAndCategoryIdAndIsVisionStatementFalseAndParentMstIdIsNullOrderByOrderIdAsc`

This is a named query encoded as a Java identifier. The query is invisible to non-Java tooling, cannot be tuned without a deployment, and leaks domain knowledge into the persistence layer.

**Pattern 4: Structural Duplication at Module Scale**  
Each of the 28 modules contains nearly identical structures:
```
/controller/CmsXxxController.java     → ~600 LOC each
/model/CmsXxx.java                    → identical base fields
/repository/CmsXxxRepository.java     → near-identical query patterns
/service/CmsXxxService.java           → identical interface shape
/serviceimpl/CmsXxxServiceImpl.java   → near-identical logic
/util/XxxCategoryEnum.java            → category constants
/util/XxxCategoryEnumConvertor.java   → boilerplate converter
```

This is not modularity. It is copy-paste architecture. Adding module 29 will look exactly like modules 1–28.

**Pattern 5: Business Logic in the HTTP Layer**  
From `CmsAboutUsController.saveCmsAboutUs()` (line 173): The controller handles date parsing, file upload path construction, parent-child path inheritance, enum conversion, workflow triggers, and session-based auth checks — all inside a single `@PostMapping` method. **No service method exists for "save an about-us record."** The business logic IS the controller method.

This makes the business logic:
- Untestable without HTTP mocking
- Unreusable from batch jobs or event handlers
- Invisible to documentation tooling
- Impossible to move to a microservice

**Pattern 6: Workflow as a Bolted-On Afterthought**  
`WorkflowService` and `WorkflowItem` are referenced directly inside controllers and even models. Workflow is not a first-class concept with its own lifecycle — it's called ad hoc by individual controllers. 27 out of 28 modules independently invoke `WorkflowService`, meaning 27 different code paths manage workflow state with no unified audit trail.

**Pattern 7: Configuration Burned Into Binary**  
File storage paths (`environment.getProperty("file.repository.path")`), default values (hardcoded `"CG57006"` organisation code in `TenderNotice.java`), category IDs (hardcoded integer constants), and UI layout flags (`isGrid`, `isCard`, `isNormal`, `isVisionStatement`, `isTitleShow`) are all stored as Java class fields. Every environment difference, every client customisation, and every layout variant requires either a code change or a property file change that cannot be done at runtime.

### 1.3 The Real Cost of the Current Architecture

| Action | Current Cost | Target Cost |
|--------|-------------|-------------|
| Add a new content category | Code change + deploy | Admin UI click |
| Add a new field to a content type | Code change + DB migration + deploy | Admin UI form |
| Add a new workflow step | Code change in N controllers | Workflow designer config |
| Add a new content module | Copy-paste 7 files + 2 ENUMs + DDL | Content type configuration |
| Debug a publish failure | Trace across controller → service → scheduler → 3 repos | Single event stream lookup |
| Onboard a new developer | Read 28 modules × 7 files = 196 files | Read one domain model + architecture doc |
| Write a unit test for save logic | Mock HTTP request, session, 3 repos, 2 enums, file system | Call a pure domain service |

---

## PART 2: THE TARGET ARCHITECTURE

### 2.1 Architectural Philosophy

The new system is built on three axioms:

**Axiom 1: Content is data, not code.**  
A "Tender Notice" with fields A, B, C is not different in kind from a "Vacancy" with fields X, Y, Z. Both are instances of a content type. The content type schema is stored in the database and interpreted at runtime. No Java class exists per content type.

**Axiom 2: Behaviour is pluggable, not hardcoded.**  
When content transitions between states (draft → review → published), the transition rules, notification targets, and side effects are configured — not compiled. The workflow engine reads configuration; it does not contain content-type-specific if-else chains.

**Axiom 3: Infrastructure is explicit, not implicit.**  
Every cross-cutting concern (caching, audit, events, search indexing) is handled by an infrastructure layer that content services call through defined ports. There are no surprise side effects inside a `save()` method.

### 2.2 System Context Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            EXTERNAL USERS                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐ │
│  │ CMS Authors  │  │ Reviewers /  │  │ Public Site  │  │  Vendor / API   │ │
│  │ (Admin UI)   │  │ Approvers    │  │  Visitors    │  │  Consumers      │ │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └────────┬────────┘ │
└─────────┼─────────────────┼─────────────────┼───────────────────┼──────────┘
          │                 │                 │                   │
          ▼                 ▼                 ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         API GATEWAY (Kong / Nginx)                           │
│              Rate Limiting · Auth Routing · SSL Termination                  │
└──────────┬──────────────────────────────────────┬───────────────────────────┘
           │                                      │
    ┌──────▼──────┐                       ┌───────▼──────┐
    │  Next.js    │                       │  Next.js     │
    │  Admin App  │                       │  Public App  │
    │  (Port 3000)│                       │  (Port 3001) │
    └──────┬──────┘                       └───────┬──────┘
           │                                      │
           ▼                                      ▼
     ┌────────────────────────────────────────────────────────────────────────┐
     │                  9 SEPARATE POSTGRESQL 16 DATABASES                    │
     │  iamdb · schemadb · contentdb · workflowdb · mediadb · formdb          │
     │  notificationdb · auditdb (partitioned)  +  OpenSearch (search only)   │
     │  One database per service. No shared schema. No cross-DB FK.           │
     └────────────────────────────────────────────────────────────────────────┘
           │                                      │
┌─────────────────────────────────────────────────────────────────────────────┐
│                        MICROSERVICES LAYER                                   │
│  ┌─────────────┐ ┌────────────┐ ┌───────────┐ ┌──────────┐ ┌────────────┐ │
│  │   Content   │ │  Workflow  │ │   Media   │ │   IAM    │ │   Search   │ │
│  │   Service   │ │  Service   │ │  Service  │ │  Service │ │  Service   │ │
│  │  :8081      │ │  :8082     │ │  :8083    │ │  :8084   │ │  :8085     │ │
│  └──────┬──────┘ └─────┬──────┘ └─────┬─────┘ └────┬─────┘ └─────┬──────┘ │
│         │              │              │             │             │         │
│  ┌──────▼──────┐ ┌─────▼──────┐ ┌────▼──────┐ ┌───▼──────┐                │
│  │   Schema    │ │  Form      │ │  Notif.   │ │  Audit   │                │
│  │   Service   │ │  Service   │ │  Service  │ │  Service │                │
│  │  :8086      │ │  :8087     │ │  :8088    │ │  :8089   │                │
│  └─────────────┘ └────────────┘ └───────────┘ └──────────┘                │
└──────────────────────────────────────────────────────────────────────────────┘
           │                          │
           ▼                          ▼
    ┌─────────────┐            ┌─────────────┐
    │  Kafka      │            │   Redis     │
    │  Event Bus  │            │   Cache     │
    └─────────────┘            └─────────────┘
           │                          
           ▼                          
    ┌─────────────┐                   
    │ PostgreSQL  │                   
    │  Cluster    │                   
    └─────────────┘                   
```

---

## PART 3: MICROSERVICE BOUNDARIES

### 3.1 Service Decomposition Rationale

Each service boundary is defined by **domain cohesion** (what changes together) and **team autonomy** (who owns it), not by technical layer.

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│ SERVICE            │ OWNS                      │ ANTI-CORRUPTION GATE│ DATA STORE     │
├────────────────────┼───────────────────────────┼─────────────────────┼────────────────┤
│ content-service    │ Content lifecycle          │ ContentDTO          │ contentdb (PG) │
│ schema-service     │ Dynamic type definitions   │ SchemaDefinitionDTO │ schemadb  (PG) │
│ workflow-service   │ State machines             │ WorkflowInstanceDTO │ workflowdb(PG) │
│ media-service      │ File storage & CDN         │ MediaAssetDTO       │ mediadb   (PG) │
│ iam-service        │ Users, Roles, Permissions  │ PrincipalDTO        │ iamdb     (PG) │
│ form-service       │ Dynamic forms & validation │ FormDefinitionDTO   │ formdb    (PG) │
│ search-service     │ Full-text & faceted search │ SearchResultDTO     │ OpenSearch 2.x │
│ notification-svc   │ Email/SMS/push dispatch    │ NotificationDTO     │ notificationdb │
│ audit-service      │ Immutable event log        │ AuditEventDTO       │ auditdb   (PG) │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

**Why 9 services and not fewer?**  
- `schema-service` must be separate because other services depend on it as a registry. If it's inside `content-service`, circular dependencies form.
- `workflow-service` must be separate because the same workflow engine governs content publish, vendor registration, and tender approval — three different content domains.
- `audit-service` must be separate and write-only from other services to guarantee tamper-proof logs.

### 3.2 Content Service — The Core

This service replaces all 28 module-specific controllers, services, and repositories with a **single generic content runtime** driven by schemas from `schema-service`.

```
content-service/
├── domain/
│   ├── model/
│   │   ├── ContentItem.java          ← The universal content record
│   │   ├── ContentVersion.java       ← Immutable version snapshot
│   │   └── ContentStatus.java        ← Enum: DRAFT|REVIEW|APPROVED|PUBLISHED|ARCHIVED
│   ├── ports/
│   │   ├── in/
│   │   │   ├── CreateContentUseCase.java
│   │   │   ├── UpdateContentUseCase.java
│   │   │   ├── PublishContentUseCase.java
│   │   │   └── QueryContentUseCase.java
│   │   └── out/
│   │       ├── ContentRepository.java
│   │       ├── SchemaRegistryPort.java
│   │       ├── WorkflowPort.java
│   │       ├── SearchIndexPort.java
│   │       └── EventPublisherPort.java
│   └── service/
│       ├── ContentDomainService.java
│       ├── ContentValidationService.java
│       └── ContentPublishingService.java
├── application/
│   ├── ContentApplicationService.java
│   └── dto/
│       ├── CreateContentCommand.java
│       ├── ContentResponse.java
│       └── ContentQueryRequest.java
├── infrastructure/
│   ├── persistence/
│   │   ├── ContentItemEntity.java
│   │   ├── ContentItemRepository.java
│   │   └── ContentMapper.java
│   ├── messaging/
│   │   ├── KafkaEventPublisher.java
│   │   └── ContentEventConsumer.java
│   └── http/
│       ├── SchemaRegistryClient.java
│       └── WorkflowClient.java
└── api/
    ├── ContentController.java        ← Generic. One controller. All content types.
    └── ContentPublicController.java
```

---

## PART 4: DATA STORE DESIGN — DATABASE-PER-SERVICE

> **Canonical Reference:** All DDL, index definitions, RLS policies, cross-database reference rules, and migration history are documented in [`db-structure.md`](db-structure.md). This section provides the high-level design overview only.

### 4.1 Database Topology (Confirmed: All-PostgreSQL)

Every service owns its own **physically separate PostgreSQL 16 database**. There is no shared schema, no shared cluster (in production), and no foreign key that crosses a database boundary. This is the confirmed and final architecture after the `db-technology-selection.md` analysis.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    DATABASE-PER-SERVICE TOPOLOGY                                 │
│                                                                                  │
│  Service              Database         Engine         Contains                   │
│  ─────────────────────────────────────────────────────────────────────────────   │
│  iam-service        → iamdb            PostgreSQL 16  tenants, users, roles,     │
│                                                       permissions, api_keys,     │
│                                                       refresh_tokens, outbox     │
│                                                                                  │
│  schema-service     → schemadb         PostgreSQL 16  content_types, fields,     │
│                                                       categories, sites,         │
│                                                       menus, menu_items, outbox  │
│                                                                                  │
│  content-service    → contentdb        PostgreSQL 16  content_items (JSONB body),│
│                                                       content_versions, outbox   │
│                                                                                  │
│  workflow-service   → workflowdb       PostgreSQL 16  workflow_definitions,      │
│                                                       workflow_instances,         │
│                                                       workflow_history, outbox   │
│                                                                                  │
│  media-service      → mediadb          PostgreSQL 16  media_assets, outbox       │
│                                                                                  │
│  form-service       → formdb           PostgreSQL 16  form_definitions,          │
│                                                       form_submissions, outbox   │
│                                                                                  │
│  search-service     → searchstore      OpenSearch 2.x Denormalized search docs   │
│                                                       (no SQL schema)            │
│                                                                                  │
│  notification-svc   → notificationdb   PostgreSQL 16  notification_templates,    │
│                                                       notification_log, outbox   │
│                                                                                  │
│  audit-service      → auditdb          PostgreSQL 16  audit_events (partitioned  │
│                                                       by month, append-only)     │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Core Schema Strategy: JSONB Body (Not EAV)

Traditional EAV (Entity-Attribute-Value) is a performance disaster. The new system uses **JSONB-typed content bodies with schema validation**, giving the flexibility of EAV with the performance of a typed column.

```sql
-- contentdb — content.content_items (representative excerpt)
-- Full DDL is in db-structure.md § 6
CREATE TABLE content.content_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    content_type_id   UUID NOT NULL,          -- logical ref to schemadb, no FK
    content_type_code VARCHAR(100) NOT NULL,  -- denormalized for fast filtering
    title             VARCHAR(500) NOT NULL,
    slug              VARCHAR(500),
    body              JSONB NOT NULL DEFAULT '{}',  -- all dynamic type-specific fields
    status            VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    site_id           UUID NOT NULL,          -- logical ref to schemadb.sites, no FK
    site_code         VARCHAR(100) NOT NULL,  -- denormalized (replaces website_location)
    category_id       UUID,                   -- logical ref to schemadb.categories, no FK
    category_code     VARCHAR(100),           -- denormalized
    publish_at        TIMESTAMPTZ,
    expire_at         TIMESTAMPTZ,
    current_version   INT NOT NULL DEFAULT 1,
    version           BIGINT NOT NULL DEFAULT 0,  -- optimistic locking
    deleted_at        TIMESTAMPTZ               -- soft delete
);

CREATE INDEX idx_content_body ON content.content_items USING GIN(body);
-- Enables: body @> '{"tender_value": 5000000}'
-- Enables: (body->>'bid_submission_end')::timestamptz < NOW()
```

### 4.3 Cross-Database Reference Rule

> **No foreign key constraint may cross a database boundary.**

When a column in service A's database would logically reference a record in service B's database, it becomes a plain UUID column annotated as a logical reference:

```sql
some_column UUID NOT NULL  -- logical ref to targetdb.table_name, no FK (separate database)
```

Validation is enforced at the application layer:
1. **Synchronous API call at write time** — the writing service verifies the referenced entity exists via the owning service's API.
2. **Local read-model cache** — for high-frequency lookups, the service maintains a local projection populated from Kafka events (e.g., `content-service` caches `schemadb.content_types` from `cms.schema.changed` events).

See `db-structure.md` § 3 for the complete cross-reference map.

### 4.4 New Tables Added (database-per-service expansion)

**iamdb** — `tenants` table (root of multi-tenant model):
```sql
-- iamdb — iam.tenants  (Full DDL in db-structure.md § 4)
CREATE TABLE iam.tenants (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code    VARCHAR(100) NOT NULL UNIQUE,   -- e.g. 'acme-corp'
    name    VARCHAR(255) NOT NULL,
    domain  VARCHAR(255) UNIQUE,
    plan    VARCHAR(30)  NOT NULL DEFAULT 'STANDARD',
    status  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    settings JSONB NOT NULL DEFAULT '{}'
);
-- users.tenant_id → tenants.id is a REAL FK (both in iamdb)
```

**schemadb** — `sites`, `menus`, `menu_items` tables (replaces free-text `website_location`):
```sql
-- schemadb — schema.sites  (Full DDL in db-structure.md § 5)
CREATE TABLE schema.sites (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,   -- logical ref to iamdb.tenants
    code      VARCHAR(100) NOT NULL,   -- 'main-site', 'mobile-portal'
    name      VARCHAR(255) NOT NULL,
    domain    VARCHAR(255),
    locale    VARCHAR(10) NOT NULL DEFAULT 'en',
    UNIQUE(tenant_id, code)
);

CREATE TABLE schema.menus (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    site_id  UUID NOT NULL REFERENCES schema.sites(id),  -- real FK, same DB
    code     VARCHAR(100) NOT NULL,
    location VARCHAR(50) NOT NULL   -- 'header' | 'footer' | 'sidebar'
);

CREATE TABLE schema.menu_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_id         UUID NOT NULL REFERENCES schema.menus(id),   -- real FK
    parent_id       UUID REFERENCES schema.menu_items(id),       -- real FK (tree)
    label           VARCHAR(255) NOT NULL,
    link_type       VARCHAR(20) NOT NULL,   -- 'CONTENT' | 'EXTERNAL' | 'CATEGORY'
    content_item_id UUID,   -- logical ref to contentdb.content_items, no FK
    external_url    TEXT,
    display_order   INT NOT NULL DEFAULT 0
);
```

### 4.5 Per-Service Outbox Tables

The old `cms_core.outbox_events` centralized table is **removed**. Each producer service now has its own local `outbox_events` table inside its own database:

```sql
-- Pattern is identical in every producer DB (iamdb, schemadb, contentdb,
-- workflowdb, mediadb, formdb, notificationdb)
CREATE TABLE <schema>.outbox_events (
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
-- Each database has its own Debezium CDC connector reading from its local outbox.
```

A Debezium PostgreSQL connector per database reads from the WAL and publishes to Kafka. audit-service is a pure Kafka consumer and has no outbox.

### 4.6 Tenant Validation Without Cross-DB FK

Tenant existence and status are verified **once at the API Gateway** via the JWT claim issued by iam-service. The JWT contains `tenantId` and `tenantStatus`. The Gateway rejects requests with `tenantStatus != 'ACTIVE'`. Downstream services trust the JWT claim and do not re-validate tenant existence via SQL.

See `db-structure.md` § 14 for the full RLS and tenant isolation strategy.

> **Full DDL for all 9 databases**: [`db-structure.md`](db-structure.md)

---

_The following sections (Parts 5–17) are unchanged from the original design and remain valid. Where SQL snippets reference old `cms_*` schema prefixes, the authoritative DDL in `db-structure.md` supersedes them with the correct per-database schema names._

-- ============================================================
-- IAM: Users, Roles, Permissions
-- ============================================================

CREATE TABLE cms_iam.users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','INACTIVE','LOCKED','PENDING')),
    tenant_id       UUID NOT NULL,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID REFERENCES cms_iam.users(id),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE cms_iam.roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    name            VARCHAR(100) NOT NULL,
    code            VARCHAR(50) NOT NULL,
    description     TEXT,
    is_system_role  BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, code)
);

CREATE TABLE cms_iam.permissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource        VARCHAR(100) NOT NULL,  -- e.g. 'content', 'tender', 'media'
    action          VARCHAR(50) NOT NULL,   -- e.g. 'CREATE', 'READ', 'UPDATE', 'DELETE', 'PUBLISH'
    scope           VARCHAR(50),            -- e.g. 'OWN', 'DEPT', 'ALL'
    description     TEXT,
    UNIQUE(resource, action, scope)
);

CREATE TABLE cms_iam.role_permissions (
    role_id         UUID NOT NULL REFERENCES cms_iam.roles(id) ON DELETE CASCADE,
    permission_id   UUID NOT NULL REFERENCES cms_iam.permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE cms_iam.user_roles (
    user_id         UUID NOT NULL REFERENCES cms_iam.users(id) ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES cms_iam.roles(id) ON DELETE CASCADE,
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by      UUID REFERENCES cms_iam.users(id),
    expires_at      TIMESTAMPTZ,
    PRIMARY KEY (user_id, role_id)
);

-- ============================================================
-- SCHEMA REGISTRY: Dynamic Content Types
-- ============================================================

CREATE TABLE cms_content.content_types (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    code            VARCHAR(100) NOT NULL,         -- e.g. 'tender_notice', 'vacancy'
    display_name    VARCHAR(255) NOT NULL,
    description     TEXT,
    icon            VARCHAR(100),
    is_hierarchical BOOLEAN NOT NULL DEFAULT false, -- supports parent-child
    is_versionable  BOOLEAN NOT NULL DEFAULT true,
    is_schedulable  BOOLEAN NOT NULL DEFAULT true,
    has_workflow    BOOLEAN NOT NULL DEFAULT false,
    workflow_id     UUID,                           -- FK to workflow_definitions
    default_persona VARCHAR(100),
    metadata        JSONB NOT NULL DEFAULT '{}',    -- UI hints, rendering config
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version         INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID REFERENCES cms_iam.users(id),
    UNIQUE(tenant_id, code)
);

-- Field definitions per content type (replaces all per-module field columns)
CREATE TABLE cms_content.field_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type_id UUID NOT NULL REFERENCES cms_content.content_types(id) ON DELETE CASCADE,
    field_key       VARCHAR(100) NOT NULL,          -- e.g. 'title', 'tender_value', 'due_date'
    display_label   VARCHAR(255) NOT NULL,
    field_type      VARCHAR(50) NOT NULL            -- TEXT|NUMBER|DATE|BOOLEAN|FILE|RICHTEXT|JSON|RELATION
                    CHECK (field_type IN ('TEXT','NUMBER','DATE','DATETIME','BOOLEAN',
                                          'FILE','RICHTEXT','JSON','RELATION','SELECT','MULTISELECT')),
    is_required     BOOLEAN NOT NULL DEFAULT false,
    is_searchable   BOOLEAN NOT NULL DEFAULT false,
    is_filterable   BOOLEAN NOT NULL DEFAULT false,
    is_listable     BOOLEAN NOT NULL DEFAULT true,  -- appears in list view
    display_order   INT NOT NULL DEFAULT 0,
    default_value   TEXT,
    validation_rules JSONB NOT NULL DEFAULT '{}',   -- JSON Schema validation rules
    ui_config       JSONB NOT NULL DEFAULT '{}',    -- placeholder, help text, width, etc.
    relation_config JSONB,                          -- for RELATION type: target content_type_code
    group_name      VARCHAR(100),                   -- for UI field grouping
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(content_type_id, field_key)
);

-- Category registry (replaces all XxxCategoryEnum files)
CREATE TABLE cms_content.categories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type_id UUID NOT NULL REFERENCES cms_content.content_types(id) ON DELETE CASCADE,
    code            VARCHAR(100) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    parent_id       UUID REFERENCES cms_content.categories(id),
    description     TEXT,
    icon            VARCHAR(100),
    metadata        JSONB NOT NULL DEFAULT '{}',
    display_order   INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(content_type_id, code)
);

-- ============================================================
-- CONTENT: Universal Content Store
-- ============================================================

CREATE TABLE cms_content.content_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    content_type_id UUID NOT NULL REFERENCES cms_content.content_types(id),
    content_type_code VARCHAR(100) NOT NULL,        -- denormalized for query speed
    title           VARCHAR(500) NOT NULL,          -- universal, always present
    slug            VARCHAR(500),                   -- URL-friendly identifier
    body            JSONB NOT NULL DEFAULT '{}',    -- all dynamic fields live here
    status          VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT','IN_REVIEW','APPROVED','PUBLISHED',
                                      'SCHEDULED','ARCHIVED','TRASHED','REJECTED')),
    category_id     UUID REFERENCES cms_content.categories(id),
    parent_id       UUID REFERENCES cms_content.content_items(id),  -- hierarchical
    folder_path     TEXT,                           -- materialized path for tree queries
    website_location VARCHAR(100),                 -- target site/location
    persona         VARCHAR(100),                   -- audience segment
    publish_at      TIMESTAMPTZ,                   -- scheduled publish time
    expire_at       TIMESTAMPTZ,                   -- auto-archive time
    published_at    TIMESTAMPTZ,                   -- actual publish time
    current_version INT NOT NULL DEFAULT 1,
    workflow_instance_id UUID,                     -- active workflow instance
    media_ids       UUID[] DEFAULT '{}',           -- associated media assets
    tags            TEXT[] DEFAULT '{}',
    external_link   TEXT,
    order_index     INT NOT NULL DEFAULT 0,
    is_featured     BOOLEAN NOT NULL DEFAULT false,
    view_count      BIGINT NOT NULL DEFAULT 0,
    metadata        JSONB NOT NULL DEFAULT '{}',
    -- Audit columns
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID REFERENCES cms_iam.users(id),
    updated_by      UUID REFERENCES cms_iam.users(id),
    deleted_at      TIMESTAMPTZ,                   -- soft delete
    deleted_by      UUID REFERENCES cms_iam.users(id),
    version         BIGINT NOT NULL DEFAULT 0      -- optimistic locking
);

-- Indexes for common query patterns
CREATE INDEX idx_content_type_status ON cms_content.content_items(content_type_code, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_content_tenant ON cms_content.content_items(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_content_category ON cms_content.content_items(category_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_content_location ON cms_content.content_items(website_location) WHERE deleted_at IS NULL;
CREATE INDEX idx_content_publish_at ON cms_content.content_items(publish_at) WHERE status = 'SCHEDULED';
CREATE INDEX idx_content_expire_at ON cms_content.content_items(expire_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_content_parent ON cms_content.content_items(parent_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_content_body ON cms_content.content_items USING GIN(body);
CREATE INDEX idx_content_tags ON cms_content.content_items USING GIN(tags);
CREATE INDEX idx_content_folder_path ON cms_content.content_items(folder_path text_pattern_ops);

-- Version history (immutable snapshots)
CREATE TABLE cms_content.content_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_item_id UUID NOT NULL REFERENCES cms_content.content_items(id) ON DELETE CASCADE,
    version_number  INT NOT NULL,
    title           VARCHAR(500) NOT NULL,
    body            JSONB NOT NULL,
    status          VARCHAR(30) NOT NULL,
    change_summary  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID REFERENCES cms_iam.users(id),
    UNIQUE(content_item_id, version_number)
);

-- ============================================================
-- WORKFLOW ENGINE
-- ============================================================

CREATE TABLE cms_workflow.workflow_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    code            VARCHAR(100) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    definition      JSONB NOT NULL,  -- full state machine definition (states, transitions, actions)
    version         INT NOT NULL DEFAULT 1,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, code, version)
);

/*
Example workflow definition JSON:
{
  "initialState": "DRAFT",
  "states": ["DRAFT", "IN_REVIEW", "APPROVED", "PUBLISHED", "REJECTED"],
  "transitions": [
    {
      "from": "DRAFT", "to": "IN_REVIEW",
      "trigger": "SUBMIT_FOR_REVIEW",
      "requiredRole": "AUTHOR",
      "actions": ["NOTIFY_REVIEWERS", "CREATE_AUDIT_EVENT"]
    },
    {
      "from": "IN_REVIEW", "to": "APPROVED",
      "trigger": "APPROVE",
      "requiredRole": "REVIEWER",
      "actions": ["NOTIFY_AUTHOR", "SCHEDULE_PUBLISH"]
    }
  ]
}
*/

CREATE TABLE cms_workflow.workflow_instances (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_def_id     UUID NOT NULL REFERENCES cms_workflow.workflow_definitions(id),
    entity_type         VARCHAR(100) NOT NULL,   -- 'content_item', 'vendor_registration'
    entity_id           UUID NOT NULL,
    current_state       VARCHAR(50) NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    metadata            JSONB NOT NULL DEFAULT '{}'
);

CREATE TABLE cms_workflow.workflow_history (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id         UUID NOT NULL REFERENCES cms_workflow.workflow_instances(id),
    from_state          VARCHAR(50) NOT NULL,
    to_state            VARCHAR(50) NOT NULL,
    trigger             VARCHAR(100) NOT NULL,
    actor_id            UUID REFERENCES cms_iam.users(id),
    comment             TEXT,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata            JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_workflow_entity ON cms_workflow.workflow_instances(entity_type, entity_id);

-- ============================================================
-- MEDIA MANAGEMENT
-- ============================================================

CREATE TABLE cms_media.media_assets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    original_name   VARCHAR(500) NOT NULL,
    stored_name     VARCHAR(500) NOT NULL,
    storage_path    TEXT NOT NULL,
    storage_backend VARCHAR(50) NOT NULL DEFAULT 'LOCAL'
                    CHECK (storage_backend IN ('LOCAL','S3','GCS','AZURE_BLOB')),
    mime_type       VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    checksum        VARCHAR(64),               -- SHA-256 for dedup
    alt_text        TEXT,
    caption         TEXT,
    width           INT,
    height          INT,
    duration_ms     INT,                       -- for audio/video
    is_public       BOOLEAN NOT NULL DEFAULT false,
    folder_path     TEXT NOT NULL DEFAULT '/',
    tags            TEXT[] DEFAULT '{}',
    metadata        JSONB NOT NULL DEFAULT '{}',
    cdn_url         TEXT,
    thumbnail_url   TEXT,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    uploaded_by     UUID REFERENCES cms_iam.users(id),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_media_tenant ON cms_media.media_assets(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_folder ON cms_media.media_assets(folder_path) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_mime ON cms_media.media_assets(mime_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_checksum ON cms_media.media_assets(checksum) WHERE deleted_at IS NULL;

-- ============================================================
-- DYNAMIC FORMS
-- ============================================================

CREATE TABLE cms_form.form_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    code            VARCHAR(100) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    schema          JSONB NOT NULL,    -- JSON Schema for validation
    ui_schema       JSONB NOT NULL,    -- react-jsonschema-form ui:schema
    submit_action   VARCHAR(100),      -- action handler key
    is_active       BOOLEAN NOT NULL DEFAULT true,
    version         INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, code)
);

CREATE TABLE cms_form.form_submissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    form_id         UUID NOT NULL REFERENCES cms_form.form_definitions(id),
    submitted_data  JSONB NOT NULL,
    submitted_by    UUID REFERENCES cms_iam.users(id),
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address      INET,
    status          VARCHAR(30) NOT NULL DEFAULT 'RECEIVED'
);

-- ============================================================
-- AUDIT LOG (append-only, never updated or deleted)
-- ============================================================

CREATE TABLE cms_audit.audit_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(100) NOT NULL,       -- e.g. 'content.published', 'user.login'
    actor_id        UUID,
    actor_email     VARCHAR(255),
    entity_type     VARCHAR(100),
    entity_id       UUID,
    tenant_id       UUID,
    old_value       JSONB,
    new_value       JSONB,
    ip_address      INET,
    user_agent      TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    correlation_id  UUID                         -- trace ID across services
) PARTITION BY RANGE (occurred_at);

-- Monthly partitions for audit
CREATE TABLE cms_audit.audit_events_2025_01 PARTITION OF cms_audit.audit_events
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
-- (automate partition creation via pg_partman)

CREATE INDEX idx_audit_entity ON cms_audit.audit_events(entity_type, entity_id);
CREATE INDEX idx_audit_actor ON cms_audit.audit_events(actor_id);
CREATE INDEX idx_audit_type ON cms_audit.audit_events(event_type);
CREATE INDEX idx_audit_time ON cms_audit.audit_events(occurred_at DESC);

-- ============================================================
-- SEARCH INDEX (PostgreSQL full-text as baseline)
-- ============================================================

CREATE TABLE cms_search.search_index (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_item_id UUID NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    tenant_id       UUID NOT NULL,
    title           TEXT NOT NULL,
    body_text       TEXT,
    tags            TEXT[],
    category        TEXT,
    website_location VARCHAR(100),
    status          VARCHAR(30) NOT NULL,
    published_at    TIMESTAMPTZ,
    search_vector   TSVECTOR GENERATED ALWAYS AS (
                        setweight(to_tsvector('english', coalesce(title,'')), 'A') ||
                        setweight(to_tsvector('english', coalesce(body_text,'')), 'B') ||
                        setweight(to_tsvector('english', array_to_string(tags,' ')), 'C')
                    ) STORED,
    indexed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(content_item_id)
);

CREATE INDEX idx_search_vector ON cms_search.search_index USING GIN(search_vector);
CREATE INDEX idx_search_type ON cms_search.search_index(content_type, status);
CREATE INDEX idx_search_tenant ON cms_search.search_index(tenant_id);
```

---

## PART 5: CONTENT MODELLING & DYNAMIC CONTENT ENGINE

### 5.1 How It Works End-to-End

**Old way (current system):**
1. Developer writes `CmsTenderNotice.java` (model)
2. Developer writes `TenderNoticeCategoryEnum.java`
3. Developer writes `TenderNoticeController.java` (600 LOC)
4. Developer writes `TenderNoticeRepository.java`
5. Developer writes `TenderNoticeServiceImpl.java`
6. Database migration creates `cms_tender_notice` table
7. Deploy

**New way:**
1. Admin opens "Content Types" in CMS admin
2. Creates new type with code `tender_notice`
3. Adds fields: title, tender_value, bid_date, organisation_code...
4. Sets workflow: `standard-publish-workflow`
5. Saves → Done

No code written. No deployment. Immediate.

### 5.2 Content Type Definition Example

A `tender_notice` that today requires ~7 Java files becomes a database record:

```json
{
  "code": "tender_notice",
  "displayName": "Tender Notice",
  "isHierarchical": false,
  "isVersionable": true,
  "hasWorkflow": true,
  "workflowCode": "tender-approval-workflow",
  "fields": [
    { "key": "reference_num",         "type": "TEXT",     "required": true,  "label": "Reference Number",       "searchable": true  },
    { "key": "organisation_code",     "type": "TEXT",     "required": true,  "label": "Organisation Code",      "default": "CG57006" },
    { "key": "tender_value",          "type": "NUMBER",   "required": true,  "label": "Tender Value (INR)",     "validation": { "min": 0 } },
    { "key": "tender_fee",            "type": "NUMBER",   "required": true,  "label": "Tender Fee (INR)" },
    { "key": "emd",                   "type": "NUMBER",   "required": true,  "label": "EMD (INR)" },
    { "key": "publication_date",      "type": "DATE",     "required": true,  "label": "Publication Date" },
    { "key": "bid_submission_end",    "type": "DATETIME", "required": true,  "label": "Bid Submission Deadline" },
    { "key": "bid_open_date",         "type": "DATE",     "required": true,  "label": "Bid Opening Date" },
    { "key": "pre_qual",              "type": "TEXT",     "required": false, "label": "Pre-Qualification" },
    { "key": "tender_documents",      "type": "FILE",     "required": false, "label": "Tender Documents",       "multiple": true },
    { "key": "division",              "type": "SELECT",   "required": true,  "label": "Division",               "options_from": "divisions_lookup" },
    { "key": "department",            "type": "SELECT",   "required": true,  "label": "Department",             "options_from": "departments_lookup" }
  ],
  "categories": [
    { "code": "open_tender",    "displayName": "Open Tender" },
    { "code": "limited_tender", "displayName": "Limited Tender" },
    { "code": "single_tender",  "displayName": "Single Tender" }
  ]
}
```

This replaces: `TenderNotice.java` (30KB model) + `TenderNoticeCategoryEnum.java` + controller + service + repository + migration.

---

## PART 6: WORKFLOW ENGINE DESIGN

### 6.1 State Machine Architecture

The workflow engine is a **data-driven state machine** where states, transitions, guards, and actions are all defined in JSONB configuration. No workflow logic exists in content controllers.

```
┌─────────────────────────────────────────────────────────────────────┐
│                    WORKFLOW ENGINE                                    │
│                                                                      │
│  ContentEvent              WorkflowDefinition (from DB)              │
│  (content.created) ──────► ┌──────────────────────────────┐         │
│                             │ StateMachineFactory           │         │
│                             │  .build(definition)           │         │
│                             └──────────┬───────────────────┘         │
│                                        │                             │
│                             ┌──────────▼───────────────────┐         │
│                             │ WorkflowInstance              │         │
│                             │  .trigger("SUBMIT_FOR_REVIEW")│         │
│                             └──────────┬───────────────────┘         │
│                                        │                             │
│                      ┌─────────────────┼───────────────────┐         │
│                       │                │                   │         │
│              ┌────────▼────┐  ┌────────▼────┐  ┌──────────▼───┐     │
│              │ GuardChain  │  │ActionChain  │  │ AuditLogger  │     │
│              │ (validates  │  │ (side       │  │ (writes      │     │
│              │  permission)│  │  effects)   │  │  history)    │     │
│              └─────────────┘  └─────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.2 Workflow Definition (JSONB stored in DB)

```json
{
  "code": "content-standard-publish",
  "name": "Standard Content Publish Workflow",
  "initialState": "DRAFT",
  "states": {
    "DRAFT":     { "label": "Draft",       "terminal": false },
    "IN_REVIEW": { "label": "In Review",   "terminal": false },
    "APPROVED":  { "label": "Approved",    "terminal": false },
    "PUBLISHED": { "label": "Published",   "terminal": false },
    "REJECTED":  { "label": "Rejected",    "terminal": false },
    "ARCHIVED":  { "label": "Archived",    "terminal": true  }
  },
  "transitions": [
    {
      "id": "submit_for_review",
      "from": "DRAFT",
      "to": "IN_REVIEW",
      "trigger": "SUBMIT_FOR_REVIEW",
      "guards": [
        { "type": "ROLE_CHECK",       "params": { "roles": ["AUTHOR", "EDITOR"] } },
        { "type": "FIELD_NOT_EMPTY",  "params": { "fields": ["title", "body"] } }
      ],
      "actions": [
        { "type": "NOTIFY",           "params": { "to": "role:REVIEWER", "template": "review_requested" } },
        { "type": "EMIT_EVENT",       "params": { "topic": "cms.workflow.submitted" } },
        { "type": "AUDIT_LOG",        "params": { "event": "content.submitted_for_review" } }
      ]
    },
    {
      "id": "approve",
      "from": "IN_REVIEW",
      "to": "APPROVED",
      "trigger": "APPROVE",
      "guards": [
        { "type": "ROLE_CHECK",       "params": { "roles": ["REVIEWER", "ADMIN"] } },
        { "type": "NOT_OWN_CONTENT",  "params": {} }
      ],
      "actions": [
        { "type": "NOTIFY",           "params": { "to": "content:author", "template": "content_approved" } },
        { "type": "SCHEDULE_PUBLISH", "params": { "field": "publish_at" } },
        { "type": "EMIT_EVENT",       "params": { "topic": "cms.workflow.approved" } }
      ]
    },
    {
      "id": "reject",
      "from": "IN_REVIEW",
      "to": "REJECTED",
      "trigger": "REJECT",
      "guards": [
        { "type": "ROLE_CHECK",       "params": { "roles": ["REVIEWER", "ADMIN"] } }
      ],
      "actions": [
        { "type": "NOTIFY",           "params": { "to": "content:author", "template": "content_rejected" } },
        { "type": "REQUIRE_COMMENT",  "params": {} }
      ]
    },
    {
      "id": "publish",
      "from": "APPROVED",
      "to": "PUBLISHED",
      "trigger": "PUBLISH",
      "guards": [
        { "type": "ROLE_CHECK",       "params": { "roles": ["ADMIN", "PUBLISHER"] } }
      ],
      "actions": [
        { "type": "SET_PUBLISHED_AT", "params": {} },
        { "type": "INDEX_SEARCH",     "params": {} },
        { "type": "INVALIDATE_CACHE", "params": { "patterns": ["content:*", "page:*"] } },
        { "type": "EMIT_EVENT",       "params": { "topic": "cms.content.published" } }
      ]
    }
  ]
}
```

### 6.3 Adding a New Workflow (Zero Code Change)

Need a "Tender-specific 3-level approval" workflow?
1. Open Workflow Designer in CMS Admin
2. Define states and transitions via UI
3. Assign to `tender_notice` content type
4. Done — existing content types unaffected

---

## PART 7: DYNAMIC FORM ENGINE

### 7.1 Architecture

Forms are rendered using **JSON Schema + UI Schema** on the frontend (react-jsonschema-form or equivalent). The backend stores schema, validates submissions, and routes them to handlers.

```
┌─────────────────────────────────────────────────────────────┐
│                    FORM LIFECYCLE                            │
│                                                              │
│  DESIGN                 RENDER               SUBMIT         │
│  ─────────              ──────               ──────         │
│  Admin creates          Next.js fetches      POST /submit   │
│  form schema  ─────────► schema & renders   ─────────────►  │
│  in UI        ◄────────  form dynamically   ◄─────────────  │
│               API        (react-jsonschema   Server         │
│               saves      or custom engine)   validates,     │
│               to DB                          saves, routes  │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 Form Schema Example (Vendor Registration)

```json
{
  "code": "vendor_registration",
  "schema": {
    "type": "object",
    "required": ["company_name", "pan_number", "gst_number", "email"],
    "properties": {
      "company_name":  { "type": "string", "minLength": 2, "maxLength": 255 },
      "pan_number":    { "type": "string", "pattern": "^[A-Z]{5}[0-9]{4}[A-Z]$" },
      "gst_number":    { "type": "string", "pattern": "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$" },
      "email":         { "type": "string", "format": "email" },
      "phone":         { "type": "string", "pattern": "^[6-9][0-9]{9}$" },
      "documents":     { "type": "array",  "items": { "type": "string", "format": "uri" } }
    }
  },
  "uiSchema": {
    "pan_number":  { "ui:placeholder": "ABCDE1234F", "ui:help": "10-character PAN number" },
    "gst_number":  { "ui:placeholder": "22AAAAA0000A1Z5" },
    "documents":   { "ui:widget": "file-upload", "ui:multiple": true }
  },
  "submitAction": "vendor.registration.submit"
}
```

---

## PART 8: MEDIA MANAGEMENT ARCHITECTURE

```
┌─────────────────────────────────────────────────────────────────────┐
│                    MEDIA SERVICE                                      │
│                                                                      │
│  Upload Request                                                      │
│  ─────────────                                                       │
│  1. Validate (type, size, virus scan)                                │
│  2. Generate UUID filename                                           │
│  3. Check SHA-256 checksum (dedup)                                   │
│  4. Store to backend (Local/S3/GCS)                                  │
│  5. Generate thumbnail (images/video)                                │
│  6. Write metadata to DB                                             │
│  7. Emit media.uploaded event                                        │
│  8. Return MediaAssetDTO                                             │
│                                                                      │
│  Storage Backends (abstracted via StoragePort interface)             │
│  ┌──────────┐  ┌───────────┐  ┌───────────┐  ┌──────────────┐      │
│  │  Local   │  │  AWS S3   │  │  GCS      │  │ Azure Blob   │      │
│  │  (dev)   │  │  (prod)   │  │  (alt)    │  │ (enterprise) │      │
│  └──────────┘  └───────────┘  └───────────┘  └──────────────┘      │
│                                                                      │
│  Media Processing Pipeline (async via Kafka)                         │
│  ┌──────────────────────────────────────────────────────────┐       │
│  │ media.uploaded → thumbnail → resize → CDN invalidate     │       │
│  └──────────────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────────┘
```

**Key design:** The `StoragePort` interface means switching from local filesystem to AWS S3 requires a configuration change, not code change. This directly eliminates the hardcoded `environment.getProperty("file.repository.path")` pattern found in the current system.

---

## PART 9: RBAC & AUTHORIZATION

### 9.1 Permission Model

The current system uses session-based role checks scattered across 28 controllers. The new system centralises authorization using a **Resource-Action-Scope** model enforced at the API gateway and service layer.

```
Permission = Resource + Action + Scope

Examples:
  content:CREATE:OWN        → Can create content (for themselves)
  content:READ:ALL          → Can read all content
  content:PUBLISH:DEPT      → Can publish content in own department
  tender:APPROVE:ALL        → Can approve any tender
  workflow:MANAGE:ALL       → Can configure workflows
  media:DELETE:OWN          → Can delete own media
```

### 9.2 Policy Enforcement Points

```
HTTP Request
    │
    ▼
API Gateway (JWT validation, rate limiting)
    │
    ▼
Service Layer @PreAuthorize("hasPermission('content', 'PUBLISH', 'ALL')")
    │
    ▼
Domain Service (row-level security via predicate)
    │
    ▼
Repository (tenant isolation, soft-delete filter)
```

### 9.3 Row-Level Security (PostgreSQL RLS)

```sql
-- contentdb — Enable RLS on content_items (per-database, not shared schema)
ALTER TABLE content.content_items ENABLE ROW LEVEL SECURITY;

-- Policy: users can only see their tenant's content
-- tenant_id is populated from the JWT claim — no cross-DB FK to iamdb
CREATE POLICY tenant_isolation ON content.content_items
    TO cms_content_app_role
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

-- Policy: authors can only see their own drafts (others see published)
CREATE POLICY content_visibility ON content.content_items
    USING (
        status = 'PUBLISHED'
        OR created_by = current_setting('app.current_user_id')::UUID
        OR current_setting('app.user_role') IN ('ADMIN', 'REVIEWER', 'EDITOR')
    );
-- Same RLS pattern applied independently in each of the 7 PostgreSQL producer databases.
-- Tenant existence is pre-validated by the API Gateway (JWT tenantStatus check).
-- See db-structure.md § 14 for the complete RLS strategy.
```

---

## PART 10: EVENT-DRIVEN COMMUNICATION

### 10.1 Kafka Topic Design

```
cms.content.created          → search-service indexes, audit-service logs
cms.content.updated          → search-service re-indexes, cache invalidation
cms.content.published        → notification-service notifies, CDN purge
cms.content.archived         → search-service removes, analytics event
cms.workflow.transitioned    → notification-service, audit-service
cms.media.uploaded           → thumbnail generation, virus scan
cms.user.login               → audit-service, anomaly detection
cms.user.role.changed        → audit-service, cache invalidation
cms.schema.changed           → all services refresh schema cache
```

### 10.2 Event Schema (CloudEvents standard)

```json
{
  "specversion": "1.0",
  "type": "cms.content.published",
  "source": "cms/content-service",
  "id": "uuid-v4",
  "time": "2025-01-01T00:00:00Z",
  "datacontenttype": "application/json",
  "tenantid": "tenant-uuid",
  "correlationid": "trace-uuid",
  "data": {
    "contentItemId": "content-uuid",
    "contentTypeCode": "tender_notice",
    "publishedAt": "2025-01-01T00:00:00Z",
    "publishedBy": "user-uuid",
    "websiteLocation": "main-site",
    "slug": "tender-2025-001"
  }
}
```

### 10.3 Outbox Pattern (Guaranteed Delivery — Per-Database)

Never call Kafka inside a DB transaction. Instead, write to a local `outbox_events` table in the **same transaction** as the business write. Debezium CDC reads from the table via PostgreSQL WAL logical replication and publishes to Kafka.

**Important:** There is no longer a centralized `cms_core.outbox_events` table. Every producer service has its own local outbox inside its own database:

```
Debezium connector per database:
  debezium-connector-iamdb       → reads iamdb.iam.outbox_events        → Kafka
  debezium-connector-schemadb    → reads schemadb.schema.outbox_events   → Kafka
  debezium-connector-contentdb   → reads contentdb.content.outbox_events → Kafka
  debezium-connector-workflowdb  → reads workflowdb.workflow.outbox_events → Kafka
  debezium-connector-mediadb     → reads mediadb.media.outbox_events     → Kafka
  debezium-connector-formdb      → reads formdb.form.outbox_events       → Kafka
  debezium-connector-notifdb     → reads notificationdb.notification.outbox_events → Kafka
  (audit-service: pure consumer, no outbox needed)
```

Write path example (content-service, one DB transaction):
```sql
-- In contentdb — one atomic transaction:
INSERT INTO content.content_items (...) VALUES (...);
INSERT INTO content.content_versions (content_item_id, ...) VALUES (...);
INSERT INTO content.outbox_events (aggregate_type, event_type, payload)
    VALUES ('content_item', 'cms.content.published', '{...}');
-- COMMIT — Debezium picks up outbox row from WAL and publishes to Kafka
```

This guarantees exactly-once delivery semantics even if Kafka is temporarily down. Each service's outbox is independent — a CDC failure on one database does not affect other services.

---

## PART 11: CACHING STRATEGY

### 11.1 Cache Layers

```
Browser Cache (CDN headers)
    │  Cache-Control: public, max-age=300 (5 min for published content)
    │
    ▼
CDN (CloudFront / Nginx)
    │  Static assets: 1 year with content hash
    │  API responses: Surrogate-Control headers
    │
    ▼
Redis (Application Cache)
    │
    ├── content:{id}           TTL: 10 min  → individual content items
    ├── content:list:{typeCode}:{siteCode}:{page}   TTL: 5 min  → listing pages
    ├── schema:{typeCode}      TTL: 60 min  → schema definitions (rarely change)
    ├── menu:{siteCode}:{location}  TTL: 15 min  → navigation menus (site-scoped)
    ├── user:{id}:permissions  TTL: 5 min   → permission bitset
    ├── workflow:{code}:def    TTL: 30 min  → workflow definitions
    └── search:facets:{type}   TTL: 10 min  → search aggregation results
```

### 11.2 Cache Invalidation via Events

```java
@KafkaListener(topics = "cms.content.published")
public void onContentPublished(ContentPublishedEvent event) {
    cacheService.evict("content:" + event.getContentItemId());
    cacheService.evictPattern("content:list:" + event.getContentTypeCode() + ":*");
    // site_code replaces websiteLocation — cache key updated accordingly
    cacheService.evictPattern("menu:" + event.getSiteCode() + ":*");
}
```

This replaces the ad-hoc `@Scheduled` cache refresh found in the current system with event-driven, targeted invalidation.

---

## PART 12: MONITORING & OBSERVABILITY

### 12.1 Three Pillars

```
METRICS (Micrometer → Prometheus → Grafana)
─────────────────────────────────────────────
cms_content_create_total{type="tender_notice",status="success"}
cms_content_publish_duration_seconds{type="tender_notice"}
cms_workflow_transition_total{from="DRAFT",to="IN_REVIEW",result="success"}
cms_cache_hit_ratio{region="content"}
cms_kafka_consumer_lag{topic="cms.content.published",group="search-service"}

TRACES (OpenTelemetry → Jaeger / Tempo)
───────────────────────────────────────
Every HTTP request gets a trace ID.
Every service-to-service call carries the trace.
"Why did this tender publish fail?" → Single trace shows all service calls.

LOGS (Logback → Fluentd → Elasticsearch → Kibana)
───────────────────────────────────────────────────
Structured JSON logs with:
  { "traceId": "...", "tenantId": "...", "userId": "...", "event": "...", "contentId": "..." }
Never raw string logs. Always structured. Always correlated.
```

### 12.2 Health Checks

```java
// Each service exposes Spring Boot Actuator endpoints:
GET /actuator/health          → liveness (is the process alive?)
GET /actuator/health/readiness → readiness (can it serve traffic?)
GET /actuator/metrics          → Prometheus scrape endpoint
GET /actuator/info             → build metadata

// Custom health indicators:
@Component
class KafkaHealthIndicator implements HealthIndicator { ... }
class SchemaRegistryHealthIndicator implements HealthIndicator { ... }
```

---

## PART 13: CI/CD STRATEGY

```
Developer pushes to feature branch
    │
    ▼
GitHub Actions Pipeline
    │
    ├── Stage 1: Validate (2 min)
    │   ├── mvn compile
    │   ├── mvn checkstyle:check
    │   └── mvn spotbugs:check
    │
    ├── Stage 2: Test (10 min, parallel)
    │   ├── mvn test (unit)
    │   ├── mvn verify (integration, Testcontainers)
    │   └── npm test (frontend)
    │
    ├── Stage 3: Build (5 min)
    │   ├── mvn package -DskipTests
    │   └── docker build → push to ECR
    │
    ├── Stage 4: Deploy to Dev (auto)
    │   ├── helm upgrade --install cms-dev
    │   └── Run smoke tests
    │
    └── Stage 5: Deploy to Prod (manual gate)
        ├── Blue/Green deployment
        ├── Run integration tests against green
        └── Swap traffic if green passes
```

**Zero-downtime deployments** are possible because:
- DB migrations run separately via Flyway before deploy
- Migrations must be backward-compatible with the previous version
- Services are stateless (session in Redis, not JVM memory)
- Kubernetes rolling update with health check gates

---

## PART 14: MIGRATION STRATEGY

### 14.1 Phased Migration (12–16 weeks)

**Phase 1 — Foundation (Weeks 1–4)**  
Deploy new infrastructure alongside old system. No user-facing change.
- Set up Kubernetes cluster, Kafka, Redis
- Deploy 9 separate PostgreSQL 16 instances (one per service) using CloudNativePG operator
- Deploy `iam-service` (iamdb), `schema-service` (schemadb), `audit-service` (auditdb)
- Seed default tenant record in iamdb; seed sites (replacing `website_location` free-text)
- Migrate users and roles to new IAM; seed menu/navigation data into schemadb
- Run both systems in parallel with data sync

**Phase 2 — Content Types (Weeks 5–8)**  
Migrate category enums to database-driven categories.
- Map each `XxxCategoryEnum` to `cms_content.categories` rows
- Deploy `content-service` alongside old module services
- New content writes to both (dual-write with idempotency)
- Read from old, verify parity

**Phase 3 — Traffic Cut-over (Weeks 9–12)**  
Gradually shift traffic to new services.
- Route 10% → 25% → 50% → 100% using feature flags
- Remove old module controllers one by one
- Maintain old DB schema until read-path is confirmed clean

**Phase 4 — Decommission (Weeks 13–16)**  
Remove old code, simplify DB, finalize.
- Remove all 25 ENUM files and 22 Convertor files
- Consolidate 28 module tables into `content_items` + archive old tables
- Remove old controllers, services, repositories

### 14.2 Data Migration SQL Pattern

```sql
-- Migrate CmsAboutUs records to content_items
INSERT INTO cms_content.content_items (
    id, tenant_id, content_type_id, content_type_code,
    title, body, status, category_id,
    folder_path, site_id, site_code, persona,
    publish_at, expire_at, published_at,
    created_at, updated_at, created_by
)
SELECT
    gen_random_uuid(),
    'default-tenant-uuid',
    (SELECT id FROM cms_content.content_types WHERE code = 'about_us'),
    'about_us',
    au.title,
    jsonb_build_object(
        'description', au.discription,
        'externalLink', au.external_link,
        'isGrid', au.is_grid,
        'isCard', au.is_card,
        'fields', au.fields
    ),
    CASE
        WHEN au.is_delete = true THEN 'ARCHIVED'
        WHEN au.is_publish = true THEN 'PUBLISHED'
        ELSE 'DRAFT'
    END,
    (SELECT id FROM cms_content.categories WHERE code = au.category_id::TEXT),
    au.folder_path,
    -- website_location migrated to site_id + site_code (see schemadb.sites)
    (SELECT id   FROM schemadb_link.sites WHERE code = au.website_location AND tenant_id = 'default-tenant-uuid'),
    au.website_location,  -- preserved as site_code (denormalized)
    au.persona,
    au.publish_date,
    au.due_date,
    CASE WHEN au.is_publish = true THEN au.updated_date END,
    au.created_date,
    au.updated_date,
    (SELECT id FROM cms_iam.users WHERE old_user_id = au.created_by)
FROM cms.cms_about_us au
WHERE au.is_delete = false;
```

---

## PART 15: FOLDER STRUCTURES

### 15.1 Backend (Spring Boot) — Hexagonal Architecture

```
cms-platform/
├── services/
│   ├── content-service/
│   │   ├── src/main/java/com/cms/content/
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   ├── ContentItem.java
│   │   │   │   │   ├── ContentVersion.java
│   │   │   │   │   └── ContentStatus.java
│   │   │   │   ├── ports/
│   │   │   │   │   ├── in/
│   │   │   │   │   │   ├── CreateContentUseCase.java
│   │   │   │   │   │   ├── PublishContentUseCase.java
│   │   │   │   │   │   └── QueryContentUseCase.java
│   │   │   │   │   └── out/
│   │   │   │   │       ├── ContentRepository.java
│   │   │   │   │       ├── SchemaRegistryPort.java
│   │   │   │   │       ├── WorkflowPort.java
│   │   │   │   │       └── EventPublisherPort.java
│   │   │   │   └── service/
│   │   │   │       ├── ContentDomainService.java
│   │   │   │       └── ContentPublishingService.java
│   │   │   ├── application/
│   │   │   │   ├── ContentApplicationService.java
│   │   │   │   └── dto/
│   │   │   ├── infrastructure/
│   │   │   │   ├── persistence/
│   │   │   │   │   ├── ContentItemEntity.java
│   │   │   │   │   ├── ContentItemJpaRepository.java
│   │   │   │   │   └── ContentMapper.java
│   │   │   │   ├── messaging/
│   │   │   │   │   ├── KafkaEventPublisher.java
│   │   │   │   │   └── OutboxEventProcessor.java
│   │   │   │   ├── cache/
│   │   │   │   │   └── RedisContentCache.java
│   │   │   │   └── http/
│   │   │   │       ├── SchemaRegistryClient.java
│   │   │   │       └── WorkflowServiceClient.java
│   │   │   └── api/
│   │   │       ├── ContentRestController.java    ← ONE controller
│   │   │       ├── ContentPublicController.java
│   │   │       └── advice/
│   │   │           └── GlobalExceptionHandler.java
│   │   ├── src/test/
│   │   │   ├── unit/domain/
│   │   │   ├── integration/infrastructure/
│   │   │   └── arch/ArchitectureTest.java       ← ArchUnit tests
│   │   └── pom.xml
│   │
│   ├── workflow-service/    (same hexagonal structure)
│   ├── schema-service/      (same hexagonal structure)
│   ├── media-service/       (same hexagonal structure)
│   ├── iam-service/         (same hexagonal structure)
│   ├── search-service/      (same hexagonal structure)
│   ├── notification-service/(same hexagonal structure)
│   └── audit-service/       (same hexagonal structure)
│
├── libs/
│   ├── cms-common/          ← Shared DTOs, events, exceptions
│   ├── cms-security/        ← JWT, permission annotations
│   └── cms-testing/         ← Test utilities, fixtures
│
├── infrastructure/
│   ├── docker/
│   │   └── docker-compose.yml
│   ├── kubernetes/
│   │   ├── base/
│   │   │   ├── content-service/
│   │   │   │   ├── deployment.yaml
│   │   │   │   ├── service.yaml
│   │   │   │   └── hpa.yaml
│   │   │   └── ...
│   │   ├── overlays/
│   │   │   ├── dev/
│   │   │   ├── staging/
│   │   │   └── production/
│   ├── helm/
│   │   └── cms-platform/
│   └── terraform/
│       ├── rds.tf
│       ├── eks.tf
│       └── kafka.tf
│
├── db/
│   ├── README.md           ← Points to db-structure.md as canonical reference
│   └── (Flyway migrations are co-located per service, not in a shared db/ folder)
│       ├── iam-service/src/main/resources/db/migration/  V1__create_tenants.sql ...
│       ├── schema-service/src/main/resources/db/migration/ V1__create_content_types.sql ...
│       ├── content-service/src/main/resources/db/migration/ V1__create_content_items.sql ...
│       └── ... (one independent Flyway history per database — see db-structure.md § 15)
│
└── docs/
    ├── architecture/
    ├── api/
    │   └── openapi.yaml
    └── runbooks/
```

### 15.2 Frontend (Next.js) Structure

```
cms-frontend/
├── apps/
│   ├── admin/                     ← CMS Admin App
│   │   ├── src/
│   │   │   ├── app/               ← Next.js 14 App Router
│   │   │   │   ├── (auth)/
│   │   │   │   ├── (dashboard)/
│   │   │   │   │   ├── content/
│   │   │   │   │   │   ├── [type]/
│   │   │   │   │   │   │   ├── page.tsx          ← Content list (dynamic)
│   │   │   │   │   │   │   └── [id]/
│   │   │   │   │   │   │       ├── page.tsx      ← Content edit (dynamic)
│   │   │   │   │   │   │       └── versions/
│   │   │   │   │   ├── schema/                   ← Content type manager
│   │   │   │   │   ├── workflow/                 ← Workflow designer
│   │   │   │   │   ├── media/                    ← Media library
│   │   │   │   │   └── settings/
│   │   │   ├── components/
│   │   │   │   ├── form-engine/
│   │   │   │   │   ├── DynamicForm.tsx           ← Renders any schema
│   │   │   │   │   ├── fields/
│   │   │   │   │   │   ├── TextField.tsx
│   │   │   │   │   │   ├── FileUploadField.tsx
│   │   │   │   │   │   ├── RichTextField.tsx
│   │   │   │   │   │   ├── DateField.tsx
│   │   │   │   │   │   └── RelationField.tsx
│   │   │   │   │   └── FieldRegistry.ts          ← Maps type → component
│   │   │   │   ├── workflow/
│   │   │   │   │   ├── WorkflowStatus.tsx
│   │   │   │   │   ├── WorkflowActions.tsx
│   │   │   │   │   └── WorkflowHistory.tsx
│   │   │   │   └── ui/                           ← shadcn/ui components
│   │   │   ├── store/
│   │   │   │   ├── contentSlice.ts
│   │   │   │   ├── schemaSlice.ts
│   │   │   │   └── workflowSlice.ts
│   │   │   └── lib/
│   │   │       ├── api/
│   │   │       │   ├── contentClient.ts
│   │   │       │   └── schemaClient.ts
│   │   │       └── hooks/
│   │   │           ├── useContentList.ts
│   │   │           └── useSchema.ts
│   │   └── package.json
│   │
│   └── public/                    ← Public-facing site
│       └── ...
│
└── packages/
    ├── ui/                        ← Shared design system
    ├── api-client/                ← Typed API client (generated from OpenAPI)
    └── types/                     ← Shared TypeScript types
```

---

## PART 16: API STANDARDS & CONVENTIONS

### 16.1 REST API Design

```
Base URL: /api/v1

Content API (generic, driven by content type):
GET    /api/v1/content/{type}                    → List content
POST   /api/v1/content/{type}                    → Create
GET    /api/v1/content/{type}/{id}               → Get by ID
PUT    /api/v1/content/{type}/{id}               → Update
DELETE /api/v1/content/{type}/{id}               → Soft delete
POST   /api/v1/content/{type}/{id}/workflow      → Trigger workflow action
GET    /api/v1/content/{type}/{id}/versions      → Version history
GET    /api/v1/content/{type}/{id}/workflow      → Workflow status

Schema API:
GET    /api/v1/schema/types                      → List content types
POST   /api/v1/schema/types                      → Create content type
GET    /api/v1/schema/types/{code}               → Get type + fields
PUT    /api/v1/schema/types/{code}               → Update type
GET    /api/v1/schema/types/{code}/fields        → Get field definitions

Workflow API:
GET    /api/v1/workflows                         → List workflow definitions
POST   /api/v1/workflows                         → Create workflow
GET    /api/v1/workflows/{code}                  → Get definition
PUT    /api/v1/workflows/{code}                  → Update definition
GET    /api/v1/workflow-instances/{id}           → Get instance + history

Media API:
POST   /api/v1/media/upload                      → Upload file
GET    /api/v1/media/{id}                        → Get asset metadata
DELETE /api/v1/media/{id}                        → Delete asset
GET    /api/v1/media?folder=/path&type=image     → Browse media
```

### 16.2 Response Envelope

```json
{
  "success": true,
  "data": { ... },
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 142,
    "totalPages": 8
  },
  "timestamp": "2025-01-01T00:00:00Z",
  "traceId": "abc-123-def-456"
}
```

Error response:
```json
{
  "success": false,
  "error": {
    "code": "CONTENT_NOT_FOUND",
    "message": "Content item with ID 'xyz' not found",
    "details": [],
    "traceId": "abc-123-def-456"
  }
}
```

---

## PART 17: KEY ARCHITECTURE DECISIONS & RATIONALE

| Decision | Rationale | Trade-off |
|----------|-----------|-----------|
| **JSONB body for content fields** | Eliminates per-content-type tables. Schema flexibility. PostgreSQL GIN index makes it fast. | Harder to write raw SQL queries. Mitigated by search service. |
| **Hexagonal Architecture per service** | Domain logic has zero framework dependencies. Business rules are testable without Spring, JPA, or HTTP mocking. | More boilerplate than traditional layered arch. Justified by testability gains. |
| **Outbox Pattern for events** | Guarantees event delivery even when Kafka is down. Eliminates dual-write consistency bugs. | Requires a CDC connector or scheduler. Small operational overhead. |
| **Kafka for async communication** | Services are decoupled. search-service can fail without blocking content-service. Replay capability. | Operational complexity. Mitigated by managed MSK or Confluent Cloud. |
| **Workflow as JSONB config** | New approval processes require zero code. Configurable from admin UI. | More complex execution engine. Justified by eliminating repeated workflow code across 28 modules. |
| **PostgreSQL RLS for tenant isolation** | Tenant data isolation enforced at DB level, not application code. Impossible to accidentally leak across tenants. | Requires setting session variables before every query. Applied independently in each of the 7 PostgreSQL producer databases. |
| **UUID primary keys** | Safe for distributed generation. No sequence coordination across services. Opaque to clients (no enumeration attacks). | Slightly larger than BIGINT. UUIDv7 (ordered) mitigates index fragmentation. |
| **Soft deletes via deleted_at** | Audit trail preservation. Recoverable. | More complex queries (always filter deleted_at IS NULL). Mitigated by views. |
| **Database-per-service (all PostgreSQL)** | Full service autonomy, independent scaling, no shared schema locks. Evaluated and confirmed in `db-technology-selection.md`. | 9x backup/monitoring surface. Mitigated by CloudNativePG operator (uniform tooling) and centralised Grafana dashboards with per-datasource selectors. |
| **Per-service local outbox tables** | Each service's CDC is independent — a Debezium failure on one database does not block others. | 7 Debezium connector instances to configure and monitor. Mitigated by Kafka Connect cluster with shared connector framework. |
| **sites/menus/menu_items in schemadb** | `website_location` free-text replaced with typed site identity. Menu structure is navigable via SQL within schemadb. | menu_item.content_item_id is a logical ref (no FK to contentdb). Validated via sync API at write time. |

---

## SUMMARY: From 28 Modules → 9 Services + Configuration

**Before:** Adding "Grievance Portal" content module required:  
— Copy 7 Java files from an existing module  
— Write 2 new ENUMs + 2 ENUMConvertors  
— Write DB migration  
— Add if-else blocks in workflow controllers  
— Deploy (downtime risk)

**After:** Adding "Grievance Portal" requires:  
— Admin → Content Types → Create New  
— Add fields, select workflow  
— Save  
— Available immediately, no deployment, no code change

This is the Open/Closed Principle applied at the system architecture level:  
**The system is open for extension through configuration, closed for modification through code.**

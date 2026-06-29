# Database Technology Selection Analysis
## NextGen Enterprise CMS — PostgreSQL vs. MongoDB per Service

> **Document Type:** Technology Analysis — Decision Support  
> **Status:** Analysis only — no files modified. Implementation follows a separate task after review.  
> **Baseline:** All 8 producer services currently use PostgreSQL 16 (per `db-structure.md`). Search-service uses OpenSearch. This document re-examines the PostgreSQL baseline service by service.  
> **See also:** `db-structure.md` (canonical schema design), `cms-architecture.md` (service boundaries)

---

## Section 1: Summary Table

| Service | Baseline | Recommendation | Confidence | One-line reason |
|---------|----------|---------------|------------|-----------------|
| iam-service | PostgreSQL | **Stay PostgreSQL** | High | RBAC junction tables, uniqueness constraints, and real FK chains are exactly what SQL is built for — there is no meaningful MongoDB advantage here |
| schema-service | PostgreSQL | **Stay PostgreSQL** | High | Rich internal relations (types → fields → categories → sites → menus → menu_items) with real FKs, low write volume, read-heavy with GIN indexes doing real work |
| content-service | PostgreSQL | **Stay PostgreSQL** | High | JSONB body column already delivers MongoDB's schema flexibility while keeping ACID, optimistic locking, GIN indexes, and versioning with a real FK chain |
| workflow-service | PostgreSQL | **Stay PostgreSQL** | High | Strong internal relations (definitions → instances → history with real FKs), multi-row transactional guards, and versioned state machine definitions all favor relational |
| media-service | PostgreSQL | **Stay PostgreSQL** | High | Fundamentally tabular metadata with a checksum-based deduplication unique constraint — "has a tags array" is not a sufficient reason to leave PostgreSQL |
| form-service | PostgreSQL | **Stay PostgreSQL — with a caveat** | Medium | Submissions are the strongest MongoDB candidate in the set, but the ACID outbox pattern, RLS replacement cost, and the FK between submissions and definitions make the overall case unconvincing; PostgreSQL JSONB + partitioning handles the workload |
| search-service | OpenSearch | **Confirmed OpenSearch** | High | Purpose-built for faceted full-text search with cross-service denormalized documents; neither SQL nor MongoDB is the right tool here |
| notification-service | PostgreSQL | **Stay PostgreSQL** | High | Templates are structured/relational, log is append-mostly time-range queried — both are a natural fit; notification volume is not large enough to justify a second engine |
| audit-service | PostgreSQL (partitioned) | **Stay PostgreSQL, flag ClickHouse/Timescale at scale** | Medium | Monthly RANGE partitioning handles the near-term load; if audit volume grows beyond ~10M rows/month, a columnar store beats both PostgreSQL and MongoDB — but that is a future trigger, not a current requirement |

**Overall conclusion:** All 8 PostgreSQL services should remain on PostgreSQL. MongoDB does not win on any dimension for any individual service when weighed honestly against the cost of running a second database engine and re-implementing RLS-based tenant isolation at the application layer.

---

## Section 2: Per-Service Deep Dives

---

### 2.1 iam-service — `iamdb`

#### Data Shape

Highly relational and fixed-schema. Tables: `tenants`, `users`, `roles`, `permissions`, `role_permissions` (junction), `user_roles` (junction), `api_keys`, `refresh_tokens`. The core of the service is a many-to-many RBAC graph: users have roles, roles have permissions, permissions are scoped to (resource, action, scope) triples.

#### Decision Framework Scoring

| Dimension | Assessment |
|-----------|-----------|
| **Data shape** | Fully tabular. No nested or variable-structure fields. `tenants.settings` is JSONB for feature flags — a rare exception, not the norm. |
| **Schema volatility** | Completely fixed at design time. The permission vocabulary (resource × action × scope) expands slowly and predictably through seeded migrations, not at runtime per tenant. |
| **Internal relations** | Very high: `users → tenants (FK)`, `roles → tenants (FK)`, `user_roles (junction, 2 FKs)`, `role_permissions (junction, 2 FKs)`, `api_keys → tenants (FK)`, `refresh_tokens → users (FK)`. This is the definition of a workload relational databases were built for. |
| **ACID needs** | Critical. Granting a role means atomically writing to `user_roles`; revoking one must not leave partial state. Uniqueness constraints (`UNIQUE(tenant_id, username)`, `UNIQUE(resource, action, scope)`) must be enforced by the database, not application logic. |
| **Read/write pattern** | Read-heavy after login. The hot path is JWT validation from cache (Redis), not DB reads. DB reads are: login lookup by email, role/permission load (infrequent, cached). Writes are rare admin operations. |
| **Consistency** | Strong consistency required. A revoked role must not grant access milliseconds later. This rules out eventual consistency. |
| **Query complexity** | Moderate SQL: `SELECT p.resource, p.action, p.scope FROM permissions p JOIN role_permissions rp ON rp.permission_id = p.id JOIN user_roles ur ON ur.role_id = rp.role_id WHERE ur.user_id = :uid`. This is a natural 3-table join — awkward in MongoDB's aggregation pipeline. |
| **Scale/volume** | Very low write volume. A government/enterprise CMS has hundreds to thousands of users, not millions. IAM is not a scale problem. |
| **Operational fit** | PostgreSQL is the right and obvious tool. |

#### Recommendation

**Stay PostgreSQL.** This is the fastest, most confident verdict in the analysis. The RBAC model is exactly what relational databases are designed for: junction tables, uniqueness constraints, referential integrity, transactional role grants. MongoDB would require rebuilding all of this in application code while losing database-level enforcement.

**What would be gained by switching to MongoDB:** Nothing of value. The `tenants.settings` JSONB column is already handled by PostgreSQL JSONB.

**What would be lost:** Uniqueness constraints on `(tenant_id, username)` and `(resource, action, scope)` become application-enforced (race-condition-prone). Junction tables lose referential integrity. Cross-document "joins" for permission resolution become application-side aggregations.

---

### 2.2 schema-service — `schemadb`

#### Data Shape

Relational registry with internal tree structures. Tables: `content_types`, `field_definitions` (child of content_types), `categories` (self-referential tree per content_type), `sites`, `menus` (child of sites), `menu_items` (self-referential tree per menu). The `content_types.metadata` and `field_definitions.validation_rules/ui_config` columns are JSONB for extensible configuration.

#### Decision Framework Scoring

| Dimension | Assessment |
|-----------|-----------|
| **Data shape** | Mostly tabular with structural JSONB in specific config columns. The core entities (type, field, category) have well-known fixed shapes — they are a registry, not arbitrary documents. |
| **Schema volatility** | Low-to-medium. Content types are created by admins, not at arbitrary runtime. Field definitions have a known field_type vocabulary. The JSONB `validation_rules` and `ui_config` columns absorb variability within otherwise stable rows. |
| **Internal relations** | High and meaningful: `field_definitions.content_type_id → content_types` (FK), `categories.content_type_id → content_types` (FK), `categories.parent_id → categories` (self-ref FK for tree), `menus.site_id → sites` (FK), `menu_items.menu_id → menus` (FK), `menu_items.parent_id → menu_items` (self-ref FK for tree). These FKs cascade on delete — a content type deletion cascades through fields and categories. This cascade logic is trivially enforced by SQL and painful to replicate in MongoDB. |
| **ACID needs** | Moderate. Creating a content type with its initial field set should be atomic (one transaction). Deleting a content type must cascade-delete fields and categories atomically. |
| **Read/write pattern** | Very read-heavy. Schema definitions are read by every content write and form render, cached aggressively in Redis (60-min TTL). Writes (schema design changes) are rare and admin-initiated. |
| **Consistency** | Strong consistency required. A schema change must be immediately visible to all services that read it (via cache invalidation + Kafka event). Eventual consistency on schema definitions would cause field validation failures mid-edit. |
| **Query complexity** | Category tree traversal, multi-level menu item resolution. Both use `WITH RECURSIVE` in PostgreSQL — a well-optimized feature. MongoDB's `$graphLookup` for tree traversal is less ergonomic and carries a depth limit. |
| **Scale/volume** | Very low. A typical tenant has 10–50 content types, each with 5–30 fields. This is a registry table, not a high-volume data store. The schema data for an entire organization fits in kilobytes. |
| **Operational fit** | PostgreSQL is appropriate. |

#### Recommendation

**Stay PostgreSQL.** The rich internal FK graph (including two separate self-referential trees for categories and menu_items), cascade-delete requirements, and low write volume with aggressive caching all point strongly to PostgreSQL. MongoDB's document model would require embedding or application-level referencing for the tree structures, and cascade behavior would have to be implemented manually.

**What would be gained by switching to MongoDB:** Slightly more natural storage of the JSONB config columns (though PostgreSQL JSONB already handles these), and native document embedding (e.g., embedding field_definitions inside content_type documents). The embedding benefit is real but minor.

**What would be lost:** Cascade-delete on type deletion (must be re-implemented in application code), uniqueness of `(tenant_id, code)` across the collection (must be application-enforced or rely on MongoDB unique indexes without transactional guarantees), `WITH RECURSIVE` for category/menu trees.

---

### 2.3 content-service — `contentdb`

#### Data Shape

`content_items` rows have 20+ fixed structural columns (tenant_id, site_id, status, slug, publish_at, etc.) plus a `body JSONB` column that stores all dynamic, per-content-type fields. `content_versions` is a child table with immutable snapshots. This is intentionally a hybrid design: fixed structure where the system needs to query/filter (status, site, type), flexible document where the content varies (body).

#### Decision Framework Scoring

| Dimension | Assessment |
|-----------|-----------|
| **Data shape** | Deliberately hybrid. The outer envelope (title, slug, status, timestamps, site, category) is tabular and queried directly. The inner payload (`body`) is a variable JSONB document per content type. This is precisely the use case PostgreSQL JSONB was designed for. |
| **Schema volatility** | The outer schema is fixed; the inner `body` varies per content type at runtime. PostgreSQL JSONB handles this: new field types in body require no migration, and GIN indexes allow querying inside the body. |
| **Internal relations** | Moderate but meaningful: `content_versions.content_item_id → content_items` (real FK, cascade delete). Self-referential `parent_id` for hierarchical content (real FK). The version child relationship is an excellent candidate for a PostgreSQL FK with cascade — in MongoDB this would be a manual reference with no cascade guarantee. |
| **ACID needs** | High. Writing a new content version must atomically: insert to `content_items` (update), insert to `content_versions`, and insert to `outbox_events` (for Debezium CDC). These three operations must commit together or not at all. This is the transactional outbox pattern — it fundamentally requires a relational database with multi-table transaction support. |
| **Read/write pattern** | Read-heavy on published content (cached in Redis). Write-moderate on admin edits. The key admin queries are: list by (type, status, tenant, site) — all indexed column scans; JSONB body queries for advanced filtering. Both are handled by existing PostgreSQL indexes. |
| **Consistency** | Strong consistency required for write path (version increment, slug uniqueness). |
| **Query complexity** | The `body` JSONB GIN index enables queries like `body @> '{"tender_value": 5000000}'` and `(body->>'bid_submission_end')::timestamptz < NOW()`. These would be replaced by MongoDB's native BSON document queries — the query capability is comparable. However, the slug uniqueness partial index (`WHERE deleted_at IS NULL`) and the scheduled publish index (`WHERE status = 'SCHEDULED'`) are PostgreSQL partial indexes with no direct MongoDB equivalent. |
| **Scale/volume** | Medium. A government CMS with 28 modules and multiple content types could accumulate 100k–1M content items over time. This is comfortably within PostgreSQL's range with proper indexing. True horizontal write scaling is not needed at this volume. |
| **Operational fit** | The transactional outbox pattern (Debezium CDC on PostgreSQL WAL) is already designed into the system. Switching to MongoDB would require replacing Debezium with MongoDB Change Streams — a different CDC mechanism with different reliability characteristics and operational tooling. |

#### Key Question: PostgreSQL JSONB vs. MongoDB Document

This is the most important comparison in the entire analysis. The temptation to switch content-service to MongoDB comes from the variable `body` field. Here is the honest comparison:

| Factor | PostgreSQL JSONB | MongoDB Document |
|--------|-----------------|-----------------|
| Schema flexibility | ✅ JSONB body is schema-less | ✅ Native document model |
| GIN index on dynamic fields | ✅ `CREATE INDEX USING GIN(body)` | ✅ Wildcard index (similar capability) |
| Atomic multi-table transaction | ✅ ACID across content_items + content_versions + outbox_events | ⚠️ Single-document atomic; multi-document only with multi-document transactions (added in 4.0, higher overhead) |
| Optimistic locking (version column) | ✅ Native via `version BIGINT` and `WHERE version = :v` | ⚠️ Requires manual `findAndModify` with version check |
| Slug uniqueness (partial index) | ✅ `UNIQUE INDEX WHERE deleted_at IS NULL` | ⚠️ Partial unique indexes exist but are less mature |
| Transactional outbox (CDC) | ✅ Debezium on WAL — battle-tested | ⚠️ MongoDB Change Streams — viable but different operational profile |
| Scheduled publish index | ✅ `INDEX WHERE status = 'SCHEDULED'` | ⚠️ Partial indexes supported but less expressive |
| Hierarchical content (parent_id FK) | ✅ Real FK with ON DELETE CASCADE | ❌ No FK — application must enforce |
| Version history (child table FK) | ✅ Real FK, cascade delete | ❌ No FK — application must enforce |
| RLS tenant isolation | ✅ Native PostgreSQL RLS | ❌ Must re-implement as query-level `tenant_id` filter everywhere |

**Conclusion for content-service:** PostgreSQL JSONB already delivers the core benefit of MongoDB (schema-less content body) while keeping every structural advantage that this service actually needs: multi-table ACID for the outbox pattern, optimistic locking, partial indexes, real FKs for version history, and native RLS. Switching would trade nothing for real costs.

#### Recommendation

**Stay PostgreSQL.** The `body JSONB` column is not a compromise — it is the correct design for this exact use case. The argument "content is document-shaped, use a document database" fails when PostgreSQL JSONB provides the same document storage with additional relational capabilities that this service actively uses.

---

### 2.4 workflow-service — `workflowdb`

#### Data Shape

Three tables with strong internal relations: `workflow_definitions` (JSONB state machine graph config), `workflow_instances` (current state of an in-flight workflow), `workflow_history` (append-only transition log, FK to instances). The `definition` JSONB column contains the full state machine (states, transitions, guards, actions) as a nested document.

#### Decision Framework Scoring

| Dimension | Assessment |
|-----------|-----------|
| **Data shape** | Mixed. `workflow_definitions.definition` is a complex nested JSONB document (state machine graph). `workflow_instances` and `workflow_history` are tabular operational records. |
| **Schema volatility** | `definition` column is intentionally schema-flexible — different workflows have different states/transitions. The instance and history tables are fixed-schema. |
| **Internal relations** | High and critical: `workflow_instances.workflow_def_id → workflow_definitions` (real FK), `workflow_history.instance_id → workflow_instances` (real FK, cascade delete). The execution engine must JOIN definitions to instances to look up allowed transitions — this is a regular, performance-sensitive query. |
| **ACID needs** | High. A workflow transition must atomically: update `workflow_instances.current_state`, insert into `workflow_history`, and insert into `outbox_events`. All three must commit together — this is the core correctness invariant of the state machine. |
| **Read/write pattern** | Read-heavy on definitions (cached in Redis, 30-min TTL). Instances are point-lookup by entity_id (one active instance per content item). History is write-once, read for audit display. |
| **Consistency** | Strong. A concurrent double-submit (two users both clicking "Approve" on the same content) must not create two approved state transitions. The `workflow_instances.current_state` update must be serializable. |
| **Query complexity** | Execution path: load definition (Redis cache hit), find matching transition for current_state + trigger, check guards, update instance, write history. All intra-database queries. No complex aggregations. |
| **Scale/volume** | Low-to-medium. One workflow instance per active content item. A very busy publishing team might create 500–1000 workflow transitions per day — trivial for PostgreSQL. |

#### Should workflow_definitions be split to a document store?

The question was specifically raised in the task. The `definition` JSONB column does hold a complex nested graph document that would feel natural in MongoDB. However:

1. The execution engine reads `workflow_definitions` and then immediately needs `workflow_instances` in the same query scope (to validate current state before allowing a transition). Splitting across two stores creates a synchronous cross-store lookup on every workflow execution.
2. The ACID invariant (update instance + write history + write outbox, all atomic) requires all three tables to be in the same transaction scope. Splitting definitions to a document store doesn't help with this — instances and history must still be relational.
3. The read is heavily cached (Redis). The `definition` document is fetched from Redis on almost every workflow execution; the actual PostgreSQL query for definitions is rare.

Splitting creates two CDC connectors, two connection pools, two backup procedures, and a cross-store join on the hot path, in exchange for a marginally more natural document storage for the `definition` JSONB column. This is not a good trade.

#### Recommendation

**Stay PostgreSQL, keep all three tables together.** The ACID requirement across instances + history + outbox is the deciding factor. The JSONB definition column is well-served by PostgreSQL's JSONB support. Splitting is not worth the added complexity.

---

### 2.5 media-service — `mediadb`

#### Data Shape

`media_assets` is a single-table service. Columns: `id`, `tenant_id`, `original_name`, `stored_name`, `storage_path`, `storage_backend`, `mime_type`, `file_size_bytes`, `checksum`, `alt_text`, `caption`, `width`, `height`, `duration_ms`, `is_public`, `folder_path`, `tags TEXT[]`, `metadata JSONB`, `cdn_url`, `thumbnail_url`, `uploaded_by`, `deleted_at`.

#### Decision Framework Scoring

| Dimension | Assessment |
|-----------|-----------|
| **Data shape** | Mostly tabular. `metadata JSONB` is a catch-all for storage-backend-specific metadata (S3 version ID, CDN purge key, etc.). `tags TEXT[]` is a PostgreSQL array. The core columns (mime_type, file_size_bytes, checksum, dimensions) are all scalar, strongly typed. |
| **Schema volatility** | Low. The table structure is stable. `metadata JSONB` absorbs backend-specific variability without requiring schema changes. |
| **Internal relations** | None. `media_assets` is effectively a standalone record — there are no internal joins within mediadb. |
| **ACID needs** | Moderate. Write path: the service must atomically insert the `media_assets` row and the `outbox_events` row (for Debezium CDC → `cms.media.uploaded`). Same transactional outbox requirement as every other producer service. |
| **Read/write pattern** | Write on upload, read on browse/attach. The deduplication check (`WHERE checksum = :sha256 AND tenant_id = :tid`) is a unique index lookup. Folder browsing uses prefix matching on `folder_path`. Tag filtering uses GIN on `tags[]`. |
| **Consistency** | Strong needed for deduplication: two concurrent uploads of the same file must not create two records. The `UNIQUE(tenant_id, checksum) WHERE deleted_at IS NULL` partial index enforces this — in MongoDB, a unique index on these fields exists but lacks the `WHERE deleted_at IS NULL` partial condition (MongoDB partial indexes are supported but less powerful for this pattern). |
| **Query complexity** | Simple. No joins. Primary access patterns: by id, by tenant+folder prefix, by tenant+tag, dedup check by checksum. |
| **Scale/volume** | Medium-high in byte terms, low-medium in row count. A media library might have 50k–500k assets. Row counts are not a challenge for PostgreSQL. |
| **Operational fit** | Low complexity, single table. No strong argument either way from a feature perspective. |

#### Key Question: Is "has some JSON columns" a reason to use MongoDB?

No. `metadata JSONB` and `tags TEXT[]` are not evidence that this service needs a document database. PostgreSQL has supported these column types natively for years, with GIN indexing. If the entire row were variable and schema-less, the argument would be stronger. Here, 15 of 18 columns are fixed scalar types.

MongoDB would not provide better tag filtering, folder prefix searching, or deduplication uniqueness than PostgreSQL. The transactional outbox requirement (insert + outbox in same transaction) is actually easier to satisfy in PostgreSQL.

#### Recommendation

**Stay PostgreSQL.** This is a tabular service with two array/JSONB convenience columns. MongoDB's document model provides no meaningful advantage here. The checksum deduplication unique constraint and the transactional outbox are both better served by PostgreSQL.

---

### 2.6 form-service — `formdb`

#### Data Shape

Two tables: `form_definitions` (JSON Schema + UI schema for the form structure — two large JSONB columns) and `form_submissions` (`submitted_data JSONB` — entirely arbitrary JSON, no fixed structure). The `submitted_data` column is the most schema-less data in the entire system — it contains whatever fields the form asked for, different for every form.

This is the **strongest MongoDB candidate** in the analysis. The analysis must be rigorous.

#### Decision Framework Scoring

| Dimension | Assessment |
|-----------|-----------|
| **Data shape** | `form_definitions` is two large JSONB documents (JSON Schema + UI Schema). `form_submissions.submitted_data` is fully arbitrary with zero structural constraint at the database level. On raw data shape alone, MongoDB is a natural fit. |
| **Schema volatility** | High for submissions. A "Vendor Registration" form and a "Tender Bid" form produce completely different submitted_data structures. There is no fixed schema for submissions — this is the purest schema-less use case in the system. |
| **Internal relations** | One meaningful relation: `form_submissions.form_id → form_definitions.id` (real FK in current design). Beyond this, no joins within formdb. |
| **ACID needs** | **This is the decisive factor.** Writes to `form_submissions` must also write to `outbox_events` (for `cms.form.submitted` Kafka event → notification-service → audit-service). In PostgreSQL, this is one transaction: `INSERT form_submissions + INSERT outbox_events`. In MongoDB, this requires a multi-document transaction (supported since 4.0, but with higher overhead and different durability semantics). |
| **Read/write pattern** | Mostly insert + read-by-id. Admin listing: `SELECT * FROM form_submissions WHERE form_id = :fid AND tenant_id = :tid ORDER BY submitted_at DESC`. Status-based filtering: `WHERE status = 'RECEIVED'`. No complex aggregations inside the submitted_data document. |
| **Consistency** | Strong consistency required for submission receipt. A submitted form that fails to write the outbox event must roll back the submission itself (otherwise the audit trail is missing). |
| **Query complexity** | Simple. No queries reach inside `submitted_data` at query time — the Admin UI displays the raw JSON or a rendered view, it does not filter by field values within the submission. This removes one of the key MongoDB advantages (querying nested fields). |
| **Scale/volume** | Low-to-medium. A typical CMS receives hundreds to low-thousands of form submissions per day. This is not a high-volume write use case in this domain. |
| **Operational fit** | Introducing MongoDB for one service adds: a new database engine to back up, monitor, patch, and train staff on; a Debezium MongoDB connector (different from the PostgreSQL connector, using MongoDB Change Streams instead of WAL logical replication); replacement of PostgreSQL RLS with application-layer `tenant_id` query filtering everywhere in this service. |

#### MongoDB vs. PostgreSQL for form_submissions

| Factor | PostgreSQL | MongoDB |
|--------|-----------|---------|
| `submitted_data` flexibility | ✅ JSONB — arbitrary JSON | ✅ Native BSON document |
| Querying inside submitted_data | ✅ GIN index (if needed) | ✅ Wildcard index |
| Transactional outbox (submission + outbox atomic) | ✅ Single transaction, same DB | ⚠️ Requires multi-document transaction, higher overhead |
| FK: submissions → definitions | ✅ Real FK, enforced | ❌ Reference field, application-enforced |
| Tenant isolation | ✅ PostgreSQL RLS | ❌ Must add `tenant_id` filter to every query in application code |
| CDC mechanism | ✅ Debezium + PostgreSQL WAL (already configured) | ⚠️ Debezium + MongoDB Change Streams (additional connector, different behavior) |
| Submission listing by form+tenant+status | ✅ Composite index — fast | ✅ Compound index — fast |
| Admin pagination (cursor-based) | ✅ Keyset pagination on submitted_at | ✅ Same |

#### Where MongoDB would genuinely win

MongoDB's native BSON document model means `submitted_data` can store nested arrays and sub-objects without the JSONB type overhead. If the team wanted to query across submissions by field values inside the document (e.g., "find all vendor registrations where company_size > 500"), MongoDB's wildcard indexes are more natural. However, **this use case does not exist in the current requirements** — the service retrieves submissions by form_id and status, then displays the full JSON to admins. The inner fields of `submitted_data` are never filtered at query time.

#### Verdict

The MongoDB advantage for `submitted_data` is real but marginal in this specific context: the data is schema-less, but the queries that actually run don't reach inside the document. The cost of switching is concrete: a second database engine, a different CDC connector, loss of RLS, loss of the FK from submissions to definitions, and multi-document transactions for the outbox pattern. The PostgreSQL JSONB column already handles the schema-less requirement. The SQL that actually runs (filter by form_id, tenant_id, status, submitted_at) is simple relational querying.

#### Recommendation

**Stay PostgreSQL, with a clear trigger for reassessment.** If submission volume grows to >1M rows/month and the team needs to query inside `submitted_data` at scale, revisit with MongoDB or a time-series-friendly partitioned PostgreSQL setup. At current projected scale, PostgreSQL JSONB with partitioning on `submitted_at` handles the workload with less operational cost than introducing a second engine.

---

### 2.7 search-service — `searchstore` (OpenSearch)

#### Data Shape

Denormalized search documents — fully self-contained records containing title, body_text, tags, category, site, status, published_at, and a selection of filterable JSONB fields from content items. One index per tenant, one document per content item. Documents are never updated via SQL; they are populated by consuming Kafka events from other services.

#### Why OpenSearch and Not SQL or MongoDB

**Not SQL (PostgreSQL FTS):** PostgreSQL full-text search with `tsvector` and GIN indexes works well for single-service, moderate-scale FTS. For this system it fails on two counts:

1. The search document must aggregate fields from multiple services (content-service for the item, schema-service for content type and category names, iam-service for author email). With database-per-service, there is no SQL JOIN available. OpenSearch's denormalized document model absorbs this naturally — the document is materialized once from Kafka events.
2. PostgreSQL FTS lacks faceted aggregation (`terms` aggregation by content type, category, site) as a first-class, performant primitive. OpenSearch's aggregations framework is purpose-built for this.

**Not MongoDB:** MongoDB has decent text search capabilities (`$text` index), but:
- It lacks the relevance scoring sophistication of OpenSearch's Lucene-based BM25 ranking.
- Its aggregation pipeline for faceted search is significantly more complex and less performant than OpenSearch's `aggs`.
- It does not support field boosting (title ranked higher than body_text) natively in a clean way.
- OpenSearch is already in the design, already the confirmed decision, and its operational profile is accepted as the one non-SQL engine in the system.
- MongoDB is a general-purpose document store; OpenSearch is a purpose-built search engine. For search, use a search engine.

#### Confirmation

**OpenSearch is the correct decision for search-service.** It is not "just JSON storage" (which MongoDB or Postgres could serve), and it is not a general-purpose data store. It is a purpose-built relevance search engine with faceted aggregations, field boosting, BM25 scoring, geo-search, and percolation — features that would require months of custom code to reproduce in either SQL or MongoDB.

---

### 2.8 notification-service — `notificationdb`

#### Data Shape

Two tables: `notification_templates` (structured: code, name, channel, subject, body_template — a fixed-schema registry) and `notification_log` (structured: recipient, template_code, status, sent_at, error_message, retry_count — append-mostly operational log). The `payload` JSONB column in the log captures the template rendering data for auditability.

#### Decision Framework Scoring

| Dimension | Assessment |
|-----------|-----------|
| **Data shape** | Mostly tabular. `payload JSONB` in the log captures arbitrary template vars — a single convenience column, not the defining characteristic of the records. |
| **Schema volatility** | Low. Templates have a fixed structure (code, channel, subject, body). The log is fixed-schema. |
| **Internal relations** | None meaningful. Templates and log are independent; the log references `template_code` as a plain VARCHAR (no FK needed since templates rarely change and are looked up by code). |
| **ACID needs** | Low. Notification dispatch is inherently async. The service is a Kafka consumer; failures are handled by Kafka retry, not database transactions. |
| **Read/write pattern** | Templates: read-heavy, cached in service memory. Log: write-heavy (one row per notification sent), read by admin for delivery reports. |
| **Consistency** | Eventual consistency is acceptable for the notification log — it is an operational record, not a transactional one. |
| **Query complexity** | Simple. Log queries: by tenant, by status (FAILED, for retry dashboards), by created_at range (reporting). No joins. |
| **Scale/volume** | Medium. A busy CMS with active workflows might send 100–1000 notifications/day. Nothing approaching a scale challenge. |
| **Operational fit** | Low complexity. The optional outbox table (if notification-service emits its own `notification.sent` events) requires the same Debezium + PostgreSQL WAL setup already configured for other services. |

#### Recommendation

**Stay PostgreSQL.** The data is structured, the scale is moderate, and there is no meaningful MongoDB advantage. The `payload JSONB` column does not constitute a document-store use case. If the notification log grows very large (years of history), partition by `created_at` using PostgreSQL RANGE partitioning — the same pattern used for auditdb.

---

### 2.9 audit-service — `auditdb`

#### Data Shape

Single table: `audit_events` — append-only, partitioned by month, high write volume. Columns are denormalized (actor_email stored alongside actor_id so old events survive user deletion). Read pattern: time-range queries with filters by entity_type, entity_id, actor_id, tenant_id. No updates, no deletes, no joins.

This is a **time-series-adjacent workload**, and the analysis must address more than the Postgres vs. MongoDB binary.

#### Decision Framework Scoring

| Dimension | Assessment |
|-----------|-----------|
| **Data shape** | Flat tabular event records. Every row has the same structure. No nesting, no variable schema. |
| **Schema volatility** | Zero. Audit events have a fully fixed schema — any schema change would require a migration. |
| **Internal relations** | Zero. Audit events are self-contained denormalized records. No joins ever. |
| **ACID needs** | Low. Writes are individual event inserts. No multi-row atomicity needed. |
| **Read/write pattern** | Write-heavy (every significant event in every service produces an audit row via Kafka). Reads are admin queries: "show me all events for content item X in the last 30 days." Time-range + filter is the dominant pattern. |
| **Consistency** | Eventual consistency is acceptable. Audit events are received from Kafka — they arrive slightly after the originating operation anyway. A few seconds of lag between event and audit record is fine. |
| **Query complexity** | Simple range scans with filters. No aggregations needed beyond count. |
| **Scale/volume** | **This is the distinguishing dimension.** A system with 9 active services and active content publishing might generate 100k–1M audit events per month at scale. Monthly partitioning handles this to ~100M rows before PostgreSQL performance degrades. Beyond ~10M rows/month with complex filter queries, columnar stores excel. |

#### Three-Way Comparison: PostgreSQL vs. MongoDB vs. ClickHouse/Timescale

| Factor | PostgreSQL (partitioned) | MongoDB | ClickHouse / Timescale |
|--------|--------------------------|---------|----------------------|
| Append-only insert throughput | ✅ Good, with partitioning | ✅ Good | ✅✅ Excellent — columnar writes are extremely fast |
| Time-range range query | ✅ Good with partition pruning | ✅ Adequate | ✅✅ Purpose-built — columnar stores were designed for this |
| Filter by entity_type + entity_id + time | ✅ Good with composite indexes | ✅ Similar | ✅✅ Column pruning makes these very fast |
| Schema fixity | ✅ Enforced | ⚠️ Flexible — a disadvantage here, not an advantage | ✅ Enforced |
| No updates needed | ✅ | ✅ | ✅ — columnar stores are optimized for immutable data |
| Tenant isolation | ✅ RLS | ❌ Application-layer only | ⚠️ Application-layer only (ClickHouse has row policies, limited) |
| Operational complexity | ✅ Same PG tooling | ❌ New engine | ❌ New engine — steeper than MongoDB |
| Team familiarity | ✅ Assumed (already using PG) | ❌ New to team | ❌ Likely new to team |
| Current scale fits | ✅ Yes — partitioning handles to ~100M rows | ✅ Yes | ✅ Yes — and scales much further |
| Future scale ceiling | ⚠️ Degrades past ~100M rows/month without columnar | ⚠️ Similar — MongoDB is a document store, not a columnar store | ✅✅ Handles billions of rows efficiently |

**MongoDB is the wrong answer for audit.** An audit log is not a document store problem — it is a time-series / analytics problem. MongoDB's document model provides zero advantage over PostgreSQL for flat, fixed-schema, append-only event records. MongoDB does not have a columnar storage format for analytical queries, and its time-series collections (added in 5.0) are less mature than dedicated time-series stores.

**The honest third option is ClickHouse or TimescaleDB:**
- **ClickHouse** is purpose-built for this: columnar storage, append-mostly, excellent at time-range + filter analytics, compression ratio 5–10x better than row-oriented stores. Trade-off: steeper learning curve, no native RLS.
- **TimescaleDB** is a PostgreSQL extension (same tooling, same RLS, same Flyway migrations) that adds hypertables with automatic partitioning and columnar compression. It's the lowest-friction path to time-series optimization while staying in the PostgreSQL ecosystem.

#### Recommendation

**Stay PostgreSQL with monthly RANGE partitioning for now.** At the projected scale of this CMS (government/enterprise, thousands of users, hundreds of daily operations per service), PostgreSQL partitioned by month will comfortably handle the load for 2–3 years without performance issues.

**Explicit future trigger:** If audit event volume exceeds **5M rows/month** OR if time-range queries begin to show P95 > 500ms despite partition pruning, **migrate auditdb to TimescaleDB first** (lowest friction — same PostgreSQL wire protocol, same tooling, same Flyway, automatic hypertable partitioning). Escalate to ClickHouse only if TimescaleDB proves insufficient for the analytical query load.

MongoDB is **not** recommended for the audit service at any scale — it is the wrong tool category for this workload.

---

## Section 3: Cross-Cutting Consequences of Any Switch to MongoDB

This section documents what would change system-wide if any service were to switch engines. Although the recommendation is that no service switches, this is documented for completeness.

### 3.1 Outbox Pattern Change

The current system uses **Debezium PostgreSQL connector** reading from `pg_logical` WAL replication slots. Every producer service has its own Debezium connector pointing at its PostgreSQL instance.

If a service moved to MongoDB:
- The **MongoDB Debezium connector** reads from MongoDB **Change Streams** (not WAL). Change Streams require MongoDB replica set (not standalone) or sharded cluster — minimum 3 nodes for production. This is a higher infrastructure floor than a single-node PostgreSQL instance.
- MongoDB Change Streams operate at collection level. The outbox collection would need to be monitored per-service.
- The polling model and delivery guarantees are similar to PostgreSQL CDC, but operational procedures (connector restart, offset management, schema evolution) differ and require separate runbooks.
- **This would add a second set of Debezium connector types** to the operational stack, requiring the team to understand both `io.debezium.connector.postgresql.PostgresConnector` and `io.debezium.connector.mongodb.MongoDbConnector`.

**Bottom line:** For any single service that switches to MongoDB, the team adds a MongoDB replica set (3 nodes), a MongoDB Debezium connector, and separate operational runbooks. This is not trivial.

### 3.2 RLS Tenant Isolation Replacement

PostgreSQL Row-Level Security is used in every current service to enforce tenant isolation at the database layer. Each service's application role runs under a session variable (`app.current_tenant_id`) that the RLS policy enforces:

```sql
CREATE POLICY tenant_isolation ON content.content_items
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);
```

**MongoDB has no equivalent native RLS.** Tenant isolation in MongoDB is implemented by:
1. Adding `tenant_id` as a required filter in every query (application-enforced, bug-prone)
2. Separate database per tenant (extreme — defeats database-per-service with multiple tenants)
3. Atlas MongoDB's Data Federation with tenant-aware collection access rules (vendor lock-in)

Option 1 (application-layer filtering) is the realistic path, and it means:
- Every repository method must include `{ tenant_id: currentTenant }` in every query
- A missed filter in one query → silent cross-tenant data leak (a security incident, not an error)
- Code review and testing must enforce this instead of the database

**This is a real security cost that must be explicitly accepted**, not just noted as "a tradeoff." RLS violation is caught by the database automatically; application-layer filter omission is caught only by exhaustive testing or in production.

### 3.3 Consistency with db-structure.md

The database-per-service boundary established in `db-structure.md` remains valid regardless of which engine each database uses. MongoDB does not break the boundary — it is still "one database per service." The cross-database reference rule (no FKs across service boundaries, logical refs only) still applies and is even more relevant for MongoDB since MongoDB doesn't have FKs at all.

However, `db-structure.md` currently documents all producer services as PostgreSQL, with the DDL written in SQL. If any service were to switch to MongoDB, that service's section in `db-structure.md` would need to be rewritten to describe collection schemas (BSON document shapes) rather than DDL. This is a documentation cost on top of the implementation cost.

### 3.4 Added Operational Burden

The current "all-PostgreSQL" baseline means:
- One toolchain for all databases: `psql`, `pg_dump`, `pgBackRest`, `postgres_exporter`, `CloudNativePG` operator, Flyway
- One type of Debezium connector
- One RLS mechanism
- One migration format (SQL)

Each MongoDB service adds:
- `mongosh`, `mongodump`, MongoDB Atlas Backup or equivalent
- `mongodb_exporter` (separate Prometheus exporter)
- MongoDB Debezium connector (separate Java connector JAR, separate config)
- No Flyway (must use Mongock or a custom migration framework)
- Application-layer tenant filtering (no Flyway seed scripts for RLS policies)

For a team of 4 developers with no stated MongoDB expertise, introducing MongoDB for **one service** (form-service submissions, the only close call in this analysis) would add more operational complexity than the marginal benefit of native BSON storage for `submitted_data` warrants.

---

## Section 4: Final Recommendation

### Target Database Technology — All 9 Services

| # | Service | Database | Engine | Confidence | Notes |
|---|---------|----------|--------|------------|-------|
| 1 | iam-service | iamdb | **PostgreSQL 16** | High | RBAC relations, uniqueness constraints — PostgreSQL's natural domain |
| 2 | schema-service | schemadb | **PostgreSQL 16** | High | Rich FK graph, cascades, recursive category/menu trees |
| 3 | content-service | contentdb | **PostgreSQL 16** | High | JSONB body + ACID outbox + optimistic locking + version FK |
| 4 | workflow-service | workflowdb | **PostgreSQL 16** | High | Multi-table ACID (update instance + write history + write outbox) |
| 5 | media-service | mediadb | **PostgreSQL 16** | High | Tabular metadata, deduplication unique constraint, transactional outbox |
| 6 | form-service | formdb | **PostgreSQL 16** | Medium | JSONB submissions; MongoDB is closer here but the outbox + RLS cost tips against |
| 7 | search-service | searchstore | **OpenSearch 2.x** | High | Purpose-built relevance search, faceted aggregations — not a general document store |
| 8 | notification-service | notificationdb | **PostgreSQL 16** | High | Structured templates, flat log — no document-store advantage |
| 9 | audit-service | auditdb | **PostgreSQL 16** (partitioned by month) | Medium | Handles near-term volume; TimescaleDB is the escalation path, not MongoDB |

### Is Introducing MongoDB Org-Wide Worth It?

**No.** Here is the honest count:

- Services where MongoDB is clearly better: **0**
- Services where MongoDB is a marginal candidate worth considering: **1** (form-service submissions), and even there, PostgreSQL JSONB handles the actual workload
- Services where MongoDB would be actively harmful: **3** (iam-service, schema-service — rich FK relations; audit-service — wrong tool category)

Against:
- Operational cost of a second DB engine: applies to every service that switches (replica set requirement, new connector type, new monitoring, new backup tooling, new migration framework)
- Security cost of losing PostgreSQL RLS: must be replaced with application-layer filtering in every service that switches — a non-trivial audit and testing burden
- CDC cost: second Debezium connector type (MongoDB Change Streams) for every switched service

**The correct overall call is all-PostgreSQL (plus the already-decided OpenSearch for search).** PostgreSQL's JSONB, GIN indexes, partial indexes, RANGE partitioning, and native RLS provide the schema flexibility needed for the variable-data services (content, form, workflow) while preserving the relational guarantees, transactional outbox pattern, and operational uniformity that the system's architecture depends on.

### If MongoDB Is Revisited in the Future

The only rational reassessment trigger is if **all three of these conditions become true simultaneously**:

1. `form_submissions` grows to >5M rows/month AND
2. The team needs to query inside `submitted_data` by field values at scale AND
3. The team has dedicated MongoDB operational expertise (DBA + tooling)

If all three are true, migrating `formdb.form_submissions` (specifically, not all of formdb) to a MongoDB collection is a defensible decision. Until then, PostgreSQL JSONB is the correct answer.

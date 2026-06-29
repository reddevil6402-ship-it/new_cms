# Phase 2 Implementation Plan
## NextGen Enterprise CMS

> **Status:** PLANNING
> **Scope:** media-service, search-service, notification-service, audit-service
> **Stack:** Java 21 · Spring Boot 3.3.x · Maven · PostgreSQL 16 · OpenSearch 2.x
> **Last updated:** 2026-06-29

---

## 1. Repositories

Four separate GitHub repositories to be built in this phase. Each is an independent deployable unit.

| Repo | Type | Description |
|------|------|-------------|
| `cms-media-service` | Spring Boot app | Local file uploads, asset metadata management, serving images/files. |
| `cms-search-service` | Spring Boot app | OpenSearch full-text query engine. Connects to OpenSearch 2.x cluster. |
| `cms-notification-service` | Spring Boot app | Notification dispatch (email/SMS mock), templates, and dispatch logs. |
| `cms-audit-service` | Spring Boot app | Append-only centralized action log. |

---

## 2. Build Order

Dependencies flow top-to-bottom. All these services depend on the previously completed `cms-common`.

```
cms-common          (Already complete from Phase 1)
     │
cms-media-service   (depends on: cms-common)
cms-search-service  (depends on: cms-common, OpenSearch running)
cms-notification-service (depends on: cms-common)
cms-audit-service   (depends on: cms-common)
```

> **Note:** These four services are largely orthogonal and can be built in parallel. `cms-search-service` relies on OpenSearch rather than PostgreSQL.

---

## 3. Local Dev Strategy & Port Allocation

Each repo will have its own `docker-compose.yml` for its required databases.

| Service | App Port | DB Port / Dependency |
|---------|----------|----------------------|
| cms-media-service | 8085 | 5438 (Postgres `mediadb`) |
| cms-search-service | 8088 | 9200 (OpenSearch single-node) |
| cms-notification-service| 8086 | 5439 (Postgres `notificationdb`) |
| cms-audit-service | 8087 | 5440 (Postgres `auditdb`) |

---

## 4. Service-Specific Packages

### 4.1 cms-media-service

```
com/cms/media/
├── CmsMediaApplication.java
├── config/
│   ├── SecurityConfig.java           ← Resource server + static file serving config
│   ├── DataSourceConfig.java         ← TenantAwareDataSource
│   └── WebMvcConfig.java             ← Configures ResourceHandler for /uploads/**
├── controller/
│   └── MediaController.java          ← /api/v1/media/upload, /api/v1/media/{id}
├── service/
│   └── MediaService.java             ← Disk I/O, checksum generation, metadata extraction
├── repository/
│   └── MediaAssetRepository.java
├── domain/
│   └── MediaAsset.java               ← Tenant-scoped, tracks mime_type, file_size
└── dto/
    └── response/
        └── MediaResponse.java

db/migration/
└── V1__create_media_tables.sql
```

### 4.2 cms-search-service

```
com/cms/search/
├── CmsSearchApplication.java
├── config/
│   ├── SecurityConfig.java
│   └── OpenSearchConfig.java         ← OpenSearch Java Client configuration
├── controller/
│   └── SearchController.java         ← /api/v1/search, /api/v1/search/index
├── service/
│   └── SearchService.java            ← Indexing logic, multi-match query builder
└── dto/
    ├── request/
    │   └── IndexRequest.java
    └── response/
        └── SearchResponse.java       ← Paginated hits
```

### 4.3 cms-notification-service

```
com/cms/notification/
├── CmsNotificationApplication.java
├── config/
│   ├── SecurityConfig.java
│   └── DataSourceConfig.java
├── controller/
│   └── NotificationController.java   ← /api/v1/notifications
├── service/
│   └── NotificationService.java      ← Template rendering, dispatch mocking
├── repository/
│   ├── NotificationTemplateRepository.java
│   └── NotificationLogRepository.java
├── domain/
│   ├── NotificationTemplate.java
│   └── NotificationLog.java
└── dto/
    └── request/
        └── NotificationSendRequest.java

db/migration/
└── V1__create_notification_tables.sql
```

### 4.4 cms-audit-service

```
com/cms/audit/
├── CmsAuditApplication.java
├── config/
│   ├── SecurityConfig.java
│   └── DataSourceConfig.java
├── controller/
│   └── AuditController.java          ← /api/v1/audit/logs
├── service/
│   └── AuditService.java             ← Appends logs
├── repository/
│   └── AuditEventRepository.java
├── domain/
│   └── AuditEvent.java               ← Immutable action log
└── dto/
    └── request/
        └── AuditEventRequest.java

db/migration/
└── V1__create_audit_tables.sql
```

---

## 5. Event Pipeline Approach for Dev

In production, updates to search and audit go via Debezium CDC + Kafka.
For local Phase 2 development (to avoid the heavy Zookeeper + Kafka + Debezium stack), we will use **Direct REST fallbacks**:
- When `content-service` publishes a post, it will make an HTTP REST call to `search-service`'s `/index` endpoint.
- When `iam-service` creates a user, it will make an HTTP REST call to `audit-service`'s `/logs` endpoint.

---

## 6. Definition of Done — Phase 2

Phase 2 is complete when all of the following are true:

### cms-media-service
- [ ] User can upload images/files (multipart/form-data).
- [ ] Files are stored in the local `uploads` directory.
- [ ] Media metadata is saved to `mediadb`.
- [ ] Uploaded files are served publicly via standard static web handlers.

### cms-search-service
- [ ] OpenSearch 2.x container runs locally.
- [ ] `/index` endpoint accepts denormalized content items and stores them in OpenSearch.
- [ ] `/search` endpoint performs full-text query matching across title and body fields.

### cms-notification-service
- [ ] Templates can be created/retrieved.
- [ ] Triggering a notification writes an entry to the `notification_log` table (simulating email dispatch).

### cms-audit-service
- [ ] Immutable audit logs can be written by upstream services.
- [ ] Audit logs can be retrieved, filtered by tenant.

# NextGen CMS — Architecture Diagrams

All diagrams use Mermaid syntax. Render at https://mermaid.live or any Mermaid-compatible viewer.

---

## DIAGRAM 1: System Context (C4 Level 1)

```mermaid
C4Context
    title NextGen CMS — System Context

    Person(author, "Content Author", "Creates and manages CMS content")
    Person(reviewer, "Reviewer / Approver", "Reviews and approves content")
    Person(admin, "CMS Administrator", "Manages content types, workflows, users")
    Person(visitor, "Public Visitor", "Reads published content")
    Person(vendor, "Vendor / API Consumer", "Submits tenders, accesses public API")

    System(cms, "NextGen CMS Platform", "Enterprise content management system with dynamic content types, workflow engine, and multi-site delivery")

    System_Ext(email, "Email Service", "SMTP / SendGrid")
    System_Ext(sms, "SMS Gateway", "For notifications")
    System_Ext(cdn, "CDN", "CloudFront / Nginx for static assets")
    System_Ext(s3, "Object Storage", "S3 / GCS for media files")
    System_Ext(idp, "Identity Provider", "OAuth2 / LDAP / SAML")

    Rel(author, cms, "Creates, edits content", "HTTPS")
    Rel(reviewer, cms, "Reviews, approves content", "HTTPS")
    Rel(admin, cms, "Configures types, workflows", "HTTPS")
    Rel(visitor, cms, "Views published pages", "HTTPS")
    Rel(vendor, cms, "Registers, submits tenders", "REST API")

    Rel(cms, email, "Sends notifications")
    Rel(cms, sms, "Sends SMS alerts")
    Rel(cms, cdn, "Serves static assets via")
    Rel(cms, s3, "Stores media in")
    Rel(cms, idp, "Authenticates via")
```

---

## DIAGRAM 2: Microservices Architecture (C4 Level 2)

```mermaid
C4Container
    title NextGen CMS — Container Diagram (Database-per-Service)

    Person(user, "CMS User", "Author, Reviewer, Admin")
    Person(visitor, "Public Visitor")

    Container_Boundary(frontend, "Frontend") {
        Container(admin_app, "Admin Next.js App", "Next.js 14 + TypeScript", "CMS admin interface — dynamic forms, workflow UI, schema designer")
        Container(public_app, "Public Next.js App", "Next.js 14 + TypeScript", "Public-facing website with SSR/ISR content delivery")
    }

    Container_Boundary(gateway, "API Gateway") {
        Container(apigw, "API Gateway", "Kong / Nginx", "JWT validation, rate limiting, routing, SSL termination")
    }

    Container_Boundary(services, "Microservices") {
        Container(content_svc, "Content Service", "Spring Boot 3 / Java 21", "Universal content CRUD, lifecycle, versioning. Replaces all 28 module services.")
        Container(schema_svc, "Schema Service", "Spring Boot 3 / Java 21", "Dynamic content type registry, field definitions, categories, sites, menus")
        Container(workflow_svc, "Workflow Service", "Spring Boot 3 / Java 21", "Data-driven state machine engine for content approval workflows")
        Container(media_svc, "Media Service", "Spring Boot 3 / Java 21", "File upload, storage backend abstraction, thumbnail generation")
        Container(iam_svc, "IAM Service", "Spring Boot 3 / Java 21", "Tenants, users, roles, permissions, JWT issuance, RBAC enforcement")
        Container(form_svc, "Form Service", "Spring Boot 3 / Java 21", "Dynamic form schema management and submission handling")
        Container(search_svc, "Search Service", "Spring Boot 3 / Java 21", "Full-text search, faceted filtering, OpenSearch index management")
        Container(notif_svc, "Notification Service", "Spring Boot 3 / Java 21", "Email, SMS, in-app notification dispatch via templates")
        Container(audit_svc, "Audit Service", "Spring Boot 3 / Java 21", "Append-only immutable event log — pure Kafka consumer")
    }

    Container_Boundary(infra, "Infrastructure (Shared)") {
        Container(kafka, "Apache Kafka", "Message Broker", "Event streaming: content lifecycle, workflow transitions, media events")
        Container(redis, "Redis Cluster", "Cache", "Content cache, session store, schema cache, permission cache, menu cache")
    }

    Container_Boundary(dbs, "Databases (9 Separate Instances)") {
        ContainerDb(iamdb, "iamdb", "PostgreSQL 16", "tenants, users, roles, permissions, api_keys, refresh_tokens, outbox_events")
        ContainerDb(schemadb, "schemadb", "PostgreSQL 16", "content_types, field_definitions, categories, sites, menus, menu_items, outbox_events")
        ContainerDb(contentdb, "contentdb", "PostgreSQL 16", "content_items (JSONB body), content_versions, outbox_events")
        ContainerDb(workflowdb, "workflowdb", "PostgreSQL 16", "workflow_definitions, workflow_instances, workflow_history, outbox_events")
        ContainerDb(mediadb, "mediadb", "PostgreSQL 16", "media_assets, outbox_events")
        ContainerDb(formdb, "formdb", "PostgreSQL 16", "form_definitions, form_submissions, outbox_events")
        ContainerDb(opensearch, "searchstore", "OpenSearch 2.x", "Denormalized search documents — one index per tenant")
        ContainerDb(notifdb, "notificationdb", "PostgreSQL 16", "notification_templates, notification_log, outbox_events")
        ContainerDb(auditdb, "auditdb", "PostgreSQL 16 (partitioned)", "audit_events partitioned by month — append-only, no outbox")
    }

    Rel(user, admin_app, "Uses", "HTTPS")
    Rel(visitor, public_app, "Views", "HTTPS")
    Rel(admin_app, apigw, "API calls", "REST/HTTPS")
    Rel(public_app, apigw, "API calls", "REST/HTTPS")

    Rel(apigw, content_svc, "Routes to", "HTTP")
    Rel(apigw, schema_svc, "Routes to", "HTTP")
    Rel(apigw, workflow_svc, "Routes to", "HTTP")
    Rel(apigw, media_svc, "Routes to", "HTTP")
    Rel(apigw, iam_svc, "Routes to", "HTTP")
    Rel(apigw, search_svc, "Routes to", "HTTP")

    Rel(iam_svc, iamdb, "Reads/Writes", "JDBC")
    Rel(iam_svc, kafka, "Publishes events", "Kafka")

    Rel(schema_svc, schemadb, "Reads/Writes", "JDBC")
    Rel(schema_svc, kafka, "Publishes events", "Kafka")
    Rel(schema_svc, redis, "Caches schema", "Redis Protocol")

    Rel(content_svc, contentdb, "Reads/Writes", "JDBC")
    Rel(content_svc, kafka, "Publishes events", "Kafka")
    Rel(content_svc, redis, "Caches content", "Redis Protocol")
    Rel(content_svc, schema_svc, "Validates against schema", "HTTP/gRPC")
    Rel(content_svc, workflow_svc, "Triggers transitions", "HTTP/gRPC")

    Rel(workflow_svc, workflowdb, "Reads/Writes", "JDBC")
    Rel(workflow_svc, kafka, "Publishes events", "Kafka")

    Rel(media_svc, mediadb, "Reads/Writes", "JDBC")
    Rel(media_svc, kafka, "Publishes events", "Kafka")

    Rel(form_svc, formdb, "Reads/Writes", "JDBC")
    Rel(form_svc, kafka, "Publishes events", "Kafka")

    Rel(search_svc, kafka, "Consumes events", "Kafka")
    Rel(search_svc, opensearch, "Indexes/Queries", "HTTP")

    Rel(notif_svc, notifdb, "Reads/Writes", "JDBC")
    Rel(notif_svc, kafka, "Consumes events", "Kafka")

    Rel(audit_svc, kafka, "Consumes all events", "Kafka")
    Rel(audit_svc, auditdb, "Writes (append-only)", "JDBC")
```

---

## DIAGRAM 3: Content Lifecycle State Machine

```mermaid
stateDiagram-v2
    direction LR

    [*] --> DRAFT : Content Created

    DRAFT --> IN_REVIEW : Submit for Review\n[Author role]\n[Required fields filled]
    DRAFT --> DRAFT : Save as Draft

    IN_REVIEW --> APPROVED : Approve\n[Reviewer role]\n[Not own content]
    IN_REVIEW --> REJECTED : Reject\n[Reviewer role]\n[Comment required]
    IN_REVIEW --> DRAFT : Request Changes\n[Reviewer role]

    REJECTED --> DRAFT : Revise\n[Author role]

    APPROVED --> PUBLISHED : Publish\n[Admin/Publisher role]
    APPROVED --> SCHEDULED : Schedule Publish\n[Set publish_at date]
    APPROVED --> DRAFT : Revoke Approval

    SCHEDULED --> PUBLISHED : Auto-publish at scheduled time\n[System Scheduler]
    SCHEDULED --> DRAFT : Cancel Schedule

    PUBLISHED --> ARCHIVED : Archive\n[Admin role]
    PUBLISHED --> PUBLISHED : Update (creates new version)

    ARCHIVED --> PUBLISHED : Restore\n[Admin role]
    ARCHIVED --> [*] : Hard Delete\n[Super Admin only]

    note right of DRAFT
        Every state transition:
        1. Validated by guards
        2. Recorded in workflow_history
        3. Emits Kafka event
        4. Triggers configured actions
        (notify, cache invalidate, etc.)
    end note
```

---

## DIAGRAM 4: Dynamic Content Type — Data Flow

```mermaid
flowchart TD
    A[Admin: Create Content Type\n'tender_notice'] --> B[schema-service\nPOST /api/v1/schema/types]
    B --> C[(schemadb\ncontent_types\n+ field_definitions\n+ categories)]
    C --> D[Schema cache\ninvalidated in Redis]
    D --> E[Other services notified\nvia Kafka: cms.schema.changed]

    F[Author: Open Admin UI\nContent → Tender Notice → New] --> G[Admin Next.js App\nGET /api/v1/schema/types/tender_notice]
    G --> H{Schema in\nRedis cache?}
    H -- Yes --> I[Return cached schema]
    H -- No --> J[content-service queries\nschema-service]
    J --> I

    I --> K[DynamicForm.tsx\nrenders form from\nfield_definitions JSON]

    K --> L[Author fills in\ntender details\nand submits]
    L --> M[POST /api/v1/content/tender_notice]
    M --> N[content-service\nvalidates body\nagainst field_definitions]
    N --> O{Valid?}
    O -- No --> P[Return 400\nvalidation errors]
    O -- Yes --> Q[Save to\ncontentdb.content.content_items\nbody JSONB column + site_id + site_code]
    Q --> R[Write to contentdb.content.outbox_events\n(same transaction)]
    R --> S[Return 201 Created]

    T[Debezium CDC\ncontentdb connector] --> U[cms.content.created\nKafka event]
    U --> V[audit-service\nlogs the creation]
    U --> W[search-service\nindexes to OpenSearch]
```

---

## DIAGRAM 5: Workflow Engine — Request Flow

```mermaid
sequenceDiagram
    autonumber
    actor Author
    participant AdminUI as Admin Next.js
    participant ContentSvc as Content Service
    participant WorkflowSvc as Workflow Service
    participant DB as PostgreSQL
    participant Kafka as Apache Kafka
    participant NotifSvc as Notification Service

    Author->>AdminUI: Click "Submit for Review"
    AdminUI->>ContentSvc: POST /api/v1/content/tender_notice/{id}/workflow\n{"action": "SUBMIT_FOR_REVIEW"}

    ContentSvc->>WorkflowSvc: triggerTransition(instanceId, "SUBMIT_FOR_REVIEW", userId)

    WorkflowSvc->>DB: Load workflow_definition (cached)
    WorkflowSvc->>WorkflowSvc: Find transition: DRAFT → IN_REVIEW

    WorkflowSvc->>WorkflowSvc: Execute Guards:\n• RoleCheck: user has AUTHOR role ✓\n• FieldNotEmpty: title, body ✓

    WorkflowSvc->>DB: UPDATE workflow_instances SET current_state = 'IN_REVIEW'
    WorkflowSvc->>DB: INSERT INTO workflow_history (from=DRAFT, to=IN_REVIEW)

    WorkflowSvc->>WorkflowSvc: Execute Actions:\n• EMIT_EVENT → outbox\n• AUDIT_LOG → outbox

    ContentSvc->>DB: UPDATE content_items SET status = 'IN_REVIEW'
    ContentSvc-->>AdminUI: 200 OK {"newState": "IN_REVIEW"}
    AdminUI-->>Author: Show "Submitted for Review" toast

    Note over DB,Kafka: Outbox Processor (async)
    DB->>Kafka: cms.workflow.transitioned event

    Kafka->>NotifSvc: Consume event
    NotifSvc->>NotifSvc: Load template "review_requested"
    NotifSvc->>NotifSvc: Send email to all REVIEWER role users
```

---

## DIAGRAM 6: Caching Architecture

```mermaid
flowchart LR
    Client[Next.js Public App\nSSR Request] --> CDN[CDN\nNginx / CloudFront]
    CDN --> |Cache Miss| AGW[API Gateway]
    CDN --> |Cache Hit| Client

    AGW --> CS[Content Service]

    CS --> RC{Redis\nCache?}
    RC --> |Hit| CS
    RC --> |Miss| DB[(contentdb\nPostgreSQL 16)]
    DB --> CS
    CS --> |Write-through| RC

    subgraph RedisKeys["Redis Cache Regions (TTL)"]
        K1["content:{id}\n10 min"]
        K2["content:list:{type}:{siteCode}:{page}\n5 min"]
        K3["schema:{typeCode}\n60 min"]
        K4["menu:{siteCode}:{location}\n15 min"]
        K5["user:{id}:permissions\n5 min"]
        K6["workflow:{code}\n30 min"]
    end

    subgraph Invalidation["Event-Driven Cache Invalidation"]
        EV[cms.content.published event] --> CI[Cache Invalidator]
        CI --> K1
        CI --> K2
        CI --> K4
    end

    RedisKeys --> RC
```

---

## DIAGRAM 7: PostgreSQL Schema Relationships (ERD)

```mermaid
erDiagram
    TENANTS {
        uuid id PK
        varchar code UK
        varchar name
        varchar domain
        varchar plan
        varchar status
        jsonb settings
    }

    USERS {
        uuid id PK
        uuid tenant_id FK
        varchar username UK
        varchar email UK
        varchar status
        bigint version
    }

    ROLES {
        uuid id PK
        uuid tenant_id FK
        varchar code UK
        varchar name
    }

    PERMISSIONS {
        uuid id PK
        varchar resource
        varchar action
        varchar scope
    }

    SITES {
        uuid id PK
        uuid tenant_id
        varchar code UK
        varchar name
        varchar domain
        varchar locale
    }

    MENUS {
        uuid id PK
        uuid site_id FK
        varchar code
        varchar location
    }

    MENU_ITEMS {
        uuid id PK
        uuid menu_id FK
        uuid parent_id FK
        varchar label
        varchar link_type
        uuid content_item_id
        int display_order
    }

    CONTENT_TYPES {
        uuid id PK
        uuid tenant_id
        varchar code UK
        varchar display_name
        boolean is_hierarchical
        boolean has_workflow
        uuid workflow_id
        jsonb metadata
    }

    FIELD_DEFINITIONS {
        uuid id PK
        uuid content_type_id FK
        varchar field_key
        varchar field_type
        boolean is_required
        boolean is_searchable
        jsonb validation_rules
        jsonb ui_config
        int display_order
    }

    CATEGORIES {
        uuid id PK
        uuid content_type_id FK
        varchar code
        varchar display_name
        uuid parent_id FK
        int display_order
    }

    CONTENT_ITEMS {
        uuid id PK
        uuid tenant_id
        uuid content_type_id
        varchar content_type_code
        varchar title
        jsonb body
        varchar status
        uuid category_id
        varchar category_code
        uuid parent_id FK
        text folder_path
        uuid site_id
        varchar site_code
        timestamptz publish_at
        timestamptz expire_at
        uuid workflow_instance_id
        uuid created_by
        bigint version
    }

    CONTENT_VERSIONS {
        uuid id PK
        uuid content_item_id FK
        int version_number
        varchar title
        jsonb body
        varchar status
        timestamptz created_at
    }

    WORKFLOW_DEFINITIONS {
        uuid id PK
        uuid tenant_id
        varchar code
        jsonb definition
        int version
        boolean is_active
    }

    WORKFLOW_INSTANCES {
        uuid id PK
        uuid workflow_def_id FK
        varchar entity_type
        uuid entity_id
        varchar current_state
        timestamptz started_at
    }

    WORKFLOW_HISTORY {
        uuid id PK
        uuid instance_id FK
        varchar from_state
        varchar to_state
        varchar trigger
        uuid actor_id
        varchar actor_email
        text comment
        timestamptz occurred_at
    }

    MEDIA_ASSETS {
        uuid id PK
        uuid tenant_id
        varchar stored_name
        text storage_path
        varchar storage_backend
        varchar mime_type
        bigint file_size_bytes
        varchar checksum
        uuid uploaded_by
    }

    AUDIT_EVENTS {
        uuid id PK
        varchar event_type
        uuid actor_id
        varchar actor_email
        varchar entity_type
        uuid entity_id
        varchar source_service
        jsonb old_value
        jsonb new_value
        timestamptz occurred_at
    }

    %% iamdb internal relations (real FKs)
    TENANTS ||--o{ USERS : "has users"
    TENANTS ||--o{ ROLES : "has roles"
    USERS ||--o{ ROLES : "user_roles (junction)"
    ROLES ||--o{ PERMISSIONS : "role_permissions (junction)"

    %% schemadb internal relations (real FKs)
    SITES ||--o{ MENUS : "has menus"
    MENUS ||--o{ MENU_ITEMS : "has items"
    MENU_ITEMS ||--o{ MENU_ITEMS : "parent-child"
    CONTENT_TYPES ||--o{ FIELD_DEFINITIONS : "defines fields"
    CONTENT_TYPES ||--o{ CATEGORIES : "has categories"
    CATEGORIES ||--o{ CATEGORIES : "parent-child"

    %% contentdb internal relations (real FKs)
    CONTENT_ITEMS ||--o{ CONTENT_VERSIONS : "versioned by"
    CONTENT_ITEMS }o--o| CONTENT_ITEMS : "parent-child"

    %% workflowdb internal relations (real FKs)
    WORKFLOW_DEFINITIONS ||--o{ WORKFLOW_INSTANCES : "instantiated as"
    WORKFLOW_INSTANCES ||--o{ WORKFLOW_HISTORY : "history"

    %% Cross-database LOGICAL references (no FK — separate databases)
    %% SITES.tenant_id → TENANTS.id (iamdb → iamdb — logical ref)
    %% CONTENT_ITEMS.site_id → SITES.id (contentdb → schemadb)
    %% CONTENT_ITEMS.content_type_id → CONTENT_TYPES.id (contentdb → schemadb)
    %% CONTENT_ITEMS.category_id → CATEGORIES.id (contentdb → schemadb)
    %% CONTENT_ITEMS.workflow_instance_id → WORKFLOW_INSTANCES.id (contentdb → workflowdb)
    %% MENU_ITEMS.content_item_id → CONTENT_ITEMS.id (schemadb → contentdb)
    %% WORKFLOW_HISTORY.actor_id → USERS.id (workflowdb → iamdb)
    %% MEDIA_ASSETS.uploaded_by → USERS.id (mediadb → iamdb)
```

---

## DIAGRAM 8: CI/CD Pipeline

```mermaid
flowchart TD
    DEV[Developer\npushes to\nfeature branch] --> PR[Pull Request\nto develop]

    PR --> V[Stage 1: VALIDATE\n< 2 min\n• mvn compile\n• Checkstyle\n• SpotBugs\n• ESLint]

    V --> T[Stage 2: TEST\n< 10 min parallel\n• Unit tests\n• Integration tests\n  via Testcontainers\n• Frontend tests\n• Architecture tests\n  via ArchUnit]

    T --> B[Stage 3: BUILD\n< 5 min\n• mvn package\n• Docker build\n• Push to ECR\n• Tag with git SHA]

    B --> DD[Stage 4: DEPLOY DEV\nauto on develop merge\n• helm upgrade\n• Smoke tests\n• E2E tests subset]

    DD --> QA[Stage 5: DEPLOY STAGING\nmanual promote\n• Full E2E tests\n• Performance tests\n• Security scan]

    QA --> GATE{Manual\nApproval\nGate}

    GATE --> PROD[Stage 6: DEPLOY PROD\n• Blue/Green deployment\n• Health check gates\n• Traffic shift 10→50→100%\n• Automatic rollback on fail]

    PROD --> MON[Post-Deploy\nMonitoring\n• Error rate watch\n• Latency watch\n• 15 min observation\n• Auto-rollback if degraded]

    style V fill:#e8f5e9
    style T fill:#e8f5e9
    style B fill:#e3f2fd
    style DD fill:#fff3e0
    style QA fill:#fff3e0
    style PROD fill:#fce4ec
    style MON fill:#f3e5f5
```

---

## DIAGRAM 9: Migration Strategy (Strangler Fig Pattern)

```mermaid
timeline
    title Migration from Old CMS to NextGen CMS
    section Phase 1 - Foundation (Weeks 1-4)
        Week 1-2 : Deploy K8s cluster, Kafka, Redis
                 : Set up new DB schemas alongside old
                 : Deploy IAM Service
                 : Migrate users & roles
        Week 3-4 : Deploy Schema Service
                 : Seed content_types from old ENUMs
                 : Deploy Audit Service
                 : Begin dual-write for audit trail

    section Phase 2 - Content Engine (Weeks 5-8)
        Week 5-6 : Deploy Content Service
                 : Run migration SQL for first 5 modules
                 : Dual-write: new writes go to both
                 : Verify data parity
        Week 7-8 : Deploy Workflow Service
                 : Deploy Media Service
                 : Migrate remaining 23 modules
                 : 100% data parity confirmed

    section Phase 3 - Traffic Cutover (Weeks 9-12)
        Week 9   : Route 10% read traffic to new stack
                 : Monitor error rates, latency
        Week 10  : 25% traffic to new stack
        Week 11  : 50% traffic + new write traffic
        Week 12  : 100% traffic on new stack
                 : Old stack in read-only standby

    section Phase 4 - Decommission (Weeks 13-16)
        Week 13-14 : Remove 25 ENUM files
                   : Remove 22 Convertor files
                   : Remove 28 module controllers
        Week 15-16 : Archive old DB tables
                   : Decommission old application servers
                   : Final performance baseline
```

---

## DIAGRAM 10: Observability Stack

```mermaid
flowchart LR
    subgraph Services["Spring Boot Services"]
        S1[Content Service]
        S2[Workflow Service]
        S3[Schema Service]
        SN[... N Services]
    end

    subgraph Instrumentation["Auto-Instrumentation (OpenTelemetry Agent)"]
        OTEL[OTel Collector]
    end

    Services --> |Traces\nSpans\nLogs\nMetrics| OTEL

    OTEL --> |Traces| J[Jaeger\nor Tempo]
    OTEL --> |Metrics| P[Prometheus]
    OTEL --> |Logs| ES[Elasticsearch\nor Loki]

    P --> G[Grafana Dashboards\n• Service health\n• Content publish rates\n• Cache hit ratios\n• Kafka consumer lag\n• Workflow SLA metrics]

    J --> G
    ES --> K[Kibana\nLog exploration\nError investigation]

    subgraph Alerts["Alertmanager"]
        A1[PagerDuty\nfor P1/P2]
        A2[Slack\nfor P3/P4]
    end

    G --> Alerts
```

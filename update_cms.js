const fs = require('fs');

let html = fs.readFileSync('cms.html', 'utf8');

// 1. Update schemaSql
const dbStructure = fs.readFileSync('db-structure.md', 'utf8');
const escapedDbStructure = dbStructure.replace(/\\/g, '\\\\').replace(/\`/g, '\\`').replace(/\$/g, '\\$');

// Find const schemaSql = ` ... `;
const schemaStart = html.indexOf('const schemaSql = \`');
const schemaEnd = html.indexOf('\`;', schemaStart);
if (schemaStart !== -1 && schemaEnd !== -1) {
    html = html.substring(0, schemaStart) + 'const schemaSql = \`' + escapedDbStructure + html.substring(schemaEnd);
}

// 2. Replace Diagram 2
const diag2new = `C4Container
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

    Rel(content_svc, contentdb, "Reads/Writes", "JDBC")
    Rel(schema_svc, schemadb, "Reads/Writes", "JDBC")
    Rel(workflow_svc, workflowdb, "Reads/Writes", "JDBC")
    Rel(media_svc, mediadb, "Reads/Writes", "JDBC")
    Rel(iam_svc, iamdb, "Reads/Writes", "JDBC")
    Rel(form_svc, formdb, "Reads/Writes", "JDBC")
    Rel(notif_svc, notifdb, "Reads/Writes", "JDBC")
    Rel(audit_svc, auditdb, "Writes", "JDBC")
    Rel(search_svc, opensearch, "Reads/Writes", "REST")

    Rel(content_svc, kafka, "Publishes", "Debezium CDC")
    Rel(schema_svc, kafka, "Publishes", "Debezium CDC")
    Rel(workflow_svc, kafka, "Publishes", "Debezium CDC")
    Rel(media_svc, kafka, "Publishes", "Debezium CDC")
    Rel(iam_svc, kafka, "Publishes", "Debezium CDC")
    Rel(form_svc, kafka, "Publishes", "Debezium CDC")

    Rel(search_svc, kafka, "Consumes", "Kafka Client")
    Rel(audit_svc, kafka, "Consumes", "Kafka Client")
    Rel(notif_svc, kafka, "Consumes", "Kafka Client")`;

const d2start = html.indexOf('<div class="section" id="diag-2">');
const d2mermaidStart = html.indexOf('<div class="mermaid">', d2start) + 21;
const d2mermaidEnd = html.indexOf('</div></div></div>', d2mermaidStart);
if (d2mermaidStart !== -1 && d2mermaidEnd !== -1) {
    html = html.substring(0, d2mermaidStart) + diag2new + html.substring(d2mermaidEnd);
}

// 3. Replace Diagram 7
const diag7new = `erDiagram
    CONTENT_TYPES { uuid id PK; varchar code UK; boolean has_workflow; uuid workflow_id FK; jsonb metadata }
    FIELD_DEFINITIONS { uuid id PK; uuid content_type_id FK; varchar field_key; varchar field_type; boolean is_required; jsonb validation_rules }
    CATEGORIES { uuid id PK; uuid content_type_id FK; varchar code; uuid parent_id FK }
    SITES { uuid id PK; uuid tenant_id; varchar code UK; varchar domain }
    MENUS { uuid id PK; uuid site_id FK; varchar location }
    MENU_ITEMS { uuid id PK; uuid menu_id FK; uuid content_item_id; varchar label }
    CONTENT_ITEMS { uuid id PK; uuid tenant_id; uuid content_type_id FK; varchar title; jsonb body; varchar status; uuid category_id FK; uuid site_id FK; uuid workflow_instance_id FK; bigint version }
    CONTENT_VERSIONS { uuid id PK; uuid content_item_id FK; int version_number; jsonb body }
    WORKFLOW_DEFINITIONS { uuid id PK; varchar code; jsonb definition; boolean is_active }
    WORKFLOW_INSTANCES { uuid id PK; uuid workflow_def_id FK; varchar entity_type; varchar current_state }
    WORKFLOW_HISTORY { uuid id PK; uuid instance_id FK; varchar from_state; varchar to_state; uuid actor_id FK }
    TENANTS { uuid id PK; varchar code UK; varchar plan }
    USERS { uuid id PK; uuid tenant_id FK; varchar username UK; varchar email UK; varchar status }
    ROLES { uuid id PK; uuid tenant_id FK; varchar code UK }
    PERMISSIONS { uuid id PK; varchar resource; varchar action; varchar scope }
    MEDIA_ASSETS { uuid id PK; text storage_path; varchar mime_type; varchar checksum; uuid uploaded_by FK }
    AUDIT_EVENTS { uuid id PK; varchar event_type; uuid entity_id; jsonb old_value; jsonb new_value }
    OUTBOX_EVENTS { uuid id PK; varchar event_type; jsonb payload; timestamptz processed_at }

    CONTENT_TYPES ||--o{ FIELD_DEFINITIONS : "defines fields"
    CONTENT_TYPES ||--o{ CATEGORIES : "has categories"
    CONTENT_TYPES ||--o{ CONTENT_ITEMS : "instances of"
    SITES ||--o{ MENUS : "has"
    MENUS ||--o{ MENU_ITEMS : "contains"
    CATEGORIES ||--o{ CATEGORIES : "parent-child"
    CONTENT_ITEMS ||--o{ CONTENT_VERSIONS : "versioned by"
    CONTENT_ITEMS }o--o| CATEGORIES : "classified by"
    CONTENT_ITEMS }o--o| CONTENT_ITEMS : "parent-child"
    CONTENT_ITEMS }o--o| WORKFLOW_INSTANCES : "governed by"
    WORKFLOW_DEFINITIONS ||--o{ WORKFLOW_INSTANCES : "instantiated as"
    TENANTS ||--o{ USERS : "has"
    TENANTS ||--o{ ROLES : "defines"
    USERS }o--o{ ROLES : "assigned"
    ROLES }o--o{ PERMISSIONS : "grants"
    MEDIA_ASSETS }o--o| USERS : "uploaded by"`;

const d7start = html.indexOf('<div class="section" id="diag-7">');
const d7mermaidStart = html.indexOf('<div class="mermaid">', d7start) + 21;
const d7mermaidEnd = html.indexOf('</div></div></div>', d7mermaidStart);
if (d7mermaidStart !== -1 && d7mermaidEnd !== -1) {
    html = html.substring(0, d7mermaidStart) + diag7new + html.substring(d7mermaidEnd);
}

// 4. Update text for 9 databases
html = html.replace('8 schemas', '9 separate databases, one per service');
html = html.replace('Single PostgreSQL 16 Cluster', '9 Separate PostgreSQL 16 Databases');
html = html.replace('Multi-schema PostgreSQL design', 'Database-per-service topology (9 separate databases) with Row-Level Security');

fs.writeFileSync('cms.html', html, 'utf8');
console.log('Successfully updated cms.html');

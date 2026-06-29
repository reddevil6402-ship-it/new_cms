# Workflow Document
## NextGen Enterprise CMS — Editorial & Development Workflow

> **Document Type:** Workflow Reference  
> **Project:** NextGen Enterprise CMS  
> **Version:** 1.0  
> **Scope:** Content lifecycle workflows, development workflows, team processes, sprint structure

---

## Table of Contents

1. [Content Lifecycle Workflows](#1-content-lifecycle-workflows)
2. [Workflow Engine Internals](#2-workflow-engine-internals)
3. [Role-Based Workflow Permissions](#3-role-based-workflow-permissions)
4. [Pre-Built Workflow Definitions](#4-pre-built-workflow-definitions)
5. [Development Workflow (SDLC)](#5-development-workflow-sdlc)
6. [AI-First Development SOP](#6-ai-first-development-sop)
7. [Sprint Structure](#7-sprint-structure)
8. [Team Ownership Map](#8-team-ownership-map)
9. [Development Order (Module Sequence)](#9-development-order-module-sequence)
10. [Git Branching Strategy](#10-git-branching-strategy)
11. [Definition of Done](#11-definition-of-done)
12. [QA Workflow](#12-qa-workflow)
13. [AI Prompt Library for Developers](#13-ai-prompt-library-for-developers)

---

## 1. Content Lifecycle Workflows

### 1.1 Standard Content Lifecycle

All content items progress through a defined state machine. The **transitions available depend on the assigned workflow definition** for that content type.

```
                    ┌─────────────────────────────────────────────┐
                    │           CONTENT STATE MACHINE             │
                    └─────────────────────────────────────────────┘

[New] ──► DRAFT ─────────────────────────────────────────────────────────► TRASHED
            │                                                                  ▲
            │ Submit for Review (Author)                              (Admin)  │
            ▼                                                                  │
         IN_REVIEW ──────────────────────────────────────────────────────────►│
            │           │                │                                     │
            │ Approve   │ Reject         │ Request Changes                     │
            ▼           ▼                ▼                                     │
         APPROVED    REJECTED ──► DRAFT (revise)                               │
            │                                                                  │
            │ Publish (Publisher/Admin)                                        │
            ├──────────────────────────────────────────────────────────────────┤
            │                                                                  │
            │ Schedule (set publish_at)                                        │
            ▼                                                                  │
         SCHEDULED ──► PUBLISHED (auto at publish_at)                         │
                              │                                                │
                              │ Archive (Admin)                                │
                              ▼                                                │
                           ARCHIVED ◄─────────────────────────────────────────┘
                              │
                              │ Restore (Admin)
                              ▼
                           PUBLISHED
```

### 1.2 State Definitions

| State | Description | Who Can Be Here |
|-------|-------------|----------------|
| **DRAFT** | Initial state, work in progress | Author |
| **IN_REVIEW** | Submitted for editorial review | Reviewer |
| **APPROVED** | Review approved, ready to publish | Publisher/Admin |
| **SCHEDULED** | Approved, set for future publish | System |
| **PUBLISHED** | Live on public site | Public |
| **REJECTED** | Review rejected; author must revise | Author |
| **ARCHIVED** | Removed from public view but preserved | Admin |
| **TRASHED** | Soft-deleted, pending hard-delete | Super Admin |

### 1.3 Transition Rules

| From | To | Trigger | Required Role | Guards |
|------|----|---------|---------------|--------|
| DRAFT | IN_REVIEW | SUBMIT_FOR_REVIEW | AUTHOR, EDITOR | FIELD_NOT_EMPTY(title, body) |
| DRAFT | DRAFT | SAVE | AUTHOR | — |
| IN_REVIEW | APPROVED | APPROVE | REVIEWER, ADMIN | NOT_OWN_CONTENT |
| IN_REVIEW | REJECTED | REJECT | REVIEWER, ADMIN | REQUIRE_COMMENT |
| IN_REVIEW | DRAFT | REQUEST_CHANGES | REVIEWER, ADMIN | REQUIRE_COMMENT |
| REJECTED | DRAFT | REVISE | AUTHOR | — |
| APPROVED | PUBLISHED | PUBLISH | PUBLISHER, ADMIN | — |
| APPROVED | SCHEDULED | SCHEDULE | PUBLISHER, ADMIN | FIELD_NOT_EMPTY(publish_at) |
| APPROVED | DRAFT | REVOKE_APPROVAL | ADMIN | — |
| SCHEDULED | PUBLISHED | AUTO_PUBLISH | SYSTEM | publish_at <= NOW() |
| SCHEDULED | DRAFT | CANCEL_SCHEDULE | PUBLISHER, ADMIN | — |
| PUBLISHED | ARCHIVED | ARCHIVE | ADMIN | — |
| PUBLISHED | PUBLISHED | UPDATE | AUTHOR, EDITOR | (creates new version) |
| ARCHIVED | PUBLISHED | RESTORE | ADMIN | — |
| ARCHIVED | TRASHED | TRASH | SUPER_ADMIN | — |

---

## 2. Workflow Engine Internals

### 2.1 Execution Flow (Per Transition)

```
1. API receives: POST /api/v1/content/{type}/{id}/workflow
   Body: { "action": "SUBMIT_FOR_REVIEW", "comment": "Ready for review" }

2. content-service → workflow-service.triggerTransition(instanceId, action, userId)

3. workflow-service:
   a. Load workflow_definition (from Redis cache or DB)
   b. Find transition: current_state → trigger → next_state
   c. Execute GuardChain:
      - ROLE_CHECK: actor has required role?
      - FIELD_NOT_EMPTY: required fields present in content body?
      - NOT_OWN_CONTENT: actor did not create the content?
      - REQUIRE_COMMENT: comment provided?
      → Any guard fails → throw GuardFailedException → 400 response
   d. Update workflow_instances.current_state = 'IN_REVIEW'
   e. INSERT workflow_history record
   f. Execute ActionChain (writes to outbox_events):
      - NOTIFY → notification-svc sends email to REVIEWER role
      - EMIT_EVENT → cms.workflow.transitioned Kafka event
      - AUDIT_LOG → cms.audit.state_changed Kafka event

4. content-service updates content_items.status = 'IN_REVIEW'

5. content-service returns 200 with { "newState": "IN_REVIEW", "transitionedAt": "..." }

6. Async (Kafka consumers):
   - notification-svc: sends emails to reviewers
   - audit-service: writes to audit_events
```

### 2.2 Guard Implementation Reference

```
ROLE_CHECK(roles: string[])     → actor.roles contains any of roles
FIELD_NOT_EMPTY(fields: string[]) → content.body[field] is not null/empty for each field
NOT_OWN_CONTENT                 → content.created_by != actor.id
REQUIRE_COMMENT                 → comment param is not null and not blank
TENANT_MATCH                    → content.tenant_id == actor.tenant_id (always enforced)
```

### 2.3 Action Implementation Reference

```
NOTIFY(to: string, template: string)
  → to: "role:REVIEWER" | "content:author" | "user:{id}" | "email:{address}"
  → Writes NotificationRequest to outbox_events

EMIT_EVENT(topic: string)
  → Writes CloudEvent to outbox_events → Debezium → Kafka

AUDIT_LOG(event: string)
  → Writes AuditEvent to outbox_events → Kafka → audit-service

SET_PUBLISHED_AT
  → Updates content_items.published_at = NOW()

SCHEDULE_PUBLISH(field: string)
  → Reads content_items.body[field] as timestamptz → sets publish_at

INDEX_SEARCH
  → Emits cms.content.published → search-service re-indexes

INVALIDATE_CACHE(patterns: string[])
  → Emits cache invalidation event → Redis SCAN+DEL on patterns
```

---

## 3. Role-Based Workflow Permissions

### 3.1 Permission Matrix by Role

| Action | AUTHOR | REVIEWER | PUBLISHER | ADMIN | SUPER_ADMIN |
|--------|--------|----------|-----------|-------|-------------|
| Create Draft | ✅ | ✅ | ✅ | ✅ | ✅ |
| Submit for Review | ✅ (own) | ✅ (own) | ✅ | ✅ | ✅ |
| Approve Content | ❌ | ✅ (not own) | ❌ | ✅ | ✅ |
| Reject Content | ❌ | ✅ | ❌ | ✅ | ✅ |
| Publish Content | ❌ | ❌ | ✅ | ✅ | ✅ |
| Schedule Publish | ❌ | ❌ | ✅ | ✅ | ✅ |
| Archive Content | ❌ | ❌ | ❌ | ✅ | ✅ |
| Restore Archived | ❌ | ❌ | ❌ | ✅ | ✅ |
| Hard Delete | ❌ | ❌ | ❌ | ❌ | ✅ |
| Configure Workflow | ❌ | ❌ | ❌ | ✅ | ✅ |
| View Audit Log | ❌ | ❌ | ❌ | ✅ | ✅ |

---

## 4. Pre-Built Workflow Definitions

### 4.1 Standard Publish Workflow (default for most content types)

```json
{
  "code": "standard-publish",
  "name": "Standard Content Publish Workflow",
  "initialState": "DRAFT",
  "transitions": [
    {
      "id": "submit_for_review",
      "from": "DRAFT", "to": "IN_REVIEW", "trigger": "SUBMIT_FOR_REVIEW",
      "guards": [
        { "type": "ROLE_CHECK", "params": { "roles": ["AUTHOR", "EDITOR", "PUBLISHER"] } },
        { "type": "FIELD_NOT_EMPTY", "params": { "fields": ["title"] } }
      ],
      "actions": [
        { "type": "NOTIFY", "params": { "to": "role:REVIEWER", "template": "review_requested" } },
        { "type": "EMIT_EVENT", "params": { "topic": "cms.workflow.transitioned" } },
        { "type": "AUDIT_LOG", "params": { "event": "content.submitted_for_review" } }
      ]
    },
    {
      "id": "approve",
      "from": "IN_REVIEW", "to": "APPROVED", "trigger": "APPROVE",
      "guards": [
        { "type": "ROLE_CHECK", "params": { "roles": ["REVIEWER", "ADMIN"] } },
        { "type": "NOT_OWN_CONTENT", "params": {} }
      ],
      "actions": [
        { "type": "NOTIFY", "params": { "to": "content:author", "template": "content_approved" } },
        { "type": "AUDIT_LOG", "params": { "event": "content.approved" } }
      ]
    },
    {
      "id": "reject",
      "from": "IN_REVIEW", "to": "REJECTED", "trigger": "REJECT",
      "guards": [
        { "type": "ROLE_CHECK", "params": { "roles": ["REVIEWER", "ADMIN"] } },
        { "type": "REQUIRE_COMMENT", "params": {} }
      ],
      "actions": [
        { "type": "NOTIFY", "params": { "to": "content:author", "template": "content_rejected" } },
        { "type": "AUDIT_LOG", "params": { "event": "content.rejected" } }
      ]
    },
    {
      "id": "publish",
      "from": "APPROVED", "to": "PUBLISHED", "trigger": "PUBLISH",
      "guards": [
        { "type": "ROLE_CHECK", "params": { "roles": ["PUBLISHER", "ADMIN"] } }
      ],
      "actions": [
        { "type": "SET_PUBLISHED_AT", "params": {} },
        { "type": "INDEX_SEARCH", "params": {} },
        { "type": "INVALIDATE_CACHE", "params": { "patterns": ["content:list:*"] } },
        { "type": "EMIT_EVENT", "params": { "topic": "cms.content.published" } },
        { "type": "AUDIT_LOG", "params": { "event": "content.published" } }
      ]
    }
  ]
}
```

### 4.2 Direct Publish Workflow (for trusted editors — skip review)

Same as above but DRAFT → PUBLISHED directly allowed for PUBLISHER role.

### 4.3 Multi-Level Approval Workflow (for Tenders)

DRAFT → LEVEL1_REVIEW → LEVEL2_REVIEW → APPROVED → PUBLISHED  
Each level has different role requirements.

---

## 5. Development Workflow (SDLC)

### 5.1 Feature Development Flow

```
1. Read SRS requirements for the feature
   ↓
2. Design (30 min max)
   - DB schema changes needed?
   - API endpoints needed?
   - Frontend pages/components needed?
   ↓
3. Write Flyway migration (if DB changes)
   ↓
4. Use AI to generate (Cursor AI / Claude):
   - Domain model + ports
   - Application service
   - JPA entity + repository adapter
   - REST controller
   - DTOs + validation
   - Frontend form/table component
   - Unit tests
   - Integration tests
   ↓
5. Developer reviews AI output:
   - Does it follow hexagonal architecture? (ArchUnit will catch it if not)
   - Are domain rules correctly implemented?
   - Are edge cases handled?
   - Are security annotations correct?
   ↓
6. Run tests locally:
   - mvn test (unit)
   - mvn verify (integration, Testcontainers)
   - npm test (frontend)
   ↓
7. Push to feature branch → PR to develop
   ↓
8. CI pipeline runs automatically (validate → test → build)
   ↓
9. Human code review (PR review checklist)
   ↓
10. QA validation on dev environment
    ↓
11. Merge to develop
```

### 5.2 PR Review Checklist

- [ ] Does it follow hexagonal architecture? (no Spring imports in domain package)
- [ ] Are all new endpoints protected with `@PreAuthorize`?
- [ ] Are domain rules tested in unit tests (not integration tests)?
- [ ] Is the response envelope correct (`ApiResponse<T>`)?
- [ ] Are all state-changing operations audited?
- [ ] Are there ArchUnit violations? (CI will catch, but review manually)
- [ ] Is the Flyway migration backward-compatible?
- [ ] Are all new Kafka events documented in this workflow doc?
- [ ] Are error cases handled (not swallowed silently)?
- [ ] Is sensitive data absent from logs?

---

## 6. AI-First Development SOP

### 6.1 Primary AI Tools

| Tool | Primary Use |
|------|------------|
| **Cursor AI** | In-IDE code generation, refactoring, test generation |
| **Claude Sonnet** | Architecture decisions, complex business logic design, PR review |
| **ChatGPT** | Quick SQL queries, boilerplate, config generation |

### 6.2 Cursor AI SOPs

#### Generate a new microservice layer (Domain → Infrastructure)

```
PROMPT:
I'm building the [ServiceName] for a NextGen CMS.
Architecture: Hexagonal (Ports & Adapters) with Spring Boot 3 + Java 21.
Package: com.cms.[service-name]

Generate:
1. Domain model: [ModelName].java (plain Java, no Spring/JPA imports)
2. Port interface: [UseCaseName].java (in com.cms.[service].domain.ports.in)
3. Repository port: [RepoName].java (in com.cms.[service].domain.ports.out)
4. Domain service: [DomainService].java (implements use case, calls repository port)
5. JPA Entity: [Entity].java (in infrastructure.persistence)
6. Repository adapter: [RepoAdapter].java (implements repository port, uses Spring Data JPA)
7. Application service: [AppService].java (orchestration, transaction boundary)
8. REST controller: [Controller].java (thin adapter, delegates to application service)
9. Response DTO: [Response].java
10. Unit test for domain service (no Spring context)
11. Integration test for repository adapter (Testcontainers + PostgreSQL)

Domain requirements:
[Paste SRS requirements here]

Table definition:
[Paste DDL here]
```

#### Generate a frontend dynamic table

```
PROMPT:
Generate a Next.js 14 (App Router, TypeScript) content list page for content type "[type]".

Requirements:
- Server Component fetches content list from: GET /api/v1/content/[type]
- Use TanStack Table v8 for the table
- Columns: auto-generated from field_definitions where is_listable = true
- Features: pagination (server-side), column sort, status filter, search input
- Row actions: Edit, View, Delete (with confirm dialog)
- Status badge uses our StatusBadge component
- Skeleton loading state while fetching
- Error boundary with retry

API response shape: [paste ContentListResponse shape]
Field definitions for this type: [paste field_definitions]
```

#### Generate unit tests for domain service

```
PROMPT:
Write comprehensive JUnit 5 unit tests for this domain service:
[paste domain service code]

Test coverage requirements:
- Happy path for each public method
- All business rule violations (InvalidStatusTransitionException, etc.)
- Edge cases: null inputs, empty collections
- Use Mockito to mock out port interfaces
- No Spring context (pure Java tests)
- Use @DisplayName with descriptive names
- Follow Given/When/Then structure
```

### 6.3 Claude SOP (Architecture & Complex Logic)

Use Claude for:
- Reviewing workflow guard implementations for correctness
- Designing complex database queries
- Reviewing hexagonal architecture compliance
- Designing event schemas
- Security review of authentication/authorization logic

```
PROMPT TEMPLATE FOR CLAUDE:
Context: [brief system description]
Current code: [paste relevant code]
Question: [specific architectural or logic question]
Constraints: [list constraints — hexagonal, no Spring in domain, etc.]

Do NOT just agree with my approach. If you see a flaw, say so clearly.
```

---

## 7. Sprint Structure

### Sprint Duration: 2 Weeks

### Sprint 1 — Foundation & Infrastructure

**Week 1:**
- [ ] Monorepo setup (cms-platform, cms-frontend)
- [ ] Shared libraries: cms-common, cms-security, cms-testing
- [ ] Kubernetes local development setup (Skaffold + Helm)
- [ ] Kafka + Redis + PostgreSQL via docker-compose for dev
- [ ] Flyway migrations V1–V9 (all schema creation)
- [ ] iam-service: user registration, login, JWT issuance
- [ ] GitHub Actions CI pipeline (validate + test stages)

**Week 2:**
- [ ] iam-service: RBAC, roles, permissions, refresh tokens
- [ ] API Gateway configuration (Kong/Nginx JWT validation)
- [ ] Admin App Next.js setup, design system, Auth flow
- [ ] Seed data: default roles, permissions
- [ ] ArchUnit test suite (architecture constraint tests)

### Sprint 2 — Schema Service + Content Engine

**Week 3:**
- [ ] schema-service: content type CRUD, field definitions, categories
- [ ] Admin UI: Content Type designer, Field builder
- [ ] Flyway V12: seed default content types (from legacy modules)
- [ ] content-service: core CRUD (create, read, update, soft delete)
- [ ] content-service: versioning

**Week 4:**
- [ ] content-service: workflow integration
- [ ] workflow-service: state machine engine, guard chain, action chain
- [ ] Admin UI: DynamicForm (schema-driven form renderer)
- [ ] Admin UI: DynamicTable (schema-driven list table)
- [ ] Admin UI: WorkflowPanel component
- [ ] Integration tests: content lifecycle end-to-end

### Sprint 3 — Media + Search + Notifications

**Week 5:**
- [ ] media-service: upload, deduplication, storage abstraction (Local profile)
- [ ] media-service: async thumbnail generation (Kafka consumer)
- [ ] Admin UI: MediaPicker, MediaLibrary
- [ ] search-service: index management, Kafka consumer, full-text search API
- [ ] Admin UI: Global search component

**Week 6:**
- [ ] notification-svc: email dispatch (SMTP/SendGrid), template engine
- [ ] notification-svc: in-app notifications (polling or SSE)
- [ ] Admin UI: Notification bell + dropdown
- [ ] audit-service: Kafka consumer, audit log storage
- [ ] Admin UI: Audit Log viewer page

### Sprint 4 — Forms + Public App + Reports

**Week 7:**
- [ ] form-service: form schema management, submission handling
- [ ] Public App: Form renderer (react-jsonschema-form)
- [ ] Public App: Content listing pages (ISR)
- [ ] Public App: Content detail pages (SSR)
- [ ] Public App: Search results page

**Week 8:**
- [ ] Reports: Dashboard stats, content by type, workflow SLA
- [ ] Reports: PDF export, Excel export
- [ ] Admin UI: Reports pages
- [ ] Performance testing (Gatling baseline)
- [ ] Security scan (OWASP Dependency Check)

### Sprint 5 — Migration + QA + Stabilization

**Week 9–10:**
- [ ] Data migration scripts (legacy → content_items)
- [ ] Dual-write validation
- [ ] Staging deployment
- [ ] Full E2E test suite (Playwright)
- [ ] UAT with stakeholders
- [ ] Bug fixes and performance tuning

---

## 8. Team Ownership Map

| Developer | Owns | Microservices |
|-----------|------|--------------|
| **Dev 1 (Tech Lead)** | Infrastructure, Auth, Security | iam-service, API Gateway config, CI/CD pipeline, shared libraries |
| **Dev 2 (Content Lead)** | Content Engine, Schema | content-service, schema-service |
| **Dev 3 (Workflow/Media)** | Workflow, Media, Search | workflow-service, media-service, search-service |
| **Dev 4 (Frontend + Reports)** | All frontend apps, Forms, Notifications, Reports | form-service, notification-svc, Admin App, Public App |
| **QA** | Testing | All services — functional, regression, E2E |

---

## 9. Development Order (Module Sequence)

**Follow this exact order. Do not skip ahead.**

```
Step 1:  Infrastructure Setup (K8s, Docker, CI/CD)
Step 2:  Shared Libraries (cms-common, cms-security, cms-testing)
Step 3:  Database Schema (Flyway V1–V9)
Step 4:  IAM Service (auth, JWT, RBAC)
Step 5:  Schema Service (content types, fields, categories)
Step 6:  Content Service (CRUD, versioning, status management)
Step 7:  Workflow Service (state machine engine)
Step 8:  Audit Service (Kafka consumer, append-only log)
Step 9:  Media Service (upload, storage abstraction)
Step 10: Search Service (index + query)
Step 11: Notification Service (email, in-app)
Step 12: Form Service (schema + submissions)
Step 13: Admin App (auth flow, shell, DynamicForm, DynamicTable)
Step 14: Admin UI — Content management pages
Step 15: Admin UI — Schema designer
Step 16: Admin UI — Workflow designer
Step 17: Admin UI — Media library
Step 18: Admin UI — Reports
Step 19: Public App (content delivery, search, forms)
Step 20: Data Migration (legacy → new schema)
Step 21: Staging Deployment + Full E2E Tests
Step 22: UAT
Step 23: Production Release
```

**Why this order?**
- IAM must be first: every other service depends on JWT validation
- Schema Service must precede Content Service: content validates against schema
- Workflow must precede Content publish: publish transitions need workflow
- Audit must be early: start capturing audit trail before business modules
- Frontend comes after backend APIs are stable (prevents rework)

---

## 10. Git Branching Strategy

```
main          ← Production releases (tagged: v1.0.0)
  └── develop ← Integration branch (all features merge here)
        └── feature/{ticket-id}-{description}
              e.g.:
              feature/CMS-001-iam-service-setup
              feature/CMS-012-content-service-crud
              feature/CMS-025-dynamic-form-component
              feature/CMS-031-workflow-state-machine

        └── bugfix/{ticket-id}-{description}
              e.g.:
              bugfix/CMS-042-slug-uniqueness-constraint

        └── hotfix/{ticket-id}-{description} ← Merges to both main and develop
```

### Branch Rules

- Feature branches branch from `develop`
- PRs must target `develop`
- Merges to `main` are release-only, tagged with semver
- Squash merge on PR merge to keep history clean
- Branch names must include ticket ID for traceability

---

## 11. Definition of Done

A feature/story is **Done** when ALL of the following are true:

**Code:**
- [ ] Feature code implemented and reviewed
- [ ] No ArchUnit violations
- [ ] No Checkstyle violations
- [ ] No SpotBugs high-severity findings
- [ ] ESLint passes (frontend)

**Tests:**
- [ ] Domain unit tests written and passing (≥ 80% domain layer coverage)
- [ ] API integration tests written and passing
- [ ] Happy path + error cases tested
- [ ] Playwright E2E test written for critical user flows (if applicable)

**Documentation:**
- [ ] OpenAPI spec updated/auto-generated
- [ ] Any new Kafka events documented in this workflow doc
- [ ] Any new env variables documented in README

**Security:**
- [ ] New endpoints have `@PreAuthorize` annotations
- [ ] No sensitive data in logs
- [ ] Flyway migration is backward-compatible

**Quality:**
- [ ] CI pipeline passes (all stages green)
- [ ] QA has validated on dev environment
- [ ] No known critical/high bugs

---

## 12. QA Workflow

### 12.1 Test Pyramid Coverage Targets

| Level | Coverage | Tools |
|-------|---------|-------|
| Unit (Domain) | ≥ 90% | JUnit 5, Mockito |
| Unit (Application) | ≥ 80% | JUnit 5, Mockito |
| Integration | ≥ 70% | Testcontainers, MockMvc |
| E2E (Critical Flows) | 100% of critical paths | Playwright |

### 12.2 Critical E2E Flows (Playwright)

1. **Content Create → Submit → Approve → Publish**
2. **Media Upload → Attach to Content → Publish**
3. **Schema Change → Form Updates Immediately**
4. **Role Enforcement** — AUTHOR cannot publish, REVIEWER cannot approve own content
5. **JWT Expiry → Auto-Refresh → Continue Flow**
6. **Search** — Content published → appears in search results within 30s
7. **Scheduled Publish** — Content enters SCHEDULED → auto-publishes at time

### 12.3 QA Environment Setup

- Dedicated `qa` namespace in Kubernetes
- Data reset script runs before each test cycle
- Playwright tests run against qa environment in CI (Stage 5)
- Performance baseline: Gatling runs against staging (100 virtual users, 10 min)

---

## 13. AI Prompt Library for Developers

### Generate Flyway Migration

```
Generate a Flyway migration SQL file (PostgreSQL 16) for the following table:
Table name: [name in schema cms_xxx]
Columns: [list columns with types]
Requirements:
- UUID PK with gen_random_uuid()
- Standard audit columns (created_at, updated_at, created_by, deleted_at, version)
- JSONB columns for [specify]
- Appropriate indexes for [specify query patterns]
- RLS policy for tenant isolation using cms_iam.users reference
- Follow naming: V{N}__{description}.sql
```

### Generate Kafka Event Consumer

```
Generate a Spring Boot 3 Kafka consumer for the event: [event_type]
Package: com.cms.[service].infrastructure.messaging
Requirements:
- @KafkaListener with consumer group id: [service-name]-group
- CloudEvents 1.0 format deserialization
- Idempotent processing (check if already processed by event ID)
- Error handling: retry 3 times with backoff, then dead-letter topic
- Structured logging: traceId, tenantId, eventType
- Transaction support: @Transactional
Processing logic: [describe what it should do]
```

### Generate OpenAPI Documentation

```
Generate OpenAPI 3.0 annotations for this Spring Boot controller:
[paste controller code]
Requirements:
- @Tag with service name and description
- @Operation for each method with summary and description
- @ApiResponse for 200, 400, 401, 403, 404, 500
- @Parameter for path and query params
- @RequestBody with schema reference
- Use the ApiResponse<T> envelope wrapper
```

### Generate Testcontainers Integration Test

```
Generate a Testcontainers integration test for:
Class under test: [paste class]
Requirements:
- PostgreSQL 16 container with schema init script
- [Kafka container if needed]
- @SpringBootTest with RANDOM_PORT
- Flyway migrations run before tests
- Test data setup with @BeforeEach (use builders from cms-testing library)
- Test cases: [list scenarios]
- Assert on DB state after operation (not just return value)
```

### Generate Playwright E2E Test

```
Generate a Playwright test (TypeScript) for this user flow:
Flow: [describe the flow step by step]
App URL: http://localhost:3000
Test user: { email: "admin@test.com", password: "admin123", role: "ADMIN" }
Requirements:
- Login before test (use shared auth fixture)
- Wait for network responses (not fixed timeouts)
- Assert on UI state (not just URL)
- Take screenshot on failure
- Test ID: use data-testid attributes for selectors
```

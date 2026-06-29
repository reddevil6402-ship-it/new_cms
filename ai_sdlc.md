# AI SDLC — Project State & Continuity File
## NextGen Enterprise CMS

> **Purpose:** This file is the single source of truth for AI agents and developers picking up this project mid-session or after a break. It contains every decision made, every constraint agreed upon, the current build state, and exact next steps. Read this file fully before doing anything.
>
> **Update rule:** This file must be updated at the end of every session, or whenever a phase/milestone is completed. Never let this file fall out of sync.
>
> **Last updated:** 2026-06-29 | Session ended at: Phase 2 Services complete and verified.

---

## 1. Project Identity

| Field | Value |
|---|---|
| **Project name** | NextGen Enterprise CMS |
| **Type** | Personal project (solo build), designed for team handoff later |
| **Domain** | Enterprise/Government Content Management System (Indian market) |
| **Purpose** | Full microservice rebuild of a monolithic CMS with 28 modules, 351 Java files, and 7 identified structural anti-patterns |
| **Workspace root** | `C:\Users\stadmin\Desktop\CMS_NEW\` |
| **GitHub org/repos** | Not yet created — pending setup before first code commit |

---

## 2. Agent Behaviour Constraints (MANDATORY — apply to every response)

These constraints were set by the user and **must be respected by every AI agent** working on this project:

1. **DO NOT agree by default.** If something is wrong, risky, or suboptimal — say so clearly. Disagreement is expected and welcome.
2. **DO NOT assume anything.** If a requirement, value, file, or intent is unclear — STOP and ask. Never fill in blanks with guesses.
3. **DO NOT make any changes without explicit approval.** Describe the plan, wait for go-ahead. This includes "small" or "obvious" fixes.
4. **Ask ONE clarifying question at a time.** Prioritize the most critical blocker first.
5. **Point out concerns and red flags** even when the user seems confident.

---

## 3. Architecture Summary

### 3.1 System Design

- **Pattern:** Microservices, database-per-service
- **Total services (full system):** 9 (IAM, Schema, Content, Workflow, Media, Search, Notification, Audit, Form)
- **Phase 1 services:** 5 (IAM, Schema, Content, Workflow) + Gateway + Common library
- **Key design:** Dynamic content types via JSONB schemas — no code change required to add a content type at runtime

### 3.2 Technology Stack (LOCKED)

| Layer | Technology | Notes |
|---|---|---|
| Language | Java 21 | LTS, locked |
| Framework | Spring Boot 3.3.x | Locked |
| Build tool | Maven | Locked |
| API Gateway | Spring Cloud Gateway | Locked |
| Auth | RS256 JWT (asymmetric) | Locked |
| Databases | PostgreSQL 16 | Separate DB per service |
| Cache | Redis 7 | Used in iam-service for login session context |
| Search | OpenSearch 2.x | Phase 2 only |
| Messaging | Kafka | Phase 2 only |
| Shared library | `cms-common` → GitHub Packages | Locked |
| Repo structure | Separate GitHub repo per service | Locked — team handoff reason |
| Container | Docker + docker-compose (local dev) | Kubernetes deferred |

### 3.3 Key Reference Docs (all in `C:\Users\stadmin\Desktop\CMS_NEW\`)

| File | Purpose | Status |
|---|---|---|
| `cms-architecture.md` | Full architecture blueprint, anti-pattern analysis | Reference — do not modify lightly |
| `db-structure.md` | **Canonical** DDL for all 9 service databases | Authoritative — supersedes any DDL in other docs |
| `auth-flow.md` | JWT issuance, refresh, revocation, API Gateway validation | **FINALISED** — all decisions locked |
| `phase1-plan.md` | Phase 1 implementation plan, build order, Definition of Done | **APPROVED** — ready to implement |
| `open-questions.md` | Deferred decisions and Phase 2 items | Living doc — update as items resolve |
| `db-technology-selection.md` | PostgreSQL vs MongoDB decision rationale | Reference only |
| `cms-diagrams.md` | Architecture diagrams | Reference only |
| `docs/workflow.md` | Workflow engine design detail | Reference for cms-workflow-service |

---

## 4. All Locked Decisions

These decisions are final. Do not re-open without an explicit user instruction to do so.

### 4.1 Auth & Security

| Decision | Value |
|---|---|
| JWT algorithm | RS256 (asymmetric — iam-service holds private key, all others hold public key only) |
| Access token TTL | 15 minutes |
| Refresh token | Opaque, SHA-256 hashed in DB, one-time use (rotated on every refresh) |
| Refresh token TTL | 7 days |
| Multi-device sessions | Allowed — INSERT new refresh token on login, do not revoke existing tokens |
| jti blocklist | Deferred to Phase 2 — 15-min TTL is Phase 1 mitigation |
| Key rotation cadence | Deferred to Phase 2 |
| Admin bootstrap | Flyway migration: `V2__seed_super_admin.sql` + `V3__seed_default_tenant.sql` |
| API Gateway | Spring Cloud Gateway |
| Token storage (client) | Access token: JS memory only. Refresh token: HttpOnly Secure SameSite=Strict cookie |
| Password hashing | bcrypt, cost factor 12 minimum |

### 4.2 RLS Tenant Isolation (CRITICAL — security implementation)

Decided pattern: **HikariCP DataSource wrapper + `SET LOCAL`**

- `TenantContext.java` (in `cms-common`): ThreadLocal holder for current tenantId
- `TenantContextFilter.java` (in `cms-common`): `OncePerRequestFilter` — reads `X-Tenant-Id` header (forwarded by Gateway) → sets TenantContext
- `TenantAwareDataSource.java` (in `cms-common`): Wraps HikariCP, calls `SET LOCAL app.current_tenant_id = ?` (PreparedStatement) on every connection checkout
- `SET LOCAL` is transaction-scoped — auto-cleared on commit/rollback — pool-safe by design
- **Never use `SET` (non-local)** — it persists for the entire connection session and WILL cause cross-tenant data leakage

### 4.3 Shared Library

| Decision | Value |
|---|---|
| Strategy | `cms-common` published to GitHub Packages |
| Maven groupId | `com.cms` (confirm when org is created) |
| Initial version | `1.0.0` |
| Developer setup | `~/.m2/settings.xml` with GitHub PAT required for every team member |
| PAT scope needed | `read:packages` (consumers), `write:packages` (CI publisher only) |

### 4.4 Repository Structure

| Decision | Value |
|---|---|
| Structure | Separate GitHub repo per service |
| Reason | Team handoff — different teams will own different services |
| Repos | `cms-common`, `cms-gateway`, `cms-iam-service`, `cms-schema-service`, `cms-content-service`, `cms-workflow-service` |

### 4.5 Deployment

| Decision | Value |
|---|---|
| Target | Not yet decided — defer until something is ready to deploy |
| Cloud options discussed | AWS (ECR/EKS/MSK), GCP, Azure, NIC Cloud, on-premise |
| Current action | None — do not write any infra/deployment code yet |

---

## 5. Build Order (APPROVED)

Must be followed strictly — services depend on each other in this order:

```
Step 1: cms-common          → publish to GitHub Packages first
Step 2: cms-gateway         → depends on cms-common (parallel with iam)
Step 2: cms-iam-service     → depends on cms-common (parallel with gateway)
Step 3: cms-schema-service  → depends on common + auth contract from iam
Step 4: cms-workflow-service→ depends on common + auth contract from iam
Step 5: cms-content-service → depends on common + schema-service + workflow-service
```

---

## 6. Local Dev Port Allocation (LOCKED)

| Service | App Port | DB Host Port | DB Name / Dependency |
|---------|----------|-------------|---------|
| cms-gateway | 8080 | — | — |
| cms-iam-service | 8081 | 5433 | iamdb |
| cms-schema-service | 8082 | 5434 | schemadb |
| cms-content-service | 8083 | 5435 | contentdb |
| cms-workflow-service | 8084 | 5436 | workflowdb |
| cms-media-service | 8085 | 5438 | mediadb |
| cms-notification-service| 8086 | 5439 | notificationdb |
| cms-audit-service | 8087 | 5440 | auditdb |
| cms-search-service | 8088 | 9200 | OpenSearch (no PG DB) |
| Redis (shared) | — | 6379 | — |

---

## 7. cms-common Contents (APPROVED)

```
com.cms.common/
├── constants/    CmsHeaders.java, CmsRoles.java
├── dto/          ApiResponse.java, ErrorResponse.java, PagedResponse.java
├── exception/    CmsException.java, ErrorCode.java, GlobalExceptionHandler.java
├── security/     CmsJwtAuthenticationConverter.java, TenantAwareDataSource.java,
│                 TenantContext.java, TenantContextFilter.java
└── util/         UuidUtils.java (UUID v7 for time-ordered PKs)
```

---

## 8. Per-Service Package Template (ALL services follow this structure)

```
com/cms/{service}/
├── {Service}Application.java
├── config/
│   ├── SecurityConfig.java        ← JWT resource server setup
│   └── DataSourceConfig.java      ← TenantAwareDataSource bean
├── controller/
├── service/
├── repository/
├── domain/                        ← JPA entities
├── dto/
│   ├── request/
│   └── response/
└── exception/                     ← Service-specific exceptions (extend CmsException)

resources/
├── application.yml                ← Base config (no secrets, committed)
├── application-local.yml          ← Local overrides (GITIGNORED — never commit)
└── db/migration/                  ← Flyway: V1__, V2__, etc. (never edit committed migrations)
```

---

## 9. Phase 1 Definition of Done

### cms-common
- [ ] Published to GitHub Packages at `1.0.0`
- [ ] All consuming services resolve dependency via Maven

### cms-gateway
- [ ] Routes all requests to correct downstream service
- [ ] Rejects invalid/expired JWTs (401)
- [ ] Rejects `tenantStatus != ACTIVE` (403)
- [ ] Public routes pass without auth (`/auth/**`, `/public/**`, `/actuator/health`)

### cms-iam-service
- [ ] Login returns RS256 JWT + HttpOnly refresh token cookie
- [ ] Refresh token rotates on every use
- [ ] Logout revokes refresh token in DB
- [ ] SUPER_ADMIN + default tenant seeded via Flyway
- [ ] JWKS endpoint returns public key
- [ ] Force-revoke-all-tokens endpoint functional

### cms-schema-service
- [ ] SUPER_ADMIN can create a content type with field definitions
- [ ] Content type is tenant-scoped (PostgreSQL RLS verified)
- [ ] Supported field types: TEXT, RICHTEXT, NUMBER, DATE, BOOLEAN, REFERENCE

### cms-content-service
- [ ] Create content item against a valid content type
- [ ] Payload validated against field definitions
- [ ] Each save creates an immutable version snapshot
- [ ] Content is tenant-scoped (RLS verified)

### cms-workflow-service
- [ ] Define a workflow (DRAFT → REVIEW → PUBLISHED) via API
- [ ] Content moves through workflow states correctly
- [ ] Transition guards enforced via JWT permissions

---

## 10. Current Project Status

### Phase Progress

| Phase | Name | Status | Completion |
|---|---|---|---|
| **Phase 0** | Planning & Documentation | ✅ COMPLETE | 100% |
| **Phase 1** | Core Services Build | ✅ COMPLETE | 100% |
| **Phase 2** | Media, Search, Notifications, Audit | ✅ COMPLETE | 100% |
| **Phase 3** | Form Service, Admin UI | ✅ COMPLETE | 100% |
| **Phase 4** | Deployment, NFR tuning, team handoff | 🔲 NOT STARTED | 0% |

### Phase 1 — Service Build Status

| Service | Status | Notes |
|---|---|---|
| `cms-common` | ✅ DONE | All source files written. Install locally: `mvn install -DskipTests` in cms-common. |
| `cms-iam-service` | ✅ DONE | Phase 1 DoD complete. Login/refresh/logout/JWKS/force-revoke all tested via dev UI at :8081/dev/auth. |
| `cms-gateway` | ✅ DONE | Gateway routing, security configuration, tenant status gating, header forwarding, and dev UI fully tested and verified. |
| `cms-schema-service` | ✅ DONE | ContentType + FieldDefinition entities, REST controller APIs, Tenant isolation validated, and test UI console fully functional. |
| `cms-content-service` | ✅ DONE | Dynamic JSONB content CRUD, schema validation integration, version snapshooting, and dev test console verified. |
| `com.cms.workflow-service` | ✅ DONE | WorkflowDefinition + WorkflowInstance + WorkflowHistory entities, state machine transitions engine, and test UI console fully verified. |

### Phase 2 — Service Build Status

| Service | Status | Notes |
|---|---|---|
| `cms-media-service` | ✅ DONE | Multipart uploads of files saved to local uploads directory, public download route configured, JPA metadata map and tags list mapped to prevent database NOT NULL constraint violations. Dev UI console at :8085/dev/media. |
| `cms-search-service` | ✅ DONE | High-level OpenSearch Java Client integration, automatic dynamic index creation for tenant scopes, multi-match query DSL supporting wildcards (`body.*`). Dev UI console at :8088/dev/search. |
| `cms-notification-service` | ✅ DONE | Delivery templates registry, dispatch logs tracking. Resolved session timezone parameter discrepancy by forcing UTC in JDBC connection options. Dev UI console at :8086/dev/notification. |
| `cms-audit-service` | ✅ DONE | Centralized system events audit logs database, PostgreSQL INET support for IP address recording via custom ColumnTransformer write casts. Dev UI console at :8087/dev/audit. |

### Phase 3 — Service & UI Build Status

| Component | Status | Notes |
|---|---|---|
| `cms-form-service` | ✅ DONE | FormDefinition and FormSubmission entities, public endpoints via X-Tenant-Id header bypassing JWT, protected definitions endpoints. Includes a bug fix for NullPointerException in public submissions. |
| `cms-admin-ui` | ✅ DONE | Next.js + Tailwind UI. Premium dark mode design system (Inter font, brand gradients, glassmorphism). Fetch API wrapper handling HttpOnly refresh tokens. Full dashboard layout, schema, content, media, and form pages connected to gateway API. |

### Phase 0 — What Was Completed

| Deliverable | File | Status |
|---|---|---|
| Architecture analysis (7 anti-patterns identified) | `cms-architecture.md` | ✅ |
| DB-per-service schema design | `db-structure.md` | ✅ |
| Auth flow design (JWT, refresh, gateway, RLS) | `auth-flow.md` | ✅ FINALISED |
| Phase 1 implementation plan | `phase1-plan.md` | ✅ APPROVED |
| Open questions / deferred items tracker | `open-questions.md` | ✅ |
| AI SDLC continuity file | `ai_sdlc.md` (this file) | ✅ |
| File structure for all 6 repos defined | `phase1-plan.md § 5-6` | ✅ |

### Phase 1 — cms-common: Files Written

All source files created at `C:\Users\stadmin\Desktop\CMS_NEW\cms-common\`.

| File | Package | Purpose |
|---|---|---|
| `pom.xml` | — | Library POM, imports spring-boot-dependencies BOM, all deps optional |
| `.gitignore` | — | Standard Java ignores |
| `.github/workflows/publish.yml` | — | Publishes to GitHub Packages on version tag |
| `.github/workflows/build.yml` | — | CI compile check on every push/PR |
| `CmsHeaders.java` | `constants` | `X-Tenant-Id`, `X-User-Id`, `X-Tenant-Code` header name constants |
| `CmsRoles.java` | `constants` | Role name string constants matching iamdb values |
| `ApiResponse.java` | `dto` | Generic response envelope `{success, data, error, meta}` |
| `ErrorResponse.java` | `dto` | Error payload `{code, message, details[]}` |
| `PagedResponse.java` | `dto` | Paginated list wrapper `{items, page, size, total, totalPages}` |
| `ErrorCode.java` | `exception` | Enum: all system error codes with HTTP status |
| `CmsException.java` | `exception` | Base runtime exception (carries ErrorCode) |
| `GlobalExceptionHandler.java` | `exception` | `@RestControllerAdvice` maps CmsException + validation errors |
| `TenantContext.java` | `security` | ThreadLocal holder for current tenantId |
| `TenantAwareDataSource.java` | `security` | HikariCP wrapper — `SET LOCAL app.current_tenant_id` per transaction |
| `TenantContextFilter.java` | `security` | Reads `X-Tenant-Id` header → TenantContext (with finally clear()) |
| `CmsJwtAuthenticationConverter.java` | `security` | JWT `permissions[]` claim → Spring Security authorities |
| `UuidUtils.java` | `util` | UUID v7 generator (time-ordered PKs) |
| `CmsCommonAutoConfiguration.java` | `autoconfigure` | Spring Boot auto-config: registers TenantContextFilter + GlobalExceptionHandler |
| `AutoConfiguration.imports` | `resources/META-INF/spring` | Spring Boot 3.x auto-configuration registration |

### What's Next (First Task for Next Session)

**Step 1 — Before next session starts:** User must update `pom.xml` `distributionManagement` URL with the actual GitHub org name (replace `YOUR_GITHUB_ORG`). Same org name should go into consuming services' `pom.xml` repository URLs.

**Step 2 — Next build target: `cms-iam-service`**

Build order for next session:
1. User creates GitHub repos + clones locally (git commands provided in session)
2. User copies `cms-common` files into the cloned repo and does initial commit + tag `v1.0.0` to trigger publish
3. Agent scaffolds `cms-iam-service` — this is the largest service and the auth foundation
4. Agent scaffolds `cms-gateway` in parallel (simpler)

**cms-iam-service contains:**
- RSA key pair config (`JwtConfig.java`)
- Flyway migrations V1 (schema), V2 (SUPER_ADMIN seed), V3 (default tenant seed)
- Login/refresh/logout endpoints
- JWKS endpoint
- User + role management endpoints
- `docker-compose.yml` with PostgreSQL 16 (port 5433) + Redis (port 6379)

---

## 11. Known Risks & Flags for Incoming Agent

| Risk | Detail | Mitigation |
|---|---|---|
| **RLS cross-tenant leak** | If `TenantAwareDataSource` is not implemented with `SET LOCAL` (scoped to transaction), a pooled connection can carry a previous tenant's ID to the next request. Catastrophic data breach. | Mandatory: use `SET LOCAL` via PreparedStatement. See `auth-flow.md § 7.4` for authoritative implementation. |
| **cms-common breaking changes** | A major change to shared classes breaks all 5 consuming services simultaneously | Semantic versioning enforced. Breaking changes → major version bump → coordinated update across all services |
| **Service coupling** | `cms-content-service` calls `cms-schema-service` and `cms-workflow-service` synchronously via RestClient. If either is down, content operations fail. | Accepted for Phase 1. Circuit breakers and async patterns deferred to Phase 2. |
| **Stale DDL in cms-architecture.md** | `cms-architecture.md` contains SQL snippets referencing old `cms_*` schema prefixes. These are superseded. | Always use `db-structure.md` as the canonical DDL source. Never use DDL from `cms-architecture.md`. |
| **GitHub Packages auth** | Every developer needs a PAT in `~/.m2/settings.xml` to pull `cms-common`. Missing setup = build failure. | Onboarding guide must be written before team handoff (tracked in `open-questions.md § I4`) |

---

## 12. Questions the Next Agent Must NOT Assume Answers To

These are currently undecided — ask the user before proceeding:

1. **GitHub org/account name** — required before any repo or pom.xml is created
2. **Deployment target** — explicitly deferred, do not raise unless user brings it up
3. **Admin UI tech stack** — Next.js mentioned in architecture but not decided or scoped

---

## 13. Session Log

| Date | Session Summary | Docs Produced/Updated |
|---|---|---|
| 2026-06-26 | Full architecture review. Identified 5 critical gaps. Agreed on auth design (RS256, refresh rotation, RLS pattern). Decided: Phase 1 scope, build order, tech stack, repo strategy, shared library approach. All planning decisions locked. | `auth-flow.md` (created + finalised), `phase1-plan.md` (created), `open-questions.md` (created), `ai_sdlc.md` (created) |
| 2026-06-29 | Scaffolded and implemented all 4 Phase 2 services (Media, Search, Notification, Audit). Configured local Docker databases (PostgreSQL and OpenSearch) with timezone safety. Mapped custom PostgreSQL types like JSONB and INET in JPA. Created and verified dev console UIs. | `phase2-plan.md` (created), `walkthrough.md` (updated), `task.md` (updated), `ai_sdlc.md` (updated) |
| 2026-06-29 (Session 2) | Fixed NullPointerException in FormService by bypassing TenantContext for public form submissions and fetching tenant from HTTP headers. Built the cms-admin-ui with Next.js and Tailwind CSS matching the premium dark mode aesthetic (glassmorphism, Inter font, custom gradients). Connected the UI to the API Gateway handling JWT access and HttpOnly refresh tokens. | `implementation_plan.md` (created), `ai_sdlc.md` (updated) |

---

*This file is maintained by the AI agent. Update the Session Log and Phase Progress table at the end of every working session.*


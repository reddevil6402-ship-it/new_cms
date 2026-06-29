# Phase 1 Implementation Plan
## NextGen Enterprise CMS

> **Status:** APPROVED — ready for implementation  
> **Scope:** iam-service, schema-service, content-service, workflow-service + gateway + common library  
> **Stack:** Java 21 · Spring Boot 3.3.x · Maven · PostgreSQL 16 · Redis · Spring Cloud Gateway  
> **Last updated:** 2026-06-26

---

## 1. Repositories

Six separate GitHub repositories. Each is an independent deployable unit.

| Repo | Type | Description |
|------|------|-------------|
| `cms-common` | Maven library | Shared DTOs, exceptions, JWT utilities, RLS tenant context. Published to GitHub Packages. |
| `cms-gateway` | Spring Boot app | Spring Cloud Gateway. JWT validation, tenant status check, route forwarding. |
| `cms-iam-service` | Spring Boot app | Auth (login/refresh/logout), user management, RBAC, API keys. |
| `cms-schema-service` | Spring Boot app | Content type definitions, field definitions, validation rules. |
| `cms-content-service` | Spring Boot app | Content CRUD, versioning, workflow triggering. |
| `cms-workflow-service` | Spring Boot app | Workflow definitions, state machine transitions, content state management. |

---

## 2. Build Order

Dependencies flow top-to-bottom. Never build a service before its dependency is stable.

```
cms-common          (no dependencies)
     │
cms-gateway         (depends on: cms-common)
cms-iam-service     (depends on: cms-common)
     │
cms-schema-service  (depends on: cms-common, cms-iam-service for auth)
     │
cms-content-service (depends on: cms-common, cms-schema-service, cms-workflow-service)
     │
cms-workflow-service(depends on: cms-common)
```

> **Note:** cms-gateway and cms-iam-service can be built in parallel once cms-common is published.  
> cms-content-service should be started only after cms-schema-service and cms-workflow-service have stable APIs.

---

## 3. cms-common — What Goes In It

`cms-common` is a plain Maven library (no Spring Boot main class). Services declare it as a dependency.

### 3.1 Package Structure

```
com.cms.common/
├── dto/
│   ├── ApiResponse.java          ← Standard response envelope {success, data, error, meta}
│   ├── PagedResponse.java        ← Paginated response with {items, page, size, total}
│   └── ErrorResponse.java        ← {code, message, details[]}
├── exception/
│   ├── CmsException.java         ← Base runtime exception (carries ErrorCode + HTTP status)
│   ├── ErrorCode.java            ← Enum: TENANT_NOT_FOUND, INVALID_CREDENTIALS, etc.
│   └── GlobalExceptionHandler.java ← @RestControllerAdvice mapping CmsException → ApiResponse
├── constants/
│   ├── CmsHeaders.java           ← X-Tenant-Id, X-User-Id header name constants
│   └── CmsRoles.java             ← SUPER_ADMIN, TENANT_ADMIN, EDITOR, REVIEWER, VIEWER
├── security/
│   ├── TenantContext.java        ← ThreadLocal holder for current tenant ID
│   ├── TenantAwareDataSource.java← HikariCP wrapper: SET LOCAL app.current_tenant_id per txn
│   ├── TenantContextFilter.java  ← OncePerRequestFilter: extracts X-Tenant-Id → TenantContext
│   └── CmsJwtAuthenticationConverter.java ← Converts JWT claims → Spring Security authorities
└── util/
    └── UuidUtils.java            ← UUID v7 generation (time-ordered, good for PK performance)
```

### 3.2 What Does NOT Go in cms-common

- No service-specific DTOs (LoginRequest belongs in cms-iam-service, not common)
- No Spring Boot auto-configuration classes (keeps it a plain library)
- No database entities (JPA entities are per-service)
- No business logic

### 3.3 Versioning

Semantic versioning: `MAJOR.MINOR.PATCH`

- `1.0.0` — initial Phase 1 release
- Patch bumps for bug fixes; minor bumps for new shared utilities
- **Breaking changes require a major bump and coordinated update across all services**

---

## 4. GitHub Packages Setup

### 4.1 Publishing cms-common

`cms-common/pom.xml` distribution management:

```xml
<distributionManagement>
  <repository>
    <id>github</id>
    <name>GitHub Packages</name>
    <url>https://maven.pkg.github.com/YOUR_ORG/cms-common</url>
  </repository>
</distributionManagement>
```

Publish via GitHub Actions on every tag push to `main`.

### 4.2 Consuming Services

Each service `pom.xml` repository config:

```xml
<repositories>
  <repository>
    <id>github</id>
    <name>GitHub Packages</name>
    <url>https://maven.pkg.github.com/YOUR_ORG/cms-common</url>
  </repository>
</repositories>
```

### 4.3 Developer Setup (Required for every team member)

Add to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>GITHUB_USERNAME</username>
      <password>GITHUB_PERSONAL_ACCESS_TOKEN</password>
    </server>
  </servers>
</settings>
```

> **PAT scope required:** `read:packages` (for consumers), `write:packages` (for the publisher CI only).  
> See `open-questions.md § I4` — a full onboarding doc for the team needs to be written before handoff.

---

## 5. Per-Service Structure

Every Spring Boot service follows this exact layout (no exceptions without documented reason):

```
cms-{service-name}/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/cms/{service}/
│   │   │       ├── {Service}Application.java
│   │   │       ├── config/
│   │   │       │   ├── SecurityConfig.java       ← JWT resource server config
│   │   │       │   └── DataSourceConfig.java      ← TenantAwareDataSource bean
│   │   │       ├── controller/                    ← REST controllers (@RestController)
│   │   │       ├── service/                       ← Business logic (interfaces + impls)
│   │   │       ├── repository/                    ← Spring Data JPA repositories
│   │   │       ├── domain/                        ← JPA entities
│   │   │       ├── dto/
│   │   │       │   ├── request/                   ← Inbound request bodies
│   │   │       │   └── response/                  ← Outbound response bodies
│   │   │       └── exception/                     ← Service-specific exceptions (extend CmsException)
│   │   └── resources/
│   │       ├── application.yml                    ← Base config (no secrets)
│   │       ├── application-local.yml              ← Local overrides (DB URL, ports) — gitignored
│   │       └── db/
│   │           └── migration/                     ← Flyway scripts (V1__, V2__, etc.)
│   └── test/                                      ← (placeholder, not built in Phase 1)
├── .github/
│   └── workflows/
│       └── build.yml                              ← CI: compile + Flyway validate on PR
├── Dockerfile
├── docker-compose.yml                             ← Runs this service + its DB dependency only
├── .gitignore
├── pom.xml
└── README.md
```

---

## 6. Service-Specific Packages

### 6.1 cms-gateway

```
com/cms/gateway/
├── CmsGatewayApplication.java
├── config/
│   ├── GatewayRoutesConfig.java      ← Route definitions (all 5 services)
│   └── SecurityConfig.java           ← Public routes list, JWT filter config
├── filter/
│   ├── TenantStatusGatewayFilter.java← Checks tenantStatus == ACTIVE from JWT claim
│   └── RequestLoggingFilter.java     ← Logs method + path (masks Authorization header)
└── jwks/
    └── JwksKeyResolver.java          ← Fetches + caches public key from iam-service JWKS endpoint
```

### 6.2 cms-iam-service

```
com/cms/iam/
├── CmsIamApplication.java
├── config/
│   ├── SecurityConfig.java           ← Permits /auth/**, /internal/**, /actuator/health
│   ├── JwtConfig.java                ← Loads RSA key pair, exposes JwtEncoder + JwtDecoder beans
│   └── DataSourceConfig.java         ← TenantAwareDataSource (iam uses single system tenant)
├── controller/
│   ├── AuthController.java           ← /api/v1/auth/login, /refresh, /logout
│   ├── UserController.java           ← /api/v1/iam/users/**
│   └── JwksController.java           ← GET /api/v1/auth/.well-known/jwks.json
├── internal/
│   └── ApiKeyValidationController.java ← POST /internal/api-keys/validate (Gateway only)
├── service/
│   ├── AuthService.java              ← Login, refresh, logout orchestration
│   ├── TokenService.java             ← JWT sign, refresh token generate/hash/rotate
│   ├── UserService.java              ← User CRUD, role assignment
│   └── PermissionService.java        ← Resolves role → permission set for JWT embedding
├── repository/
│   ├── UserRepository.java
│   ├── TenantRepository.java
│   ├── RoleRepository.java
│   ├── RefreshTokenRepository.java
│   └── ApiKeyRepository.java
├── domain/
│   ├── User.java
│   ├── Tenant.java
│   ├── Role.java
│   ├── Permission.java
│   ├── RefreshToken.java
│   └── ApiKey.java
└── dto/
    ├── request/
    │   ├── LoginRequest.java
    │   └── RefreshRequest.java       ← (cookie-based, may not need a body)
    └── response/
        ├── LoginResponse.java        ← {accessToken, refreshToken, tokenType, expiresIn}
        └── UserResponse.java

db/migration/
├── V1__create_iam_schema.sql         ← All tables from db-structure.md § 4
├── V2__seed_super_admin.sql          ← Hashed SUPER_ADMIN user
└── V3__seed_default_tenant.sql       ← Default system tenant
```

### 6.3 cms-schema-service

```
com/cms/schema/
├── CmsSchemaApplication.java
├── config/
│   ├── SecurityConfig.java
│   └── DataSourceConfig.java
├── controller/
│   ├── ContentTypeController.java    ← /api/v1/schema/content-types/**
│   └── FieldDefinitionController.java← /api/v1/schema/content-types/{id}/fields/**
├── service/
│   ├── ContentTypeService.java
│   └── FieldDefinitionService.java
├── repository/
│   ├── ContentTypeRepository.java
│   └── FieldDefinitionRepository.java
├── domain/
│   ├── ContentType.java              ← name, slug, tenant_id, field_schema JSONB
│   └── FieldDefinition.java          ← field_name, field_type, is_required, validation_rules JSONB
└── dto/
    ├── request/
    │   ├── ContentTypeRequest.java
    │   └── FieldDefinitionRequest.java
    └── response/
        ├── ContentTypeResponse.java
        └── FieldDefinitionResponse.java

db/migration/
└── V1__create_schema_tables.sql      ← From db-structure.md § content_types, field_definitions
```

### 6.4 cms-content-service

```
com/cms/content/
├── CmsContentApplication.java
├── config/
│   ├── SecurityConfig.java
│   ├── DataSourceConfig.java
│   └── ServiceClientConfig.java      ← RestClient beans for schema-service + workflow-service
├── controller/
│   └── ContentController.java        ← /api/v1/content/{contentTypeSlug}/**
├── client/
│   ├── SchemaServiceClient.java      ← Fetches ContentType definition for validation
│   └── WorkflowServiceClient.java    ← Triggers workflow on publish
├── service/
│   ├── ContentService.java
│   └── ContentValidationService.java ← Validates payload against schema field definitions
├── repository/
│   ├── ContentItemRepository.java
│   └── ContentVersionRepository.java
├── domain/
│   ├── ContentItem.java              ← type_slug, tenant_id, data JSONB, status, workflow_id
│   └── ContentVersion.java           ← Immutable snapshot per save
└── dto/
    ├── request/
    │   └── ContentRequest.java
    └── response/
        ├── ContentResponse.java
        └── ContentListResponse.java

db/migration/
└── V1__create_content_tables.sql
```

### 6.5 cms-workflow-service

```
com/cms/workflow/
├── CmsWorkflowApplication.java
├── config/
│   ├── SecurityConfig.java
│   └── DataSourceConfig.java
├── controller/
│   ├── WorkflowDefinitionController.java ← /api/v1/workflow/definitions/**
│   └── WorkflowController.java           ← /api/v1/workflow/content/{contentId}/action
├── service/
│   ├── WorkflowDefinitionService.java
│   └── WorkflowExecutionService.java     ← State machine transitions
├── repository/
│   ├── WorkflowDefinitionRepository.java
│   └── ContentWorkflowRepository.java
├── domain/
│   ├── WorkflowDefinition.java           ← name, states JSONB, transitions JSONB
│   └── ContentWorkflow.java              ← content_id, current_state, history JSONB
└── dto/
    ├── request/
    │   ├── WorkflowDefinitionRequest.java
    │   └── WorkflowActionRequest.java    ← {action: "SUBMIT_FOR_REVIEW", comment: "..."}
    └── response/
        ├── WorkflowDefinitionResponse.java
        └── WorkflowStateResponse.java

db/migration/
└── V1__create_workflow_tables.sql
```

---

## 7. Local Dev Strategy

Since repos are separate, each has its own `docker-compose.yml` that brings up only its own DB dependency. To run all Phase 1 services together, a dedicated `cms-local-dev` repo (or a shared compose file) is needed.

### 7.1 Per-service docker-compose (example: cms-iam-service)

```yaml
services:
  iamdb:
    image: postgres:16
    environment:
      POSTGRES_DB: iamdb
      POSTGRES_USER: cms_iam
      POSTGRES_PASSWORD: localpassword
    ports:
      - "5433:5432"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

> Each service uses a different host port to avoid conflicts when running multiple services locally.

### 7.2 Port Allocation (local dev)

| Service | App Port | DB Port |
|---------|----------|---------|
| cms-gateway | 8080 | — |
| cms-iam-service | 8081 | 5433 |
| cms-schema-service | 8082 | 5434 |
| cms-content-service | 8083 | 5435 |
| cms-workflow-service | 8084 | 5436 |
| Redis (shared) | — | 6379 |

---

## 8. Definition of Done — Phase 1

Phase 1 is complete when all of the following are true:

### cms-common
- [ ] Published to GitHub Packages at version `1.0.0`
- [ ] All consuming services resolve it successfully via Maven

### cms-gateway
- [ ] Routes all requests to correct downstream service
- [ ] Rejects requests with invalid/expired JWTs (401)
- [ ] Rejects requests with `tenantStatus != ACTIVE` (403)
- [ ] Public routes (`/auth/**, /public/**`) pass through without auth

### cms-iam-service
- [ ] Login returns valid RS256 JWT + refresh token cookie
- [ ] Refresh token rotates correctly
- [ ] Logout revokes refresh token
- [ ] `SUPER_ADMIN` seed exists via Flyway migration
- [ ] JWKS endpoint returns public key
- [ ] Force-revoke-all-tokens endpoint works

### cms-schema-service
- [ ] SUPER_ADMIN can create a content type with field definitions
- [ ] Content type is tenant-scoped (RLS verified)
- [ ] Field definitions support: TEXT, RICHTEXT, NUMBER, DATE, BOOLEAN, REFERENCE field types

### cms-content-service
- [ ] Can create a content item against a valid content type
- [ ] Content item payload validated against field definitions
- [ ] Content versioning: each save creates an immutable version
- [ ] Content is tenant-scoped (RLS verified)

### cms-workflow-service
- [ ] Can define a workflow (DRAFT → REVIEW → PUBLISHED) via API
- [ ] Content item moves through workflow states correctly
- [ ] Transition guards (only REVIEWER can approve) enforced via permissions

---

*See `open-questions.md` for all deferred Phase 2 items.*

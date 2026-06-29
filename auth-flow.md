# Authentication & Authorization Flow
## NextGen Enterprise CMS — IAM Service Design

> **Document Type:** Auth Design Reference
> **Status:** DRAFT — Pending review and approval
> **Scope:** JWT issuance, refresh token lifecycle, API Gateway validation, downstream service trust model
> **See also:** `db-structure.md § 4` (iamdb schema), `cms-architecture.md § 9` (RBAC model)

---

## Table of Contents

1. [Overview & Design Principles](#1-overview--design-principles)
2. [JWT Structure](#2-jwt-structure)
3. [Login Flow](#3-login-flow)
4. [Token Refresh Flow](#4-token-refresh-flow)
5. [Logout & Token Revocation](#5-logout--token-revocation)
6. [API Gateway Validation](#6-api-gateway-validation)
7. [Downstream Service Trust Model](#7-downstream-service-trust-model)
8. [API Key Authentication (Machine-to-Machine)](#8-api-key-authentication-machine-to-machine)
9. [Security Constraints & Edge Cases](#9-security-constraints--edge-cases)
10. [Endpoints Reference](#10-endpoints-reference)
11. [Open Questions](#11-open-questions)

---

## 1. Overview & Design Principles

### 1.1 Who Issues Tokens

**iam-service is the single authority for token issuance.** No other service creates or signs JWTs. Every token carries a signature verifiable using iam-service's public key (asymmetric RS256).

### 1.2 Design Principles

- **Stateless access tokens** — downstream services validate the JWT signature locally using the public key. No round-trip to iam-service per request.
- **Stateful refresh tokens** — refresh tokens are stored in `iamdb.iam.refresh_tokens`. Revocation is immediate and DB-backed.
- **Short-lived access tokens** — access token TTL is **15 minutes**. This limits the blast radius if a token is stolen. Clients use refresh tokens to get new access tokens silently.
- **Tenant context travels in the JWT** — every token carries `tenantId` and `tenantStatus`. The API Gateway enforces tenant activity check from the token itself — no DB call per request.
- **Zero trust between services** — downstream services (content-service, schema-service, etc.) do not accept plain user IDs. They extract identity from the forwarded JWT only.

### 1.3 Token Types

| Token | TTL | Storage | Revocable |
|-------|-----|---------|-----------|
| Access Token (JWT) | 15 minutes | Client memory only (never localStorage) | No — short TTL is the mitigation |
| Refresh Token (opaque) | 7 days | `iam.refresh_tokens` (hashed) | Yes — immediate DB revocation |

---

## 2. JWT Structure

### 2.1 Algorithm

**RS256** (RSA + SHA-256, asymmetric). iam-service holds the private key for signing. All other services hold the public key for verification only.

> **Why RS256 over HS256?** HS256 requires every service to know the shared secret, which means every service could forge tokens. With RS256, only iam-service can issue tokens. Downstream services can verify but never create.

### 2.2 Header

```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "cms-key-v1"
}
```

`kid` (Key ID) allows key rotation without downtime — new tokens use `cms-key-v2`, old ones still validate against `cms-key-v1` during the rotation window.

### 2.3 Payload (Claims)

```json
{
  "iss": "cms-iam-service",
  "sub": "user-uuid",
  "iat": 1700000000,
  "exp": 1700000900,
  "jti": "unique-token-uuid",

  "tenantId":     "tenant-uuid",
  "tenantCode":   "acme-corp",
  "tenantStatus": "ACTIVE",

  "userId":       "user-uuid",
  "email":        "user@acme.com",
  "fullName":     "Dhrumil Shah",

  "roles": ["EDITOR", "REVIEWER"],

  "permissions": [
    "content:CREATE:OWN",
    "content:READ:ALL",
    "content:UPDATE:OWN",
    "content:PUBLISH:ALL",
    "schema:READ:ALL"
  ]
}
```

### 2.4 Claims Design Decisions

**Permissions embedded in token (not roles only):**
Downstream services use `permissions[]` directly for `@PreAuthorize` checks without calling iam-service. The resolved permission set is computed once at login and embedded.

**Trade-off acknowledged:** If a user's role is changed, their existing access token continues to carry the old permissions for up to 15 minutes. This is acceptable — the 15-minute TTL is the revocation window. For immediate revocation (e.g., termination, security incident), see § 5.

**`tenantStatus` in token:**
The API Gateway checks `tenantStatus == "ACTIVE"` from the JWT claim directly — no DB call. If a tenant is suspended, the next refresh token call returns an error and no new access token is issued. Existing access tokens expire naturally within 15 minutes.

**`jti` (JWT ID):**
Unique identifier per token. Used for logout/revocation tracking if needed in the future. Not actively checked on every request in Phase 1 (adds latency); can be added later with a Redis blocklist.

---

## 3. Login Flow

### 3.1 Sequence

```
Client (Admin UI)          API Gateway              iam-service              iamdb (Redis)
     │                         │                         │                       │
     │  POST /api/v1/auth/login│                         │                       │
     │  { email, password,     │                         │                       │
     │    tenantCode }         │                         │                       │
     ├────────────────────────►│                         │                       │
     │                         │  Forward (no auth       │                       │
     │                         │  check — public route)  │                       │
     │                         ├────────────────────────►│                       │
     │                         │                         │  SELECT user           │
     │                         │                         │  WHERE tenant+email    │
     │                         │                         ├──────────────────────►│
     │                         │                         │◄──────────────────────┤
     │                         │                         │  Verify bcrypt hash    │
     │                         │                         │  Check user.status     │
     │                         │                         │  Check tenant.status   │
     │                         │                         │  Load roles+perms      │
     │                         │                         │  (JOIN query)          │
     │                         │                         ├──────────────────────►│
     │                         │                         │◄──────────────────────┤
     │                         │                         │                       │
     │                         │                         │  Sign JWT (RS256)      │
     │                         │                         │  INSERT refresh_token  │
     │                         │                         │  (hashed, in iamdb)    │
     │                         │                         │                       │
     │                         │                         │  Cache permissions     │
     │                         │                         │  in Redis              │
     │                         │                         │  key: user:{id}:perms  │
     │                         │                         │  TTL: 15 min           │
     │                         │◄────────────────────────┤                       │
     │◄────────────────────────┤                         │                       │
     │  200 OK                 │                         │                       │
     │  {                      │                         │                       │
     │    accessToken: "...",  │                         │                       │
     │    refreshToken: "...", │                         │                       │
     │    expiresIn: 900       │                         │                       │
     │  }                      │                         │                       │
```

### 3.2 Login Request

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@acme.com",
  "password": "plaintextPassword",
  "tenantCode": "acme-corp"
}
```

`tenantCode` is required — the same email can exist across multiple tenants. This disambiguates.

### 3.3 Login Response

```json
{
  "success": true,
  "data": {
    "accessToken":  "eyJhbGc...",
    "refreshToken": "opaque-random-256-bit-string",
    "tokenType":    "Bearer",
    "expiresIn":    900
  }
}
```

**Refresh token handling on the client:** The refresh token must be stored in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie — not in JavaScript-accessible storage. The access token is stored in memory only (JS variable). This protects against XSS stealing the refresh token.

### 3.4 Login Failure Cases

| Condition | HTTP Status | Error Code |
|-----------|-------------|------------|
| Tenant not found | 401 | `TENANT_NOT_FOUND` |
| Tenant suspended/cancelled | 403 | `TENANT_INACTIVE` |
| User not found | 401 | `INVALID_CREDENTIALS` (do not distinguish — prevents user enumeration) |
| Wrong password | 401 | `INVALID_CREDENTIALS` |
| User locked (`locked_until` in future) | 423 | `ACCOUNT_LOCKED` |
| User inactive/pending | 403 | `ACCOUNT_INACTIVE` |

### 3.5 Brute Force Protection

On each failed login attempt for a valid user:
- Increment `users.failed_attempts`
- At 5 failed attempts: set `users.locked_until = NOW() + INTERVAL '15 minutes'`
- At 10 cumulative failed attempts: set `users.status = 'LOCKED'` (requires admin unlock)
- Reset `failed_attempts` to 0 on successful login

---

## 4. Token Refresh Flow

### 4.1 Sequence

```
Client                  API Gateway             iam-service             iamdb / Redis
  │                          │                       │                       │
  │  POST /api/v1/auth/refresh                       │                       │
  │  Cookie: refreshToken=..│                        │                       │
  ├─────────────────────────►│                       │                       │
  │                          │  Forward              │                       │
  │                          ├──────────────────────►│                       │
  │                          │                       │  Hash incoming token   │
  │                          │                       │  SELECT from           │
  │                          │                       │  refresh_tokens        │
  │                          │                       │  WHERE token_hash=...  │
  │                          │                       ├──────────────────────►│
  │                          │                       │◄──────────────────────┤
  │                          │                       │  Check:                │
  │                          │                       │  - not revoked         │
  │                          │                       │  - not expired         │
  │                          │                       │  - user.status=ACTIVE  │
  │                          │                       │  - tenant.status=ACTIVE│
  │                          │                       │                        │
  │                          │                       │  Issue new access token │
  │                          │                       │  Rotate refresh token  │
  │                          │                       │  (revoke old, insert   │
  │                          │                       │   new — one-time use)  │
  │                          │◄──────────────────────┤                       │
  │◄─────────────────────────┤                       │                       │
  │  200 OK                  │                       │                       │
  │  { accessToken: "...",   │                       │                       │
  │    refreshToken: "..." } │                       │                       │
```

### 4.2 Refresh Token Rotation

Every refresh returns a **new refresh token** and revokes the old one (token rotation). This means a stolen refresh token can only be used once — if the attacker uses it, the legitimate user's next refresh will fail (old token is already revoked), triggering a forced re-login.

### 4.3 Refresh Token Storage in DB

The raw refresh token is **never stored**. Only `SHA-256(rawToken)` is stored in `refresh_tokens.token_hash`. The raw token is sent to the client and never retrievable from the DB.

---

## 5. Logout & Token Revocation

### 5.1 Standard Logout

```http
POST /api/v1/auth/logout
Authorization: Bearer {accessToken}
Cookie: refreshToken=...
```

iam-service:
1. Sets `refresh_tokens.revoked_at = NOW()` for the token matching the cookie.
2. Returns 204 No Content.
3. Client clears the access token from memory and the refresh token cookie.

The access token itself is NOT blocklisted (no Redis lookup per request). It expires naturally in ≤15 minutes. For Phase 1, this is acceptable.

### 5.2 Force Revocation (Admin Use)

For cases like account compromise or role removal where you cannot wait 15 minutes:

```http
POST /api/v1/iam/users/{userId}/revoke-all-tokens
Authorization: Bearer {adminToken}
```

This revokes all active refresh tokens for the user in `iamdb`. The user's next access token use will succeed for up to 15 min, but any refresh attempt will fail, forcing re-login.

> **⚠️ Known limitation:** A stolen access token remains valid for up to 15 minutes after revocation. This is a deliberate trade-off between statelessness and immediate revocation. If your use case requires instant revocation, a Redis-backed JWT blocklist using `jti` must be added (Phase 2 enhancement).

### 5.3 Tenant Suspension

When a tenant is suspended (admin action in iamdb):
- All new logins fail immediately (`TENANT_INACTIVE`).
- All refresh token calls fail immediately (tenant status check at refresh time).
- Existing access tokens expire within 15 minutes.

---

## 6. API Gateway Validation

> **Note:** The architecture references Kong/Nginx as the API Gateway. The exact gateway tool is not yet decided. This section documents the **validation logic** that must be implemented regardless of which tool is chosen.

### 6.1 Validation Steps (every protected request)

```
Incoming Request
      │
      ▼
1. Extract Bearer token from Authorization header
      │
      ▼
2. Verify JWT signature using iam-service public key (RS256)
   → Fail: 401 INVALID_TOKEN
      │
      ▼
3. Check token expiry (exp claim)
   → Fail: 401 TOKEN_EXPIRED
      │
      ▼
4. Check tenantStatus claim == "ACTIVE"
   → Fail: 403 TENANT_INACTIVE
      │
      ▼
5. Forward request to downstream service with:
   - Original Authorization: Bearer header (JWT passed through)
   - X-Tenant-Id: {tenantId from JWT}
   - X-User-Id:   {userId from JWT}
      │
      ▼
Downstream Service
```

### 6.2 Public Routes (No Auth Check)

The following routes bypass JWT validation at the Gateway:

```
POST /api/v1/auth/login
POST /api/v1/auth/refresh
GET  /api/v1/public/**        ← Public-facing content reads
GET  /actuator/health
GET  /actuator/health/readiness
```

All other routes require a valid JWT.

### 6.3 Public Key Distribution

iam-service exposes its public key at:

```
GET /api/v1/auth/.well-known/jwks.json
```

Response (JWKS format):
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "kid": "cms-key-v1",
      "alg": "RS256",
      "n":   "...",
      "e":   "AQAB"
    }
  ]
}
```

The API Gateway fetches this once on startup and caches it. Key rotation is announced by adding a new key with a new `kid` while keeping the old one active during the rotation window (e.g., 24 hours).

---

## 7. Downstream Service Trust Model

### 7.1 What Downstream Services Do

Downstream services (content-service, schema-service, workflow-service) **do not call iam-service per request**. They:

1. Validate the JWT signature locally using the cached RS256 public key.
2. Extract claims: `userId`, `tenantId`, `roles`, `permissions`.
3. Set the PostgreSQL session variable for RLS: `SET app.current_tenant_id = '{tenantId}'`.
4. Use `@PreAuthorize` annotations to check permissions from the token.

### 7.2 Spring Security Configuration (per downstream service)

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(cmsJwtConverter()))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // Loads public key from iam-service JWKS endpoint
        return NimbusJwtDecoder.withJwkSetUri("http://iam-service/api/v1/auth/.well-known/jwks.json")
            .build();
    }

    @Bean
    public CmsJwtAuthenticationConverter cmsJwtConverter() {
        return new CmsJwtAuthenticationConverter(); // extracts permissions[] from claims
    }
}
```

### 7.3 Permission Check Example

```java
@RestController
@RequestMapping("/api/v1/content")
public class ContentRestController {

    @PostMapping("/{type}")
    @PreAuthorize("hasAuthority('content:CREATE:OWN') or hasAuthority('content:CREATE:ALL')")
    public ResponseEntity<ContentResponse> createContent(...) { ... }

    @PostMapping("/{type}/{id}/workflow")
    @PreAuthorize("hasAuthority('content:PUBLISH:ALL')")
    public ResponseEntity<?> triggerWorkflow(...) { ... }
}
```

### 7.4 RLS Session Variable Injection

**Decided implementation: HikariCP `DataSource` wrapper using `SET LOCAL`.**

`SET LOCAL` scopes the variable to the current transaction only. When the connection is returned to the pool, the variable is automatically cleared — no cross-tenant leakage is possible.

#### TenantAwareDataSource (in `cms-common`)

```java
public class TenantAwareDataSource extends DelegatingDataSource {

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = super.getConnection();
        applyTenantContext(conn);
        return conn;
    }

    private void applyTenantContext(Connection conn) throws SQLException {
        String tenantId = TenantContext.getCurrentTenantId(); // ThreadLocal
        if (tenantId != null) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SET LOCAL app.current_tenant_id = ?")) {
                ps.setString(1, tenantId);
                ps.execute();
            }
        }
    }
}
```

#### TenantContext (ThreadLocal holder, in `cms-common`)

```java
public final class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    public static void setCurrentTenantId(String tenantId) { CURRENT_TENANT.set(tenantId); }
    public static String getCurrentTenantId() { return CURRENT_TENANT.get(); }
    public static void clear() { CURRENT_TENANT.remove(); }
}
```

#### TenantContextFilter (per downstream service, sets ThreadLocal from JWT header)

```java
@Component
@Order(1)
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String tenantId = request.getHeader("X-Tenant-Id"); // forwarded by Gateway
            if (tenantId != null) {
                TenantContext.setCurrentTenantId(tenantId);
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear(); // always clear — prevents ThreadLocal leakage in pool threads
        }
    }
}
```

> **Why `SET LOCAL` over `SET`:** `SET` persists for the entire connection session — if the connection is reused from the pool, the previous tenant's ID is still set. `SET LOCAL` is transaction-scoped and resets automatically on `COMMIT` or `ROLLBACK`, making it pool-safe by design.

---

## 8. API Key Authentication (Machine-to-Machine)

For external systems (e.g., a government portal consuming the CMS API programmatically), API keys stored in `iamdb.iam.api_keys` are supported.

### 8.1 Request Format

```http
GET /api/v1/content/tender_notice
X-API-Key: cms_live_<prefix>_<random256bits>
```

### 8.2 Gateway Validation for API Keys

The Gateway checks the `X-API-Key` header when no `Authorization: Bearer` header is present:

1. Parse the key prefix.
2. Compute `SHA-256(rawKey)`.
3. Call iam-service: `POST /internal/api-keys/validate` with the hash.
4. iam-service returns the `tenantId`, `scopes[]`, and rate limit config.
5. Gateway enforces rate limit and forwards the request with `X-Tenant-Id` header.

> **Note:** API key validation requires a synchronous call to iam-service on every request (unlike JWT which is validated locally). This is a known performance trade-off. API key responses should be cached in Redis at the Gateway level with a short TTL (e.g., 60 seconds).

### 8.3 API Key Scope Model

API keys carry scopes (not full permissions). Scopes are coarser:

```
cms:content:read
cms:content:write
cms:schema:read
cms:admin
```

---

## 9. Security Constraints & Edge Cases

### 9.1 Password Requirements

- Minimum 8 characters
- At least one uppercase, one digit, one special character
- Stored as bcrypt with cost factor 12 minimum
- Password history is **not** tracked in Phase 1 (can be added later)

### 9.2 Token Leakage via Logs

**Access tokens must never be logged.** The Authorization header must be masked in all request logs at the Gateway and service level. Refresh tokens must never appear in logs at all.

### 9.3 Cross-Tenant Access

A JWT issued for `tenantId = A` must never be usable to access `tenantId = B` data. This is enforced at two levels:
1. PostgreSQL RLS policy on every content table (`USING (tenant_id = current_setting('app.current_tenant_id')::UUID)`).
2. Application-level check in every service before any write operation.

### 9.4 Clock Skew

JWT expiry validation allows a **30-second clock skew** between issuer and validator. This is the Spring Security default and is acceptable.

### 9.5 What Happens If iam-service Is Down

- **Login/refresh:** Fails — users cannot authenticate. This is unavoidable.
- **Existing access tokens:** Continue to work for their remaining TTL (up to 15 min) since validation is local.
- **Services:** Continue to serve requests with valid tokens. No dependency on iam-service per request.

This is the primary availability benefit of short-lived stateless JWTs.

---

## 10. Endpoints Reference

All auth endpoints are served by **iam-service** and proxied through the API Gateway.

| Method | Path | Auth Required | Description |
|--------|------|--------------|-------------|
| POST | `/api/v1/auth/login` | No | Issue access + refresh token |
| POST | `/api/v1/auth/refresh` | No (cookie) | Rotate tokens |
| POST | `/api/v1/auth/logout` | Bearer | Revoke refresh token |
| GET | `/api/v1/auth/.well-known/jwks.json` | No | Public key for JWT verification |
| POST | `/api/v1/iam/users/{id}/revoke-all-tokens` | Bearer (ADMIN) | Force revoke all sessions |
| GET | `/api/v1/iam/users/me` | Bearer | Get current user profile |
| POST | `/api/v1/iam/users` | Bearer (ADMIN) | Create user |
| PUT | `/api/v1/iam/users/{id}/roles` | Bearer (ADMIN) | Assign roles |
| POST | `/internal/api-keys/validate` | Internal only | API key validation (Gateway → iam-service) |

---

## 11. Decisions Log

All questions resolved. Recorded here for traceability.

| # | Question | Decision | Rationale |
|---|----------|----------|-----------|
| 1 | **Gateway tool** | **Spring Cloud Gateway** | Pure Spring Boot app — no separate infra to manage locally. Native JWT support. Simplest for solo Phase 1 build. |
| 2 | **Key rotation cadence** | **Deferred to Phase 2** | Operational concern. Phase 1 uses a single key pair. Manual rotation procedure will be documented before team handoff. |
| 3 | **`jti` blocklist** | **Deferred to Phase 2** | 15-min TTL is the mitigation in Phase 1. Redis blocklist adds per-request latency. Added to `open-questions.md` for Phase 2 tracking. |
| 4 | **Multi-device sessions** | **Multiple sessions allowed** | At login, `INSERT` a new refresh token row — do not revoke existing tokens. `revoke-all-tokens` endpoint covers security incidents. |
| 5 | **Admin bootstrap** | **Flyway migration** | `V2__seed_super_admin.sql` inserts hashed SUPER_ADMIN password + `V3__seed_default_tenant.sql`. Required before Day 1 of iam-service can be run. |

---

*Document status: **FINALISED**. Decisions locked. See `phase1-plan.md` for implementation sequencing and `open-questions.md` for Phase 2 deferred items.*

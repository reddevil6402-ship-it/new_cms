# Open Questions & Deferred Decisions
## NextGen Enterprise CMS

> **Purpose:** Living document. Every deferred decision, unresolved question, and Phase 2 item is tracked here.  
> **Process:** When a question is resolved, move it to the relevant finalized document with its decision. Delete it from here.  
> **Last updated:** 2026-06-26

---

## Phase 2 Deferrals (auth-flow.md)

| # | Item | Context | Owner |
|---|------|---------|-------|
| A1 | **RS256 key rotation cadence** | How often to rotate the signing key pair. Phase 1 uses a single key. Procedure must be documented before team handoff. See `auth-flow.md § 2.2` for `kid` design. | — |
| A2 | **`jti` Redis blocklist** | Immediate access token revocation (< 15 min) requires a Redis blocklist on `jti` per request. Adds latency. Phase 1 uses TTL as the mitigation. | — |

---

## Infrastructure & Deployment (deferred)

| # | Item | Context |
|---|------|---------|
| I1 | **Deployment target** | On-premise, NIC cloud, AWS, Azure, GCP — not yet decided. Architecture supports all; infra manifests will be written once decided. |
| I2 | **NFRs — concurrent user load** | Not yet defined. Architecture scales to any load, but tuning (connection pool sizes, Kafka partition counts, OpenSearch shard counts) requires a target. Revisit before production sizing. |
| I3 | **Bursty traffic strategy** | Government CMS patterns (e.g., tender deadline day) can produce extreme load spikes. Rate limiting config, auto-scaling policies, and circuit breaker thresholds need an NFR baseline to configure. |
| I4 | **GitHub Packages auth onboarding doc** | Every team member needs `~/.m2/settings.xml` configured with a GitHub PAT to pull `cms-common`. Needs a one-page onboarding guide before team handoff. |

---

## Phase 2 Services (out of Phase 1 scope)

| Service | Blocked On |
|---------|-----------|
| `cms-media-service` | Phase 1 content-service must be stable first |
| `cms-search-service` (OpenSearch) | Content data must exist to index |
| `cms-notification-service` | Kafka infrastructure, Phase 1 workflows |
| `cms-audit-service` | All other services must emit events first |
| `cms-form-service` | Relatively independent, but low priority vs core CMS |

---

## Design Questions (not yet raised)

| # | Item | Why It Matters |
|---|------|---------------|
| D1 | **Admin UI framework** | The architecture mentions a Next.js admin UI. Tech stack, repo, and integration pattern (calls Gateway directly?) not decided. Not a Phase 1 blocker — services can be tested via API client (Postman/curl) initially. |
| D2 | **Public content delivery** | `GET /api/v1/public/**` is mentioned as a bypass route. Caching strategy (CDN? Redis?) for public reads not designed. |
| D3 | **Workflow designer UI** | How admins will configure workflow state machines (currently JSONB configs). Visual designer or JSON editor? Not a Phase 1 blocker. |
| D4 | **Tenant provisioning flow** | How new tenants are onboarded end-to-end. Currently only the seed SUPER_ADMIN + default tenant exist. Multi-tenant provisioning UI/API not designed. |

---

*Update this doc whenever a decision is deferred, or when a deferred item is resolved (move it to its home doc).*

# Phase 3 Implementation Plan
## NextGen Enterprise CMS

> **Status:** PLANNING
> **Scope:** form-service, admin-ui
> **Stack:** Java 21 · Spring Boot 3.3.x · Maven · PostgreSQL 16 · Next.js 14 (App Router)
> **Last updated:** 2026-06-29

---

## 1. Repositories

Two separate repositories to be built in this phase.

| Repo | Type | Description |
|------|------|-------------|
| `cms-form-service` | Spring Boot app | Dynamic forms definition and submission handling. |
| `cms-admin-ui` | Next.js 14 app | The main React frontend for CMS administration, schema design, and content authoring. |

---

## 2. Build Order

Dependencies flow top-to-bottom.

```text
cms-common          (Complete)
      │
cms-form-service    (depends on: cms-common)
      │
cms-admin-ui        (depends on: ALL phase 1 & 2 backend services + form-service)
```

---

## 3. Local Dev Strategy & Port Allocation

| Service | App Port | DB Port / Dependency |
|---------|----------|----------------------|
| cms-form-service | 8089 | 5441 (Postgres `formdb`) |
| cms-admin-ui | 3000 | Node.js (Next.js) |

---

## 4. Architecture & Packages

### 4.1 cms-form-service

```text
com/cms/form/
├── CmsFormApplication.java
├── config/
│   ├── SecurityConfig.java
│   └── DataSourceConfig.java
├── controller/
│   ├── FormDefinitionController.java   ← /api/v1/forms/definitions
│   └── FormSubmissionController.java   ← /api/v1/forms/{code}/submit
├── service/
│   ├── FormDefinitionService.java
│   └── FormSubmissionService.java      ← JSON Schema validation logic
├── repository/
│   ├── FormDefinitionRepository.java
│   └── FormSubmissionRepository.java
├── domain/
│   ├── FormDefinition.java             ← form_definitions table
│   └── FormSubmission.java             ← form_submissions table
└── dto/
    └── request/
        └── FormSubmitRequest.java

db/migration/
└── V1__create_form_tables.sql
```

### 4.2 cms-admin-ui (Next.js App Router)

```text
cms-admin-ui/
├── src/
│   ├── app/
│   │   ├── (auth)/
│   │   │   └── login/page.tsx
│   │   ├── dashboard/
│   │   │   ├── content/
│   │   │   ├── schema/
│   │   │   ├── media/
│   │   │   └── settings/
│   │   ├── layout.tsx
│   │   └── page.tsx
│   ├── components/
│   │   ├── ui/             ← Reusable UI tokens (buttons, cards, inputs)
│   │   └── layout/         ← Sidebar, Topbar
│   ├── lib/
│   │   ├── api.ts          ← Axios/Fetch interceptors handling JWT injection
│   │   └── auth.ts         ← Auth session / Client storage wrappers
│   └── types/
│       └── cms.d.ts        ← TypeScript interfaces matching backend DTOs
├── public/
├── package.json
└── next.config.mjs
```

---

## 5. Next.js 14 Admin UI Design

### 5.1 Tech Stack
- **Framework:** Next.js 14 (App Router)
- **Language:** TypeScript
- **Styling:** Vanilla CSS (prioritized for custom premium aesthetics, no Tailwind CSS by default)
- **State Management:** React Context + Hooks
- **Data Fetching:** Native `fetch` mapped to backend API Gateway (`http://localhost:8080`)

### 5.2 Aesthetics & UX
- **Rich Aesthetics**: The Admin UI will feature modern web design principles—glassmorphism, vibrant but curated HSL color palettes, sleek dark modes, and dynamic micro-animations.
- **Premium UX**: Hover states, smooth gradients, and interactive layouts. It must feel like a state-of-the-art SaaS platform, not a simple minimum viable product.

---

## 6. Definition of Done — Phase 3

Phase 3 is complete when all of the following are true:

### cms-form-service
- [ ] Users can define a dynamic form with a JSON schema.
- [ ] Users can submit data to an active form definition.
- [ ] Submissions are validated against the defined JSON schema.

### cms-admin-ui
- [ ] Next.js 14 App Router project is scaffolded using `npx create-next-app`.
- [ ] Implements a Login screen that authenticates against `cms-iam-service`.
- [ ] Builds a beautiful, premium Dashboard shell (sidebar, header).
- [ ] Implements the Schema Builder UI (CRUD for Content Types and Field Definitions).
- [ ] Implements the Content Authoring UI (dynamic forms built dynamically based on Field Definitions).
- [ ] Implements the Media Library UI (uploading and selecting files).

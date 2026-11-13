# Architecture

## Overview

```
Browser ── /            ──▶ Angular app (nginx in production, ng serve in dev)
        └─ /api/*       ──▶ Quarkus backend ──▶ PostgreSQL
                                   └──────────▶ slicer service (optional, OrcaSlicer, SLICER_URL)
```

The browser only ever talks to one origin: `/api` is proxied to the backend (`proxy.conf.json` in dev, nginx `BACKEND_URL` in the frontend image). That keeps the refresh-token cookie first-party and removes the need for CORS in normal use.

## Backend (`PrintBridge-Backend`)

Packages under `com.beauver.printbridge`, one per domain:

| Package | Contents |
|---|---|
| `about` | Public `/about`: licence and source link for the footer |
| `auth` | Setup, login/refresh/logout, JWT and refresh tokens, `CurrentUser`, stale-token filter |
| `user` | `User` entity, user management, password hashing |
| `printrequest` | Print requests and their model files: entities, upload validation, access rules |
| `modelinfo` | Model analysis of uploaded files (`analysis`: STL/3MF readers, also the 3D preview mesh), stored results, startup backfill |
| `preview` | 3D preview: mesh encoding, preview before upload, plate images |
| `settings` | Admin-editable application settings (owned printers with their build plate, server slicing) |
| `slicing` | Server slicing: job queue, planner (which printer/nozzle/profiles), background worker, client for the slicer service |
| `storage` | `FileStorage`: files on disk under `STORAGE_PATH` |
| `common.error` | Domain exceptions and their HTTP mapping |
| `common.transaction` | `AfterTransaction`: run disk side effects only after commit/rollback |

Layering:
- **Resource** (`*Resource`): HTTP only. Paths, status codes, `@RolesAllowed`/`@PermitAll`, Bean Validation on DTOs. No business logic.
- **Service** (`*Service`): business rules, ownership checks, transactions. Throws domain exceptions (`InvalidInputException` → 400, `ConflictException` → 409, `ResourceNotFoundException` → 404, `InvalidCredentialsException` → 401), mapped in `ExceptionMappers`.
- **Repository** (`*Repository`, Panache repository pattern): queries.
- Entities never leave the backend: resources return DTO records built by mappers.
- Services that check ownership take a `Caller` (`id`, `admin`) from `CurrentUser.caller()` instead of reading the JWT themselves.

Conventions:
- All endpoints live under `/api` (`quarkus.rest.path`).
- Unannotated endpoints are denied (`quarkus.security.jaxrs.deny-unannotated-endpoints`); every resource states its access rule.
- Schema changes only via Flyway (`src/main/resources/db/migration`). Hibernate validates the mapping against it in tests and never changes it.
- Config via env vars, see `application.properties` and `/.env.example`.

## Slicer service (`PrintBridge-Slicer`)

Optional. OrcaSlicer's command line behind a small Python HTTP API (standard library only), in its own Docker image. The backend calls it over HTTP from a single background worker; see [server slicing](features/server-slicing.md) and the service's README.

## Frontend (`PrintBridge-Frontend`)

| Folder | Contents |
|---|---|
| `core/` | App-wide services: auth (service, interceptor, guards), setup status, about (licence/source), theme, file saving, models, HTTP error helper |
| `features/<name>/` | One folder per page/feature: page component (`.ts` + `.html`) and its API service |
| `layout/` | Navbar, footer (licence and source links), theme toggle |
| `shared/` | Reusable form and display helpers (status labels/badges, file sizes, prices, model sizes/weights/times); `model-viewer/`: the Three.js 3D preview, loaded on demand |

Layering: template → component → service → backend API. Components never use `HttpClient` directly.

Angular style: standalone components, signals for state, `inject()`, lazy-loaded routes, reactive forms. UI is Bootstrap 5 (CSS only, no Bootstrap JS); dark theme by default with a floating toggle bottom-right (`ThemeService`, `layout/theme-toggle`). Interactive elements carry `data-cy` attributes for future Cypress tests.

## Testing

- Backend: Mockito unit tests for services, `@QuarkusTest` + RestAssured for resources against a throwaway Postgres (Dev Services, needs Docker). Coverage: `./gradlew test` → `build/reports/jacoco/test/html`.
- Frontend: Vitest via `ng test`, testing component and service logic rather than rendered HTML; coverage with `ng test --no-watch --coverage` → `coverage/` (templates excluded).

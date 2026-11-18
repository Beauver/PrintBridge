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
| `license` | Public `/license`: licence and source link for the footer |
| `auth` | Login/refresh/logout, `CurrentUser`. `token`: JWT and refresh tokens, refresh cookie, stale-token filter. `setup`: first-run setup |
| `user` | `User` entity, user management, password hashing |
| `printrequest` | Print requests: entity, status flow, upload rules, access rules. `file`: the model files of a request |
| `modelfile` | Reading model files. The package itself is what others use: file types and validation, `ModelAnalyzer`, `PreviewMeshReader`, read limits, `StoredModelFiles` (find a stored file by id). `analysis`: what an analysis returns. `stl`, `threemf`: the readers per format (Bambu Studio / OrcaSlicer projects are in `threemf`). `geometry`: bounds, transforms, mesh building. `mesh`: the preview mesh and its placement on the bed |
| `modelinfo` | What is known about an uploaded file: stored analysis results, startup backfill |
| `preview` | 3D preview: mesh encoding, preview before upload, mesh and plate images of request files |
| `settings` | Admin-editable application settings (server slicing, upload limits). `printer`: the owned printers with their build plate |
| `slicing` | Server slicing: planner (which printer/nozzle/profiles), background worker. `result`: stored results, which are also the job queue. `client`: client for the slicer service |
| `storage` | `FileStorage`: files on disk under `STORAGE_PATH` |
| `common.error` | Domain exceptions and their HTTP mapping |
| `common.ratelimit` | `@RateLimit` for REST endpoints and the in-memory `RateLimiter` behind it |
| `common.transaction` | `AfterTransaction`: run disk side effects only after commit/rollback. `Transactions`: run one step of a longer flow in its own short transaction |

Layering:
- **Resource** (`*Resource`): HTTP only. Paths, status codes, `@RolesAllowed`/`@PermitAll`, Bean Validation on DTOs. No business logic.
- **Service** (`*Service`): business rules, ownership checks, transactions. Slow work (copying an upload, reading a model, calling the slicer) stays outside transactions: such a method is `NOT_SUPPORTED` and wraps its database steps in `Transactions`. Throws domain exceptions (`InvalidInputException` → 400, `ConflictException` → 409, `ResourceNotFoundException` → 404, `InvalidCredentialsException` → 401, `TooManyRequestsException` → 429), mapped in `ExceptionMappers`.
- **Repository** (`*Repository`, Panache repository pattern): queries.
- Entities never leave the backend: resources return DTO records built by mappers.
- **Mapper** (`*Mapper`): only copies fields into DTOs. It injects no service or repository; what isn't on the entity is loaded or worked out by the service and handed in (`ModelDetailsCalculator` for model details, `RequestTotals` for a request's totals).
- A resource never calls a client of another system directly: `SlicerResource` → `SlicerService` → `SlicerClient`, and the API has its own DTOs, so the slicer's format can change on its own.
- Services that check ownership take a `Caller` (`id`, `admin`) from `CurrentUser.caller()` instead of reading the JWT themselves.
- A package that grows past about ten files gets sub-packages by what the code is about (`token`, `printer`, `threemf`), not by layer: the class names already say resource, service or repository.
- `dto` sub-packages hold only what crosses HTTP (JSON records, multipart forms). Values used between services sit in the package itself.

Dependencies between packages go one way:

```
common, storage ◀─ modelfile ◀─ settings ◀─ modelinfo, slicing ◀─ printrequest ◀─ preview
```

- `auth` and `user` belong together and use each other; everything may use them.
- `modelinfo` and `slicing` work on files by id and find them through `modelfile.StoredModelFiles`, which `printrequest` implements (`RequestFileLookup`). Links (#59) live in the same table but are never found there, so they are never analysed or sliced.
- Deleting a user fires `UserDeleted`; `printrequest` listens and removes the user's files, so `user` doesn't know about print requests.

Conventions:
- All endpoints live under `/api` (`quarkus.rest.path`).
- Unannotated endpoints are denied (`quarkus.security.jaxrs.deny-unannotated-endpoints`); every resource states its access rule.
- Rate limits: put `@RateLimit(limit = 10, windowSeconds = 60)` on a resource method (or class). `per = IP` (default) counts by client address, `per = USER` by logged-in user. Over the limit the endpoint answers `429` with `Retry-After` before it runs. Limits that depend on an outcome (failed logins) use `RateLimiter` from the service instead.
- Schema changes only via Flyway (`src/main/resources/db/migration`). Hibernate validates the mapping against it in tests and never changes it.
- Config via env vars, see `application.properties` and `PrintBridge-Backend/.env.example` (running from source) or `deploy/.env.example` (running the images, see [deployment](deployment.md)).

## Slicer service (`PrintBridge-Slicer`)

Optional. OrcaSlicer's command line behind a small Python HTTP API (standard library only), in its own Docker image. The backend calls it over HTTP from a single background worker; see [server slicing](features/server-slicing.md) and the service's README.

## Frontend (`PrintBridge-Frontend`)

| Folder | Contents |
|---|---|
| `core/` | App-wide services: auth (service, interceptor, guards), setup status, licence and source link, theme, file saving, models, HTTP error helper |
| `features/<name>/` | One folder per page/feature: page component (`.ts` + `.html`) and its API service |
| `layout/` | Navbar, footer (licence and source links), theme toggle |
| `shared/` | Reusable pieces: `format/` (status labels/badges, file sizes, prices, model sizes/weights/times), `forms/` (account form, field error texts), `price-tag/`, `model-viewer/` (the Three.js 3D preview, loaded on demand) |

Test fixtures shared by the specs are in `src/testing/`, outside the app build.

Layering: template → component → service → backend API. Components never use `HttpClient` directly. A flow over several calls belongs in a service too: `RequestUploadService` uploads the files of a request one at a time for both the new request page and "Add files".

Angular style: standalone components, signals for state, `inject()`, lazy-loaded routes, reactive forms. UI is Bootstrap 5 (CSS only, no Bootstrap JS); dark theme by default with a floating toggle bottom-right (`ThemeService`, `layout/theme-toggle`). Interactive elements carry `data-cy` attributes for future Cypress tests.

## Testing

- Backend: Mockito unit tests for services, `@QuarkusTest` + RestAssured for resources against a throwaway Postgres (Dev Services, needs Docker). Coverage: `./gradlew test` → `build/reports/jacoco/test/html`.
- Frontend: Vitest via `ng test`, testing component and service logic rather than rendered HTML; coverage with `ng test --no-watch --coverage` → `coverage/` (templates excluded).

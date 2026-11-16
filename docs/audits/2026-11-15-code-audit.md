# Code audit, 2026-11-15

Code quality, security and best-practices audit of the whole repository (`PrintBridge-Backend`, `PrintBridge-Frontend`, `PrintBridge-Slicer`, `docs/`, config and CI), followed by a review of the file structure.

- **Audited:** `main` at `f6c6086` (same content as `dev` at `cc50de4`).
- **Updated:** after the file structure cleanup (#29, merged) the paths below were corrected and the finished file structure advice was removed. After #31 (merged) the findings it fixed were removed too: N+1 queries on a request's files, mappers with business logic, `SlicerResource` without a service, and the upload flow in components. After #33 (merged) both High findings were removed: nested 3MF components and unbounded 3MF metadata JSON, which were closed with limits on placed geometry and metadata size. After #35 (merged) the refresh token findings were removed: rotation without concurrency control (now a row lock and a 10 second grace window) and tokens never being purged; the `.env.example` finding was cut down to what is left. After #37 (merged) the login throttling finding was removed: login is limited per address and per account and address, through a reusable `@RateLimit` annotation. After #39 (merged) the upload transaction finding was removed: the file copy and the model analysis now run outside the transaction that inserts the file row. After #41 (merged) the deployment and configuration items were updated: `deploy/` and `docs/deployment.md` exist, the local slicer port is bound to localhost, Spoolman is out of the config, the backend's `.env.example` sits where Quarkus reads it and nginx's body limit follows `UPLOAD_MAX_SIZE_MB`. The slicer finding was removed with that; its optional shared secret was deliberately left out, because the slicer has no published port in `deploy/`. Line numbers are from the audited commit and may have shifted.
- **Method:** reading the code. No test suites were run and no code was changed. One finding (nested 3MF components, since fixed) was reproduced with a throwaway harness outside the repository; every other finding is from reading only and says so where it matters.
- **Result:** no Critical findings, 2 High, 9 Medium, 14 Low / Nitpick at the time of the audit. Still open below: no High, 4 Medium, 9 Low / Nitpick.

Contents:

1. [Findings](#findings): [High](#high), [Medium](#medium), [Low / Nitpick](#low--nitpick)
2. [What held up](#what-held-up)
3. [Top 5 quick wins](#top-5-quick-wins)
4. [File structure](#file-structure)

## Findings

### High

None open. Both High findings (nested 3MF components, unbounded 3MF metadata JSON) were fixed in #33.

### Medium

- **[MEDIUM] A paid request can be declined and then stays "paid" forever**
  - **Location:** `.../printrequest/PrintRequestService.java:254` (`changeStatus`), `:291` (`changePaid`); `RequestStatus.java:53`
  - **Category:** State machine
  - **Issue:** `PRINTING → DECLINED` is allowed without looking at `paidAt`. Once declined, `allowsPayment()` is false, so the paid flag can no longer be cleared and the price is locked. The result is a final, declined request that shows as paid.
  - **Recommendation:** Refuse declining while `paidAt != null` ("mark as unpaid first"), or clear it explicitly as part of declining. Add a test for the transition.

- **[MEDIUM] Preview meshes are rebuilt in memory on every request, with no cache or concurrency limit**
  - **Location:** `.../preview/PreviewService.java:87` (`encode`), `PreviewResource.java:38` (`no-store`), `MeshEncoder.java:45`
  - **Category:** Performance, DoS
  - **Issue:** Each preview re-parses the file and holds vertices, indices, their `ByteBuffer` copies and the gzip output at once (tens of MB at the 2M-triangle limit). `Cache-Control: no-store` means every click repeats it, and `POST /api/previews` lets a user trigger it with arbitrary files. A few parallel requests can exhaust a small heap.
  - **Recommendation:** Cache the encoded mesh per stored file (on disk next to the upload, invalidated when printers change), allow private caching with an ETag, and cap concurrent preview builds with a semaphore.

- **[MEDIUM] Subscriptions outlive their components: polling continues and a destroyed output is emitted**
  - **Location:** `PrintBridge-Frontend/src/app/features/requests/request-detail/request-detail-page.ts` (`show`, `refresh`); `review-panel/review-panel.ts` (`applyStatus`, `save`); `shared/model-viewer/model-preview.ts` (`show`)
  - **Category:** RxJS
  - **Issue:** Most `.subscribe()` calls are not torn down. Concretely:
    - If a poll request is in flight when the detail page is destroyed, its response calls `show()`, which arms a new timer, so polling runs until slicing ends.
    - `ReviewPanel` calls `output.emit()` after destroy, which throws NG0953 in current Angular.
    - `ModelPreview` can show file A's mesh under file B when the loader changes mid-request.
    - The upload flows (new request page, "Add files") were fixed in #31.
  - **Recommendation:** Add `takeUntilDestroyed(destroyRef)` to these pipelines and use a `switchMap` on the loader in `ModelPreview`.

- **[MEDIUM] README describes features that do not exist**
  - **Location:** `README.md` (feature list and status flow); `CLAUDE.md`
  - **Category:** Documentation drift
  - **Issue:** The README presents Spoolman filament selection, comments, a price indication behind `PricingStrategy` and admin editing of request details as features. None exist in code: there is no Spoolman client, no comment entity, no pricing class and no MakerWorld handling. The status flow omits `PRINTING`. (The unused `SPOOLMAN_*` settings and the Spoolman container were removed in #41, which also corrected the setup steps.)
  - **Recommendation:** Split the README into "available now" and "planned (#11 …)" and fix the status flow.

### Low / Nitpick

- **[LOW] Concurrent file removals can leave a request with no files**
  - **Location:** `.../printrequest/PrintRequestService.java:139` (`removeFile`)
  - **Category:** Concurrency
  - **Issue:** `addFile` locks the request but `removeFile` does not, so two removals on a two-file request both pass the `size() == 1` check. `PrintRequest` has no `@Version` either.
  - **Recommendation:** Use `findLockedWithDetails` here too, or add optimistic locking.

- **[LOW] "Final" requests are not final for admin file changes**
  - **Location:** `PrintRequestService.java:211` (`checkMayChangeFiles`), `:314` (`reslice`)
  - **Category:** State machine
  - **Issue:** `RequestStatus` says nothing changes after DECLINED / COMPLETED, and quantity changes respect that, but the admin can still add, remove and re-slice files.
  - **Recommendation:** Apply `isFinal()` consistently, or document the exception.

- **[LOW] List endpoints are unpaginated and uploads have no per-user quota**
  - **Location:** `PrintRequestRepository.java:16-35`; `PrintRequestService.create`
  - **Category:** Scalability
  - **Issue:** `/requests` and `/requests/all` return every row. A user can create unlimited requests of 50 × 100 MB.
  - **Recommendation:** Add `page` / `size` and a configurable cap on open requests per user.

- **[LOW] Error payloads come in several shapes and there is no catch-all mapper**
  - **Location:** `.../common/error/ExceptionMappers.java`
  - **Category:** Error handling
  - **Issue:** Domain errors return `{message}`, validation returns Quarkus' `violations`, and unexpected exceptions (for example `UncheckedIOException` on a full disk) and 413s fall through to the framework defaults. No stack traces leak in prod mode.
  - **Recommendation:** Add mappers for `ConstraintViolationException` and `Throwable` that log and return `{message}`.

- **[LOW] nginx sends no security headers, and the slicer has its own upload limit**
  - **Location:** `PrintBridge-Frontend/docker/default.conf.template`; `PrintBridge-Slicer/slicer_service.py:32`
  - **Category:** Configuration
  - **Issue:** There is no CSP, `X-Content-Type-Options` or frame policy. The slicer's `MAX_UPLOAD_MB=200` is a separate limit that follows neither `UPLOAD_MAX_SIZE_MB` nor any documented setting, so a ceiling above 200 MB uploads fine and then fails to slice. (The hardcoded `client_max_body_size 100m` was fixed in #41: it follows `UPLOAD_MAX_SIZE_MB` now.)
  - **Recommendation:** Add the standard headers, and pass the upload ceiling to the slicer in `deploy/docker-compose.yml` or document `MAX_UPLOAD_MB`.

- **[LOW] Slicer service: unhandled exceptions and a likely unit mismatch**
  - **Location:** `PrintBridge-Slicer/slicer_service.py:430-464`, `:357`
  - **Category:** Error handling
  - **Issue:** Anything other than `BadRequest` / `Busy` / `TimedOut` (bad JSON in the statistics, a non-numeric `Content-Length`) drops the connection, which the backend reports as "can't be reached". `--mstpp` receives seconds; OrcaSlicer probably reads it as minutes, which was not confirmed. The outer `subprocess` timeout still applies.
  - **Recommendation:** Add a catch-all that returns a 500 JSON error, and check the `--mstpp` unit against OrcaSlicer's command line help.

- **[LOW] WebGL contexts are not released explicitly**
  - **Location:** `PrintBridge-Frontend/src/app/shared/model-viewer/preview-scene.ts:149` (`dispose`)
  - **Category:** Three.js
  - **Issue:** Disposal is otherwise complete (observer, controls, geometries, materials, renderer, canvas). Each opened file preview creates its own renderer, though, and browsers cap live contexts at about 16; a request can have 50 files.
  - **Recommendation:** Call `renderer.forceContextLoss()` in `dispose`, and consider one shared viewer per page.

- **[LOW] Smaller documentation and process drift**
  - **Location:** `docs/api.md:78` vs `:94`; `docs/auth.md:40`; `docs/architecture.md:33`; issue #23
  - **Category:** Documentation drift
  - **Issue:** `api.md` describes the request shape twice, once without `quantity` / `totals`. `auth.md` still says the admin gets "later: all requests". The architecture doc omits the 422 / 503 exceptions. Issue #23 is still open although it is on `main`, because `Closes #N` in a PR that targets `dev` does not close the issue.
  - **Recommendation:** Fix the three spots and close issues by hand, or repeat `Closes` in the dev → main PR.

- **[NITPICK] Build and CI hygiene**
  - **Location:** `.github/workflows/ci.yml`; `PrintBridge-Backend/build.gradle.kts:9`; `application.properties:16`
  - **Category:** Supply chain, configuration
  - **Issue:** `actions/*` are on tags while `docker/*` are pinned to SHAs (a known follow-up). `mavenLocal()` is a resolvable repository. CORS stays enabled in production with a `localhost:4200` default although the app is same-origin.
  - **Recommendation:** Pin the remaining actions, drop `mavenLocal()`, and disable CORS unless `FRONTEND_ORIGIN` is set.

#7 (bcrypt ignores bytes after 72) was tracked separately and is fixed since #35.

## What held up

- **Access control:** `deny-unannotated-endpoints` is on, every resource declares its rule, and all request, file, preview and thumbnail access goes through `findVisible(caller)`. No IDOR found.
- **Layering and ORM:** `@Transactional` is on services only, entities never leave the backend, every `@ManyToOne` is `LAZY`, list queries use `join fetch`, and Hibernate is `none` / `validate` with Flyway V1–V8 consistent.
- **Uploads:** stored names are UUIDs under a normalised root, client filenames are sanitised, XXE is disabled and ZIP expansion is budgeted. Since #33 placed geometry and metadata parts are budgeted too.
- **Slicing:** runs on a background thread outside transactions, with connect and request timeouts and a 503 mapping when the slicer is down.
- **Frontend:** admin routes have `adminGuard`, token injection and the 401 refresh are in one interceptor, all 21 `@for` blocks have `track`, there is no `any`, and the TypeScript interfaces match the backend DTOs.
- **Not applicable:** no user-supplied URL is fetched by the server, so there is no SSRF surface yet.

## Top 5 quick wins

The first two of the original list (budget the component walk, cap the metadata JSON size) were done in #33, and the two refresh token items that replaced them in #35; open findings took their place. Binding the local slicer port to localhost (half of item 3) was done in #41.

1. **Block declining a paid request:** one check in `changeStatus` plus a test.
2. **Add `takeUntilDestroyed`** to the subscriptions in the detail page, `ReviewPanel` and `ModelPreview`.
3. **Correct the README feature list** and its status flow.
4. **Keep at least one file on a request:** lock the request when removing a file, so two removals at once can't empty it.
5. **Add a catch-all exception mapper** so every error has the same `ApiError` shape.

## File structure

Most of the file structure advice from this audit was carried out in #29: one-way package dependencies, a `modelfile` package, sub-packages by concern, wire-only `dto` packages, test fixtures in `src/testing/`, `shared/format/` and `shared/forms/`, short module READMEs and the unused Dockerfiles removed. `docs/architecture.md` describes the result. #41 added `deploy/` and moved the backend's `.env.example` into `PrintBridge-Backend/`. What is left:

### Still open

The deployment example with its doc and the two `.env.example` files were sorted out in #41. One item is left:

1. **Add a `SECURITY.md`.**
   The repository is public and has no stated way to report a vulnerability privately.

### Smaller points

- **Two conventions for pages:** in `features/requests` every page has its own folder, while `features/settings`, `features/users` and `features/login` keep the page at the feature root. Either is fine; pick one rule, for example "own folder once a feature has more than one page".
- **`shared/model-viewer/` spec names:** `preview-scene.spec.ts` and `preview-scene-class.spec.ts` both test `preview-scene.ts`. Name them after what they cover.
- **Backend test names that match no class:** `ModelInfoResourceTest`, `SlicingResourceTest` and `PrintRequestReviewResourceTest`. `ModelInfoService`, `PreviewService`, `SettingsService` and `SlicingService` have no unit test of their own and are only covered through `@QuarkusTest` resource tests, which need Docker.
- **Frontend files without a spec:** `layout/theme-toggle/theme-toggle.ts` and `shared/model-viewer/preview-scene-factory.ts`.
- **Backend Dockerfile location:** `src/main/docker/Dockerfile.jvm` is the Quarkus default, while the frontend and slicer keep theirs at the module root. Moving it would let CI drop the `file:` argument; optional.

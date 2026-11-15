# Code audit, 2026-11-15

Code quality, security and best-practices audit of the whole repository (`PrintBridge-Backend`, `PrintBridge-Frontend`, `PrintBridge-Slicer`, `docs/`, config and CI), followed by a review of the file structure.

- **Audited:** `main` at `f6c6086` (same content as `dev` at `cc50de4`).
- **Updated:** after the file structure cleanup (#29, merged) the paths below were corrected and the finished file structure advice was removed. Line numbers are from the audited commit and may have shifted.
- **Method:** reading the code. No test suites were run and no code was changed. One finding (nested 3MF components) was reproduced with a throwaway harness outside the repository; every other finding is from reading only and says so where it matters.
- **Result:** no Critical findings, 2 High, 9 Medium, 13 Low / Nitpick.

Contents:

1. [Findings](#findings): [High](#high), [Medium](#medium), [Low / Nitpick](#low--nitpick)
2. [What held up](#what-held-up)
3. [Top 5 quick wins](#top-5-quick-wins)
4. [File structure](#file-structure)

## Findings

### High

- **[HIGH] Nested 3MF components cause unbounded CPU and memory use**
  - **Location:** `PrintBridge-Backend/src/main/java/com/beauver/printbridge/modelfile/threemf/ThreeMfGeometry.java` (`walk`), reached from `ThreeMfAnalyzer.singlePlate` / `bambu` and `ThreeMfMeshReader.addParts`
  - **Category:** Security (DoS), file uploads
  - **Issue:** `MAX_DEPTH = 8` limits how deep components nest but not how many components each object has, and nothing counts visits. Reproduced: a 487-byte 3MF with 10 components per level took 5.6 s to analyse (0.98 s at 8 per level), and the cost grows with the 8th power of the fan-out. At 30 per level that extrapolates to roughly ten hours for a file still under 5 KB. The walk runs on the request thread inside the upload transaction, so any logged-in user can pin cores and worker threads. On `POST /api/previews` the same walk allocates a `PlacedMesh` per visit, which exhausts the heap instead.
  - **Recommendation:** Charge every `walk` visit and every placed vertex to `ReadBudget` (for example a few million placed vertices) and fail with `ModelTooDetailedException`. Add a regression test with a small nested file.

- **[HIGH] 3MF metadata JSON is parsed into memory with only the 1 GiB budget**
  - **Location:** `.../modelfile/threemf/ProjectSettings.java` (`read`), `BambuProject.java` (`readFilaments`), `.../modelfile/ReadBudget.java`
  - **Category:** Security (DoS), file uploads
  - **Issue:** `Metadata/project_settings.config` is read with `new ObjectMapper().readTree(in)` under the general 1 GiB uncompressed budget. JSON compresses extremely well, so a small upload can expand into a multi-gigabyte `JsonNode` tree. `OutOfMemoryError` is not caught by `catch (IOException | RuntimeException)` and affects the whole JVM. From reading only; not exercised.
  - **Recommendation:** Give metadata parts their own small budget (a few MB), as `thumbnail` already does with `MAX_THUMBNAIL_BYTES`, or read only the handful of keys needed with a streaming parser.

### Medium

- **[MEDIUM] Model analysis and file copy run inside the upload transaction, holding a row lock**
  - **Location:** `.../printrequest/PrintRequestService.java:118` (`addFile`), `:194` (`attach`); `.../modelinfo/ModelInfoService.java:50` (`analyze`)
  - **Category:** Transactions, performance
  - **Issue:** `addFile` takes a `PESSIMISTIC_WRITE` lock on the request, then validates, copies up to 100 MB and parses the model before committing. A large 3MF can exceed Quarkus' default 60 s transaction timeout, which rolls the upload back with a 500. Meanwhile an admin status change on the same request blocks, and a database connection is held throughout.
  - **Recommendation:** Keep the transaction to "check the limit, insert the row". Run the analysis afterwards in its own transaction, or on the background worker like slicing; the frontend already handles `PENDING`.

- **[MEDIUM] Refresh-token rotation has no concurrency control or grace window**
  - **Location:** `.../auth/token/RefreshTokenService.java` (`consume`); `PrintBridge-Frontend/src/app/core/auth/auth.service.ts:53` (`restoreSession`)
  - **Category:** Security, auth
  - **Issue:** Every page load calls refresh. Two tabs loading together send the same cookie: either both read the token as unrevoked (no lock, two live chains), or the second sees it revoked and `revokeAllForUser` logs the user out everywhere. Reuse detection then fires on normal behaviour.
  - **Recommendation:** Lock the token row in `consume` (`PESSIMISTIC_WRITE`) and accept a just-rotated token for a few seconds by returning its successor, before treating reuse as theft.

- **[MEDIUM] A paid request can be declined and then stays "paid" forever**
  - **Location:** `.../printrequest/PrintRequestService.java:254` (`changeStatus`), `:291` (`changePaid`); `RequestStatus.java:53`
  - **Category:** State machine
  - **Issue:** `PRINTING → DECLINED` is allowed without looking at `paidAt`. Once declined, `allowsPayment()` is false, so the paid flag can no longer be cleared and the price is locked. The result is a final, declined request that shows as paid.
  - **Recommendation:** Refuse declining while `paidAt != null` ("mark as unpaid first"), or clear it explicitly as part of declining. Add a test for the transition.

- **[MEDIUM] No login throttling**
  - **Location:** `.../auth/AuthResource.java:36`, `AuthService.java:34`
  - **Category:** Security
  - **Issue:** `/api/auth/login` accepts unlimited attempts. Bcrypt slows guessing but also makes each attempt cost server CPU, so it is both a brute-force and a cheap DoS surface. `docs/features/setup-auth-users.md` lists this as out of scope, so it is known but has no issue.
  - **Recommendation:** Add a per-account and per-IP attempt limit with backoff (a small table or an in-memory counter is enough for one instance), plus nginx `limit_req` on `/api/auth/`.

- **[MEDIUM] Preview meshes are rebuilt in memory on every request, with no cache or concurrency limit**
  - **Location:** `.../preview/PreviewService.java:87` (`encode`), `PreviewResource.java:38` (`no-store`), `MeshEncoder.java:45`
  - **Category:** Performance, DoS
  - **Issue:** Each preview re-parses the file and holds vertices, indices, their `ByteBuffer` copies and the gzip output at once (tens of MB at the 2M-triangle limit). `Cache-Control: no-store` means every click repeats it, and `POST /api/previews` lets a user trigger it with arbitrary files. A few parallel requests can exhaust a small heap.
  - **Recommendation:** Cache the encoded mesh per stored file (on disk next to the upload, invalidated when printers change), allow private caching with an ETag, and cap concurrent preview builds with a semaphore.

- **[MEDIUM] N+1 queries when describing a request's files**
  - **Location:** `.../modelinfo/ModelInfoService.java:91` (`describe`), called per file from `PrintRequestMapper.java:33`; `SettingsService.java:155` (`slicing`)
  - **Category:** Panache/ORM
  - **Issue:** For each file the mapper loads `ModelInfo` and `SliceResult` and queries the printers list again. A 50-file request costs about 150 queries, and the detail page polls every 3 s while slicing.
  - **Recommendation:** Load the model infos and slice results for all file ids in two `in` queries, build `SlicingSettings` once per request and pass it into the mapper.

- **[MEDIUM] Subscriptions outlive their components: polling continues and destroyed outputs are emitted**
  - **Location:** `PrintBridge-Frontend/src/app/features/requests/request-detail/request-detail-page.ts:264-288`; `add-files/add-files.ts:120`; `review-panel/review-panel.ts:226,249`; `shared/model-viewer/model-preview.ts:41`
  - **Category:** RxJS
  - **Issue:** Only 5 of 38 `.subscribe()` calls are torn down. Concretely:
    - If a poll request is in flight when the detail page is destroyed, its response calls `show()`, which arms a new timer, so polling runs until slicing ends.
    - `AddFiles` and `ReviewPanel` call `output.emit()` after destroy, which throws NG0953 in current Angular and stalls the rest of the upload queue.
    - `ModelPreview` can show file A's mesh under file B when the loader changes mid-request.
  - **Recommendation:** Add `takeUntilDestroyed(destroyRef)` to these pipelines and use a `switchMap` on the loader in `ModelPreview`.

- **[MEDIUM] README and config describe features that do not exist**
  - **Location:** `README.md:3-15,24`; `.env.example:12-14`; `application.properties:47-48`; `local/docker-compose.yml:24`; `CLAUDE.md`
  - **Category:** Documentation drift
  - **Issue:** The README presents Spoolman filament selection, comments, a price indication behind `PricingStrategy` and admin editing of request details as features. None exist in code: there is no Spoolman client, no comment entity, no pricing class and no MakerWorld handling. The `SPOOLMAN_*` settings are read by nothing, yet the local stack starts `spoolman:latest`. The status flow omits `PRINTING`.
  - **Recommendation:** Split the README into "available now" and "planned (#11 …)", fix the status flow, and drop or comment out the Spoolman config and container until #11 lands.

- **[MEDIUM] Slicer service is unauthenticated and published on all host interfaces locally**
  - **Location:** `PrintBridge-Slicer/slicer_service.py:487`; `local/docker-compose.yml:48`
  - **Category:** Security, configuration
  - **Issue:** The service binds `0.0.0.0` with no authentication, and compose publishes it as `18000:8000`, so anyone on the LAN can feed arbitrary files to OrcaSlicer or hold the single slicing slot. `docs/features/server-slicing.md` says to keep it internal, but the shipped compose file does not.
  - **Recommendation:** Publish it as `127.0.0.1:${SLICER_PORT}:8000`, and add an optional shared-secret header checked by the service and sent by `SlicerClient`.

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

- **[LOW] Upload flow logic lives in components**
  - **Location:** `PrintBridge-Frontend/src/app/features/requests/new-request/new-request-page.ts:162-228`; `request-detail/add-files/add-files.ts:115-144`
  - **Category:** Architecture (fat component)
  - **Issue:** Sequential multi-file upload with failure collection is implemented twice, recursively, in components. Leaving the new-request page mid-upload also still triggers `router.navigate` to the detail page later.
  - **Recommendation:** Move it to one service method that returns a progress stream (`concatMap`), and add a `canDeactivate` guard while uploading.

- **[LOW] Mappers contain business logic and database access**
  - **Location:** `.../printrequest/PrintRequestMapper.java:18,43`; `.../modelinfo/ModelInfoMapper.java`; `SettingsService.java:201-236`
  - **Category:** SOLID (single responsibility)
  - **Issue:** `PrintRequestMapper` injects a service that queries, and computes totals. `ModelInfoMapper` does fit checks and weight estimation. `SettingsService` maps inline.
  - **Recommendation:** Compute totals and fit in a service or a domain object and keep the mappers pure.

- **[LOW] `SlicerResource` skips the service layer and returns client types**
  - **Location:** `.../slicing/SlicerResource.java:30-57`
  - **Category:** Architecture
  - **Issue:** The resource calls `SlicerClient` directly, builds the status in a try/catch, and returns `SlicerPrinter` / `SlicerProfiles` (the slicer's wire format) as the API response.
  - **Recommendation:** Add a thin service with its own DTOs, so the slicer contract can change independently.

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

- **[LOW] nginx body limit is hardcoded and security headers are missing**
  - **Location:** `PrintBridge-Frontend/docker/default.conf.template:12`
  - **Category:** Configuration
  - **Issue:** `client_max_body_size 100m` ignores `UPLOAD_MAX_SIZE_MB`, so raising the ceiling fails with an HTML 413 from nginx; the slicer's `MAX_UPLOAD_MB=200` is a third separate limit. There is no CSP, `X-Content-Type-Options` or frame policy.
  - **Recommendation:** Template the limit from an environment variable and add the standard headers.

- **[LOW] `.env.example` ships `AUTH_COOKIE_SECURE=false`**
  - **Location:** `.env.example:37`
  - **Category:** Secrets hygiene
  - **Issue:** Copying the example to production yields a refresh cookie without `Secure`. The code default is `true`, so the example weakens it.
  - **Recommendation:** Leave it commented out with a note for plain-HTTP local runs. Also document `MAX_UPLOAD_MB` and `printbridge.preview.max-triangles`.

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

- **[LOW] Refresh tokens are never purged**
  - **Location:** `.../auth/token/RefreshTokenRepository.java`
  - **Category:** Data hygiene
  - **Issue:** Every page load adds a row; revoked and expired rows are kept forever.
  - **Recommendation:** Delete rows that expired or were revoked more than a few days ago, on a schedule or at startup.

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

Already tracked elsewhere and not repeated here: #7 (bcrypt ignores bytes after 72).

## What held up

- **Access control:** `deny-unannotated-endpoints` is on, every resource declares its rule, and all request, file, preview and thumbnail access goes through `findVisible(caller)`. No IDOR found.
- **Layering and ORM:** `@Transactional` is on services only, entities never leave the backend, every `@ManyToOne` is `LAZY`, list queries use `join fetch`, and Hibernate is `none` / `validate` with Flyway V1–V8 consistent.
- **Uploads:** stored names are UUIDs under a normalised root, client filenames are sanitised, XXE is disabled and ZIP expansion is budgeted (apart from the two High findings).
- **Slicing:** runs on a background thread outside transactions, with connect and request timeouts and a 503 mapping when the slicer is down.
- **Frontend:** admin routes have `adminGuard`, token injection and the 401 refresh are in one interceptor, all 21 `@for` blocks have `track`, there is no `any`, and the TypeScript interfaces match the backend DTOs.
- **Not applicable:** no user-supplied URL is fetched by the server, so there is no SSRF surface yet.

## Top 5 quick wins

1. **Budget the component walk** in `ThreeMfGeometry`: a few lines close the reproduced DoS.
2. **Cap the metadata JSON size** in `ProjectSettings` / `BambuProject`: one small `ReadBudget` per metadata part.
3. **Block declining a paid request:** one check in `changeStatus` plus a test.
4. **Add `takeUntilDestroyed`** to the subscriptions in the detail page, `AddFiles`, `ReviewPanel` and `ModelPreview`.
5. **Bind the local slicer port to `127.0.0.1`** and correct the README feature list.

## File structure

Most of the file structure advice from this audit was carried out in #29: one-way package dependencies, a `modelfile` package, sub-packages by concern, wire-only `dto` packages, test fixtures in `src/testing/`, `shared/format/` and `shared/forms/`, short module READMEs and the unused Dockerfiles removed. `docs/architecture.md` describes the result. What is left:

### Still open

1. **Add a deployment example and its doc.**
   `local/` only starts development dependencies. There is no compose file or document showing how the three published images run together. A `deploy/docker-compose.yml` (backend, frontend, Postgres, optional slicer on an internal network, volumes for uploads and JWT keys) with a `docs/deployment.md` would also be the natural home for the slicer isolation, the body-size limit and the cookie settings from the findings above.

2. **Sort out the two `.env.example` files.**
   The root file is for the backend and `local/.env.example` is for compose, with the database password in both under different names. As far as I can tell Quarkus only reads a `.env` from its working directory, which is `PrintBridge-Backend/` when running `./gradlew quarkusDev`, so a `.env` at the repository root (README step 1) is not picked up unless it is exported by hand. Moving the backend example to `PrintBridge-Backend/.env.example` would make it work as the README describes.

3. **Add a `SECURITY.md`.**
   The repository is public and has no stated way to report a vulnerability privately.

### Smaller points

- **Two conventions for pages:** in `features/requests` every page has its own folder, while `features/settings`, `features/users` and `features/login` keep the page at the feature root. Either is fine; pick one rule, for example "own folder once a feature has more than one page".
- **`shared/model-viewer/` spec names:** `preview-scene.spec.ts` and `preview-scene-class.spec.ts` both test `preview-scene.ts`. Name them after what they cover.
- **Backend test names that match no class:** `ModelInfoResourceTest`, `SlicingResourceTest` and `PrintRequestReviewResourceTest`. `ModelInfoService`, `PreviewService`, `SettingsService` and `SlicingService` have no unit test of their own and are only covered through `@QuarkusTest` resource tests, which need Docker.
- **Frontend files without a spec:** `layout/theme-toggle/theme-toggle.ts` and `shared/model-viewer/preview-scene-factory.ts`.
- **Backend Dockerfile location:** `src/main/docker/Dockerfile.jvm` is the Quarkus default, while the frontend and slicer keep theirs at the module root. Moving it would let CI drop the `file:` argument; optional.

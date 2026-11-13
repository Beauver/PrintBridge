# API

All endpoints are under `/api`. Errors handled by the app return `{"message": "..."}`; validation errors return Quarkus' format with a `violations` list (`field`, `message`).

## Setup

| Method | Path | Access | Body | Response |
|---|---|---|---|---|
| GET | `/setup` | public | – | `200 {setupRequired}` |
| POST | `/setup` | public, only while no admin exists | `{username, email, password}` | `201` auth response + refresh cookie; `409` if already done; `400` invalid input |

## Auth

| Method | Path | Access | Body | Response |
|---|---|---|---|---|
| POST | `/auth/login` | public | `{login, password}` (`login` = username or email, case-insensitive) | `200` auth response + refresh cookie; `401 Invalid credentials` |
| POST | `/auth/refresh` | refresh cookie | – | `200` auth response + new cookie; `401` |
| POST | `/auth/logout` | public | – | `204`, cookie cleared |
| GET | `/auth/me` | logged in | – | `200` user |

Auth response: `{accessToken, expiresIn (seconds), user}`.

## Users (admin only)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/users` | – | `200` list of users, sorted by username |
| POST | `/users` | `{username, email, password}` | `201` user; `409` username/email taken; `400` invalid |
| PUT | `/users/{id}/password` | `{password}` | `204`; `404` unknown user. Logs the user out everywhere |
| DELETE | `/users/{id}` | – | `204`; `404` unknown; `409` deleting yourself |

User: `{id, username, email, role, createdAt}`.

Validation: username 3–32 characters (`A-Z a-z 0-9 . _ -`), unique ignoring case; email valid, max 254, unique ignoring case; password 8–72 characters.

Non-admins get `403`, requests without a valid token `401`.

Deleting a user also deletes their print requests and files.

## Print requests (logged in)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/requests` | – | `200` list of the caller's own request summaries, newest first |
| GET | `/requests/upload-config` | – | `200 {maxSizeBytes, allowedExtensions}` |
| POST | `/requests` | `multipart/form-data`: `name` (1–100), `description` (optional, max 2000), `file` (`.stl`/`.3mf`) | `201` request + `Location`; `400` invalid input or file; `413` body larger than the limit |
| GET | `/requests/{id}` | – | `200` request; `404` unknown or not yours |
| GET | `/requests/{id}/files/{fileId}` | – | `200` file bytes (`model/stl` or `model/3mf`, `Content-Disposition: attachment` with the original name); `404` |
| DELETE | `/requests/{id}` | – | `204`; `404` unknown or not yours; `409` owner deleting a request that is no longer SUBMITTED |

Admin only (users get `403`):

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/requests/all?status=&q=` | – | `200` everyone's request summaries, newest first. `status`: `OPEN` (default: SUBMITTED, IN_REVIEW, ACCEPTED), `ALL` or one status, case-insensitive; `q`: matches name or username, ignoring case. `400` unknown status |
| PUT | `/requests/{id}/status` | `{status, declineReason}` (reason optional, max 2000, only kept when declining) | `200` request; `409` move not allowed, request final, or completing without a price; `404`; `400` invalid |
| PUT | `/requests/{id}/price` | `{finalPrice}` (EUR, 0–99999999.99, max 2 decimals; `null` clears) | `200` request; `409` request final or paid; `404`; `400` invalid |
| PUT | `/requests/{id}/paid` | `{paid}` (`true`/`false`) | `200` request; `409` not ACCEPTED/COMPLETED, or marking paid without a price; `404`; `400` missing `paid` |
| POST | `/requests/{id}/reslice` | – | `200` request (files queued for server slicing); `409` slicing is off or a file is still queued/running; `404` |

Allowed moves: SUBMITTED → IN_REVIEW / ACCEPTED / DECLINED; IN_REVIEW → ACCEPTED / DECLINED; ACCEPTED → COMPLETED. DECLINED and COMPLETED are final.

The admin can open, download and delete any request (delete in any status). Other users' requests are always `404`, never `403`, so their existence doesn't leak.

Summary: `{id, name, status, finalPrice, paidAt, ownerUsername, createdAt}`.
Request: `{id, name, description, status, finalPrice, declineReason, paidAt, ownerId, ownerUsername, createdAt, updatedAt, files: [{id, originalFilename, type, sizeBytes, createdAt, modelInfo}], nextStatuses}` (`nextStatuses`: where the admin may move it; empty once final).
`status` is `SUBMITTED | IN_REVIEW | ACCEPTED | DECLINED | COMPLETED`; `type` is `STL | THREE_MF`; `finalPrice` is EUR or `null` (pending); `paidAt` is when it was marked paid, or `null`.

`modelInfo` is what is known about the file, see [model info](features/model-info.md) and [server slicing](features/server-slicing.md): `{status, source, volumeCm3, totalGrams, weight, printSeconds, fits, fitPrinter, plates: [{index, name, width, depth, height, grams, estimated, printSeconds, fits, filaments: [{type, color, grams}], sliceError}], slicing: {enabled, status, printer, nozzle, madeForPrinter, madeForNozzle, error}}`. `fitPrinter` is the printer the fit check is for.
- `status` is `PENDING` (not analysed yet), `ANALYZED` or `FAILED`; unless ANALYZED, only `status` and `slicing` are set and `plates` is empty. `source` is `MESH | BAMBU`.
- `weight` is `EXACT`, `PARTLY_ESTIMATED` or `ESTIMATED`; a plate's `estimated` says whether its `grams` come from its volume.
- `slicing.status` is `null` (never queued), `QUEUED`, `RUNNING`, `DONE` or `FAILED`. `slicing.error` and the plates' `sliceError` are only filled in for the admin.

Upload errors (`400 {message}`): `Only .stl, .3mf files are allowed`, `The file is empty`, `The file is larger than N MB`, `The file is not a valid STL file` / `3MF file`. Missing name or file gives a validation error (`violations`).

## Settings (admin only)

| Method | Path | Body | Result |
|---|---|---|---|
| GET | `/settings` | – | `200 {slicing: {enabled, timeoutMinutes}, defaultPrinterId, printers: [{id, model, buildPlate: {x, y, z}, ownedNozzles, defaultNozzle, process, filament}]}` |
| PUT | `/settings/slicing` | `{enabled, timeoutMinutes (1–60)}`; switching on needs at least one printer, with nozzles and profiles for every printer | `200` settings; `400` invalid |
| POST | `/settings/printers` | `{model, buildPlate: {x, y, z} (whole mm 1–2000), ownedNozzles, defaultNozzle, process, filament}`; the default nozzle must be owned; while slicing is on, nozzles and profiles are required. The first printer becomes the default | `201` settings; `400` invalid; `409` model already added |
| PUT | `/settings/printers/{id}` | same as POST | `200` settings; `400`; `404`; `409` |
| DELETE | `/settings/printers/{id}` | – | `200` settings (the oldest remaining printer becomes the default); `404`; `409` last printer while slicing is on |
| PUT | `/settings/printers/{id}/default` | – | `200` settings; `404` |

Switching slicing off drops queued jobs.

## Slicer (admin only)

What the slicer service offers, for the settings page. `503 {message}` when it isn't set up or can't be reached (except `status`).

| Method | Path | Result |
|---|---|---|
| GET | `/slicer/status` | `200 {configured, reachable, orcaVersion, message}` |
| GET | `/slicer/printers` | `200 [{model, vendor, nozzles: [{diameter, machine, bed: {x, y, z}}]}]` |
| GET | `/slicer/profiles?printer=&nozzle=` | `200 {machine, processes, filaments}`; `400` missing/invalid parameters |

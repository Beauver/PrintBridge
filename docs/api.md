# API

All endpoints are under `/api`. Errors handled by the app return `{"message": "..."}`; validation errors return Quarkus' format with a `violations` list (`field`, `message`).

## Setup

| Method | Path | Access | Body | Response |
|---|---|---|---|---|
| GET | `/setup` | public | – | `200 {setupRequired}` |
| POST | `/setup` | public, only while no admin exists | `{username, email, password}` | `201` auth response + refresh cookie; `409` if already done; `400` invalid input |

## Licence

| Method | Path | Access | Body | Response |
|---|---|---|---|---|
| GET | `/license` | public | – | `200 {license, sourceUrl}`: `"AGPL-3.0-or-later"` and where the source of this installation is (`SOURCE_URL`), see [licence](licence.md) |

## Auth

| Method | Path | Access | Body | Response |
|---|---|---|---|---|
| POST | `/auth/login` | public | `{login, password}` (`login` = username or email, case-insensitive) | `200` auth response + refresh cookie; `401 Invalid credentials`; `429` with `Retry-After` after too many attempts (see [auth](auth.md#login-throttling)) |
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

Validation: username 3–32 characters (`A-Z a-z 0-9 . _ -`), unique ignoring case; email valid, max 254, unique ignoring case; password at least 8 characters and at most 72 bytes in UTF-8 (72 characters for plain ASCII).

Non-admins get `403`, requests without a valid token `401`.

An endpoint with a rate limit answers `429 Too Many Requests` once the limit is passed, with a `Retry-After` header (seconds) and `{"message": "Too many attempts, try again in N seconds"}`. Currently only login has one.

Deleting a user also deletes their print requests and files.

## Print requests (logged in)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/requests` | – | `200` list of the caller's own request summaries (`{id, name, status, finalPrice, paidAt, ownerUsername, createdAt, itemsCompleted, itemsActive}`; the last two: completed and not-declined items, #61), newest first |
| GET | `/requests/upload-config` | – | `200 {maxSizeBytes, maxFiles, allowedExtensions}` (the [upload settings](features/print-requests.md#upload-settings-adminsettings)) |
| POST | `/requests` | `multipart/form-data`: `name` (1–100), `description` (optional, max 2000), either `file` (`.stl`/`.3mf`) or a link (#59): `url` (`http(s)`, max 2048) and `linkName` (optional, max 100); `quantity` (optional, 1–100, default 1) | `201` request + `Location`; `400` invalid input, file or link, or both or neither of `file` and `url`; `413` body larger than the limit |
| GET | `/requests/{id}` | – | `200` request; `404` unknown or not yours |
| GET | `/requests/{id}/files/{fileId}` | – | `200` file bytes (`model/stl` or `model/3mf`, `Content-Disposition: attachment` with the original name); `404` (also for a link) |
| GET | `/requests/{id}/files/{fileId}/preview` | – | `200` preview mesh (`application/vnd.printbridge.mesh`, gzipped, see [3D preview](features/model-preview.md)); `404`; `422 {message}` "This model is too detailed for a preview" / "No preview available for this file" |
| GET | `/requests/{id}/files/{fileId}/plates/{plate}/thumbnail` | – | `200` the plate's PNG from a Bambu Studio / OrcaSlicer project; `404` none (STL, plain 3MF, no image) or not yours |
| POST | `/requests/{id}/files` | `multipart/form-data`: `file`, `quantity` (optional, 1–100, default 1) | `201` request; `400` invalid file or quantity, or the request already holds *max files*; `404`; `409` owner and the request is no longer SUBMITTED; `413` |
| POST | `/requests/{id}/links` | `{url, name, quantity}`: `url` `http(s)` (max 2048), `name` optional (max 100), `quantity` optional (1–100, default 1) | `201` request; `400` invalid link or quantity, or the request already holds *max files* (files and links together); `404`; `409` owner and the request is no longer SUBMITTED |
| DELETE | `/requests/{id}/files/{fileId}` | – (also removes a link) | `200` request; `404`; `409` the last file or link, or owner and the request is no longer SUBMITTED |
| PUT | `/requests/{id}/files/{fileId}/quantity` | `{quantity}` (1–100) | `200` request; `400`; `404`; `409` owner and no longer SUBMITTED, or admin and the request is final |
| PUT | `/requests/{id}/files/{fileId}/filaments` | `{wholeItem, plateIndex, originalType, originalColor, spoolmanFilamentId}` (#11): `wholeItem` `false` with a colour of a plate as the model info lists it (`originalColor` `#RRGGBB`), or `true` with the other three `null` for the whole item (a file without colours, or a link). Choosing again replaces | `200` request; `400` no such colour on that plate, colours on the file (whole item refused), a plate or colour with `wholeItem`, no plate without it, unknown filament, invalid; `404`; `409` colour selection off, owner and no longer SUBMITTED, or admin and the request is final; `503` Spoolman can't be read |
| DELETE | `/requests/{id}/files/{fileId}/filaments/{choiceId}` | – (#11) | `200` request; `404`; `409` as above |
| GET | `/requests/{id}/files.zip` | – | `200` ZIP (`application/zip`, named after the request) with every file under its original name; duplicates get a number. With links, also `links.txt` (name or host, quantity and URL per link); `404` |
| DELETE | `/requests/{id}` | – | `204`; `404` unknown or not yours; `409` owner deleting a request that is no longer SUBMITTED |

Admin only (users get `403`):

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/requests/all?status=&q=` | – | `200` everyone's request summaries, newest first. `status`: `OPEN` (default: SUBMITTED, IN_REVIEW, ACCEPTED, PRINTING), `ALL` or one status, case-insensitive; `q`: matches name or username, ignoring case. `400` unknown status |
| PUT | `/requests/{id}/status` | `{status, declineReason}` (reason optional, max 2000, only kept when declining) | `200` request (accepting, declining and completing also move its items along, see [item statuses](features/admin-review.md#item-statuses)); `409` move not allowed, request final, or completing without a price; `404`; `400` invalid |
| PUT | `/requests/{id}/files/{fileId}/status` | `{status, declineReason}` (#61): `PENDING`, `ACCEPTED`, `DECLINED`, `PRINTING`, `COMPLETED`; reason optional, max 500, only kept when declining | `200` request; `409` move outside the item's `nextStatuses`, or the request is final; `404`; `400` invalid |
| PUT | `/requests/{id}/price` | `{finalPrice}` (EUR, 0–99999999.99, max 2 decimals; `null` clears) | `200` request; `409` request final or paid; `404`; `400` invalid |
| PUT | `/requests/{id}/paid` | `{paid}` (`true`/`false`) | `200` request; `409` not ACCEPTED/PRINTING/COMPLETED, or marking paid without a price; `404`; `400` missing `paid` |
| POST | `/requests/{id}/files/{fileId}/reslice` | JSON `{printerId, nozzle, process, filament}` (#21): `process`/`filament` `null` = the project's own | `200` request (that file queued with the chosen settings); `400` printer not owned or not set up, nozzle not owned on it, profile unknown for that printer + nozzle, `null` process outside a project on its own printer and nozzle, `null` filament for a non-project; `409` slicing is off, the file is still queued/running, or it is a link; `503` the slicer can't be reached to check the profiles; `404` |
| GET | `/requests/{id}/files/{fileId}/reslice` | – | `200 {printers: [{id, model, ownedNozzles, defaultNozzle, process, filament}], project, projectPrinter, projectNozzle, start: {printerId, nozzle, process, filament, custom}}` for the *Re-slice with…* dialog (#21); `409` a link; `404` |

Allowed moves: SUBMITTED → IN_REVIEW / ACCEPTED / DECLINED; IN_REVIEW → ACCEPTED / DECLINED; ACCEPTED → PRINTING / COMPLETED; PRINTING → COMPLETED / ACCEPTED / DECLINED. DECLINED and COMPLETED are final.

The admin can open, download and delete any request (delete in any status). Other users' requests are always `404`, never `403`, so their existence doesn't leak.

Summary: `{id, name, status, finalPrice, paidAt, ownerUsername, createdAt, itemsCompleted, itemsActive, estimatedPrice}`.
Request: `{id, name, description, status, finalPrice, declineReason, paidAt, ownerId, ownerUsername, createdAt, updatedAt, files, totals, nextStatuses, filamentSelection, notEnoughFilamentMessage, noMatchMessage, estimatedPrice}` (`files` and `totals`: see below; `nextStatuses`: where the admin may move it; empty once final; `filamentSelection`: the caller may choose filaments from Spoolman now, #11; `notEnoughFilamentMessage`: the admin's warning for a filament that runs short; `estimatedPrice`: `{amount, partial}` while there is no final price and the request isn't declined, else `null`, see [pricing](features/pricing.md)).
`status` is `SUBMITTED | IN_REVIEW | ACCEPTED | PRINTING | DECLINED | COMPLETED`; `type` is `STL | THREE_MF`; `finalPrice` is EUR or `null` (pending); `paidAt` is when it was marked paid, or `null`.

`modelInfo` is what is known about the file, see [model info](features/model-info.md) and [server slicing](features/server-slicing.md): `{status, source, volumeCm3, totalGrams, weight, printSeconds, fits, fitPrinter, plates: [{index, name, width, depth, height, grams, estimated, printSeconds, fits, filaments: [{type, color, grams}], sliceError}], slicing: {enabled, status, printer, nozzle, madeForPrinter, madeForNozzle, custom, process, filament, error}}`. `fitPrinter` is the printer the fit check is for. `custom`: sliced with the admin's choice (#21), with the chosen `process` and `filament` (`null` = the project's own).
- `status` is `PENDING` (not analysed yet), `ANALYZED` or `FAILED`; unless ANALYZED, only `status` and `slicing` are set and `plates` is empty. `source` is `MESH | BAMBU`.
- `weight` is `EXACT`, `PARTLY_ESTIMATED` or `ESTIMATED`; a plate's `estimated` says whether its `grams` come from its volume.
- `slicing.status` is `null` (never queued), `QUEUED`, `RUNNING`, `DONE` or `FAILED`. `slicing.error` and the plates' `sliceError` are only filled in for the admin.

## Previews (logged in)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/previews` | `multipart/form-data`: `file` | `200` preview mesh of a file that isn't uploaded yet, on the default printer's bed; nothing is stored. `400` the same upload errors as below; `422` as for an uploaded file |

Errors are always JSON (`{message}`), also on the endpoints that otherwise return a file or a mesh.

A request has `files: [{id, kind, originalFilename, type, sizeBytes, url, linkName, quantity, status, declineReason, nextStatuses, createdAt, modelInfo, filamentChoices, unmatchedColors}]` and `totals: {files, links, declined, completed, copies, grams, weight, printSeconds, printTimePartial, fits}` (every file and link times its quantity, declined items left out except in `files`, `links` and `declined`; see [model info](features/model-info.md#totals-of-a-request)). `kind` is `FILE` or `LINK` (#59): a file has `originalFilename`, `type`, `sizeBytes` and `modelInfo`; a link has `url` and maybe `linkName`, and those file fields are `null`.

`filamentChoices` (#11, see [Spoolman filaments](features/spoolman.md)): `[{id, wholeItem, source, plateIndex, originalType, originalColor, spoolmanFilamentId, name, vendor, material, color, colors, chosenAt, orphaned, inSpoolman, gramsLeft, gramsNeeded, notEnough}]`. Name, vendor, material and colour(s) are a copy from when it was chosen. `orphaned`: the file no longer has that colour on that plate. `inSpoolman` and `gramsLeft` are `null` while the integration is off or Spoolman can't be read. `gramsNeeded`: what the whole request needs of that filament (`null` when unknown); `notEnough`: less is left than that, or nothing. `source`: `MANUAL` or `AUTO` (automatic matching, #76). `unmatchedColors`: `[{plateIndex, originalType, originalColor}]`, colours automatic matching found no close filament for and nobody decided on since.

Upload errors (`400 {message}`): `Only .stl, .3mf files are allowed`, `The file is empty`, `The file is larger than N MB`, `The file is not a valid STL file` / `3MF file`, `A request can hold at most N files and links`, `Add a model file or a link`. Link errors: `The link must start with http:// or https://`, `The link is not a valid web address`, `The link must be at most 2048 characters`, `The link name must be at most 100 characters`. A missing name gives a validation error (`violations`).

## Settings (admin only)

| Method | Path | Body | Result |
|---|---|---|---|
| GET | `/settings` | – | `200 {slicing: {enabled, timeoutMinutes}, defaultPrinterId, printers: [{id, model, buildPlate: {x, y, z}, ownedNozzles, defaultNozzle, process, filament}], uploads: {maxFiles, maxFileSizeMb, ceilingMb}, spoolman: {enabled, colorSelection, prices, autoMatch, matchTolerance, notEnoughMessage, noMatchMessage}, pricing: {markup, pricePer100Grams}}` (`notEnoughMessage` with the default filled in) |
| PUT | `/settings/pricing` | `{markup (0.01–100), pricePer100Grams (0–1000)}`, at most 2 decimals each (#72) | `200` settings; `400` invalid |
| PUT | `/settings/spoolman` | `{enabled, colorSelection, prices, autoMatch, matchTolerance (1–100), notEnoughMessage, noMatchMessage}` (#11, `prices` #72, matching #76); switching on needs `SPOOLMAN_URL`, colour selection and prices need `enabled`, `autoMatch` needs `colorSelection`; texts optional, max 300; `notEnoughMessage` optional, max 300, empty or the default = the default text | `200` settings; `400` invalid |
| PUT | `/settings/uploads` | `{maxFiles (1–50), maxFileSizeMb (1 up to the ceiling UPLOAD_MAX_SIZE_MB)}` | `200` settings; `400` invalid |
| PUT | `/settings/slicing` | `{enabled, timeoutMinutes (1–60)}`; switching on needs at least one printer, with nozzles and profiles for every printer | `200` settings; `400` invalid |
| POST | `/settings/printers` | `{model, buildPlate: {x, y, z} (whole mm 1–2000), ownedNozzles, defaultNozzle, process, filament}`; the default nozzle must be owned; while slicing is on, nozzles and profiles are required. The first printer becomes the default | `201` settings; `400` invalid; `409` model already added |
| PUT | `/settings/printers/{id}` | same as POST | `200` settings; `400`; `404`; `409` |
| DELETE | `/settings/printers/{id}` | – | `200` settings (the oldest remaining printer becomes the default); `404`; `409` last printer while slicing is on |
| PUT | `/settings/printers/{id}/default` | – | `200` settings; `404` |

Switching slicing off drops queued jobs.

## Spoolman

Issue #11, see [Spoolman filaments](features/spoolman.md).

| Method | Path | Who | Result |
|---|---|---|---|
| GET | `/spoolman/status` | admin | `200 {configured, reachable, url, version, message}`; never fails, the reason is in `message` |
| GET | `/spoolman/filaments` | logged in | `200 [{id, name, vendor, material, color, colors, gramsLeft}]`: every filament, used-up ones too, sorted by material, vendor and name. `gramsLeft`: summed over its spools that aren't archived, `null` when a spool's weight is unknown. `409` colour selection is off; `503` Spoolman can't be read |

## Slicer (admin only)

What the slicer service offers, for the settings page. `503 {message}` when it isn't set up or can't be reached (except `status`).

| Method | Path | Result |
|---|---|---|
| GET | `/slicer/status` | `200 {configured, reachable, orcaVersion, message}` |
| GET | `/slicer/printers` | `200 [{model, vendor, nozzles: [{diameter, machine, bed: {x, y, z}}]}]` |
| GET | `/slicer/profiles?printer=&nozzle=` | `200 {machine, processes, filaments}`; `400` missing/invalid parameters |

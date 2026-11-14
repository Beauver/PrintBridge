# Print requests and STL/3MF uploads

Issue: #4 (part of #2); several files per request, quantities and upload settings: #23.

## What it does

- **My requests** (start page, *My requests* in the navbar): the logged-in user's own requests, newest first. Shows the name, a status badge, the price ("Price pending" until the admin sets one, see [admin review](admin-review.md)) and the creation date. It's a table on wider screens and cards on phones. With no requests yet, the page shows a *New request* button (plus a hint to create accounts for the admin).
- **New request** (*New request* in the navbar, `/requests/new`):
  - Fields: name (required, max 100), description (optional, max 2000) and one or more model files (`.stl` or `.3mf`).
  - The file input takes several files at once; choosing again adds more. Each file is a row with its name, size, a **quantity** (copies, 1–100, default 1), *Remove* and *Show 3D preview* ([3D preview](model-preview.md)). Under the input: "3 of 10 files · max 100 MB each".
  - Every file is checked in the browser before uploading (extension, not empty, size per file), as is the number of files; the limits come from the backend ([Settings → Uploads](#upload-settings-adminsettings)).
  - **Submitting** creates the request with the first file, then uploads the other files one at a time, each with its own progress bar. Afterwards the new request opens.
    - If the first upload fails, the error appears above the form and nothing is created.
    - If a later file fails (refused or a network error), the request keeps the files that worked; its page lists the files that weren't added and why, so the owner can add them again with *Add files*.
- **Request detail** (`/requests/:id`):
  - The details card has two columns (one on phones): on the left *Requested by* (admin), *Description*, *Created* and *Last updated*; on the right *Price* and the **totals** of the request, every file times its quantity (see [model info](model-info.md#totals-of-a-request)): *Filament* (`~412 g`, with a note on estimates) and *Print time* (`~12 h 5 min (partial time)` while some files have no time yet). A decline reason gets a full-width row below.
  - Problems with a file show as badges in its Model card header, also when collapsed: *Too big for …* and *Slicing failed*.
  - **Files:** a heading with the counts ("3 files · 7 copies") and *Download all (ZIP)*.
  - **One Model card per file** ([model info](model-info.md)), collapsible: the first is open, the others closed. The header shows the file name, "× quantity", grams, time and the slicing / fit badges. The body starts with the file's type and size, its quantity, *Download* and *Remove*.
  - **Add files** (owner while *Submitted*, admin always): choose one or more files; they go through the same checks and limits and are uploaded one at a time with one copy each. Files that fail are listed with the reason. New files are analysed and, with server slicing on, queued.
  - **Quantity:** a number field per file (1–100), saved when it changes. The owner can change it while *Submitted*, the admin until the request is final.
  - **Remove** a file (owner while *Submitted*, admin always) asks for confirmation. The last file can't be removed; delete the request instead.
  - **Download all (ZIP)** holds every file under its original name; a duplicate name gets a number (`part (2).stl`), also when only the case differs.
  - The admin also sees who submitted it.
  - *Delete request* asks for confirmation, then removes the request and its files.
    - Owners only get the button while the request is *Submitted*.
    - The admin always gets it.
- **Admin:** can submit their own requests like any user. Everyone's requests are in *All requests*; reviewing (status, price) is described in [admin review](admin-review.md).
- **Deleting a user** also deletes their requests and the files on disk.

## Rules

| Who | See / open | Download (file, ZIP) | Add / remove files | Quantity | Delete |
|---|---|---|---|---|---|
| Owner | own requests | own files | only while SUBMITTED (else `409`) | only while SUBMITTED (else `409`) | own, only while SUBMITTED (else `409`) |
| Other user | `404` | `404` | `404` | `404` | `404` |
| Admin | every request (*All requests*) | every file | in any status | until DECLINED / COMPLETED (else `409`) | any, in any status |

Removing the last file of a request is always refused (`409`).

## Upload validation (backend)

Every file is its own HTTP request (the first with `POST /requests`, the others with `POST /requests/{id}/files`), and each goes through the same checks:

1. Extension: `.stl` or `.3mf` (case-insensitive), further limited by `UPLOAD_ALLOWED_EXTENSIONS`.
2. Size: not empty and at most the *max size per file* from [the upload settings](#upload-settings-adminsettings) (default: the ceiling `UPLOAD_MAX_SIZE_MB`, default 100).
   - The HTTP layer itself rejects bodies above the ceiling with `413`. A file just under the limit can still hit it, because the multipart form adds a little overhead. The frontend shows the same "larger than" message for that.
   - nginx in the frontend image has a fixed `client_max_body_size 100m`; raising `UPLOAD_MAX_SIZE_MB` above 100 also needs `docker/default.conf.template` changed. A reverse proxy in front needs a matching `client_max_body_size` as well. Because every file is uploaded on its own, these only have to fit one file.
3. Number of files: an added file is refused (`400`) when the request already holds *max files per request*. The request's row is locked while adding, so two uploads at once can't both pass.
4. Content:
   - **STL:** binary if the file size is exactly 84 + 50 × the triangle count in the header (and at least one triangle). Otherwise ASCII if it starts with `solid` and has a `facet` in the first 64 KB.
   - **3MF:** must be a readable ZIP containing a `.model` part under `3D/`.

This is a sanity check, not a full parse: a file that passes may still not be printable.

## Upload settings (`/admin/settings`)

The *Uploads* card on the admin settings page:

- **Max files per request:** 1–50, default 10.
- **Max size per file:** in MB, from 1 up to the server's ceiling `UPLOAD_MAX_SIZE_MB`, default that ceiling. The card explains what to raise for more (the env var, the frontend's nginx limit, a reverse proxy's `client_max_body_size`).
- Values outside the range are refused with `400`. New uploads use the new limits right away; existing requests keep their files.
- If the ceiling is lowered in the environment later, a saved size above it is capped at the ceiling.
- The allowed extensions stay in the environment (`UPLOAD_ALLOWED_EXTENSIONS`).

## Storage

- Files are stored under `STORAGE_PATH` as `<requestId>/<random-uuid>.<ext>`. The client's filename is never used in a path; it is only kept for display and as the download name.
- In production, set `STORAGE_PATH` to an absolute path (e.g. `/data/uploads`) and mount a volume there. A relative path is resolved against the backend's working directory. The directory must not be served by a web server.
- Disk work follows the database transaction:
  - If creating a request or adding a file fails after the file was stored, the file is removed again.
  - A removed file, and the files of deleted requests or users, are removed after the commit. A failure there is logged and leaves an orphan file; the deletion still succeeds.

## Data

- `print_requests`: owner, name, description, `final_price` (`NUMERIC(10,2)`, EUR, null = pending), status (`SUBMITTED`, `IN_REVIEW`, `ACCEPTED`, `PRINTING` (V7), `DECLINED`, `COMPLETED`; see [admin review](admin-review.md) for the flow), decline reason, timestamps. `ON DELETE CASCADE` from users. `paid_at` (V3) marks when it was paid; only possible with a price.
- `request_files`: one row per file: original filename, storage path, type (`STL`/`THREE_MF`), size, `quantity` (V8, 1–100, existing files got 1). `ON DELETE CASCADE` from requests; a file's model info and slicing result go with it.
- `app_settings.upload_max_files` / `upload_max_file_size_mb` (V8): the upload settings; `NULL` means the default.

## Not included

Review (see [admin review](admin-review.md)), comments, editing name/description, Spoolman/pricing (#11; it will use the quantities). Out of #23: reordering files, per-file statuses or prices, uploading folders or ZIPs to unpack, changing the allowed extensions in the app, resumable uploads.

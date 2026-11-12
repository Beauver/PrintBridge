# Print requests and STL/3MF uploads

Issue: #4 (part of #2).

## What it does

- **My requests** (start page, *My requests* in the navbar): the logged-in user's own requests, newest first. Shows the name, a status badge, the price ("Price pending" until the admin sets one in #5) and the creation date. It's a table on wider screens and cards on phones. With no requests yet, the page shows a *New request* button (plus a hint to create accounts for the admin).
- **New request** (*New request* in the navbar, `/requests/new`):
  - Fields: name (required, max 100), description (optional, max 2000) and one model file (`.stl` or `.3mf`).
  - The file's extension and size are checked in the browser before uploading; the limits come from the backend.
  - A progress bar shows while the file uploads. Afterwards the new request opens.
  - Errors appear above the form, and nothing is created.
- **Request detail** (`/requests/:id`):
  - Shows the name, status, price, decline reason (if any), description, created/updated dates and the file (name, type, size) with a *Download* button.
  - The admin also sees who submitted it.
  - *Delete request* asks for confirmation, then removes the request and its file.
    - Owners only get the button while the request is *Submitted*.
    - The admin always gets it.
- **Admin:** can submit their own requests like any user. Others' requests are opened by URL for now (the overview list comes in #5). The detail page is read-only apart from download and delete.
- **Deleting a user** also deletes their requests and the files on disk.

## Rules

| Who | See / open | Download | Delete |
|---|---|---|---|
| Owner | own requests | own files | own, only while SUBMITTED (else `409`) |
| Other user | `404` | `404` | `404` |
| Admin | every request (list only own until #5) | every file | any, in any status |

## Upload validation (backend)

1. Extension: `.stl` or `.3mf` (case-insensitive), further limited by `UPLOAD_ALLOWED_EXTENSIONS`.
2. Size: at most `UPLOAD_MAX_SIZE_MB` (default 100) and not empty.
   - The HTTP layer itself rejects bodies above that size with `413`. A file just under the limit can still hit it, because the multipart form adds a little overhead. The frontend shows the same "larger than" message for that.
   - nginx in the frontend image has a fixed `client_max_body_size 100m`; raising `UPLOAD_MAX_SIZE_MB` above 100 also needs `docker/default.conf.template` changed.
3. Content:
   - **STL:** binary if the file size is exactly 84 + 50 × the triangle count in the header (and at least one triangle). Otherwise ASCII if it starts with `solid` and has a `facet` in the first 64 KB.
   - **3MF:** must be a readable ZIP containing a `.model` part under `3D/`.

This is a sanity check, not a full parse: a file that passes may still not be printable.

## Storage

- Files are stored under `STORAGE_PATH` as `<requestId>/<random-uuid>.<ext>`. The client's filename is never used in a path; it is only kept for display and as the download name.
- In production, mount a volume on `STORAGE_PATH`. It must not be served by a web server.
- Disk work follows the database transaction:
  - If creating a request fails after the file was stored, the file is removed again.
  - Files of deleted requests or users are removed after the commit. A failure there is logged and leaves an orphan file; the deletion still succeeds.

## Data

- `print_requests`: owner, name, description, `final_price` (`NUMERIC(10,2)`, EUR, null = pending), status (`SUBMITTED`, `IN_REVIEW`, `ACCEPTED`, `DECLINED`, `COMPLETED`; only SUBMITTED is used until #5), decline reason, timestamps. `ON DELETE CASCADE` from users.
- `request_files`: one row per file (exactly one for now): original filename, storage path, type (`STL`/`THREE_MF`), size. `ON DELETE CASCADE` from requests.

## Not included

Admin overview and filters, review actions (#5), 3D preview, comments, editing, multiple files per request, Spoolman/pricing.

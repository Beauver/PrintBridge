# Server slicing

Issue: #15 (builds on [model info](model-info.md), #12). The spike report is on the issue.

Uploads are sliced on the server with [OrcaSlicer](https://github.com/OrcaSlicer/OrcaSlicer), for the admin's printer, so the Model card shows exact filament and print time even when the requester didn't slice the model. It is optional: it needs the slicer service (`PrintBridge-Slicer`, `SLICER_URL`) and is switched on in the admin settings.

## Settings (`/admin/settings`, *Server slicing* card)

- **Status line:** whether the slicer is set up and reachable, with OrcaSlicer's version.
- **Slice uploads on the server:** on/off. Switching it on needs a printer, owned nozzles, a default nozzle and both profiles.
- **Timeout per file:** whole minutes, 1–60 (default 10). Covers all plates of a file; also passed to OrcaSlicer as its per-plate limit.
- **Printer:** every printer model OrcaSlicer has profiles for (430 in 2.5). Choosing one preselects its 0.4 mm nozzle (or the first) and fills in the [build plate](model-info.md) size, which still has to be saved with the build plate's *Save*.
- **Nozzles you own** (checkboxes, the sizes the printer has profiles for) and the **default nozzle** (one of them): used for STL files and for projects made for a nozzle you don't own.
- **Process and filament profile** for the default nozzle, from OrcaSlicer's profiles compatible with that printer + nozzle. The filament field searches as you type and only accepts a listed profile.
- While the slicer can't be reached, the saved choices stay visible but the lists are empty.

## What gets sliced, and how

- New uploads are queued while slicing is on. Older requests are only sliced when the admin clicks **Re-slice** on the Model card (also after changing profiles).
- One job at a time, in the background. The queue is the `slice_results` table, so it survives a restart (a job that was running is queued again).
- Each plate is sliced separately, so a plate OrcaSlicer refuses only loses that plate.

| File | Printer / nozzle | Process and filaments |
|---|---|---|
| STL, plain 3MF, other slicers' projects | your printer, default nozzle (objects re-arranged) | your profiles |
| Bambu/Orca project for a nozzle **you own** | your printer in that nozzle; on the same printer model the project's own printer settings are kept | the project's |
| Bambu/Orca project for a nozzle **you don't own** | your printer, default nozzle | your profiles, keeping the designer's infill %, wall loops and supports on/off; your filament in every slot |
| Bambu/Orca project without a known nozzle | your printer, default nozzle | the project's |

Objects are re-arranged on the plate whenever your printer model differs from the project's.

## On the Model card

- **Slicing…** badge while queued or running; the page reloads every 3 seconds until it is done.
- When done: the server's grams, time and filaments per plate (the same type and colour from several slots are added up), and a *Nozzle* line: "Sliced for a 0,4 mm nozzle", or "Made for a 0,8 mm nozzle · numbers for your 0,4 mm" (then the colours shown are the project's, since your filament was used for every slot).
- A plate OrcaSlicer refused shows its own estimate (`~X g`); the total then says *(partly estimated)*. The admin also sees OrcaSlicer's reason under the plate.
- When the whole job failed (slicer unreachable, timed out, file missing, …) the card falls back to the file's own slice data or the estimate. The admin sees *Slicing failed: <reason>* and can re-slice; the requester just sees the fallback.
- **Re-slice** (admin, while slicing is on): queues the files again. Refused (`409`) while a file is still queued or running.
- Switching slicing off drops queued jobs (a running one finishes). Switching it on again doesn't re-queue them.

## Slicer service

See [PrintBridge-Slicer/README.md](../../PrintBridge-Slicer/README.md): OrcaSlicer's command line behind a small HTTP API, about 1.6 GB, x86_64 only.

- Local: `cd local && docker compose --profile slicer up -d`, with `SLICER_URL=http://localhost:18000` for the backend.
- Production: run the `printbridge-slicer` image next to the backend and set `SLICER_URL` (e.g. `http://slicer:8000`). The service has no authentication: keep it on the internal network.

Measured on the four sample projects and an STL: 1–5 s per file, peak memory 0.4–0.9 GB. Grams and time are within 2–3 % of Bambu Studio's own numbers.

## Data

- `app_settings` (V5): `slicing_enabled`, `slicing_timeout_minutes`, `slicer_printer`, `owned_nozzles` (comma-separated mm), `default_nozzle`, `slicer_process`, `slicer_filament`.
- `slice_results` (V5): one row per file (`ON DELETE CASCADE`): `status` (`QUEUED`, `RUNNING`, `DONE`, `FAILED`), `nozzle`, `made_for_nozzle`, `plates` (JSONB per plate: grams, seconds, filaments or OrcaSlicer's refusal), `error`, `queued_at`, `finished_at`. Only the numbers are kept; the G-code is thrown away.

## Not included

Keeping or downloading the sliced file, uploading your own profiles, multiple printers, prices (#11), 3D preview (#13).

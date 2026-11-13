# Server slicing

Issue: #15 (builds on [model info](model-info.md), #12). The spike report is on the issue.

Uploads are sliced on the server with [OrcaSlicer](https://github.com/OrcaSlicer/OrcaSlicer), once, on one of the admin's printers, so the Model card shows exact filament and print time even when the requester didn't slice the model. It is optional: it needs the slicer service (`PrintBridge-Slicer`, `SLICER_URL`) and is switched on in the admin settings.

## Settings (`/admin/settings`)

**Printers** card (also used for the [fit check](model-info.md), with or without slicing):

- A list of the printers you own; **Add printer**, **Edit**, **Remove** and **Make default** per printer. The first printer you add becomes the default. Removing the default makes the oldest remaining printer the default; the last printer can't be removed while slicing is on.
- **Printer:** every printer model OrcaSlicer has profiles for (430 in 2.5); a model can only be added once. Choosing one preselects its 0.4 mm nozzle (or the first) and fills in its build plate. While the slicer can't be reached, the model is typed in instead (slicing needs OrcaSlicer's exact name).
- **Build plate** X/Y/Z in whole mm (1–2000), required.
- **Nozzles you own** (checkboxes, the sizes the printer has profiles for) and the **default nozzle** (one of them): used for STL files and for projects made for a nozzle you don't own on this printer.
- **Process and filament profile** for the default nozzle, from OrcaSlicer's profiles compatible with that printer + nozzle. The filament field searches as you type and only accepts a listed profile.
- Nozzles and profiles may stay empty while slicing is off; while it is on, every printer needs them.

**Server slicing** card:

- **Status line:** whether the slicer is set up and reachable, with OrcaSlicer's version.
- **Slice uploads on the server:** on/off. Switching it on needs at least one printer, and nozzles and profiles for every printer.
- **Timeout per file:** whole minutes, 1–60 (default 10). Covers all plates of a file; also passed to OrcaSlicer as its per-plate limit.

## What gets sliced, and how

- New uploads are queued while slicing is on. Older requests are only sliced when the admin clicks **Re-slice** on the Model card (also after changing printers or profiles).
- One job at a time, in the background. The queue is the `slice_results` table, so it survives a restart (a job that was running is queued again).
- Every file is sliced once, on one printer (not once per printer). Each plate is sliced separately, so a plate OrcaSlicer refuses only loses that plate.

| File | Printer / nozzle | Process and filaments |
|---|---|---|
| STL, plain 3MF, other slicers' projects | default printer, its default nozzle (objects re-arranged) | that printer's profiles |
| Bambu/Orca project for a printer **you own**, nozzle owned on it | that printer and nozzle, with the project's printer settings | the project's |
| Bambu/Orca project for a printer **you own**, nozzle not owned on it | that printer, its default nozzle | that printer's profiles, keeping the designer's infill %, wall loops and supports on/off; your filament in every slot |
| Bambu/Orca project for a printer **you don't own** | default printer, its default nozzle (objects re-arranged) | the default printer's profiles, keeping the designer's choices as above |
| Bambu/Orca project for an owned printer without a known nozzle | that printer, its default nozzle | the project's |
| Bambu/Orca project without saved settings | default printer, its default nozzle (objects re-arranged) | the project's |

A printer counts as owned when the project names the same model (case doesn't matter); a P1S project is not sliced on an X1 Carbon.

## On the Model card

- **Slicing…** badge while queued or running; the page reloads every 3 seconds until it is done.
- When done: the server's grams, time and filaments per plate (the same type and colour from several slots are added up), and a *Sliced for* line: "Bambu Lab P1S · 0,4 mm nozzle". When it was sliced on another printer or nozzle than the project's: "Made for Bambu Lab A1 · 0,2 mm nozzle. Numbers are for your Bambu Lab P1S · 0,4 mm nozzle" (then the colours shown are the project's, since your filament was used for every slot).
- The fit check uses the build plate of the printer it was sliced for.
- A plate OrcaSlicer refused shows its own estimate (`~X g`); the total then says *(partly estimated)*. The admin also sees OrcaSlicer's reason under the plate.
- When the whole job failed (slicer unreachable, timed out, file missing, …) the card falls back to the file's own slice data or the estimate. The admin sees *Slicing failed: <reason>* and can re-slice; the requester just sees the fallback.
- **Re-slice** (admin, while slicing is on): queues the files again. Refused (`409`) while a file is still queued or running.
- Switching slicing off drops queued jobs (a running one finishes). Switching it on again doesn't re-queue them.

## Slicer service

See [PrintBridge-Slicer/README.md](../../PrintBridge-Slicer/README.md): OrcaSlicer's command line behind a small HTTP API, about 1.6 GB, x86_64 only.

- Local: `cd local && docker compose --profile slicer up -d`, with `SLICER_URL=http://localhost:18000` for the backend.
- Production: run the `printbridge-slicer` image next to the backend and set `SLICER_URL` (e.g. `http://slicer:8000`). The service has no authentication: keep it on the internal network.

Projects saved by Bambu Studio 2.x can hold values OrcaSlicer rejects once a process profile is loaded over them (`raft_first_layer_expansion: -1`); the service fills in OrcaSlicer's default for those.

Measured on the four sample projects and an STL: 1–5 s per file, peak memory 0.4–0.9 GB. Grams and time are within 2–3 % of Bambu Studio's own numbers.

## Data

- `app_settings`: `slicing_enabled`, `slicing_timeout_minutes` (V5), `default_printer_id` (V6).
- `printers` (V6): `model` (unique, case-insensitive), `build_plate_x/y/z`, `owned_nozzles` (`NUMERIC(4,2)[]`, mm), `default_nozzle`, `process`, `filament`. V6 moved the single printer of V4/V5 (when its build plate was set) into this table as the default.
- `slice_results` (V5, printer columns V6): one row per file (`ON DELETE CASCADE`): `status` (`QUEUED`, `RUNNING`, `DONE`, `FAILED`), `printer`, `nozzle`, `made_for_printer`, `made_for_nozzle`, `plates` (JSONB per plate: grams, seconds, filaments or OrcaSlicer's refusal), `error`, `queued_at`, `finished_at`. Only the numbers are kept; the G-code is thrown away.

## Not included

Keeping or downloading the sliced file, uploading your own profiles, slicing a file for every printer, picking the printer per request, prices (#11), 3D preview (#13).

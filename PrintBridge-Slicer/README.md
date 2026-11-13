# PrintBridge slicer service

Optional service that slices uploads for PrintBridge's server slicing ([docs](../docs/features/server-slicing.md)). It runs the [OrcaSlicer](https://github.com/OrcaSlicer/OrcaSlicer) command line behind a small HTTP API (`slicer_service.py`, Python standard library only). The backend decides what to slice with which printer and profiles; this service only runs OrcaSlicer and reads its output.

## API

| Method | Path | Result |
|---|---|---|
| GET | `/health` | `{status, orcaVersion}` |
| GET | `/printers` | printer models with their nozzles: `[{model, vendor, nozzles: [{diameter, machine, bed: {x, y, z}}]}]` |
| GET | `/profiles?printer=&nozzle=` | `{machine, processes, filaments}` compatible with that printer + nozzle |
| POST | `/slice?…` | body: the model file. Query: `format` (`stl`/`3mf`), `printer`, `nozzle`, `plates` (`1,2,3`), `timeout` (seconds), optional `projectPrinter=1`, `process`, `filament`, `filamentSlots`, `overrides` (JSON: `sparse_infill_density`, `wall_loops`, `enable_support`), `arrange=1`. Returns `{printer, nozzle, machine, plates: [{index, grams, seconds, filaments: [{type, color, grams}]} or {index, error}]}` |

Errors: `400` invalid request or unknown profile, `413` file too large (`MAX_UPLOAD_MB`, default 200), `503` busy (one job at a time), `504` timed out.

Each plate is sliced in its own OrcaSlicer run, so a plate OrcaSlicer refuses (print-by-object clearance, wipe tower conflict, too big for the bed, …) only loses that plate.

## Image

- OrcaSlicer's Linux AppImage (pinned version and SHA-256 in the `Dockerfile`), unpacked, on `debian:trixie-slim` (it needs FFmpeg 7). x86_64 only.
- OrcaSlicer's system profiles as JSON from the same release tag (the AppImage only has them packed). At build time the service also writes every profile with its inherited settings merged into `resources/profiles/<vendor>/<kind>_full/`, where OrcaSlicer looks for a project's presets when the printer changes.
- GUI-only parts (printer error docs, translations, icons, fonts, Mesa GPU drivers, …) are removed: about 1.6 GB instead of 2.5 GB.
- Runs as a non-root user; one slicing job at a time.

To update OrcaSlicer: change `ORCA_VERSION` and `ORCA_SHA256` in the `Dockerfile`, rebuild, and check the samples still slice.

## Tests

```sh
python3 -m unittest -v
```

The tests use fake profiles and a fake OrcaSlicer run, so they don't need the image.

## Licence

OrcaSlicer is licensed under the GNU AGPL-3.0; its source and licence are at https://github.com/OrcaSlicer/OrcaSlicer. This service runs it as a separate program. Images that contain OrcaSlicer pass on its licence and source link.

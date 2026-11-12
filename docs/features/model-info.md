# Model info and settings

Issue: #12. The 3D preview is #13; material data from Spoolman is #11.

## What it does

- **Analysis:** every uploaded STL/3MF is read right after the upload and the result is stored with the file. If the file can't be read (odd or damaged, but it passed the [upload validation](print-requests.md#upload-validation-backend)), the upload still succeeds and the result is marked failed.
- **Model card** on the request detail page, for the requester and the admin:

  | | STL / plain 3MF | Bambu Studio / OrcaSlicer project | PrusaSlicer project |
  |---|---|---|---|
  | Dimensions | whole model | per plate (table) | whole model |
  | Plates | 1 | all plates, with names | 1 |
  | Volume | ✅ | ✅ (only what is on a plate) | ✅ |
  | Filament grams | estimate | exact per plate **if sliced**, else estimate | estimate |
  | Print time | – | per plate and total, **if sliced** | – |
  | Filaments (colour + type) | – | per plate: exact if sliced, else from the object/part assignments | from the project settings |
  | "Doesn't fit the build plate" | ✅ | ✅ per plate | ✅ |

  - States: *Model details aren't available yet* (not analysed, see backfill) and *Couldn't read model details from this file* (failed).
- **Sliced** means the project was sliced in Bambu Studio / OrcaSlicer and then saved: the slicer then writes `Metadata/slice_info.config` with the time, weight and filaments per plate. MakerWorld downloads usually aren't sliced. PrusaSlicer project files never contain slicing results.
- **Estimate:** `~X g (estimate)` for the whole model = volume (cm³) × 1.24 g/cm³ (PLA) × 0.35. Both numbers are hardcoded (`FilamentEstimate`, `TODO: #11`). Totals for grams and time are only exact when every plate with objects is sliced; otherwise the card shows the estimate and the sliced plates show their own grams in the table.
- **Fit check:** a plate (or the whole model) fits if X and Y fit as placed or turned 90°, and the height fits Z (0,01 mm slack for rounding). It is done when the request is shown, against the current settings, so changing the plate size applies to existing requests. Without a plate size there is no check; the admin sees a hint linking to *Settings*.
- **Settings** (`/admin/settings`, navbar *Settings*, admin only): the build plate size X/Y/Z in whole mm (1–2000), empty until set. One printer.
- **Older uploads:** at startup the backend analyses all files that have no result yet, one per transaction, in a background thread (`ModelInfoBackfill`, switch: `printbridge.model-info.backfill-on-start`). Failed files keep their failed result and are not retried.
- Numbers are shown the Dutch way like prices: `120 × 80,5 × 40 mm`, `63,2 cm³`, `33,69 g`; times as `2 h 5 min`.

## How files are read (`modelinfo.analysis`)

- **STL:** binary or ASCII (same detection as the upload validation). STL has no unit; millimetres are assumed. Volume is the signed volume of the triangles, so it is only right for a closed mesh.
- **3MF:** the root model part (from `_rels/.rels`, else `3D/3dmodel.model`), objects (meshes, or components that point to objects in other parts as Bambu Studio does with `3D/Objects/*.model`) and the build items with their transforms. Units (`micron` … `meter`) are converted to mm.
- **Bambu Studio / OrcaSlicer** (detected by `Metadata/model_settings.config`):
  - plates and the object instances on them from `model_settings.config`; an instance is the n-th build item of that object;
  - modifier, negative and support parts are left out of size and volume;
  - filament slots from `Metadata/project_settings.config` (`filament_type`, `filament_colour`); slots with the same type and colour are listed once;
  - sliced data from `Metadata/slice_info.config` (`prediction` in seconds, `weight`, `<filament type color used_g>`).
  - Painted (multi-colour painted) objects only show the object's own filament.
- **PrusaSlicer** (detected by `Metadata/Slic3r_PE.config` or `Slic3r_PE_model.config`): `; filament_type = …` / `; filament_colour = …` (semicolon-separated slots) and the extruder per object and volume (volume 0 = the object's). Built from the documented format, **not checked against a real PrusaSlicer file yet**. PrusaSlicer 2.9's multiple beds are not detected; the file counts as one plate.
- Checked against real Bambu Studio files: three unsliced MakerWorld projects (2, 7 and 23 plates) and one sliced project (2 plates).

## Limits and safety

- A 3MF is a ZIP: everything read from it is counted, and the analysis stops above 1 GiB uncompressed (zip bombs) or 5 million vertices kept in memory (60 MB). XML is parsed streaming with DTDs and external entities disabled.
- Components nested more than 8 deep (or in a cycle) and more than 500 plates are refused.
- Text and colours from the file are cleaned before storing: control characters removed, at most 100 characters, colours only as `#RRGGBB`.

## Data

- `model_info` (V4): one row per analysed file (`file_id` = `request_files.id`, `ON DELETE CASCADE`), `status` (`ANALYZED`/`FAILED`), `source` (`MESH`/`BAMBU`/`PRUSA`), `volume_mm3`, `plates` (JSONB: index, name, size, sliced grams/time, filaments), `analyzed_at`. No row = not analysed yet (`PENDING` in the API).
- `app_settings` (V4): a single row (`id = 1`) with `build_plate_x/y/z` (mm, `NULL` until set).

## Not included

3D preview and plate thumbnails (#13), mesh repair/watertight check, object/triangle counts, running a real slicer, multiple printers, price calculation and Spoolman (#11), model stats in the request lists.

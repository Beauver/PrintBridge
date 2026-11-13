# 3D preview

Issue: #13 (builds on [model info](model-info.md) and [server slicing](server-slicing.md)).

The requester and the admin can look at a model in 3D without downloading it, on the request detail page and on the new request form before submitting.

## On the Model card

- **Show 3D preview** loads the viewer; nothing heavy loads before that click. Three.js itself (about 115 kB) is also only downloaded then.
- **Viewer:** drag to rotate, scroll or pinch to zoom, right-drag to pan. About 400 px high (300 px on phones).
- **Plates:** files with more than one plate get plate buttons ("1. Lid", "Plate 2", …; empty plates greyed out). It starts at the first plate with objects. One plate is shown at a time.
- **Colours:** project objects and parts use their filament slot's colour; STL and plain 3MF files one neutral colour. Modifiers, negative parts and support blockers aren't shown.
- **Bed:** a 10 mm grid of the bed of the printer the fit check uses (the one it was sliced for, else the default printer). Without printers there is no grid and the model is centred on 0, 0.
- **Plate images:** Bambu Studio / OrcaSlicer projects carry a rendered image per plate (`Metadata/plate_N.png`). It is shown small in the plate table, and the first plate's image is the placeholder above the button.
- Only shown for files that could be analysed (a file without model details has no preview either).

## On the new request form

After choosing a file with an allowed extension and size, **Show 3D preview** sends it to the backend, which checks it like an upload, reads it and throws it away. The same viewer shows it on the default printer's bed. The file is sent again on *Submit request*.

## Placement

- Bambu Studio / OrcaSlicer keep all plates in one coordinate space: plate N is shifted by 1.2 × the bed size in a grid of ⌈√plates⌉ columns (rows towards −y). Each plate is moved back onto its own bed (bed size from `printable_area` in `project_settings.config`); that bed is then centred on the printer's bed.
- STL files, plain 3MF files and projects without a bed size are centred on the bed and stood on it (lowest point at z = 0).

## Limits and errors

| Case | What the user sees |
|---|---|
| More than 2 million placed triangles (`printbridge.preview.max-triangles`; an object placed twice counts twice) | "This model is too detailed for a preview" |
| More than 5 million vertices or triangles in the file, or anything the model reader refuses | "This model is too detailed for a preview" / "No preview available for this file" |
| No WebGL in the browser, or the 3D code can't be downloaded | "Your browser can't show 3D (WebGL is off or not supported)." |
| Preview before uploading with a wrong extension, empty, too big or not really an STL/3MF | the same message as the upload |

Measured on the four sample projects: 35–350 ms on the server, 130 kB – 1.5 MB transferred (the 23-plate project with 250 000 triangles is the largest).

## How it works

- `PreviewMeshReader` (backend, `modelinfo.analysis`) reuses the model info parser: STL triangles (identical corners shared), 3MF objects, components across parts (`3D/Objects/*.model`), transforms, Bambu plates and part types. For a preview the 3MF reader keeps the triangles, which model info doesn't need.
- `MeshEncoder` (backend, `preview`) writes a compact binary format, gzipped: `"PBM1"`, a uint32 header length, a JSON header (`bed`, `plates`, `parts` with plate, colour and counts), then per part float32 positions and uint32 indices, little-endian. The frontend's `decodeMesh` reads it without copying.
- `ModelPreview` (frontend, `shared/model-viewer`) is the button, spinner and error; `ModelViewer` the plate buttons and canvas; `PreviewScene` the Three.js scene, which only renders when the view changes.
- Nothing is stored or cached: the mesh is read from the file on every request.

## Not included

Model info (grams, time) before uploading, measuring, layer/G-code preview, editing or arranging objects, saving the view as an image, fullscreen, caching meshes, decimating very detailed models.

# Spoolman filaments

Issue: #11. The chosen filaments' density and price feed the [price estimate](pricing.md) (#72).

PrintBridge reads the owner's self-hosted [Spoolman](https://github.com/Donkie/Spoolman): every filament and what is left on its spools. Requesters choose a Spoolman filament for each colour of each plate of their files, and the owner sees what was chosen. PrintBridge never writes to Spoolman.

## Setting it up

- **`SPOOLMAN_URL`**: Spoolman's address as the backend reaches it. Without it nothing can be switched on.
  - Dev mode defaults to `http://localhost:7912`, the Spoolman `docker compose up -d` starts in `local/`.
  - `deploy/` starts one too and defaults to it (`http://spoolman:8000`); see [deployment](../deployment.md#spoolman).
- **`SPOOLMAN_CACHE_TTL_SECONDS`** (default 60): how long the backend keeps the filament list.
  - Opening a dropdown or a request page within that time doesn't ask Spoolman again.
  - A failed read isn't kept: the next one tries again.
  - Switching the integration on forgets the cached list.

## Settings → Spoolman (`/admin/settings?tab=spoolman`)

The settings page has one tab per card: *Printers*, *Slicing*, *Uploads*, *Spoolman* and *Pricing*. The open tab is in the URL (`?tab=`); without one, *Printers* opens.

The Spoolman tab shows:

- **Status line:**
  - *Connected to Spoolman 0.22.1 at http://…*;
  - *Can't reach Spoolman at …: reason*;
  - *Not configured: set SPOOLMAN_URL on the server.*
- **Enable Spoolman integration:** read filaments and stock from Spoolman.
- **Enable colour selection from Spoolman:** requesters choose filaments by clicking the colour circles.
- **Use prices from Spoolman:** the [price estimate](pricing.md) uses each chosen filament's price per gram (#72). Like colour selection it needs the integration and goes off with it. Spoolman's prices are taken to be in PrintBridge's [currency](pricing.md#currency) (#75).
  - Only possible while the integration is on.
  - Switching the integration off switches this off too (the form does it, and the backend refuses colour selection without the integration).
- **Warning when there isn't enough filament:** the text requesters see on a request whose chosen filament runs short (max 300 characters). Empty means the default text.
- The switches are disabled while `SPOOLMAN_URL` isn't set. While it isn't set, they also count as off, whatever is saved.

## Choosing a filament (request page)

- **Who and when:** the same rule as the quantity.
  - The owner while the request is *Submitted*, the admin until the request is final.
  - Colour selection must be on.
  - The request carries `filamentSelection` (true when the caller may choose now); otherwise every circle is read-only.
- **Circles:**
  - The colour circles of the model card are buttons: the *Filaments* row of a one-plate file and the *Colours* cell of every plate in a project's table. A changeable colour is a small outlined pill button (the circle plus a ▾ caret, in the theme's colours, so it shows on any filament colour); read-only colours are plain circles.
  - A choice is for **one colour of one plate**. The same colour on another plate is its own choice.
  - A file without colours (STL that wasn't sliced, analysis pending or failed) and a link get one grey **Pick filament** circle in the card body. It sets one filament for the whole item.
- **Dropdown:**
  - A search box (vendor, name, material).
  - **Every filament in Spoolman**, also used-up ones. Filaments of the colour's material come first, then the rest, each sorted by material, vendor and name.
  - Each row has a colour dot (multi-colour filaments split), `Vendor Name – Material` and `412 g left`.
  - Rows with less left than this colour needs (its grams × the item's quantity) are marked *Not enough in stock*. They can still be chosen.
  - *Use file colour* removes the choice.
  - States: *Loading filaments…*, *Couldn't load filaments from Spoolman* (with *Try again*), *No filaments in Spoolman*, *No filaments match*.
  - The list is loaded once per page visit. A refused choice reloads it.
- **A chosen filament:**
  - The circle takes its colour, and its tooltip reads `Vendor Name – Material (was PLA #FFFFFF)`.
  - As soon as a colour of a plate has a choice, the one-plate *Filaments* row and the preview's *Colours* row show only the circles. An **Old colours** row under them shows the file's own colours (circle + hex, read-only). Without choices the *Filaments* row keeps its labels (`PLA · #FFFFFF · 33.69 g`). A whole file or link keeps its label next to the circle.
  - A filament since deleted in Spoolman still shows what was chosen (a copy is kept), with *· no longer in Spoolman*.

- **3D preview:** the [preview](model-preview.md) draws the chosen filaments' colours, live while choosing (see there for how parts are matched). Under its plate buttons it shows the colour circles of the plate on screen (or the whole-file circle), the same pickers as in the card, so colours can be changed while watching the model. Only while choosing is allowed or something was chosen.

### Automatic matching

Issue: #76.

- **Settings → Spoolman:**
  - **Match filaments automatically**, under colour selection (default off). It needs colour selection and goes off with it.
  - **How close:**
    - *Strict* (ΔE 10, only very close shades);
    - *Normal* (ΔE 20, the same colour family, default);
    - *Loose* (ΔE 35, roughly the same colour);
    - or *Custom*: a whole number from 1 to 100.
  - **Warning when no close colour is found:** max 300 characters. Empty means the default text, *No close colour found, please select another colour for this print*.
- **When it runs:**
  - after an upload is analysed and after every successful server slice or re-slice (`FilePlatesChanged`);
  - only for Bambu/Orca projects: an STL's or plain 3MF's colours come from the slicer profile, so they are left alone;
  - only while the request isn't final;
  - existing files aren't matched when it is switched on.
- **What it picks:** every colour nobody decided on gets the Spoolman filament closest in colour. The difference is measured with CIEDE2000 ΔE, which tells how different two colours look to people.
  - **Candidates:** same material (case-insensitive; any material when the file doesn't name one), a single colour, and within the tolerance.
  - **Ignored:** stock and multi-colour filaments.
  - **The result** is saved as a normal choice marked *auto* (`source: AUTO`), so the price estimate, stock check, preview colours and density all use it.
- **No filament close enough:** the colour is stored as *no match*.
  - The owner and admin see the warning on every plate that has such a colour: under the plate in the plate table, under the *Filaments* row of a one-plate file, and under the colours below the 3D preview.
  - The colour's pill button gets a red outline (a red ring on a read-only circle).
  - Choosing a filament for that colour clears it.
- **Never touched by matching:**
  - manual choices;
  - colours already matched;
  - colours where someone chose *Use file colour* on an automatic match. That reset is remembered, so a re-slice doesn't match the colour again.
- **Spoolman can't be read:** nothing is stored and no warning shows; the next slice tries again.
- **What people see:** automatic choices look like any choice. The tooltip adds *Matched automatically*, and a small muted *auto* shows next to the circle in the one-plate *Filaments* row and under the preview. Choosing another filament makes it a normal choice.

### Not enough filament

- **Grams needed per filament:** what the whole request needs of it. For every colour it was chosen for, take the grams for one copy and multiply by the item's quantity:
  - for a plate's colour: that colour's sliced grams; else, when it is the plate's only colour, the plate's grams (exact or estimated); else unknown;
  - for a whole file: its total grams; a link has none.
  - Declined items, choices the file no longer has, and unknown grams don't count.
- **Warning:** when more is needed than is left on the filament's spools that aren't archived, or nothing is left at all:
  - every circle using it gets a ⚠;
  - the card shows the admin's warning text (*Settings → Spoolman*), by default *Not enough filament for this print, it will be ordered but your print might be delayed*.

  Owner and admin both see it. A spool whose weight Spoolman doesn't know makes the amount left unknown: no warning.
- **When it is checked:** only while the integration is on and Spoolman can be read. Otherwise the choices still show, without stock information.

### Re-slicing and switching off

- **Re-slicing:** [server slicing](server-slicing.md) or *Re-slice with…* can change a plate's colours.
  - Choices that still match a plate + colour stay on their circle.
  - The others are kept and listed under **No longer in this file** in the card (filament, plate and original colour), with *Remove* for whoever may choose.
  - A whole-file choice is listed there too once the file has colours.
- **Switching off:** with colour selection or the integration off, saved choices stay visible and read-only.

## API

See [API](../api.md#spoolman).

- `PUT /requests/{id}/files/{fileId}/filaments` chooses a filament.
- `DELETE /requests/{id}/files/{fileId}/filaments/{choiceId}` removes one.
- `GET /spoolman/filaments` lists the filaments.
- `GET /spoolman/status` and `PUT /settings/spoolman` serve the settings tab.

Status codes:

- `400`: the file has no such colour on that plate, or Spoolman has no such filament.
- `409`: colour selection is off, or the request may no longer change for the caller.
- `503`: Spoolman can't be read while choosing.
- `404`: someone else's request.

## Data

- `color_decisions` (V17): colours decided on without a filament: `NO_MATCH` or `FILE_COLOR` (an automatic match reset to the file's colour), per file, plate, type and colour; they go with their file.
- `app_settings` (V13, V14, V17):
  - `spoolman_enabled`, `spoolman_color_selection` (default off);
  - `spoolman_not_enough_message` (V14; null = the default text);
  - `spoolman_auto_match` (needs colour selection), `spoolman_match_tolerance` (1–100, default 20), `spoolman_no_match_message` (V17).
  - a check that colour selection needs the integration.
- `filament_choices` (V13), one row per choice:
  - `file_id` (`request_files`, `ON DELETE CASCADE`, so choices go with their file, link or request);
  - `plate_index`, `original_type`, `original_color` (all null for a whole item);
  - `spoolman_filament_id`;
  - a copy of the filament's name, vendor, material and colour(s);
  - `chosen_at`;
  - `source` (V17, `MANUAL`/`AUTO`).

  A unique index allows one choice per item + plate + type + colour (colour ignoring case).

## Not included

- Writing to Spoolman, reserving or deducting spools.
- Slicing with a profile matching the chosen filament.
- Choosing on the *New request* form (files aren't analysed yet there).
- Choices in the request lists.
- An editable Spoolman URL in the app.

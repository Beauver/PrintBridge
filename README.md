# PrintBridge

A web app where registered users can request custom 3D prints from you. Users upload a model (STL or 3MF), pick a filament from what's actually in stock, and submit a request with an indicative price. You review each request, discuss it via comments, adjust the final price, and accept or decline it.

Payment is out of scope: prices are indications and payment happens in person.

## Features

- **Requesters:** register and log in, upload STL/3MF files with a 3D preview, choose filament (colour and material) from live data, set quantity and notes, see the estimated and final price, track status and comment.
- **Owner (admin):** dashboard of all requests with filters and search, review the model and choices, comment, edit details and the final price, accept or decline with a reason, update status.
- **Filaments from Spoolman:** the backend reads your self-hosted [Spoolman](https://github.com/Donkie/Spoolman) (read-only, with caching), so options always reflect your inventory. Requesters pick a filament per colour of each plate; when there isn't enough left, they're told it will be ordered and the print may be delayed.
- **Model info and server slicing:** dimensions, plates, filament and print time per upload. With the optional slicer service, every upload is sliced with OrcaSlicer for your printer, so the numbers are exact.
- **Price indication:** estimated weight × the chosen filament's Spoolman price per gram × a markup, or a fixed price per 100 g, behind a `PricingStrategy`. Estimated grams use the filament's density. The owner's final price overrides it.

Status flow: `SUBMITTED → IN_REVIEW → ACCEPTED / DECLINED → COMPLETED`

## Tech stack

| Part     | Technology                                        |
|----------|---------------------------------------------------|
| Backend  | Quarkus, REST, Hibernate ORM with Panache, SmallRye JWT |
| Database | PostgreSQL                                        |
| Frontend | Angular (Three.js for model preview)              |
| Integration | Spoolman REST API (`/api/v1/`), backend only   |

## Repository layout

```
PrintBridge-Backend/    Quarkus API
PrintBridge-Frontend/   Angular app
PrintBridge-Slicer/     optional slicer service (OrcaSlicer behind a small HTTP API)
local/                  docker compose for development dependencies (Postgres, slicer, Spoolman)
deploy/                 docker compose for running the published images
docs/                   architecture, auth, API, deployment and per-feature documentation
```

## Getting started

**Running it:** see [deployment](docs/deployment.md) for the compose file in `deploy/`.

**Developing:**

1. Start the local dependencies, see [local/README.md](local/README.md):
   ```sh
   cd local && cp .env.example .env && docker compose up -d
   ```
2. Run the backend: `./gradlew quarkusDev` in `PrintBridge-Backend` (JDK 21+). It connects to the local Postgres with its defaults and runs the Flyway migrations. To change a setting, copy `PrintBridge-Backend/.env.example` to `PrintBridge-Backend/.env`.
3. Run the frontend: `npm start` in `PrintBridge-Frontend`, then open http://localhost:4200. The dev server proxies `/api` to the backend on :8080.
4. The first visit asks you to create the admin account.

Dev mode and tests generate throwaway JWT keys; real keys are only needed for a deployment. Documentation lives in [`docs/`](docs/README.md).

All external connections (database, file storage, slicer) are configured through environment variables.

## Out of scope (v1)

Payments, writing back to Spoolman (including filament deduction), print queues, configurable pricing rules, and multi-owner support.

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first: commits need a `Signed-off-by` line, and contributors allow the project to relicense their changes.

## Licence

Copyright (C) 2026 Beauver

PrintBridge is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version (SPDX: `AGPL-3.0-or-later`). It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See [LICENSE](LICENSE) for the full text.

In short:
- **You may** use PrintBridge for anything, including commercially (e.g. running it for paying customers), and change it.
- **If you hand out a changed version** (code or images), you must give its source under the same licence.
- **If you run a changed version for other people over a network**, you must offer them its source too: point `SOURCE_URL` at your code, so the *Source* link in the app's footer leads there.

The optional slicer image contains [OrcaSlicer](https://github.com/OrcaSlicer/OrcaSlicer) (AGPL-3.0); see [PrintBridge-Slicer/README.md](PrintBridge-Slicer/README.md#licence). More details: [docs/licence.md](docs/licence.md).

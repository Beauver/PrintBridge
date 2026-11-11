# PrintBridge

A web app where registered users can request custom 3D prints from you. Users upload a model (STL or 3MF), pick a filament from what's actually in stock, and submit a request with an indicative price. You review each request, discuss it via comments, adjust the final price, and accept or decline it.

Payment is out of scope: prices are indications and payment happens in person.

## Features

- **Requesters:** register and log in, upload STL/3MF files with a 3D preview, choose filament (colour and material) from live data, set quantity and notes, see the estimated and final price, track status and comment.
- **Owner (admin):** dashboard of all requests with filters and search, review the model and choices, comment, edit details and the final price, accept or decline with a reason, update status.
- **Filaments from Spoolman:** the backend reads your self-hosted [Spoolman](https://github.com/Donkie/Spoolman) (read-only, with caching), so options always reflect your inventory. Used-up filaments are hidden.
- **Price indication:** estimated weight × price per gram × quantity, behind a `PricingStrategy` so it can be extended later. The owner's final price overrides it.

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
local/                  docker-compose for local dependencies (Postgres, Spoolman)
.env.example            configuration template
```

## Getting started

1. Copy `.env.example` to `.env` and adjust the values.
2. Start the local dependencies, see [local/README.md](local/README.md):
   ```sh
   cd local && cp .env.example .env && docker compose up -d
   ```
3. Generate the JWT keys (also described in `local/README.md`).
4. Run the backend (`./gradlew quarkusDev` in `PrintBridge-Backend`) and the frontend (`npm start` in `PrintBridge-Frontend`).

All external connections (database, Spoolman, file storage) are configured through environment variables.

## Out of scope (v1)

Payments, writing back to Spoolman (including filament deduction), automatic slicing or print queues, configurable pricing rules, and multi-owner support.

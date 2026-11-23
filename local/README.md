# Local development stack

The services the backend needs while you run it and the frontend from source. To run PrintBridge itself from the published images, see [`deploy/`](../deploy/README.md).

```sh
cd local
cp .env.example .env   # first time only
docker compose up -d
```

| Service  | URL / port                   |
|----------|------------------------------|
| Postgres | `localhost:5432`             |
| Slicer | http://localhost:18000 (`/health`) |
| Spoolman | http://localhost:7912 |

`docker compose up -d` starts all of them. The first time it builds the OrcaSlicer image (a few minutes, ~1.6 GB, x86_64 only). To leave a service out: `docker compose up -d --scale slicer=0` (or `spoolman=0`).

All ports are bound to `127.0.0.1`: they are not reachable from other machines.

The backend's own settings have defaults that match this stack. To change one, copy `PrintBridge-Backend/.env.example` to `PrintBridge-Backend/.env`. If you change the Postgres settings or ports in `local/.env`, set the matching `DB_URL`, `DB_USER`, `DB_PASSWORD`, `SLICER_URL` or `SPOOLMAN_URL` there.

## Server slicing

In dev mode the backend finds it at http://localhost:18000 by default (override with `SLICER_URL`). Switch it on and choose the printer in the admin *Settings* page. See [server slicing](../docs/features/server-slicing.md).

## Spoolman

Add a few filaments and spools in its web UI on http://localhost:7912. In dev mode the backend finds it there by default (override with `SPOOLMAN_URL`). Switch it on in *Settings → Spoolman*. See [Spoolman filaments](../docs/features/spoolman.md).

## JWT keys

Not needed for development: in dev mode and in tests the backend generates a throwaway key pair at startup. Production keys are described in [deployment](../docs/deployment.md#jwt-keys).

## Reset

`docker compose down -v` wipes the database and Spoolman's data. Uploaded files of a dev run are in `PrintBridge-Backend/local/data/uploads` (git-ignored).

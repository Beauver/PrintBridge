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
| Slicer (optional, `--profile slicer`) | http://localhost:18000 (`/health`) |

Both ports are bound to `127.0.0.1`: they are not reachable from other machines.

The backend's own settings have defaults that match this stack. To change one, copy `PrintBridge-Backend/.env.example` to `PrintBridge-Backend/.env`.

## Server slicing (optional)

```sh
docker compose --profile slicer up -d   # builds the OrcaSlicer image the first time (a few minutes, ~1.6 GB)
```

In dev mode the backend finds it at http://localhost:18000 by default (override with `SLICER_URL`). Switch it on and choose the printer in the admin *Settings* page. See [server slicing](../docs/features/server-slicing.md).

## JWT keys

Not needed for development: in dev mode and in tests the backend generates a throwaway key pair at startup. Production keys are described in [deployment](../docs/deployment.md#jwt-keys).

## Reset

`docker compose down -v` wipes the database. Uploaded files of a dev run are in `PrintBridge-Backend/local/data/uploads` (git-ignored).

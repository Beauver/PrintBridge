# Local development stack

```sh
cd local
cp .env.example .env   # first time only
docker compose up -d
```

| Service  | URL / port                   |
|----------|------------------------------|
| Postgres | `localhost:5432`             |
| Spoolman | http://localhost:7912 (UI + `/api/v1/`) |
| Slicer (optional, `--profile slicer`) | http://localhost:18000 (`/health`) |

Add some filaments in the Spoolman UI so the picker has data.

## Server slicing (optional)

```sh
docker compose --profile slicer up -d   # builds the OrcaSlicer image the first time (a few minutes, ~1.6 GB)
```

In dev mode the backend finds it at http://localhost:18000 by default (override with `SLICER_URL`). Switch it on and choose the printer in the admin *Settings* page. See [server slicing](../docs/features/server-slicing.md).

## JWT keys (SmallRye JWT)

```sh
mkdir -p keys && cd keys
openssl genpkey -algorithm RSA -out privateKey.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in privateKey.pem -out publicKey.pem
```

`local/keys/` and `local/data/` are git-ignored.

## Reset

`docker compose down -v` wipes the database and Spoolman data.

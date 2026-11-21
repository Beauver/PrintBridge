# Deployment

How to run PrintBridge from the published images with the compose file in [`deploy/`](../deploy). For development, see [`local/`](../local/README.md).

## What runs

| Service | Image | Reachable from |
|---|---|---|
| `frontend` | `beauver/printbridge-frontend` (nginx: the web app, and `/api` forwarded to the backend) | the published port |
| `backend` | `beauver/printbridge-backend` | the compose network only |
| `postgres` | `postgres:17-alpine` | the compose network only |
| `slicer` | `beauver/printbridge-slicer` | the compose network only |
| `spoolman` | `ghcr.io/donkie/spoolman` | `SPOOLMAN_BIND:SPOOLMAN_PORT` (default `127.0.0.1:7912`), for its own web UI |

`docker compose up -d` starts all of them. Server slicing and Spoolman still stay off until the admin switches them on in *Settings*. To leave the slicer or Spoolman out, start with `docker compose up -d --scale slicer=0` (or `spoolman=0`).

Only the frontend has a public port. The slicer has no authentication, so it must stay that way. Spoolman has none either: its port is bound to `127.0.0.1` by default; put a proxy with a login in front before opening it up.

Data lives in two named volumes and one folder:

| Where | What |
|---|---|
| volume `postgres-data` | the database |
| volume `uploads` | the uploaded model files |
| `deploy/keys/` | the JWT key pair |
| volume `spoolman-data` | the bundled Spoolman's database |

## First start

1. Copy the example settings and fill in the two required ones:
   ```sh
   cd deploy
   cp .env.example .env
   ```
   - `PUBLIC_URL`: the address users will open, e.g. `https://print.example.com`.
   - `POSTGRES_PASSWORD`: a long random password. It is only used between the backend and the database.
2. Create the JWT keys, see [below](#jwt-keys).
3. Start it:
   ```sh
   docker compose up -d
   ```
4. Put a reverse proxy with HTTPS in front, see [HTTPS](#https).
5. Open `PUBLIC_URL`. The first visit asks you to create the admin account.

## JWT keys

The backend signs login tokens with an RSA key pair that you create once. Run this in `deploy/`:

```sh
mkdir -p keys
openssl genpkey -algorithm RSA -out keys/privateKey.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in keys/privateKey.pem -out keys/publicKey.pem
sudo chown 185 keys/privateKey.pem
chmod 600 keys/privateKey.pem
```

- The backend runs as user id 185 in its container and has to read both files; the `chown` gives it the private key. If you can't use `sudo`, `chmod 644 keys/privateKey.pem` works too, but then every user on the machine can read the key.
- Keep the keys: replacing them logs everyone out. `deploy/keys/` is git-ignored.

## HTTPS

The compose file serves plain HTTP on `HTTP_BIND:HTTP_PORT` (default `127.0.0.1:8080`). Run a reverse proxy with a certificate in front of it (Caddy, Traefik, nginx, a tunnel) and point it at that port. Without HTTPS users can't stay logged in: the login cookie is only sent over HTTPS.

Things the proxy has to do:

- **Allow large uploads.** At least `UPLOAD_MAX_SIZE_MB` (default 100 MB) as the maximum request body.
- **Pass the host name**, so the app sees the address users opened (`Host`, or `X-Forwarded-Host`).

**Client addresses and login throttling.** Login is limited per client address (see [auth](auth.md#login-throttling)). Behind your proxy, the PrintBridge frontend sees every request coming from the proxy, so all users share one address and the limit of 10 logins a minute applies to everyone together. For a small installation that is usually fine. To count per real client, give the frontend's nginx the real address: extend the frontend image with a config that sets `set_real_ip_from <your proxy's address>` and `real_ip_header X-Forwarded-For`.

For a quick test without HTTPS, set `AUTH_COOKIE_SECURE=false` and `PUBLIC_URL=http://<host>:8080`. Don't leave it like that.

## Settings

All in `deploy/.env`; the file explains each one. The ones that matter most:

| Setting | Default | Meaning |
|---|---|---|
| `PUBLIC_URL` | (required) | The address users open. |
| `POSTGRES_PASSWORD` | (required) | Database password. |
| `HTTP_BIND`, `HTTP_PORT` | `127.0.0.1`, `8080` | Where the app listens on the host. |
| `PRINTBRIDGE_VERSION` | `latest` | Image tag for all three images. |
| `UPLOAD_MAX_SIZE_MB` | `100` | Ceiling per uploaded file, for nginx and the backend. The admin sets the actual limit in *Settings → Uploads*, up to this. |
| `SLICER_URL` | `http://slicer:8000` | The bundled slicer; empty to not use one. |
| `SPOOLMAN_URL` | `http://spoolman:8000` | The bundled Spoolman, or your own as the backend container reaches it; empty to not use one. |
| `SPOOLMAN_CACHE_TTL_SECONDS` | `60` | How long the filament list from Spoolman is kept. |
| `AUTH_COOKIE_SECURE` | `true` | `false` only for a test without HTTPS. |
| `SOURCE_URL` | this repository | Where the footer's source link points; change it if you run a changed version for others (AGPL-3.0). |

Other backend settings (token lifetimes, `RATE_LIMIT_ENABLED`, ...) are listed in `PrintBridge-Backend/.env.example`; add them under `backend: environment:` in the compose file if you need them.

## Server slicing

The slicer starts with everything else. Switch slicing on in *Settings → Slicing* after adding your printers. The image is about 1.6 GB and x86_64 only (on ARM, start with `--scale slicer=0`). See [server slicing](features/server-slicing.md).

## Spoolman

PrintBridge reads filaments and stock from [Spoolman](https://github.com/Donkie/Spoolman) (read-only). The bundled one starts with everything else: manage your spools in its web UI (`http://127.0.0.1:7912`) and switch the integration on in *Settings → Spoolman*. If you already run your own, set `SPOOLMAN_URL` to an address the backend container can reach and start with `--scale spoolman=0`. See [Spoolman filaments](features/spoolman.md).

## Updating

```sh
docker compose pull
docker compose up -d
```

- Database changes are applied by the backend when it starts.
- Pin `PRINTBRIDGE_VERSION` to a release tag if you want to choose when to update.
- Make a backup first.

## Backups

Three things, all needed to restore an installation:

```sh
# database
docker compose exec postgres pg_dump -U printbridge printbridge > printbridge.sql
# uploaded files
docker run --rm -v printbridge-app_uploads:/data:ro -v "$PWD":/backup alpine tar czf /backup/uploads.tar.gz -C /data .
# keys and settings: copy deploy/keys/ and deploy/.env
```

The volume names start with the compose project name, `printbridge-app`.

## Removing

`docker compose down` stops everything and keeps the data. `docker compose down -v` also deletes the database and the uploads.

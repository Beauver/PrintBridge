# Deployment

Compose file for running PrintBridge from the published images: web app, backend, PostgreSQL, the slicer and Spoolman; `docker compose up -d` starts them all. Only the web app gets a public port (Spoolman's web UI stays on `127.0.0.1`); put a reverse proxy with HTTPS in front of it.

```sh
cd deploy
cp .env.example .env        # set PUBLIC_URL and POSTGRES_PASSWORD
# create the JWT keys once: see docs/deployment.md
docker compose up -d
```

Everything else (keys, HTTPS, updates, backups, slicer, Spoolman) is in [docs/deployment.md](../docs/deployment.md). For development use [`local/`](../local/README.md) instead.

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

Add some filaments in the Spoolman UI so the picker has data.

## JWT keys (SmallRye JWT)

```sh
mkdir -p keys && cd keys
openssl genpkey -algorithm RSA -out privateKey.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in privateKey.pem -out publicKey.pem
```

`local/keys/` and `local/data/` are git-ignored.

## Reset

`docker compose down -v` wipes the database and Spoolman data.

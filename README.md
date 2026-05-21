# Masterdoc API (Kotlin proxy)

Proxies Onyx for KMP clients. Public base: `https://api.masterdoc.pro/v1`.

## Env (server: `/etc/masterdoc/backend.env`)

```bash
ONYX_BASE_URL=http://127.0.0.1:3000/api
ONYX_PAT=onyx_pat_...
PORT=8081
```

## Run locally

```bash
export ONYX_BASE_URL=http://80.87.196.33:3000/api
export ONYX_PAT=...
./gradlew run
curl http://127.0.0.1:8081/health
curl http://127.0.0.1:8081/v1/assistants
```

## Deploy

Push to `main` → GitHub Actions builds JAR, rsyncs to Onyx VPS, systemd restart.

Nginx on `80.87.196.33`: `api.masterdoc.pro` → `127.0.0.1:8081`.

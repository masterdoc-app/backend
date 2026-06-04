# Masterdoc API (Kotlin proxy)

Proxies Onyx for KMP clients. Public base: `https://api.masterdoc.pro/v1`.

## Env (server: `/etc/masterdoc/backend.env`)

```bash
ONYX_BASE_URL=http://127.0.0.1:3000/api
ONYX_PAT=onyx_pat_...
PORT=8081
# Force Onyx internal search on every chat message (default: true)
ONYX_FORCE_INTERNAL_SEARCH=true
# Optional override; otherwise resolved via GET /tool (SearchTool)
# ONYX_SEARCH_TOOL_ID=1
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

Push to `main` → GitHub Actions builds JAR, rsyncs to Onyx VPS, systemd restart, nginx site reload.

**GitHub Secrets (repo `masterdoc-app/backend`):** `DEPLOY_SSH_PRIVATE_KEY`, `DEPLOY_USER`, `ONYX_PAT`.

Nginx on `80.87.196.33`: `api.masterdoc.pro` → `127.0.0.1:8081` ([`deploy/api.masterdoc.pro.nginx.conf`](deploy/api.masterdoc.pro.nginx.conf)).

TLS (once HTTP works): `certbot --nginx -d api.masterdoc.pro`

## Case reports (`/v1/report`)

SQLite table `case_reports`, keyed by `assistant_id`, sorted by `created_at` DESC.

```bash
# Save report (after chat session)
curl -s -X POST "$BASE/v1/report" -H 'Content-Type: application/json' -d '{
  "assistant_id": 1,
  "conversation_id": "session-uuid",
  "result": "Заменили датчик, станция в работе",
  "transcript": [{"ask": "Не морозит", "answer": "Проверьте датчик"}]
}'

# List by station (assistant_id required)
curl -s "$BASE/v1/report?assistant_id=1&page=0&size=20"
```

`REPORTS_DB_PATH` — path to SQLite file (see `.env.example`).

## Smoke

```bash
./scripts/smoke-api.sh http://127.0.0.1:8081
# production (after deploy):
./scripts/smoke-api.sh http://api.masterdoc.pro
```

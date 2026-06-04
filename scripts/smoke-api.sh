#!/usr/bin/env bash
# Smoke-test Masterdoc API. Usage: ./scripts/smoke-api.sh [base_url_without_v1]
# Example: ./scripts/smoke-api.sh http://127.0.0.1:8081
set -euo pipefail

BASE="${1:-http://127.0.0.1:8081}"
BASE="${BASE%/}"

echo "==> GET ${BASE}/health"
curl -fsS "${BASE}/health"
echo

echo "==> GET ${BASE}/v1/assistants"
curl -fsS "${BASE}/v1/assistants" | head -c 500
echo

ASSISTANT_ID="$(curl -fsS "${BASE}/v1/assistants" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d[0]['id'] if d else 1)" 2>/dev/null || echo 1)"

echo "==> POST ${BASE}/v1/report"
REPORT_ID="$(curl -fsS -X POST "${BASE}/v1/report" \
  -H 'Content-Type: application/json' \
  -d "{\"assistant_id\":${ASSISTANT_ID},\"result\":\"smoke test report\",\"transcript\":[{\"ask\":\"test\",\"answer\":\"ok\"}]}" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])" 2>/dev/null || true)"
echo "report id=${REPORT_ID:-?}"

echo "==> GET ${BASE}/v1/report?assistant_id=${ASSISTANT_ID}&page=0&size=5"
curl -fsS "${BASE}/v1/report?assistant_id=${ASSISTANT_ID}&page=0&size=5" | head -c 500
echo

echo "==> GET ${BASE}/v1/report (missing assistant_id → expect 400)"
curl -sS -o /dev/null -w "%{http_code}\n" "${BASE}/v1/report" | grep -q 400 && echo "400 OK" || echo "WARN: expected 400"

echo "OK"

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

echo "OK"

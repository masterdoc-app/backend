#!/usr/bin/env bash
# Smoke: create session + streaming send. Prints packet types seen.
set -euo pipefail
BASE="${1:-http://127.0.0.1:8081/v1}"

SESSION=$(curl -sf -X POST "$BASE/chat/sessions" \
  -H 'Content-Type: application/json' \
  -d '{"persona_id":1}' | python3 -c "import sys,json; print(json.load(sys.stdin)['chat_session_id'])")
echo "session=$SESSION"

TMP=$(mktemp)
curl -sfN -X POST "$BASE/chat/sessions/$SESSION/messages" \
  -H 'Content-Type: application/json' \
  -d "{\"message\":\"Почему не отжимает?\",\"chat_session_id\":\"$SESSION\",\"stream\":true}" \
  > "$TMP"

LINES=$(wc -l < "$TMP")
echo "lines=$LINES"
python3 <<PY
import json, re
from pathlib import Path
text = Path("$TMP").read_text()
types = []
for line in text.splitlines():
    line = line.strip().removeprefix("data:").strip()
    if not line.startswith("{"):
        continue
    try:
        o = json.loads(line)
    except json.JSONDecodeError:
        continue
    if "answer" in o:
        types.append("answer_json")
        continue
    t = o.get("obj", o).get("type")
    if t:
        types.append(t)
print("packet_types:", ", ".join(types[:30]) + ("..." if len(types) > 30 else ""))
print("unique:", ", ".join(sorted(set(types))))
ans = ""
for line in text.splitlines():
    line = line.strip().removeprefix("data:").strip()
    if not line.startswith("{"):
        continue
    try:
        o = json.loads(line)
    except json.JSONDecodeError:
        continue
    if o.get("answer"):
        ans = o["answer"][:200]
    if o.get("obj", {}).get("type") == "message_delta":
        ans += o.get("obj", {}).get("content", "")
print("answer_preview:", (ans or "(empty)")[:300])
PY
rm -f "$TMP"

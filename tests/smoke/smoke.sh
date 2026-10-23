#!/usr/bin/env bash
# Fast smoke test (< 30 s): is the stack up and does the happy path answer?
#   ./smoke.sh            (env: BASE_URL, API_URL, GRAFANA_URL, MIMIR_URL, LOKI_URL, TEMPO_URL)
set -u
BASE_URL=${BASE_URL:-http://localhost:3000}
API_URL=${API_URL:-http://localhost:8080}
GRAFANA_URL=${GRAFANA_URL:-http://localhost:3001}
MIMIR_URL=${MIMIR_URL:-http://localhost:9009}
LOKI_URL=${LOKI_URL:-http://localhost:3100}
TEMPO_URL=${TEMPO_URL:-http://localhost:3200}
CHECK_OBS=${CHECK_OBS:-1}   # Mimir/Loki/Tempo are not published on host ports by default -> only checked when reachable
fail=0
ok()   { printf '  \033[32mPASS\033[0m %s\n' "$1"; }
bad()  { printf '  \033[31mFAIL\033[0m %s\n' "$1"; fail=$((fail+1)); }
skip() { printf '  \033[33mSKIP\033[0m %s\n' "$1"; }
code() { curl -s -o /dev/null -m 8 -w '%{http_code}' "$@"; }
expect() { # name expected_code curl-args...
  local n=$1 e=$2; shift 2; local c; c=$(code "$@")
  [ "$c" = "$e" ] && ok "$n ($c)" || bad "$n (got $c, want $e)"
}
echo "== frontend / gateway"
expect "frontend index"         200 "$BASE_URL/"
expect "frontend /healthz"      200 "$BASE_URL/healthz"
expect "gateway health (via nginx)" 200 "$BASE_URL/api/v1/auth/login" -X POST -H 'Content-Type: application/json' -d '{"email":"admin@example.com","password":"password123"}'
expect "gateway rejects no token"  401 "$API_URL/api/v1/users/suggestions"

echo "== login as seeded admin"
LOGIN=$(curl -s -m 8 -X POST "$API_URL/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"password123"}')
TOKEN=$(printf '%s' "$LOGIN" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
UID_=$(printf '%s' "$LOGIN" | sed -n 's/.*"userId":"\([^"]*\)".*/\1/p')
if [ -n "$TOKEN" ]; then ok "admin login"; else bad "admin login: $LOGIN"; fi
A=(-H "Authorization: Bearer $TOKEN")
if [ -n "$TOKEN" ]; then
  expect "GET profile"            200 "$API_URL/api/v1/users/$UID_" "${A[@]}"
  expect "GET suggestions"        200 "$API_URL/api/v1/users/suggestions" "${A[@]}"
  expect "GET feed"               200 "$API_URL/api/v1/feed/$UID_" "${A[@]}"
  expect "GET search people"      200 "$API_URL/api/v1/search/people?q=admin" "${A[@]}"
  expect "GET notifications"      200 "$API_URL/api/v1/notifications" "${A[@]}"
  expect "GET unread-count"       200 "$API_URL/api/v1/notifications/unread-count" "${A[@]}"
fi

echo "== observability"
expect "Grafana health"  200 "$GRAFANA_URL/api/health"
if [ "$CHECK_OBS" = 1 ]; then
  for pair in "Mimir:$MIMIR_URL/ready" "Loki:$LOKI_URL/ready" "Tempo:$TEMPO_URL/ready"; do
    n=${pair%%:*}; u=${pair#*:}; c=$(code "$u")
    case $c in 200) ok "$n ready";; 000) skip "$n not published on a host port (run via Grafana datasource health instead)";; *) bad "$n ($c)";; esac
  done
  c=$(code -u "${GRAFANA_USER:-admin}:${GRAFANA_PASSWORD:-admin}" "$GRAFANA_URL/api/datasources/uid/mimir/health")
  [ "$c" = 200 ] && ok "Grafana->Mimir datasource healthy" || bad "Grafana->Mimir datasource ($c)"
  for d in loki tempo; do
    c=$(code -u "${GRAFANA_USER:-admin}:${GRAFANA_PASSWORD:-admin}" "$GRAFANA_URL/api/datasources/uid/$d/health")
    [ "$c" = 200 ] && ok "Grafana->$d datasource healthy" || bad "Grafana->$d datasource ($c)"
  done
fi
echo; [ $fail -eq 0 ] && echo "SMOKE OK" || { echo "SMOKE FAILED ($fail)"; exit 1; }

#!/usr/bin/env bash
# Runs every suite that can run against a live stack and prints a summary. Usage: ./run-all.sh [--skip-e2e] [--skip-ui]
set -u
cd "$(dirname "$0")"
SKIP_E2E=0; SKIP_UI=0
for a in "$@"; do [ "$a" = --skip-e2e ] && SKIP_E2E=1; [ "$a" = --skip-ui ] && SKIP_UI=1; done
declare -A R
run() { local n=$1; shift; echo; echo "######## $n"; if "$@"; then R[$n]=PASS; else R[$n]=FAIL; fi; }
run smoke       bash smoke/smoke.sh
[ -d integration/.venv ] || make venv-integration >/dev/null
run integration make integration
run observability make observability
[ $SKIP_E2E = 1 ] || run e2e make e2e
if [ $SKIP_UI = 0 ]; then [ -d selenium/.venv ] || make venv-selenium >/dev/null; run selenium make selenium; fi
echo; echo "================ SUMMARY"; rc=0
for k in "${!R[@]}"; do printf '%-14s %s\n' "$k" "${R[$k]}"; [ "${R[$k]}" = FAIL ] && rc=1; done
exit $rc

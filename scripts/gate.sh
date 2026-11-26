#!/usr/bin/env bash
# Run the full regression gate against one image:
#   * black-box suite on Derby, Derby+Redis, PostgreSQL+Redis and MSSQL
#   * slow tests (scheduled metrics), restart persistence, headless UI check and, when ANZO_URL/ANZO_USER/ANZO_PASS
#     are set, the real-Anzo smoke test (on Derby+Redis)
#
#   scripts/gate.sh <image> [log-dir]
#
# UI_TOOLS: directory containing node_modules/puppeteer-core (skips the UI check if unset).
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE=${1:?image}
LOGS=${2:-$ROOT/target/gate}
mkdir -p "$LOGS"
SUMMARY="$LOGS/summary.txt"
: > "$SUMMARY"
STACK="$ROOT/scripts/stack.sh"
rc=0

record() { # name exit-code log
  local status=PASS; [[ $2 -ne 0 ]] && { status=FAIL; rc=1; }
  local counts; counts=$(grep -h -E 'Tests run: [0-9]+, Failures' "$3" 2>/dev/null | tail -1 | sed 's/^\[[A-Z]*\] //')
  [[ -z "$counts" ]] && counts=$(grep -h -E 'passed$|pages clean$' "$3" 2>/dev/null | tail -1)
  printf '%-4s %-38s %s\n' "$status" "$1" "$counts" | tee -a "$SUMMARY"
}

run_suite() { # db redis extra-maven-args...
  local db=$1 redis=$2; shift 2
  local name="it[$db,redis=$redis]"
  local log="$LOGS/it-$db-$redis.log"
  if ! QTT_IMAGE=$IMAGE QTT_DB=$db QTT_REDIS=$redis "$STACK" up > "$log" 2>&1; then
    record "$name (stack up)" 1 "$log"; "$STACK" down; return
  fi
  (cd "$ROOT" && mvn -B -Pit -pl query-service-it verify -Dqtt.redis="$redis" -Dqtt.db="$db" "$@") >> "$log" 2>&1
  record "$name" $? "$log"
}

for cfg in "derby false" "postgres true" "mssql false"; do
  run_suite $cfg -Dit.excludedGroups=slow,restart-setup,restart-verify
  podman logs qtt-it-app > "$LOGS/app-${cfg// /-}.log" 2>&1
  "$STACK" down
done

# Derby + Redis: full suite including slow tests, then restart, UI and real-Anzo checks on the same stack.
run_suite derby true -Dit.excludedGroups=restart-setup,restart-verify
QTT_IMAGE=$IMAGE QTT_DB=derby QTT_REDIS=true "$STACK" restart-it > "$LOGS/restart.log" 2>&1
record "restart persistence" $? "$LOGS/restart.log"
if [[ -n "${UI_TOOLS:-}" ]]; then
  NODE_PATH="$UI_TOOLS/node_modules" node "$ROOT/scripts/ui_check.mjs" https://localhost:18443 "$LOGS/screenshots" > "$LOGS/ui.log" 2>&1
  record "ui check" $? "$LOGS/ui.log"
fi
if [[ -n "${ANZO_URL:-}" ]]; then
  python3 "$ROOT/scripts/smoke_real_anzo.py" > "$LOGS/real-anzo.log" 2>&1
  record "real Anzo smoke" $? "$LOGS/real-anzo.log"
fi
podman logs qtt-it-app > "$LOGS/app-derby-true.log" 2>&1
"$STACK" down
echo "gate: $( [[ $rc -eq 0 ]] && echo PASSED || echo FAILED ) — $SUMMARY"
exit $rc

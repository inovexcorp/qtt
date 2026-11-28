#!/usr/bin/env bash
# Run the full regression gate against one image:
#   * black-box suite on Derby, Derby+Redis, PostgreSQL+Redis and MSSQL
#   * the same suite with sign-in required (Keycloak), plus SecurityIT
#   * the query-access suites with QUERY_AUTH_DEFAULT=authenticated
#   * slow tests (scheduled metrics), restart persistence, headless UI check and, when ANZO_URL/ANZO_USER/ANZO_PASS
#     are set, the real-Anzo smoke test (on Derby+Redis)
#   * custom TLS keystore, and password encryption at rest (PostgreSQL, encryption enabled)
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
  [[ -z "$counts" ]] && counts=$(grep -h -E 'passed$|pages clean$|FAILED' "$3" 2>/dev/null | tail -1)
  printf '%-4s %-38s %s\n' "$status" "$1" "$counts" | tee -a "$SUMMARY"
}

run_suite() { # db redis extra-maven-args...   (QTT_OIDC=true in the environment: sign-in required;
              #   SUITE_TAG names a variant, e.g. a different QTT_EXTRA_ENV)
  local db=$1 redis=$2 oidc=${QTT_OIDC:-false}; shift 2
  local name="it[$db,redis=$redis]" log="$LOGS/it-$db-$redis.log"
  if [[ "$oidc" == "true" ]]; then name="it[$db,redis=$redis,oidc]"; log="$LOGS/it-$db-$redis-oidc.log"; fi
  if [[ -n "${SUITE_TAG:-}" ]]; then name="${name%]},$SUITE_TAG]"; log="${log%.log}-$SUITE_TAG.log"; fi
  if ! QTT_IMAGE=$IMAGE QTT_DB=$db QTT_REDIS=$redis QTT_OIDC=$oidc "$STACK" up > "$log" 2>&1; then
    record "$name (stack up)" 1 "$log"; "$STACK" down; return
  fi
  (cd "$ROOT" && mvn -B -Pit -pl query-service-it verify -Dqtt.redis="$redis" -Dqtt.db="$db" -Dqtt.oidc="$oidc" "$@") \
    >> "$log" 2>&1
  record "$name" $? "$log"
}

for cfg in "derby false" "postgres true" "mssql false"; do
  run_suite $cfg -Dit.excludedGroups=slow,restart-setup,restart-verify
  podman logs qtt-it-app > "$LOGS/app-${cfg// /-}.log" 2>&1
  "$STACK" down
done

# Sign-in required: the whole suite as the qtt-ci service account, plus SecurityIT (browser flow, roles, CSRF).
QTT_OIDC=true run_suite derby false -Dit.excludedGroups=slow,restart-setup,restart-verify
if [[ -n "${UI_TOOLS:-}" ]]; then
  QTT_OIDC=true NODE_PATH="$UI_TOOLS/node_modules" node "$ROOT/scripts/ui_check.mjs" https://localhost:18443 \
    "$LOGS/screenshots-oidc" > "$LOGS/ui-oidc.log" 2>&1
  record "ui check (oidc)" $? "$LOGS/ui-oidc.log"
fi
podman logs qtt-it-app > "$LOGS/app-derby-oidc.log" 2>&1
"$STACK" down

# QUERY_AUTH_DEFAULT=authenticated: routes left at "default" are closed. The rest of the suite calls routes
# anonymously, so only the query-access suites run here.
QTT_EXTRA_ENV="-e QUERY_AUTH_DEFAULT=authenticated" SUITE_TAG=closed run_suite derby false \
  -Dit.test=QueryAuthIT,ApiKeyIT,RouteUpdateIT -Dqtt.queryAuthDefault=authenticated -Dit.excludedGroups=slow,restart-setup,restart-verify
"$STACK" down

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
"$ROOT/scripts/tls_check.sh" "$IMAGE" > "$LOGS/tls.log" 2>&1
record "custom TLS keystore" $? "$LOGS/tls.log"
"$ROOT/scripts/encryption_check.sh" "$IMAGE" > "$LOGS/encryption.log" 2>&1
record "password encryption at rest" $? "$LOGS/encryption.log"
echo "gate: $( [[ $rc -eq 0 ]] && echo PASSED || echo FAILED ) — $SUMMARY"
exit $rc

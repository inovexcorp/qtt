#!/usr/bin/env bash
# Start/stop a QTT test stack with podman (or docker): QTT + mock Anzo (WireMock) + optional Redis/Postgres/MSSQL.
#
#   scripts/stack.sh up        start the stack and wait until the API and query port answer
#   scripts/stack.sh down      remove all stack containers and the network
#   scripts/stack.sh restart   restart only the QTT container and wait until ready
#   scripts/stack.sh start-app replace the QTT container with a fresh one (no wait; used by measure.py)
#   scripts/stack.sh logs      follow QTT logs
#   scripts/stack.sh it        run the black-box regression suite (query-service-it) against the stack
#
# Environment:
#   QTT_IMAGE   image under test               (default inovexis/qtt:latest)
#   QTT_DB      derby | postgres | mssql       (default derby)
#   QTT_REDIS   true | false                   (default false)
#   QTT_OIDC    true | false                   (default false) require sign-in, against a Keycloak container on
#               localhost:18180 with scripts/keycloak/qtt-realm.json (test users and clients)
#   QTT_CPUS / QTT_MEMORY  container limits    (default 2 / 2g)
#   QTT_EXTRA_ENV  extra "-e K=V" args for the QTT container
#   QTT_DATA_VOLUME  named volume mounted at /opt/qtt/data (keeps the Derby database across app containers)
#   QTT_JAVA_OPTS  JAVA_OPTS for the QTT container (replaces the image default)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENGINE="${ENGINE:-$(command -v podman || command -v docker)}"
NET=qtt-it
APP=qtt-it-app
QTT_IMAGE="${QTT_IMAGE:-inovexis/qtt:latest}"
QTT_DB="${QTT_DB:-derby}"
QTT_REDIS="${QTT_REDIS:-false}"
QTT_OIDC="${QTT_OIDC:-false}"
KEYCLOAK_IMAGE=quay.io/keycloak/keycloak:26.0.7
KEYCLOAK_PORT=18180
QTT_CPUS="${QTT_CPUS:-2}"
QTT_MEMORY="${QTT_MEMORY:-2g}"
API_PORT=18443
QUERY_PORT=18888
WIREMOCK_PORT=18080
DB_PASSWORD='verYs3cret!'

log() { echo "[stack] $*" >&2; }

arch_of_engine() { $ENGINE info --format '{{.Host.Arch}}' 2>/dev/null; }

now_ms() { python3 -c 'import time; print(int(time.time()*1000))'; }

wait_http() { # url timeout_seconds
  local url=$1 timeout=$2 start; start=$(date +%s)
  until curl -ksf -o /dev/null --max-time 5 -H 'Accept: application/json' "$url"; do
    if (( $(date +%s) - start > timeout )); then log "timeout waiting for $url"; return 1; fi
    sleep 0.25
  done
}

db_args() { # prints one "K=V" per line
  case "$QTT_DB" in
    derby) ;;
    postgres) printf '%s\n' "DB_DRIVER_NAME=PostgreSQL JDBC Driver" "DB_URL=jdbc:postgresql://qtt-it-db:5432/qtt" \
        "DB_USER=postgres" "DB_PASSWORD=$DB_PASSWORD" ;;
    mssql) printf '%s\n' "DB_DRIVER_NAME=Microsoft JDBC Driver for SQL Server" \
        "DB_URL=jdbc:sqlserver://qtt-it-db:1433;databaseName=qtt;encrypt=true;trustServerCertificate=true" \
        "DB_USER=sa" "DB_PASSWORD=$DB_PASSWORD" ;;
    *) log "unknown QTT_DB=$QTT_DB"; exit 2 ;;
  esac
}

start_db() {
  case "$QTT_DB" in
    postgres)
      $ENGINE run -d --name qtt-it-db --network $NET -e POSTGRES_PASSWORD="$DB_PASSWORD" -e POSTGRES_DB=qtt \
        docker.io/library/postgres:16-alpine >/dev/null
      until $ENGINE exec qtt-it-db pg_isready -U postgres -d qtt >/dev/null 2>&1; do sleep 0.5; done ;;
    mssql)
      if [[ "$(arch_of_engine)" == "arm64" ]]; then
        # SQL Server publishes amd64 only and segfaults under emulation; Azure SQL Edge is the arm64 equivalent
        # (same TDS protocol and JDBC driver). It ships no sqlcmd, so the qtt database is created over JDBC.
        $ENGINE run -d --name qtt-it-db --network $NET -p 11433:1433 -e ACCEPT_EULA=1 \
          -e MSSQL_SA_PASSWORD="$DB_PASSWORD" mcr.microsoft.com/azure-sql-edge:latest >/dev/null
        local jdbc; jdbc=$(ls "$HOME"/.m2/repository/com/microsoft/sqlserver/mssql-jdbc/*/mssql-jdbc-*.jre11.jar | tail -1)
        java -cp "$jdbc" "$ROOT/scripts/tools/CreateMssqlDb.java" localhost:11433 "$DB_PASSWORD" \
          || { log "azure-sql-edge did not start"; exit 1; }
      else
        $ENGINE run -d --name qtt-it-db --network $NET -e ACCEPT_EULA=Y -e MSSQL_SA_PASSWORD="$DB_PASSWORD" \
          mcr.microsoft.com/mssql/server:2022-latest >/dev/null
        local i=0
        until $ENGINE exec qtt-it-db /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$DB_PASSWORD" \
              -Q "IF DB_ID('qtt') IS NULL CREATE DATABASE qtt" >/dev/null 2>&1; do
          i=$((i+1)); (( i > 120 )) && { log "mssql did not start"; exit 1; }; sleep 1
        done
      fi ;;
  esac
}

start_app() {
  local args=()
  while IFS= read -r kv; do [[ -n "$kv" ]] && args+=(-e "$kv"); done < <(db_args)
  if [[ "$QTT_REDIS" == "true" ]]; then args+=(-e REDIS_ENABLED=true -e REDIS_HOST=qtt-it-redis); fi
  if [[ "$QTT_OIDC" == "true" ]]; then
    # QTT reaches Keycloak as keycloak:8080; Keycloak's issuer and login page use the browser-facing localhost URL.
    args+=(-e OIDC_ENABLED=true -e OIDC_AUTH_SERVER_URL=http://keycloak:8080/realms/qtt -e OIDC_CLIENT_ID=qtt
           -e OIDC_CLIENT_SECRET=qtt-test-secret -e OIDC_ADMIN_GROUPS=legacy-admins)
  fi
  if [[ -n "${QTT_DATA_VOLUME:-}" ]]; then args+=(-v "$QTT_DATA_VOLUME:/opt/qtt/data"); fi
  if [[ -n "${QTT_JAVA_OPTS:-}" ]]; then args+=(-e "JAVA_OPTS=$QTT_JAVA_OPTS"); fi
  # shellcheck disable=SC2086
  $ENGINE run -d --name $APP --network $NET --cpus "$QTT_CPUS" --memory "$QTT_MEMORY" \
    -p $API_PORT:8443 -p $QUERY_PORT:8888 ${args[@]+"${args[@]}"} \
    -e API_BASE_URL=http://localhost:$QUERY_PORT \
    -e SPARQI_ENABLED=true -e SPARQI_LLM_BASE_URL=http://wiremock:8080/v1 \
    -e SPARQI_LLM_API_KEY=mock-key -e SPARQI_LLM_MODEL=mock-model \
    ${QTT_EXTRA_ENV:-} "$QTT_IMAGE" >/dev/null
}

start_keycloak() {
  # --hostname fixes the issuer and login page at the URL tests and browsers use; backchannel-dynamic lets QTT
  # fetch keys and tokens through the container network.
  $ENGINE run -d --name qtt-it-keycloak --network $NET --network-alias keycloak -p $KEYCLOAK_PORT:8080 \
    -v "$ROOT/scripts/keycloak:/opt/keycloak/data/import:ro,Z" \
    -e KC_BOOTSTRAP_ADMIN_USERNAME=kcadmin -e KC_BOOTSTRAP_ADMIN_PASSWORD=kcadmin \
    "$KEYCLOAK_IMAGE" start-dev --import-realm \
    --hostname=http://localhost:$KEYCLOAK_PORT --hostname-backchannel-dynamic=true >/dev/null
  wait_http "http://localhost:$KEYCLOAK_PORT/realms/qtt/.well-known/openid-configuration" 180
}

wait_ready() {
  local start=$1
  wait_http "https://localhost:$API_PORT/queryrest/api/settings/sysinfo" 300
  local api_ms=$(( $(now_ms) - start ))
  log "ready: api=${api_ms}ms"
  echo "$api_ms"
}

cmd_up() {
  cmd_down >/dev/null 2>&1 || true
  $ENGINE network create $NET >/dev/null
  $ENGINE run -d --name wiremock --network $NET -p $WIREMOCK_PORT:8080 \
    -v "$ROOT/query-service-it/src/test/resources/wiremock:/home/wiremock:ro,Z" \
    docker.io/wiremock/wiremock:3.9.1 --disable-banner >/dev/null
  if [[ "$QTT_REDIS" == "true" ]]; then
    $ENGINE run -d --name qtt-it-redis --network $NET docker.io/library/redis:7-alpine >/dev/null
  fi
  start_db
  if [[ "$QTT_OIDC" == "true" ]]; then start_keycloak; fi
  wait_http "http://localhost:$WIREMOCK_PORT/__admin/health" 60
  local start; start=$(now_ms)
  start_app
  wait_ready "$start"
}

cmd_down() {
  $ENGINE rm -f $APP wiremock qtt-it-redis qtt-it-db qtt-it-keycloak >/dev/null 2>&1 || true
  $ENGINE network rm $NET >/dev/null 2>&1 || true
}

cmd_restart() {
  local start; start=$(now_ms)
  $ENGINE restart $APP >/dev/null
  wait_ready "$start"
}

cmd_it() {
  local redis=$QTT_REDIS
  (cd "$ROOT" && mvn -B -q -Pit -pl query-service-it verify -Dqtt.redis="$redis" -Dqtt.db="$QTT_DB" \
    -Dqtt.oidc="$QTT_OIDC" "$@")
}

cmd_restart_it() {
  (cd "$ROOT" && mvn -B -q -Pit -pl query-service-it verify -Dit.groups=restart-setup -Dit.excludedGroups= -Dqtt.oidc="$QTT_OIDC")
  cmd_restart >/dev/null
  (cd "$ROOT" && mvn -B -q -Pit -pl query-service-it verify -Dit.groups=restart-verify -Dit.excludedGroups= -Dqtt.oidc="$QTT_OIDC")
}

case "${1:-}" in
  up) cmd_up ;;
  down) cmd_down ;;
  restart) cmd_restart ;;
  start-app) $ENGINE rm -f $APP >/dev/null 2>&1 || true; start_app ;;
  # Graceful stop first (as in a real upgrade), so the old runtime shuts its database down cleanly.
  swap-app) $ENGINE stop -t 30 $APP >/dev/null 2>&1 || true; $ENGINE rm -f $APP >/dev/null 2>&1 || true
            start=$(now_ms); start_app; wait_ready "$start" ;;
  logs) $ENGINE logs -f $APP ;;
  it) shift; cmd_it "$@" ;;
  restart-it) cmd_restart_it ;;
  *) sed -n '2,16p' "$0"; exit 2 ;;
esac

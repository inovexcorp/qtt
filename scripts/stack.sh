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
#   QTT_PUBLISH_8888  true: also publish container port 8888 on localhost:18888 (images before /query, upgrade checks)
#   QTT_STACK   suffix for the container and network names, so a second stack can run beside the default one
#   QTT_PORT_OFFSET  added to every published port (default 0: API 18443, query 18082, WireMock 18080, Keycloak 18180)
#
#   scripts/stack.sh env       print the stack's names, ports and URLs as shell variables (for gate.sh and checks)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENGINE="${ENGINE:-$(command -v podman || command -v docker)}"
SUFFIX=${QTT_STACK:+-$QTT_STACK}
OFFSET=${QTT_PORT_OFFSET:-0}
NET=qtt-it$SUFFIX
APP=qtt-it-app$SUFFIX
# Containers other than the app; inside the network they keep their default names as aliases.
WIREMOCK=wiremock$SUFFIX
REDIS=qtt-it-redis$SUFFIX
DB=qtt-it-db$SUFFIX
KEYCLOAK=qtt-it-keycloak$SUFFIX
QTT_IMAGE="${QTT_IMAGE:-inovexis/qtt:latest}"
QTT_DB="${QTT_DB:-derby}"
QTT_REDIS="${QTT_REDIS:-false}"
QTT_OIDC="${QTT_OIDC:-false}"
KEYCLOAK_IMAGE=quay.io/keycloak/keycloak:26.0.7
KEYCLOAK_PORT=$((18180 + OFFSET))
QTT_CPUS="${QTT_CPUS:-2}"
QTT_MEMORY="${QTT_MEMORY:-2g}"
API_PORT=$((18443 + OFFSET))
HTTP_PORT=$((18082 + OFFSET))   # the plain-HTTP listener (8080); query routes are at http://localhost:18082/query/<routeId>
WIREMOCK_PORT=$((18080 + OFFSET))
MSSQL_PORT=$((11433 + OFFSET))
PORT_8888=$((18888 + OFFSET))
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
      $ENGINE run -d --name $DB --network $NET --network-alias qtt-it-db -e POSTGRES_PASSWORD="$DB_PASSWORD" -e POSTGRES_DB=qtt \
        docker.io/library/postgres:16-alpine >/dev/null
      until $ENGINE exec $DB pg_isready -U postgres -d qtt >/dev/null 2>&1; do sleep 0.5; done ;;
    mssql)
      if [[ "$(arch_of_engine)" == "arm64" ]]; then
        # SQL Server publishes amd64 only and segfaults under emulation; Azure SQL Edge is the arm64 equivalent
        # (same TDS protocol and JDBC driver). It ships no sqlcmd, so the qtt database is created over JDBC.
        $ENGINE run -d --name $DB --network $NET --network-alias qtt-it-db -p $MSSQL_PORT:1433 -e ACCEPT_EULA=1 \
          -e MSSQL_SA_PASSWORD="$DB_PASSWORD" mcr.microsoft.com/azure-sql-edge:latest >/dev/null
        local jdbc; jdbc=$(ls "$HOME"/.m2/repository/com/microsoft/sqlserver/mssql-jdbc/*/mssql-jdbc-*.jre11.jar | tail -1)
        java -cp "$jdbc" "$ROOT/scripts/tools/CreateMssqlDb.java" localhost:$MSSQL_PORT "$DB_PASSWORD" \
          || { log "azure-sql-edge did not start"; exit 1; }
      else
        $ENGINE run -d --name $DB --network $NET --network-alias qtt-it-db -e ACCEPT_EULA=Y -e MSSQL_SA_PASSWORD="$DB_PASSWORD" \
          mcr.microsoft.com/mssql/server:2022-latest >/dev/null
        local i=0
        until $ENGINE exec $DB /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$DB_PASSWORD" \
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
  if [[ "${QTT_PUBLISH_8888:-false}" == "true" ]]; then args+=(-p $PORT_8888:8888); fi
  # shellcheck disable=SC2086
  $ENGINE run -d --name $APP --network $NET --cpus "$QTT_CPUS" --memory "$QTT_MEMORY" \
    -p $API_PORT:8443 -p $HTTP_PORT:8080 ${args[@]+"${args[@]}"} \
    -e QUERY_BASE_URL=http://localhost:$HTTP_PORT/query \
    -e SPARQI_ENABLED=true -e SPARQI_LLM_BASE_URL=http://wiremock:8080/v1 \
    -e SPARQI_LLM_API_KEY=mock-key -e SPARQI_LLM_MODEL=mock-model \
    ${QTT_EXTRA_ENV:-} "$QTT_IMAGE" >/dev/null
}

start_keycloak() {
  # --hostname fixes the issuer and login page at the URL tests and browsers use; backchannel-dynamic lets QTT
  # fetch keys and tokens through the container network.
  # The realm lists the UI's redirect URIs: with a port offset, import a copy that names the stack's API port.
  local realm="$ROOT/scripts/keycloak"
  if (( OFFSET != 0 )); then
    realm="$ROOT/target/keycloak-realm$SUFFIX"   # under the repo: the podman VM mounts it
    mkdir -p "$realm"
    sed "s#https://localhost:18443/#https://localhost:$API_PORT/#" "$ROOT/scripts/keycloak/qtt-realm.json" > "$realm/qtt-realm.json"
    chmod 755 "$realm"; chmod 644 "$realm/qtt-realm.json"
  fi
  $ENGINE run -d --name $KEYCLOAK --network $NET --network-alias keycloak -p $KEYCLOAK_PORT:8080 \
    -v "$realm:/opt/keycloak/data/import:ro,Z" \
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
  $ENGINE run -d --name $WIREMOCK --network $NET --network-alias wiremock -p $WIREMOCK_PORT:8080 \
    -v "$ROOT/query-service-it/src/test/resources/wiremock:/home/wiremock:ro,Z" \
    docker.io/wiremock/wiremock:3.9.1 --disable-banner >/dev/null
  if [[ "$QTT_REDIS" == "true" ]]; then
    $ENGINE run -d --name $REDIS --network $NET --network-alias qtt-it-redis docker.io/library/redis:7-alpine >/dev/null
  fi
  start_db
  if [[ "$QTT_OIDC" == "true" ]]; then start_keycloak; fi
  wait_http "http://localhost:$WIREMOCK_PORT/__admin/health" 60
  local start; start=$(now_ms)
  start_app
  wait_ready "$start"
}

cmd_down() {
  $ENGINE rm -f $APP $WIREMOCK $REDIS $DB $KEYCLOAK >/dev/null 2>&1 || true
  $ENGINE network rm $NET >/dev/null 2>&1 || true
}

cmd_restart() {
  local start; start=$(now_ms)
  $ENGINE restart $APP >/dev/null
  wait_ready "$start"
}

# Maven properties that point the black-box suite at this stack.
it_props() {
  printf '%s\n' "-Dqtt.api=https://localhost:$API_PORT/queryrest/api" "-Dqtt.ui=https://localhost:$API_PORT" \
    "-Dqtt.query=http://localhost:$HTTP_PORT/query" "-Dqtt.wiremock=http://localhost:$WIREMOCK_PORT" \
    "-Dqtt.oidc.realm=http://localhost:$KEYCLOAK_PORT/realms/qtt"
}

cmd_env() {
  cat <<ENV
STACK_APP=$APP
STACK_DB=$DB
STACK_API=https://localhost:$API_PORT
STACK_QUERY=http://localhost:$HTTP_PORT/query
STACK_WIREMOCK=http://localhost:$WIREMOCK_PORT
STACK_REALM=http://localhost:$KEYCLOAK_PORT/realms/qtt
STACK_IT_PROPS="$(it_props | tr '\n' ' ')"
ENV
}

cmd_it() {
  local redis=$QTT_REDIS props=()
  while IFS= read -r p; do props+=("$p"); done < <(it_props)
  (cd "$ROOT" && mvn -B -q -Pit -pl query-service-it verify -Dqtt.redis="$redis" -Dqtt.db="$QTT_DB" \
    -Dqtt.oidc="$QTT_OIDC" "${props[@]}" "$@")
}

cmd_restart_it() {
  cmd_it -Dit.groups=restart-setup -Dit.excludedGroups=
  cmd_restart >/dev/null
  cmd_it -Dit.groups=restart-verify -Dit.excludedGroups=
}

case "${1:-}" in
  up) cmd_up ;;
  env) cmd_env ;;
  down) cmd_down ;;
  restart) cmd_restart ;;
  start-app) $ENGINE rm -f $APP >/dev/null 2>&1 || true; start_app ;;
  # Graceful stop first (as in a real upgrade), so the old runtime shuts its database down cleanly.
  swap-app) $ENGINE stop -t 30 $APP >/dev/null 2>&1 || true; $ENGINE rm -f $APP >/dev/null 2>&1 || true
            start=$(now_ms); start_app; wait_ready "$start" ;;
  logs) $ENGINE logs -f $APP ;;
  it) shift; cmd_it "$@" ;;
  restart-it) cmd_restart_it ;;
  *) sed -n '2,24p' "$0"; exit 2 ;;
esac

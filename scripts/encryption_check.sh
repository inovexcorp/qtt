#!/usr/bin/env bash
# Checks that datasource passwords stay encrypted in the database (PostgreSQL, encryption enabled) through every
# write path, that the API never returns them, and that passwords already stored in plain text are encrypted at
# startup.
#   scripts/encryption_check.sh <image>
set -uo pipefail
IMAGE=${1:?image}
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENGINE="${ENGINE:-$(command -v podman || command -v docker)}"
STACK="$ROOT/scripts/stack.sh"
export QTT_IMAGE=$IMAGE QTT_DB=postgres QTT_REDIS=false
export QTT_EXTRA_ENV="-e PASSWORD_ENCRYPTION_ENABLED=true -e PASSWORD_ENCRYPTION_KEY=check-key-0123456789 -e PASSWORD_ENCRYPTION_SALT=check-salt-0123"
eval "$("$STACK" env)"
API=$STACK_API/queryrest/api
QUERY=$STACK_QUERY
DS=enc-check
ROUTE=enc-check-route
PASSWORD=mock-password
failures=0

trap '"$STACK" down >/dev/null 2>&1' EXIT
"$STACK" up >/dev/null 2>&1 || { echo "stack up: FAILED"; exit 1; }

json() { curl -ks -H 'Accept: application/json' -H 'Content-Type: application/json' "$@"; }
column() { $ENGINE exec $STACK_DB psql -U postgres -d qtt -tAc "select password from datasources where datasourceid='$DS'"; }
datasource() { # timeout [password]
  printf '{"dataSourceId":"%s","url":"http://wiremock:8080","timeOutSeconds":"%s","maxQueryHeaderLength":"8192","username":"sysadmin"%s,"validateCertificate":false}' \
    "$DS" "$1" "${2:+,\"password\":\"$2\"}"
}
check() { # step
  local stored; stored=$(column)
  if [[ -z "$stored" ]]; then
    echo "$1: FAILED (stored password is empty)"; failures=$((failures + 1))
  elif [[ "$stored" == *"$PASSWORD"* ]]; then
    echo "$1: FAILED (stored password is plain text)"; failures=$((failures + 1))
  else
    echo "$1: stored encrypted"
  fi
}
EXPECTED_AUTH="Basic $(printf '%s' "sysadmin:$PASSWORD" | base64)"
check_query() { # step: the route works and sends the original password to Anzo (the mock accepts any credentials)
  curl -s -o /dev/null -X DELETE $STACK_WIREMOCK/__admin/requests
  local code auth; code=$(curl -s -o /dev/null -w '%{http_code}' "$QUERY/$ROUTE")
  auth=$(curl -s -X POST $STACK_WIREMOCK/__admin/requests/find -d '{"method":"POST","urlPathPattern":"/sparql/graphmart/.*"}' |
    python3 -c 'import sys,json; r=json.load(sys.stdin)["requests"]; print(r[0]["headers"].get("Authorization","") if r else "")')
  if [[ "$code" != 200 ]]; then echo "$1: route query FAILED ($code)"; failures=$((failures + 1))
  elif [[ "$auth" != "$EXPECTED_AUTH" ]]; then echo "$1: route query sent the wrong credentials"; failures=$((failures + 1))
  else echo "$1: route query ok, original password sent"; fi
}

json -o /dev/null -X POST "$API/datasources" -d "$(datasource 30 "$PASSWORD")"
check "create"
json -o /dev/null -X POST "$API/datasources/$DS/healthcheck"
check "health check"
WORK=$(mktemp -d)
echo 'CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 1' > "$WORK/template"
printf '%s' 'http://cambridgesemantics.com/Layer/67c99b630d494b4da6dc5a0a11cb717e' > "$WORK/layers"
curl -ks -o /dev/null -H 'Accept: application/json' \
  -F "freemarker=@$WORK/template;filename=blob;type=multipart/form-data" \
  -F "layers=@$WORK/layers;filename=blob;type=multipart/form-data" \
  "$API/routes/$ROUTE?routeParams=httpMethodRestrict=GET,POST&dataSourceId=$DS&description=enc&graphMartUri=http://cambridgesemantics.com/Graphmart/93794a1ba2a24246a0f33c4340858d80"
rm -rf "$WORK"
check "route create"
check_query "route create"
json -o /dev/null -X PUT "$API/datasources" -d "$(datasource 45)"
check "modify without password"
check_query "modify without password"

if json "$API/datasources/$DS" "$API/datasources" "$API/routes" "$API/datasources/health/summary" | grep -q "$PASSWORD"; then
  echo "API responses: FAILED (password returned)"; failures=$((failures + 1))
else
  echo "API responses: no password"
fi

# A password stored in plain text (encryption enabled later, or written by an affected build) is encrypted at startup.
$ENGINE exec $STACK_DB psql -U postgres -d qtt -qc "update datasources set password='$PASSWORD' where datasourceid='$DS'"
"$STACK" swap-app >/dev/null 2>&1 || { echo "restart: FAILED"; exit 1; }
check "startup migration"
check_query "startup migration"

# Starting with the wrong key must not damage stored passwords: health checks and the startup migration leave the
# column alone, and the right key works again afterwards.
before=$(column)
RIGHT_ENV=$QTT_EXTRA_ENV
export QTT_EXTRA_ENV="-e PASSWORD_ENCRYPTION_ENABLED=true -e PASSWORD_ENCRYPTION_KEY=wrong-key-9876543210 -e PASSWORD_ENCRYPTION_SALT=check-salt-0123"
"$STACK" swap-app >/dev/null 2>&1 || { echo "restart (wrong key): FAILED"; exit 1; }
json -o /dev/null -X POST "$API/datasources/$DS/healthcheck"
json -o /dev/null -X POST "$API/datasources/$DS/healthcheck"
if [[ "$(column)" == "$before" ]]; then echo "wrong key: stored password unchanged"; else
  echo "wrong key: FAILED (stored password was rewritten)"; failures=$((failures + 1)); fi
export QTT_EXTRA_ENV=$RIGHT_ENV
"$STACK" swap-app >/dev/null 2>&1 || { echo "restart (right key): FAILED"; exit 1; }
check_query "right key again"

if (( failures )); then echo "encryption check: FAILED ($failures)"; exit 1; fi
echo "encryption check: passed"

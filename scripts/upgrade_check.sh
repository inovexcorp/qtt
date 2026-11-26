#!/usr/bin/env bash
# Upgrade check: data written by one image must be served by another (e.g. Karaf -> Quarkus).
#   scripts/upgrade_check.sh <old-image> <new-image> <derby|postgres|mssql>
# Creates a datasource and a route with the old image, replaces only the app container with the new image
# (same database; for Derby the same data volume) and verifies both, including that the route still serves queries.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OLD=${1:?old image}; NEW=${2:?new image}; DB=${3:?db}
ENGINE="${ENGINE:-$(command -v podman || command -v docker)}"
export QTT_DB=$DB QTT_REDIS=false QTT_DATA_VOLUME=qtt-upgrade-data
$ENGINE volume rm -f qtt-upgrade-data >/dev/null 2>&1
$ENGINE volume create qtt-upgrade-data >/dev/null
# the old image may run as root or uid 1001; make the volume writable for both
$ENGINE run --rm -v qtt-upgrade-data:/d docker.io/library/busybox:1.36 chmod 777 /d >/dev/null
rc=0
QTT_IMAGE=$OLD "$ROOT/scripts/stack.sh" up >/dev/null || { echo "old image did not start"; exit 1; }
(cd "$ROOT" && mvn -B -q -Pit -pl query-service-it verify -Dit.groups=restart-setup -Dit.excludedGroups=) || rc=1
QTT_IMAGE=$NEW "$ROOT/scripts/stack.sh" swap-app >/dev/null || { echo "new image did not start on old data"; $ENGINE logs --tail 60 qtt-it-app; rc=1; }
if [[ $rc -eq 0 ]]; then
  (cd "$ROOT" && mvn -B -q -Pit -pl query-service-it verify -Dit.groups=restart-verify -Dit.excludedGroups=) || rc=1
fi
$ENGINE logs qtt-it-app > "${UPGRADE_LOG:-/dev/null}" 2>&1
"$ROOT/scripts/stack.sh" down
$ENGINE volume rm -f qtt-upgrade-data >/dev/null 2>&1
if [[ $rc -eq 0 ]]; then echo "upgrade $DB: passed"; else echo "upgrade $DB: FAILED"; fi
exit $rc

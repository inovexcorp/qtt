#!/usr/bin/env bash
# Checks that the image serves a custom TLS keystore given through KEYSTORE/PASSWORD.
#   scripts/tls_check.sh <image>
set -euo pipefail
IMAGE=${1:?image}
ENGINE="${ENGINE:-$(command -v podman || command -v docker)}"
WORK=$(mktemp -d)
trap '$ENGINE rm -f qtt-tls-check >/dev/null 2>&1; rm -rf "$WORK"' EXIT
CN="qtt-tls-check-$RANDOM"
keytool -genkeypair -alias custom -keyalg RSA -keysize 2048 -validity 2 -dname "CN=$CN" \
  -keystore "$WORK/custom.jks" -storetype JKS -storepass 'tls-Check/&|1' -keypass 'tls-Check/&|1' >/dev/null 2>&1
chmod 644 "$WORK/custom.jks"
$ENGINE run -d --name qtt-tls-check -p 19443:8443 -v "$WORK/custom.jks:/opt/qtt/custom/custom.jks:ro,Z" \
  -e KEYSTORE=/opt/qtt/custom/custom.jks -e 'PASSWORD=tls-Check/&|1' "$IMAGE" >/dev/null
for _ in $(seq 1 240); do
  if curl -ks -o /dev/null --max-time 2 https://localhost:19443/; then break; fi
  sleep 0.5
done
subject=$(echo | openssl s_client -connect localhost:19443 2>/dev/null | openssl x509 -noout -subject 2>/dev/null || true)
echo "served certificate: ${subject:-none}"
if [[ "$subject" == *"$CN"* ]]; then
  echo "custom keystore: passed"
else
  echo "custom keystore: FAILED (expected CN=$CN)"
  exit 1
fi

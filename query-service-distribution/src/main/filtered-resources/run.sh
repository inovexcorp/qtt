#!/bin/sh
# Entrypoint for the QTT container. Optionally points Pax Web at a custom keystore, then runs Karaf in the
# foreground (exec, so signals from the container runtime reach the JVM).
set -e

# Escape a value for use as the replacement in a sed s||| expression.
sed_escape() {
  printf '%s' "$1" | sed -e 's/[\\|&]/\\&/g'
}

if [ -n "$KEYSTORE" ] && [ -n "$PASSWORD" ]; then
  cfg=/opt/qtt/etc/org.ops4j.pax.web.cfg
  ks=$(sed_escape "$KEYSTORE")
  pw=$(sed_escape "$PASSWORD")
  sed -i \
    -e "s|^org.ops4j.pax.web.ssl.keystore=.*|org.ops4j.pax.web.ssl.keystore=$ks|" \
    -e "s|^org.ops4j.pax.web.ssl.keystore.password=.*|org.ops4j.pax.web.ssl.keystore.password=$pw|" \
    -e "s|^org.ops4j.pax.web.ssl.key.password=.*|org.ops4j.pax.web.ssl.key.password=$pw|" \
    "$cfg"
fi

exec /opt/qtt/bin/karaf run

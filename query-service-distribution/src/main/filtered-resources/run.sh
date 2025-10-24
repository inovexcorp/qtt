if [[ -n "$KEYSTORE" && -n "$PASSWORD" ]]; then
  cd /opt/qtt/etc/
  sed -i "s/org.ops4j.pax.web.ssl.keystore=.*/org.ops4j.pax.web.ssl.keystore=$KEYSTORE/g" org.ops4j.pax.web.cfg
  sed -i "s/org.ops4j.pax.web.ssl.keystore.password=.*/org.ops4j.pax.web.ssl.keystore.password=$PASSWORD/" org.ops4j.pax.web.cfg
  sed -i "s/org.ops4j.pax.web.ssl.key.password=.*/org.ops4j.pax.web.ssl.key.password=$PASSWORD/" org.ops4j.pax.web.cfg

fi
/opt/qtt/bin/karaf run

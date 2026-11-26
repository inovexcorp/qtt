# QTT on Apache Karaf. Built from target/assembly (see query-service-distribution/pom.xml).
# Multi-arch (amd64 + arm64) JRE; the JDK is not needed at runtime.
FROM docker.io/library/eclipse-temurin:17-jre

# Maven filtered to produce correct versioning.
ENV VERSION=${project.version} \
    QTT_HOME=/opt/qtt \
    KARAF_HOME=/opt/qtt \
    KARAF_LOG_CONSOLE=INFO \
    KARAF_EXEC=exec \
    PATH=$PATH:/opt/qtt/bin

RUN groupadd --system --gid 1001 qtt \
    && useradd --system --uid 1001 --gid qtt --home-dir /opt/qtt --no-create-home qtt \
    && mkdir -p /opt/qtt && chown qtt:qtt /opt/qtt

WORKDIR /opt/qtt
# Largest and least frequently changing content first, so rebuilds reuse layers.
COPY --chown=qtt:qtt ./system/ /opt/qtt/system/
COPY --chown=qtt:qtt ./lib/ /opt/qtt/lib/
COPY --chown=qtt:qtt ./bin/ /opt/qtt/bin/
COPY --chown=qtt:qtt ./etc/ /opt/qtt/etc/
COPY --chown=qtt:qtt ./deploy/ /opt/qtt/deploy/
COPY --chown=qtt:qtt ./data/ /opt/qtt/data/
COPY --chown=qtt:qtt --chmod=755 ./run.sh /opt/qtt/bin/run.sh

USER qtt
EXPOSE 8443 8888
CMD ["/opt/qtt/bin/run.sh"]

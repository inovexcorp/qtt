# Phase 8: native image (2026-11-26) — stopped at the timebox

Outcome: no native executable yet. Seven build iterations with the Mandrel builder for JDK 25
(`quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25`) each got further; the last one stops on the Redis client.
The JVM image from Phase 7 (352 MB, API ready in 2.1 s, 296 MB idle) remains the shipped image.

## How to build (for picking this up again)

On macOS with podman, Quarkus' own container build fails (its `--uidmap` options conflict with the podman machine's
user namespace), so build the native-image sources and run the builder directly:

```bash
mvn -Pnative -pl query-service-app package -DskipTests -Dquarkus.native.sources-only=true
cd query-service-app/target/native-sources
podman run --rm -v "$PWD:/project:z" -w /project $(cat native-builder.image) $(cat native-image.args) -J-Xmx10g
# then, from query-service-app: podman build -f src/main/docker/Dockerfile.native -t inovexis/qtt:native .
```

`-Pnative` leaves out the `query-service-derby` module (see below). A native image needs `DB_URL` pointing at
PostgreSQL or SQL Server.

## Iterations

| # | Blocker | Resolution |
|---|---|---|
| 1 | Lettuce's `DefaultPauseDetectorWrapper` (initialized at build time by Lettuce's own native config) needs the optional LatencyUtils library | Added `org.latencyutils:LatencyUtils` (30 KB) to query-service-cache |
| 2 | Run-time initialization flag for Lettuce had no effect (Lettuce forces those classes to build time) | Superseded by 1 |
| 3 | Embedded Derby boots during image generation and leaves open file descriptors in the image heap | Derby cannot be part of a native image. Derby support (driver, community dialect, persistence unit config) moved to `query-service-derby`, which JVM builds include by default and `-Pnative` leaves out |
| 4 | `MetricsController` and `SimpleMetricsScraper` held the platform MBean server in `static final` fields | Looked up on use instead |
| 5 | Apache HttpClient (via jsonld-java) links its NTLM engine, which needs commons-codec | Added `commons-codec` to query-service-core |
| 6–7 | Lettuce's client resources embed Netty's `DnsAddressResolverGroup` in the image heap; marking Lettuce's resource classes for run-time initialization does not help | **Open.** The supported route is replacing Lettuce with Quarkus' Redis client (`quarkus-redis-client`, Vert.x based) behind `CacheService`. That is a rewrite of `RedisCacheService` and its tests |

Untested because analysis never got past Redis: LangChain4j's OpenAI client (Jackson DTOs), Camel route metrics over
JMX (in-process MBean server in a native image), RDF4J Rio's service-loaded parsers, and FreeMarker. Reflection and
proxy registrations for QTT's own configuration types and JSON payloads are prepared in
`query-service-app/.../NativeRegistrations.java`.

## Recommendation

Not worth finishing now. Native would cost Derby support (the default, embedded database) and a Redis client rewrite,
and the remaining gain over the tuned JVM image is small: Phase 7 already starts in about 2.1 s (down from 12.2 s) on a
352 MB image (down from 684 MB). Native usually brings startup well under a second and a smaller image, at the price
of slower and much more memory-hungry builds (peak RSS above 5 GB at the point reached) and usually lower peak
throughput. Revisit if sub-second startup becomes a requirement, starting with the Redis client.

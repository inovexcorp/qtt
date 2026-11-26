# QTT measurements

Produced by `scripts/measure.py`; raw results in `results/`. Conditions: podman, QTT limited to 2 CPUs / 2 GB, PostgreSQL, Redis, mock Anzo (WireMock), 25 seeded routes, 10 cold starts; load from `scripts/tools/LoadGen.java` with 8 concurrent keep-alive clients for 30 s against an uncached route (every request calls the mock Anzo) and a cached route.

| Label | Commit | Image MB | Image gz MB | Layers | API ready ms (median, min-max) | First query ms (median) | RSS idle MB | RSS after load MB | Uncached rps / p50 / p95 ms | Cached rps / p50 / p95 ms | Note |
|---|---|---|---|---|---|---|---|---|---|---|---|
| phase0-karaf-baseline | 09a74fc+dirty | 684 | 400 | 12 | 12190 (12052-13871) | 12276 | 893.4 | 1043.0 | 1022 / 7.6 / 10.1 | 6312 / 1.0 / 1.9 | Karaf 4.4.1, openjdk:17.0.2-jdk, unmodified |
| phase1-karaf-jre-image | f52af70+dirty | 495 | 249 | 14 | 9350 (8556-10944) | 9418 | 818.8 | 824.4 | 1077 / 7.4 / 9.6 | 7318 / 0.9 / 1.6 | Temurin 17 JRE base, non-root, Monaco assets trimmed |
| phase2-karaf-trimmed-features | 144536b+dirty | 453 | 211 | 14 | 6218 (6043-6431) | 6284 | 673.7 | 759.1 | 1071 / 7.5 / 9.7 | 7341 / 0.9 / 1.6 | RDF4J subset bundle; no Elasticsearch/LMDB/MapDB/spatial, LangGraph4j, obr, jasypt, service-wrapper |
| phase5-quarkus-jvm-port | 7b4a1ea | 427 | 189 | 12 | 2540 (2423-2595) | 2630 | 452.1 | 569.2 | 1039 / 7.1 / 12.2 | 9002 / 0.8 / 1.3 | Camel Quarkus 3.40 JVM mode, Temurin 17 JRE; FreeMarker parse-once, Anzo client without streaming probe |
| phase7-quarkus-jvm-tuned | 97ae32d+dirty | 352 | 172 | 9 | 2114 (2100-2233) | 2193 | 295.8 | 347.8 | 1043 / 7.3 / 12.1 | 8808 / 0.8 / 1.4 | jlink Java 17 on Debian slim, AppCDS, SerialGC, unused LangChain4j jars excluded |

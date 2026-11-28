# Phase 7: Quarkus JVM tuning experiments (2026-11-26)

Each change was measured on its own against the Phase 5 Quarkus image, on the same stack as `measure.py`
(podman, QTT limited to 2 CPUs / 2 GB, PostgreSQL + Redis, mock Anzo, 25 seeded routes). Load figures come from
`scripts/tools/LoadGen.java` (8 concurrent keep-alive clients, 30 s uncached / 20 s cached after warm-up); startup
figures are the median of 5 cold starts (container start to first 200 from the API).

## Garbage collector and heap

| JAVA_OPTS | Uncached rps | p50 / p95 / p99 ms | Cached rps | RSS after load |
|---|---|---|---|---|
| G1 (JVM default), `MaxRAMPercentage=75` | 1031 / 1069 (two runs) | 7.3 / 12.2 / 13.7 | 9079 / 8937 | 653 / 612 MB |
| G1 + `-Xms512m` | 1009 | 7.7 / 12.1 / 14.2 | 9109 | 752 MB |
| ParallelGC | 1034 | 7.4 / 12.0 / 13.6 | 9119 | 663 MB |
| **SerialGC** | 1039 | 7.3 / 12.0 / 14.1 | 8717 | **430 MB** |

The collector does not explain the higher p95 than Karaf (about 9.7 ms); all options are within noise on latency.
SerialGC keeps throughput and latency and uses a third less memory, so it is the default (override with `JAVA_OPTS`).

## Runtime image and class-data sharing

| Image | Size | API ready (median) | Idle RSS |
|---|---|---|---|
| Temurin 17 JRE (Ubuntu), SerialGC | 423 MB | 2554 ms | 362 MB |
| Temurin 17 JRE + AppCDS archive | 500 MB | 2241 ms | 351 MB |
| Temurin 25 JRE | 487 MB | 2537 ms | 350 MB |
| Temurin 25 JRE + AOT cache (JEP 483/514/515) | 608 MB | 2011 ms | 344 MB |
| jlink'd Java 17 runtime on Debian 12 slim | 275 MB | 2543 ms | 306 MB |
| **jlink'd Java 17 runtime + AppCDS archive** | **352 MB** | **2143 ms** | **302 MB** |

Chosen: jlink + AppCDS on Java 17. It is about half the size of the original image with the fastest Java 17 start,
and it avoids changing the Java platform. Java 25's AOT cache starts about 0.1 s faster still, but on a much larger
image and a new Java version; revisit when moving to Java 25.

The archives are recorded during `docker build` by starting the application in a build stage of the same image and
stopping it once it reports started (`quarkus.appcds.generate` does not stop this application by itself). The
archive is therefore always produced by the exact runtime that uses it.

## Dependencies

`jtokkit` (OpenAI tokenizer, 3 MB) and `opennlp-tools` (1.3 MB) are excluded from SPARQi's LangChain4j
dependencies; SPARQi neither estimates token counts locally nor splits documents. `camel-endpointdsl`,
`camel-componentdsl` and `jaxb-runtime` are required by the Camel Quarkus and Hibernate ORM extensions and stay.

# QTT measurements

Produced by `scripts/measure.py`; raw results in `results/`. Conditions: podman, QTT limited to 2 CPUs / 2 GB, PostgreSQL, Redis, mock Anzo, 25 seeded routes, 10 cold starts, `ab` keep-alive with 8 concurrent clients for 30 s.

| Label | Commit | Image MB | Image gz MB | Layers | API ready ms (median, min-max) | First query ms (median) | RSS idle MB | RSS after load MB | Uncached rps / p50 / p95 ms | Cached rps / p50 / p95 ms | Note |
|---|---|---|---|---|---|---|---|---|---|---|---|
| phase0-karaf-baseline | 09a74fc+dirty | 684 | 400 | 12 | 12190 (12052-13871) | 12276 | 893.4 | 1042.0 | 1720 / 5 / 11 | 7262 / 1 / 3 | Karaf 4.4.1, openjdk:17.0.2-jdk, unmodified |

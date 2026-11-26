# QTT image size and startup optimization plan

Goal: a smaller container image and faster startup, ending with QTT running on Camel Quarkus instead of Karaf. Every phase ends at a gate: measure, run the full regression, do the manual checks, and stop for review before the next phase starts.

Status: draft, 2026-11-26, against `1.0.52-SNAPSHOT`.

## Assumptions (change any of these and the plan shifts)

| Topic | Assumed | Effect if different |
|---|---|---|
| Native image | Stretch goal (Phase 8), not a requirement | If required, the Phase 3 native spike becomes a go/no-go for the whole migration |
| Databases | Derby, PostgreSQL and MSSQL all stay, chosen at runtime by env var | Dropping Derby or MSSQL simplifies Phase 4 and makes native realistic |
| External contract | URLs, ports (8443 API/UI, 8888 queries), env var names and JSON shapes stay identical | Allowing a single port removes one spike and one component |
| Existing data | A database created by 1.0.x must open unchanged, including encrypted passwords | If a fresh database is acceptable, the upgrade test in Phase 4 goes away |
| Known bugs | Ported as-is and pinned with characterization tests; fixes are separate commits you approve | See "Known findings" at the end |

## The gate run after every phase

1. **Build**: clean build of everything, image built with the same command each time.
2. **Unit tests**: all Maven modules plus the Angular specs (30 spec files). Test counts are recorded, so a drop in the number of tests executed is a failure, not a pass.
3. **Black-box regression**: the `query-service-it` suite from Phase 0, run against the built image. It talks only HTTP, so the same suite runs unchanged against Karaf and Quarkus. That is what makes "parity" checkable.
4. **Manual checks I can perform**: start the stack with podman, then walk the scenario list below with curl and a headless browser for the UI. This includes a smoke test against the real Anzo test system (`graphstudio.inovexcorp.com`, read-only queries, credentials passed by env var and never committed): datasource connection test, graphmart and layer lookup, ontology load, and one real query route end to end. Anything I can't exercise (a real OpenAI key) is listed as untested in the gate report, not assumed.
5. **Measurements**: the script from Phase 0, results appended to `docs/perf/measurements.md` with the commit hash.
6. **Issues**: each issue found gets a failing test first (unit where feasible, otherwise in `query-service-it`), then the fix, then the gate re-runs from step 1.
7. **Report and stop**: a short gate report (numbers, delta from the previous phase, issues found, tests added, what was not tested). I wait for your go-ahead.

### Manual scenario list

- Datasource: create, edit, test connection, disable and enable, delete.
- Route: create, edit template, clone, stop and start, delete; call it on 8888 with GET and POST.
- Query path: cache miss then hit, cache clear, request coalescing under concurrent identical requests, Anzo error mapped to 400, 502 and 503.
- Test routes: run a template test from the UI, confirm the temporary route is cleaned up.
- Background: metrics appear after the collection interval, health check marks a dead datasource down and stops its routes, cleanup jobs run.
- Ontology autocomplete data loads; SPARQi chat with a stubbed model endpoint.
- UI: every page loads, no console errors, Monaco editor works.
- Each database: Derby, PostgreSQL, MSSQL. Redis on and off.
- Restart: routes and datasources survive a restart and routes are live again.

### Measurements

| Metric | How |
|---|---|
| Image size | Uncompressed and compressed size, layer count |
| Cold start | Container start to first 200 from the API, and to first 200 from a query route. Database seeded with 25 routes. Median and spread of 10 runs |
| Memory | RSS when idle after startup, and after the load run |
| Throughput and latency | Fixed load against one cached and one uncached route; p50, p95, requests per second. This is a guard against regressions, not a goal |
| Build time | Clean build and image build |

Fixed conditions: same machine, podman with 2 CPUs and 1 GB memory, Derby unless the row says otherwise, mock Anzo with a fixed response and fixed delay.

## Phase 0: harness and baseline

No product changes. Everything later depends on this.

- Measurement script (`scripts/measure.sh`) and `docs/perf/measurements.md`.
- Mock Anzo: a WireMock container serving canned RDF/XML and SPARQL-JSON, with error and slow-response modes. The regression suite and the measurements use the mock so they are deterministic and free of network noise; the real test system is used for the smoke test at each gate. Canned responses are recorded from the real system so the mock stays faithful.
- New module `query-service-it`: JUnit 5, RestAssured, Testcontainers. Covers every REST endpoint, the query path, cache behaviour, error mapping, test routes, and restart persistence. Parameterized by image name and database.
- Build with podman. `docker` is not installed here and the fabric8 plugin expects it, so either point it at the podman socket or build the image directly.
- Check whether JUnit 5 tests are being skipped. `route-builder` and `metrics` mix JUnit 4 and 5 with no Jupiter engine declared. If tests are skipped, fix that first, otherwise "all tests pass" means less than it says.
- Record the baseline.

Exit: suite is green against the current Karaf image, baseline numbers recorded.

## Phase 1: container image diet (Karaf)

- Replace `openjdk:17.0.2-jdk` (a full JDK, and a deprecated image) with a JRE base such as `eclipse-temurin:17-jre`.
- Clean up the Dockerfile: layer ordering, no build leftovers, non-root user, `.dockerignore`.

Expected effect: image size down substantially, startup roughly unchanged. Low risk.

## Phase 2: Karaf feature trim

- Cut `qs-rdf4j` down to what the code uses: Rio RDF/XML, JSON-LD, and SPARQL-JSON results. Remove Elasticsearch, the duplicate Guava and JTS versions, lwjgl, mapdb.
- Remove CXF/JAX-WS (`mobi-cxf`, `mobi-jaxws`), the Spring repository, `jasypt-encryption`, `obr`, `service-wrapper`, and the unused LangGraph4j dependency with its dead `SparqiState`.
- One removal per commit. OSGi wiring failures show up only at runtime, so the regression suite runs after each one.

Expected effect: smaller image and fewer bundles to resolve at startup. Medium risk, all of it in bundle resolution.

Phases 1 and 2 can ship as a normal release. They also give the honest comparison point: the migration is judged against a trimmed Karaf, not a bloated one.

## Phase 3: spikes and go/no-go

Throwaway code on a branch. Each spike answers one question.

1. **Dynamic routes**: can `platform-http` routes be added and removed at runtime, restricted by HTTP method, with blocking work (Anzo call, Redis, coalescing waits of up to 30s) kept off the Vert.x event loop?
2. **Port 8888**: `platform-http` shares the main Quarkus HTTP server. Find the cleanest way to keep the query port separate, for example `camel-netty-http` or a second Vert.x server.
3. **Database chosen at runtime**: Quarkus fixes `db-kind` at build time. Test three named datasources with only one active, against the alternative of one image per database.
4. **Schema compatibility**: open a database created by 1.0.x with Hibernate 6 and check that nothing is altered or lost, including the `text` columns and ID generation.
5. **Native feasibility** (timeboxed): embedded Derby, RDF4J Rio `ServiceLoader`, `jsonld-java` reflection.

Measurement here is a rough startup and size number from the spike app, enough to confirm the migration is worth it over the Phase 2 result.

Exit: a written decision per spike, and your go or no-go on Phases 4 to 7.

### Phase 3 results (2026-11-26)

| Spike | Result |
|---|---|
| 1. Runtime-added routes, blocking work | Camel `netty-http` consumers can be added and removed at runtime; `httpMethodRestrict` gives 405, unknown paths 404. Blocking processing runs on Netty's consumer executor: 20 concurrent requests that each block 1 s complete in 1.3 s. |
| 2. Separate query port | `netty-http` runs its own server, so query endpoints stay on 8888 with the UI/API on 8443/8080. (There is no Camel Quarkus Jetty extension; `platform-http` would share the main port.) Query parameters still become template headers, request headers are still echoed, and the port closes when the last route is removed, all as under Jetty. |
| 3. Database chosen at runtime | Hibernate's dialect is fixed at build time, so the application defines one persistence unit per database (Derby, PostgreSQL, SQL Server) over the same entities; a config source activates the one matching `DB_URL`. Quarkus dropped its Derby extension after 3.23, so Derby uses the plain driver (10.16.1.1, the newest for Java 17) with Hibernate's community Derby dialect. |
| 4. Data from 1.x opens unchanged | `scripts/upgrade_check.sh`: data written by the Karaf image (Hibernate 5, Derby 10.14) is read and served by the Quarkus image (Hibernate 7, Derby 10.16 soft upgrade) on Derby, PostgreSQL and SQL Server. The first run found two Hibernate 7 issues (a cascading orphan delete on route deletion, fixed in `RouteServiceImpl.delete`) and one Netty issue (line breaks in the `anzo.query` header), both fixed with unit tests before the check passed. |
| 5. Native image | Moved to Phase 8. Embedded Derby has no Quarkus extension, which already rules out native for Derby users. |

Decision: go. Quarkus 3.40.1.2 (newest platform release with a Camel BOM), Camel 4.22, Java 17, JVM mode.

Deviations from the mapping table above: SPARQi keeps the plain LangChain4j OpenAI client (its manual tool loop needs no CDI model wiring), Redis keeps Lettuce, and configuration keeps the OSGi PIDs and property names (`OsgiConfig` loads the former metatype interfaces from MicroProfile Config) so every environment variable and default carries over unchanged.

## Phase 4: Quarkus foundation, persistence and management API

New module `qtt-quarkus` alongside the Karaf modules, which keep building until Phase 6.

- Camel 3.20 to Camel 4 and `javax` to `jakarta` for `query-service-core`, `camel-anzo`, `query-service-cache`. Existing unit tests move with them.
- OSGi DS to CDI. Config Admin to `@ConfigMapping`, keeping the current env var names and defaults.
- Persistence on Hibernate ORM 6 with `@Transactional`, replacing `JpaTemplate`. `PasswordEncryptionService` unchanged so existing ciphertext still decrypts.
- REST controllers on Quarkus REST, same paths under `/queryrest`, same JSON.

Gate: the management-API part of `query-service-it` passes against the Quarkus image on all three databases, plus the upgrade test from spike 4. Query-path tests are expected to fail and are reported as such.

## Phase 5: query path

- `CamelRouteTemplateBuilder` on the HTTP component chosen in spike 2, with the same error mapping.
- Freemarker templates passed in memory instead of written to `data/templates`, so the container filesystem can be read-only.
- Startup route loading in a `StartupEvent` observer, replacing `CamelKarafComponent`.
- Route lifecycle (create, modify, clone, delete), test routes with their 5-minute failsafe, cache check and store processors, request coalescing.

Gate: query-path and cache tests pass. Load numbers are compared with Karaf here for the first time; a latency or throughput regression blocks the phase.

## Phase 6: remaining features, UI and cutover

- Scheduler jobs on Quarkus Scheduler with overlapping runs skipped.
- Metrics: keep the JMX-based collection through `camel-management` so min, mean and max keep their current meaning. Micrometer can be added later.
- Health checker, ontology service, SPARQi on `quarkus-langchain4j`.
- Angular build output served from `META-INF/resources`.
- Cutover: new Dockerfile, Makefile and `compose.yml` updated, GitHub workflows updated, Karaf modules and `feature.xml` deleted, docs updated.

Gate: the full suite passes on all three databases with Redis on and off, and the whole manual scenario list is walked. This is the first full Karaf-versus-Quarkus comparison.

## Phase 7: Quarkus JVM tuning

One change per measurement, kept only if it helps:

- Remove unused extensions and dependencies.
- AppCDS archive for startup.
- Smaller runtime: distroless or UBI micro base, or a `jlink` runtime with only the needed JDK modules.
- Layered jar so dependency layers are cached between releases.
- JVM flags for container memory.

## Phase 8: native image (optional)

Only if spike 5 was positive and the Phase 7 numbers leave a gap you care about.

- Reflection and resource registration for RDF4J, `jsonld-java`, entities serialized to JSON.
- Replace whatever doesn't work natively: likely embedded Derby and JMX metrics.
- Native build in CI, which is slow and memory-hungry.
- The same suite runs against the native image. Peak throughput is usually lower than JVM mode, so the load comparison matters here.

## Risks

| Risk | Where it's handled |
|---|---|
| Runtime-added HTTP routes behave differently on Vert.x | Spike 1 |
| Database kind is a build-time setting in Quarkus | Spike 3 |
| Hibernate 5 to 6 changes the schema or ID generation on existing databases | Spike 4, upgrade test in Phase 4 |
| Live config reload (`@Modified`) is lost; config changes need a restart | Accepted; documented in Phase 6 |
| `quarkus-langchain4j` pins its own LangChain4j version | Phase 6; SPARQi tests against a stubbed model |
| Mock Anzo drifts from real behaviour | Responses recorded from the test system; real-system smoke test at every gate |
| No real OpenAI key in testing | SPARQi tested against a stubbed model; a real-key check by you before release |
| Trimming a Karaf feature breaks wiring at runtime only | One removal per commit in Phase 2 |

## Known findings

These came out of the code read and are not optimizations. By default they are ported unchanged, each pinned by a test that documents current behaviour, so the migration can't be blamed for them or silently change them.

- Datasource passwords returned in plaintext by the unauthenticated API, and logged.
- Encryption at rest undone by merges of decrypted entities.
- Disabling a datasource doesn't stop its routes.
- Re-posting an existing datasource ID likely deletes its routes.
- SPARQi: certificate validation off, keyword blocklist rejects valid URIs.
- `QueryMetrics` aborts collection for all routes when one fails.

The first two are cheap to fix during Phase 4, since the REST and persistence layers are being rewritten there anyway. Say so and they become part of that phase, with the UI change it requires.

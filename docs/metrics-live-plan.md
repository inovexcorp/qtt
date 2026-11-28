# Live metrics: pushing updates from the server

Goal: the metrics page updates when the numbers change, pushed by the server, instead of each open page asking every 15 seconds. The same channel should be reusable for other live parts of the UI later (datasource health, route state).

Status: the server side is built (see the Status sections below); the plan text after them is the original
design, kept for its reasoning. The UI side is ready for it: the metrics page (branch `feature/beautification`) reads all its data from `MetricsLiveService` (`query-service-web/.../core/services/metrics-live.service.ts`), whose signals (`routes`, `history`, `status`, `updatedAt`, …) are the only contract with the page. Today that service polls; this plan replaces its insides with a server push and keeps polling as the fallback.

## Status: the shared `/api/events` channel (2026-11-28)

The shared channel this plan's last risk mentions now exists, with the topics `datasources` and `metrics` (see the
next section). Topics combine on one stream: `?topics=datasources,metrics`.

| Piece | Built |
|---|---|
| Stream | `GET /queryrest/api/events?topics=datasources` (`qtt-viewer`, `text/event-stream`). A missing, empty or unknown topic answers 400. The first event (`event: datasources`, `data:` the snapshot JSON) goes out on connect, then one only when the snapshot changed, plus `: keep-alive` every 20 s. The stream ends after `qtt.events.max-age`; the client reconnects. Headers `Cache-Control: no-store`, `X-Accel-Buffering: no`; HTTP compression is off (`quarkus.http.enable-compression` is not set) |
| Polling fallback | `GET /queryrest/api/events/datasources`: the same snapshot as JSON |
| Snapshot | `healthCheckEnabled`, and per datasource (sorted by id): `dataSourceId`, `url`, `status`, `lastHealthCheck`, `lastHealthError`, `consecutiveFailures`, `routeCount`, `recentChecks` (last 10, oldest first). Times are ISO-8601 instants (`…Z`). Records, never the entity, so no password |
| Server | `DatasourceHealthBroadcaster` (`routebuilder.events`): one shared tick while any stream is open, a rescan per tick, published only when it differs from the last snapshot; recent checks re-read only when a datasource's `lastHealthCheck` changed. `DataSourcesController` fires `DatasourcesChanged` (async CDI event) after create, modify, delete, enable, disable and a manual health check, and the broadcaster rescans at once. Scans run on the worker pool; each stream keeps only the latest snapshot on overflow. `EventsController` merges topics and keep-alives, caps the stream's life and counts open streams |
| Limits | Past `qtt.events.max-subscribers` open streams the endpoint answers 503 |
| Auth | Unchanged OIDC config: `X-Requested-With: JavaScript` on an expired session gets 499 (covered in `SignInOnTest`) |
| Tests | `DatasourceHealthBroadcasterTest`, `EventsControllerTest` (route-builder); `EventsStreamTest`, `SignInOnTest` (app) |

| Setting | Env var | Default |
|---|---|---|
| `qtt.events.interval` | `QTT_EVENTS_INTERVAL` | `5s` |
| `qtt.events.max-age` | `QTT_EVENTS_MAX_AGE` | `10m` |
| `qtt.events.keep-alive` | `QTT_EVENTS_KEEP_ALIVE` | `20s` |
| `qtt.events.max-subscribers` | `QTT_EVENTS_MAX_SUBSCRIBERS` | `200` |

The settings and the cap are shared by every topic, so the `qtt.metrics.stream.*` names proposed below become these.
The nginx notes are in `administration.md` (Live updates through a reverse proxy).

Wire detail: Quarkus writes fields without a space after the colon (`event:datasources`, `data:{…}`); SSE parsers
must accept both forms, as the spec says.

To add a topic: a broadcaster with a `stream()` of snapshots, a name in `EventsController.KNOWN_TOPICS`, and a branch
mapping it to named events.

## Status: the `metrics` topic (2026-11-28)

Phase 1 is built, as a topic on the shared channel rather than a separate `/metrics/stream`, and with full-history
events rather than appended rows (simpler for the client; the history is at most 30 minutes × routes rows).

| Piece | Built |
|---|---|
| Stream | `GET /queryrest/api/events?topics=metrics[&datasource=<id>][&history=false]` (also with `datasources`). Event `metrics`, data `{"routes":[…],"history":[…]}`: `routes` are `MetricObject`s exactly as `GET /api/metrics/routes` returns them (sorted by route id), scoped to one datasource with `datasource` (same rule as `/api/metrics/routes/{datasourceId}`; unknown id = empty list); `history` is every stored row exactly as `GET /api/metrics/route/persisted` returns them |
| When | The first event has both keys (no `history` with `history=false`). Later events go out when the stream's routes changed (all fields but `timeStamp`, which live metrics leave null anyway; `uptime` counts, and Camel formats it to the second, so a started route changes on every tick) and/or the history changed. `routes` is always there; `history` (in full) only when it changed since the last event sent to that stream, otherwise the key is absent |
| Polling fallback | `GET /queryrest/api/events/metrics[?datasource=<id>][&history=false]`: the same object, freshly read, always both keys unless `history=false`; `Cache-Control: no-store` |
| Server | `MetricsBroadcaster` (`routebuilder.events`): one JMX scan per `qtt.events.interval` tick for all streams, only while a `metrics` stream is open, filtered per stream. The route → datasource map is one `routeService.getAll()` per scan, read only when a scoped stream is open. `RouteMetricsReader` (`routebuilder.metrics`) holds the JMX scan and datasource rule, shared with `MetricsController` |
| History changes | `QueryMetrics` (after a run that stored at least one snapshot, also when a later route failed) and `CleanMetrics` (after each delete) fire `MetricsHistoryChanged` (`query-service-metrics`) with `fireAsync`. The broadcaster, only while some stream wants history, reloads the history once, and pushes it only if it differs from the last load. A history change needs no JMX scan |
| Slow clients | A stream keeps only the latest event; the history is marked delivered only when an event carrying it is emitted with demand, so a replaced event can't lose it (the next event repeats it instead) |
| Auth, limits | As for `datasources`: `qtt-viewer`, the same 400/503 rules, keep-alive, max-age and headers; 499 / 403 covered in `SignInOnTest` |
| Tests | `MetricsBroadcasterTest`, `EventsControllerTest` (route-builder); `QueryMetricsTest`, `CleanMetricsTest` (scheduler); `EventsStreamTest`, `SignInOnTest`, `role-table.txt` (app) |

Remaining:

- Client (phase 2): `MetricsLiveService` and the datasource page's usage section switch to the stream, polling
  `/api/events/metrics` as the fallback.
- Phase 3 (end-to-end browser check) and the open-streams gauge from phase 4.
- `uptime` is Camel's formatted string, so any started route produces an event most ticks; if that proves chatty, send
  uptime less precisely or derive it client-side from a start time.
- Several instances behind a load balancer: unchanged (each streams its own JMX numbers; see Risks).

## Where we are

| Piece | Today |
|---|---|
| Current route metrics | `GET /queryrest/api/metrics/routes/` (or `/routes/{datasourceId}`) reads Camel's route MBeans over JMX **on every request**. The UI asks every 15 s per open page, and stops while the browser tab is hidden |
| History | `QueryMetrics` (Quarkus scheduler, every minute) stores one snapshot per route in the database; `CleanMetrics` deletes snapshots older than 30 minutes. `GET /metrics/route/persisted` returns them all; the UI asks every fourth poll (about once a minute) |
| Auth on these calls | OIDC `hybrid`: the browser has a session cookie; the UI sends `X-Requested-With: JavaScript` on every call so an expired session answers **499** (the UI then reloads to sign in) instead of a 302 to the IdP. `@RolesAllowed(qtt-viewer)` on the resource |
| Cost | Each open page costs one JMX scan of every route per 15 s, and the full 30-minute history once a minute (routes × 30 rows) |

## Decisions (proposed)

| Topic | Proposal | Why |
|---|---|---|
| SSE or WebSockets | **Server-Sent Events** | The data only flows server → browser. SSE is plain HTTP: the session cookie, `@RolesAllowed`, the 499 handling, proxies and HTTP/2 all work unchanged, and Quarkus REST (already a dependency) serves it from a Mutiny `Multi` with no new extension. WebSockets (`quarkus-websockets-next`) add a second protocol and their own auth handshake to gain a client → server direction nothing here needs. Revisit only if the UI needs to send many subscription changes over one connection |
| Browser client | `fetch()` with a streaming reader, not `EventSource` | `EventSource` can't send headers. Without `X-Requested-With` an expired session gets a 302 to the IdP, which `EventSource` treats as a network error and retries forever. `fetch` sends the header and the cookie, and the reader parses the `event:`/`data:`/`id:` lines (about 40 lines of code) |
| What is pushed | Full snapshots, not deltas: `routes` (current metrics for the subscriber's scope) and `history` (rows added since the last one) | A route snapshot is small (12 numbers per route). Snapshots make reconnects trivial: the first event after connecting is the whole state, so there is nothing to replay and `Last-Event-ID` can be ignored |
| How often | One shared server tick, **5 s** by default (`qtt.metrics.stream.interval`); an event goes out only when a subscriber's snapshot changed, plus a comment line every 20 s as keep-alive | One JMX scan per tick for the whole server however many pages are open (today it is one per page per poll). Idle routes send nothing |
| History updates | `QueryMetrics` fires a CDI event after it stores a minute's snapshots; the stream forwards the new rows as a `history` event. The UI appends them and drops rows older than 30 minutes | History changes once a minute; pushing the new rows replaces re-downloading all of them |
| Scope (datasource) | A query parameter: `GET /queryrest/api/metrics/stream?datasource=<id>`; changing the filter reconnects | Keeps the server filter (`routeBelongsToDatasource`) where it is today. Reconnecting on a filter change is cheap and rare |
| Session expiry and revoked roles | Each stream ends after **10 minutes**; the client reconnects at once | Authentication happens when a stream opens. Capping its life means an expired session (refreshed through `quarkus.oidc.token.refresh-expired`) or a removed role takes effect within 10 minutes, without the server tracking sessions |
| Hidden tabs | The client closes the stream 30 s after the tab is hidden and reopens it when the tab is shown | A background tab shouldn't hold a connection; the short delay avoids reconnect churn when switching tabs briefly |
| Fallback | If the stream endpoint is missing (an older server: 404) or fails 3 times in a row, the client polls as today, and tries the stream again every 5 minutes | Old servers and proxies that break streaming keep working |

## Server design

`query-service-route-builder` (next to `MetricsController`):

- **`MetricsBroadcaster`** (`@ApplicationScoped`): owns the tick. `Multi.createFrom().ticks().every(interval)` → scan the route MBeans once (reusing `MetricsController`'s `queryRouteMBeans` / `populateMetricsFromBean`, moved into a shared component) → a hot stream shared by every subscriber (`.broadcast().toAllSubscribers()`), started on the first subscriber and stopped after the last (`withCancellationAfterLastSubscriber`). It also observes the `MetricsPersisted` CDI event and publishes the new snapshot rows.
- **`GET /api/metrics/stream`** (`@RolesAllowed(qtt-viewer)`, `@Produces(SERVER_SENT_EVENTS)`): returns `Multi<OutboundSseEvent>`. Per subscriber: filter routes to the requested datasource, drop a snapshot equal to the last one sent (compare a hash), name events (`init`, `routes`, `history`), merge a 20 s keep-alive comment, end after 10 minutes (`select().first(Duration)`), and on overflow keep only the latest snapshot (`onOverflow().drop()`: a newer snapshot supersedes an older one).
  - `init` carries current routes plus the stored history, so a new page needs no other request.
- **Response headers**: `Cache-Control: no-store`, `X-Accel-Buffering: no` (nginx otherwise buffers the stream). HTTP compression stays off for this path (it buffers too).
- **Limits**: a cap on concurrent streams (`qtt.metrics.stream.max-subscribers`, default 200); past it the endpoint answers 503 and the client falls back to polling.
- **`QueryMetrics`**: after storing a minute's snapshots, `Event<MetricsPersisted>.fire(rows)`.

## Client design

In `MetricsLiveService` only; the page doesn't change.

- A `MetricsStream` transport: `fetch('/queryrest/api/metrics/stream?datasource=…', { headers: { 'X-Requested-With': 'JavaScript', Accept: 'text/event-stream' }, signal })`, read `response.body` as text, split events on blank lines, parse `event:` / `data:` lines, and set the existing signals (`routes`, `history`, `updatedAt`, `status`).
- Status mapping: stream open → `live`; reconnecting with data shown → `stale`; paused → stream closed, `paused`. "Updated 4 s ago" keeps working from `updatedAt`.
- Reconnect with backoff (1, 2, 5, 10, 15 s, with jitter); 499 → the same sign-in reload the HTTP interceptor does; 403 → stop and show the error banner.
- Pause closes the stream; Refresh reconnects (the `init` event is a full refresh).
- `proxy.conf.js` (dev server): no change needed; the dev proxy streams. Document the nginx settings (`proxy_buffering off;`, `proxy_read_timeout` above the keep-alive interval) in `administration.md`.

## Phases

| Phase | Work | Done when |
|---|---|---|
| 1. Server stream | `MetricsBroadcaster`, `/metrics/stream`, CDI event from `QueryMetrics`, config, limits | Quarkus tests: auth (401/403/499), events for a route that handles traffic, no event while idle, keep-alive, stream ends at the cap, datasource filter |
| 2. Client transport | `MetricsStream` in `MetricsLiveService`, fallback to polling, hidden-tab handling | Vitest: event parser (chunk boundaries, multi-line data, comments), reconnect/backoff, fallback on 404; the existing page tests pass unchanged |
| 3. End to end | `query-service-it` test with the browser stack (`scripts/gate.sh`, `ui_check.mjs`): page shows a route's new exchange within one tick | ui_check passes with OIDC on and off |
| 4. Operations | nginx/HAProxy notes, `qtt.metrics.stream.*` settings in `configuration.md`, a gauge of open streams on the health/metrics endpoint | Docs reviewed |

Rough effort: phase 1 two days, phase 2 one to two days, phases 3–4 one day.

## Risks and open questions

- **Several QTT instances behind a load balancer**: each instance streams its own JMX numbers, as the REST endpoint does today, so a page shows whichever instance it reached. Does each instance's `QueryMetrics` job also write its own history rows (so history mixes instances)? To check before phase 3; it affects today's page too.
- **Proxies that buffer or time out idle connections**: the keep-alive comment and `X-Accel-Buffering: no` handle nginx; other proxies need documenting. Polling fallback covers anything left.
- **Native image**: Mutiny and SSE work in native mode; the JMX scan already does. Check in the native build.
- **Reuse for other pages**: datasource health and route state could share the channel as more event names on one `/api/events` stream with topics in the query string. Worth doing when a second page needs it, not before.

## Out of scope

- Per-exchange events (pushing each query as it completes): too chatty; the 5 s snapshot is enough for a dashboard.
- Changing what is measured or how long history is kept (30 minutes, `CleanMetrics.minutesToLive`).

# Routes redesign plan

**Status (2026-11-28): all phases done, uncommitted on `feature/beautification`.** Verified: web unit tests
(391 → 495, 55 → 62 spec files), production build (initial bundle 1.61 MB, below where it started), full Maven
build with every module's tests, browser flows against a mock API (list, live updates, bulk, dialogs, new route, route
page tabs, save, leave guard, degraded datasource, quick actions), and the gate against a built image: every
integration suite (Derby, PostgreSQL+Redis, SQL Server, sign-in, closed default, Redis with slow tests), restart
persistence, TLS, encryption, and `scripts/ui_check.mjs` (updated for the new pages) 13/13, and 19/19 with sign-in.
The UI check's later steps waited for `networkidle0`, which never comes while a page holds an SSE stream (a leftover
from the metrics SSE work); they now wait for `networkidle2`.

Deviations from the plan, and bugs found on the way (each with a unit test):

- **Not a bug after all:** "cache-off never sent". A save deletes and re-creates the route from a fresh entity whose
  `cacheEnabled` defaults to `false`, so leaving the parameter out did turn caching off. The client now sends every
  setting explicitly anyway. Likewise the "status Add Route ignores" was dead client code; the server decides.
- **Saving a stopped route started it again** (save = re-create, which starts). Fixed with the stop reason: a stopped
  route stays stopped across a save, except one stopped only for lack of a template.
- **Cloning twice overwrote the first copy** (always `<id>Clone`; creating under a taken id replaces). Clone now picks
  `<id>Clone`, `<id>Clone2`… and returns the new id.
- **Creating a route under a taken id silently replaced it.** `POST /routes/{id}` now answers `409` (API change,
  documented in api-reference.md; the scripts that create routes all use fresh ids).
- **The Test tab sent layer titles, not IRIs**; the test route passes them to the same `generateCamelUrl` as the real
  route, so IRIs are right (settled from the code; no Graph Studio run needed).
- **Loading a route never loaded its layers' list** in the new draft (caught by its test before shipping), and the
  **graphmart field showed the IRI** until the list arrived (fixed; component test).
- **Bulk Start counted routes that can't start**; the snackbar covered the bulk bar; sparse routes drew noise
  sparklines (minimum scale); stopped routes showed "0/min" (now dashes).
- Filters are not persisted (sort and grouping are): a remembered search hides routes on the next visit.
- The routes page is lazy-loaded now (its filters and selection would have grown the initial bundle by 80 KB).
- Stop/delete use one dialog that runs the requests itself and lists failures with a retry of just those, instead of
  the shared yes/no confirm.


Branch: `feature/beautification`. Brings the Routes area (list, add, delete, route page) to the look of the
redesigned Datasources and Metrics pages: `qtt-*` surfaces, live data over `/api/events`, motion that explains
change. Also moves the Metrics page onto the shared `qtt-*` surfaces.

## Decisions (2026-11-28)

| Topic | Decision |
|---|---|
| Scale | Up to ~50 routes. One scrolling page, no paginator. |
| List layout | Flat list of dense two-line rows, with a "Group by datasource" toggle (remembered per viewer). |
| Live data | Per-route calls/min, error rate, mean latency and a 30-minute sparkline, pushed live. |
| Row actions | Inline start/stop, copy endpoint URL, bulk select (start/stop/delete), confirm before stop. |
| Add route | A full routed page (`/routes/add`), not a dialog. |
| Route page | Persistent header + tabs Overview / Template / Test; tab in the URL (`?tab=`). |
| SPARQi | Side panel opened from the header, stays beside every tab. |
| Unsaved edits | Tracked (marker, Discard) and guarded (leave-page prompt, `beforeunload`); route page and Add route. |
| Bugs fixed along the way | Silent no-op save; cache-off never sent; Monaco completion-provider leak; add-route status ignored and `location.reload()` after add/delete. |
| Metrics page | Migrated to the shared `qtt-*` surfaces in this effort. |
| Routes stopped by the health check | The server records why a route is stopped; the list says so and offers to restart them. |
| Route page behaviour | Save stays on the page; Test results survive tab switches; Test sends layer IRIs (verify against Graph Studio). |

## Design language (shared with Datasources)

- `qtt-page` + `qtt-page-header` (title, one-line subtitle, `qtt-live-indicator`, primary action).
- A summary card: one-sentence headline, a split bar by status, `qtt-toggle` filters, `qtt-search`.
- Status colour only where it means good / bad / attention (`--qtt-good`, `--qtt-warn`, `--qtt-bad`).
- Skeletons while loading, `qtt-empty` for none / no match, `qtt-banner` for errors and paused checks.
- Numbers tween (`qttTweenNumber`); rows enter/leave with `animate.enter`/`animate.leave`; reduced motion honoured.

### Route status model

A route's displayed status combines its Camel state and its datasource's health:

| Status | Rule | Tone |
|---|---|---|
| Running | Started, datasource UP / CHECKING / UNKNOWN | good |
| Blocked | Started, datasource DOWN or DISABLED | bad (DOWN) / warn (DISABLED) |
| Stopped | Stopped | neutral |

"Blocked" is today only a small warning icon; it becomes a first-class status (split bar, filter, row tone),
because it is the thing an operator most needs to see. A stopped route whose datasource is DOWN or DISABLED shows
"can't start: datasource down" on its start control; UNKNOWN and CHECKING don't block starting (this matches the
server, which answers 409 only for DOWN and DISABLED; today's UI also blocks UNKNOWN).

**Routes the health check stopped.** With `consecutiveFailureThreshold` set (default -1, off), the health checker
stops every started route of a failing datasource and marks it Stopped; nothing restarts them when the datasource
recovers, and the data doesn't say why a route is stopped. On such a deployment "Blocked" rarely appears and
routes sit stopped after an outage. So the server records the reason (Phase 0), and Stopped splits in two for
display: "Stopped" (by a person) and "Stopped by the health check" (attention tone while its datasource is back
UP, since it is waiting to be restarted; neutral while the datasource is still down).

## Phase 0: groundwork

1. **Safety net.** Fix `config-route.component.spec.ts` (param `routeId`, not `id`); add specs for the pieces
   extracted below before moving code.
2. **`RouteActionsService`** (core/services): start, stop (with confirm), clone, delete (with confirm), copy URL,
   bulk start/stop/delete (client-side fan-out, at most 4 at a time, one result summary), `blockedReason(route)`.
   Snackbar on success/failure. Used by the list, the route page and the quick actions bar, replacing the three
   copies of start/stop and the two copies of the datasource-health check.
   Stop confirms everywhere (list, route page, bar), resolving the open question from the quick actions work.
3. **`RoutesLiveService`** (provided by RoutesComponent, shared with child pages like `DatasourceLiveService`):
   - route list from `GET /api/routes/` (REST; there is no routes topic);
   - traffic and state from the existing `metrics` topic (with history) via `MetricsLiveService`;
   - datasource health from the `datasources` topic via `DatasourceLiveService`;
   - refetches the route list when the metrics topic's set of route ids or a route's state *changes* in a way the
     list doesn't already show (another user added, deleted, started or stopped a route). It must react to changes,
     not to disagreement: the topic reports every route Camel runs, including ones with no database row (temporary
     test routes from the Test tab, for example), so a standing mismatch is normal and must not refetch every tick.
     Ids the list doesn't know are ignored after one refetch;
   - one `/api/events?topics=datasources,metrics` stream for the page rather than one per service (the endpoint
     already carries several topics; each open tab otherwise holds two long-lived connections);
   - pure derive module (`routes-derive.ts`): status model, per-minute rates and sparkline points from the
     cumulative 1-minute history (reuse `metrics-derive.ts` helpers), sorting, grouping, filtering, headline.
4. **`qtt-sparkline`** (shared): a small SVG component, not Chart.js (50 chart instances is too heavy). Morphs
   between updates; an error tick where a minute had failures; accessible label summarising the trend.
5. **Router**: `/routes/add` (lazy `loadComponent`) declared before `config-route/:routeId`. Replace the
   `router.isActive('/routes', true)` / URL-string checks with a `listActive` signal as on Datasources; unsubscribe
   the router-events subscription. A `canDeactivate` guard for unsaved edits (the app's first).

### Server: stop reason (the plan's only server change)

- `CamelRouteTemplate.stopReason` (nullable string column `stopReason`; schema strategy is `update` on Derby,
  PostgreSQL and SQL Server, so the column is added on start; existing rows read null = unknown/manual) and
  `stoppedAt` (timestamp, nullable). Values: `HEALTH_CHECK`, `MANUAL`.
- `SimpleHealthChecker.stopRoutesForDatasource` sets `HEALTH_CHECK`; `RouteManagementServiceImpl.updateRouteStatus`
  sets `MANUAL` on stop and clears both on start. Go through one `RouteService.updateRouteStatus(id, status,
  reason)` so no caller can stop a route without a reason. A route created stopped (no template) has no reason.
- Returned on `GET /api/routes` and `/api/routes/{id}` with the entity; no new endpoint. Document in
  `api-reference.md`.
- Tests: health-checker stop sets the reason, manual stop/start set and clear it, null for old rows.
- Not in scope: restarting routes automatically on recovery (an operator decision; the UI makes it one click).

## Phase 1: routes list

```
Routes                                             ● Live   [+ Add route]
The query endpoints QTT serves, with their traffic as it happens.

┌───────────────────────────────────────────────────────────────────────┐
│ 42 routes · 37 running · 2 blocked by a down datasource               │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░▒                                      │
│ [All 42][Running 37][Blocked 2][Stopped 3]   Datasource ▾  Access ▾   │
│ [Find a route…]            Sort: Name ▾       ☐ Group by datasource   │
└───────────────────────────────────────────────────────────────────────┘
☐ ● people-search   GET POST   prod-gs ●  🔒   ▁▂▃▅▃▂▁▂  12/min 0.4% 85ms  [■ Stop] ⧉ ⋮
    Find people by name and organisation
☐ ▲ asset-graph     GET        staging ✖  🔒   ────────  —                 [■ Stop] ⧉ ⋮
    Blocked: datasource staging is down · 3 failed checks
☐ ○ legacy-export   GET        prod-gs ●  🌐   ────────  stopped           [▶ Start] ⧉ ⋮
    Nightly export of the asset register
```

- **Header**: title, subtitle, live indicator (pause/refresh), Add route (editor role) linking to `/routes/add`.
- **Summary card**: headline sentence; Running / Blocked / Stopped split bar; status toggles with counts;
  datasource and access filters; search across ID, description and datasource; sort (Name, Status, Busiest,
  Slowest, Most errors); "Group by datasource" switch. Filters, sort and grouping persist per viewer
  (try/catch localStorage). Not mirrored into the URL: with ~50 routes a linkable filter isn't worth two sources
  of truth.
- **Rows** (`ul` of rows, not `mat-table`; one row is a link to the route page):
  - line 1: status dot, route ID (monospace), HTTP methods, datasource chip with its health (links to the
    datasource), access badge (open / authenticated), sparkline, calls/min, error rate, mean latency;
  - line 2: description, or the blocked reason when blocked;
  - actions: start/stop button (disabled with the reason when the datasource is unhealthy; stop confirms), copy
    endpoint URL (replaces the Endpoint URL column), ⋮ menu (Configure, Test, Clone, Delete);
  - selection checkbox (editor role) shows on hover/focus and stays visible once anything is selected.
- **Grouped view**: a section per datasource with its health pill, route count and running count in the header;
  collapsible; sections for DOWN/DISABLED datasources first.
- **Bulk bar**: sticky at the bottom when routes are selected: "3 selected · Start · Stop · Delete · Clear".
  Start skips (and reports) routes whose datasource is unhealthy.
- **Recovery prompt**: when routes stopped by the health check have a datasource that is UP again, a `qtt-banner`
  says "12 routes were stopped while prod-gs was down. It's back." with "Start them" (editor role; the bulk start
  path, one summary) and "Review" (filters the list to them). Their rows say "Stopped by the health check · 2 h
  ago" on line 2. The grouped view shows the same per datasource section. One confirm for stop/delete listing the routes;
  one summary afterwards ("2 stopped, 1 failed: …").
- **Motion**: rows tween numbers and the sparkline morphs on each push; status changes cross-fade the dot and
  slide the blocked reason in; filtered rows enter/leave; the split bar animates segment widths.
- **States**: skeleton rows, empty ("No routes yet" + Add route), no match (+ clear filters), load error banner,
  metrics-unavailable (traffic cells show "—" with a tooltip, list still works).
- **Narrow screens** (<700px): line 1 wraps; traffic collapses to calls/min + sparkline; actions to ⋮.
- Keyboard: rows are focusable links; actions reachable by Tab; Space toggles selection on a focused row.

## Phase 2: dialogs

- **Delete route**: restyle as `qtt-dialog` (matching the datasource delete dialog): what is deleted, its traffic
  in the last 30 minutes as a warning when non-zero, danger button. Returns a result; no `location.reload()`;
  error shown in the dialog. Bulk delete uses the same dialog with a list.
- **Stop confirm**: `shared/confirm-dialog` with the route's current calls/min ("callers will get errors").
- **Clone**: no dialog; after cloning, the new row enters and is highlighted, with "Open" in the snackbar.

## Phase 3: Add route page (`/routes/add`)

- Same page frame as the route page: header (breadcrumb, "New route", Cancel, Create), sections Basics (ID with
  format + uniqueness validation against the loaded list, methods, description), Data (datasource with health,
  graphmart, layers), Access, Cache (when the cache is enabled), Template (`app-code-editor`, upload, validation).
- Live endpoint preview (`app-endpoint-panel`) updates as the ID is typed.
- Create sends the status it works out (Stopped when no template) — fix `add-route.service.ts`. On success,
  navigate to the new route's page (Overview), no reload.
- Unsaved-edit guard. Quick actions "Add route" navigates to `/routes/add`; remove `ADD_ROUTE_PARAM` and its
  `onRequestedAction` hook (update `global-actions.spec.ts`, `requested-action.spec.ts`).
- Lazy-loaded (Monaco stays out of the main bundle). Encapsulated SCSS (drop `ViewEncapsulation.None`).

## Phase 4: route page (`/routes/config-route/:routeId`)

```
← Routes / people-search                     ● Unsaved changes  [Discard] [Save]
people-search  ● Running  [■ Stop]  [▶ Test]  [✦ SPARQi]  [⋮ Clone · Delete · Copy URL]
Find people by name and organisation
https://host/queryrest/api/people-search ⧉   🔒 Authenticated   prod-gs ● UP
12/min · 0.4% errors · 85 ms mean  ▁▂▃▅▃▂▁▂▃▄      Created by … · changed 2 h ago
───────────────────────────────────────────────────────────────────────────────
[Overview]  Template  Test
```

- **Header** (persistent): breadcrumb, ID, status pill + start/stop (confirm), Test (jumps to Test and runs),
  SPARQi toggle, ⋮ (Clone, Delete, Copy URL); meta line (endpoint with copy, access badge, datasource health
  pill linking to the datasource, audit line); live traffic strip with sparkline (from `RoutesLiveService`, so
  status changes made elsewhere show up). Unsaved marker + Discard + Save (editor role). The datasource warning
  banner stays, restyled as `qtt-banner`.
- **Tabs** via `?tab=overview|template|test` (default overview). Tab content kept mounted (no Monaco
  re-creation, no lost test results); hard-coded tab indices replaced by named tabs.
- **Overview**: cards for Request (methods, description, endpoint panel), Data (datasource, graphmart, layers),
  Access (`app-route-access`), Cache (enabled, TTL, key strategy, this route's cached entries, Clear cache;
  Redis host and global hit rate demoted to a hint, labelled as global). A disabled/unavailable cache is a card
  state, not a disabled tab.
- **Template**: full-height editor; fullscreen; upload; syntax error strip; SPARQi panel beside it.
- **Test**: as today (SPARQi generation, body, query params, execute, SPARQL/results/error), restyled; results
  are no longer reset on every tab entry; runs against the unsaved draft (says so when the draft differs from the
  saved route). Layers sent as IRIs, as save does (today: titles).
- **SPARQi** side panel at page level beside any tab; resize kept; width remembered.
- **Structure**: split the 1692-line component into a `RouteDraft` store (form, layers, graphmart/layer
  title→IRI maps, template, saved baseline, dirty signal) provided by the page, plus `route-header`,
  `route-overview`, `route-template`, `route-test` components. Replace the hand-built Monaco with
  `app-code-editor` (as Add route uses), keeping fullscreen/relayout via its API. Page quick actions move to the
  page shell and use `RouteActionsService`. Load the single route with `GET /api/routes/{id}` instead of the
  whole list (same entity as the list; it answers 404 when the route isn't in the Camel context, so handle that
  as "route not found" with a link back). Encapsulated SCSS replaces the 1220-line global stylesheet (check add-route's shared class names).
- **Unsaved edits**: dirty = serialized draft (form raw value + layers + template) differs from the baseline
  taken once everything has loaded (so async defaults like the cache TTL don't mark it dirty). Save stays on
  the page and refreshes the baseline and audit line (today it navigates away). Guard + `beforeunload`.
- **Bug fixes**: Save uses `getRawValue()`; when graphmarts/layers couldn't load, the fields show "couldn't load
  from Graph Studio, kept as saved" and Save sends the saved values instead of silently returning.
  `config-route.service.ts` / `add-route.service.ts` send `cacheEnabled` and `cacheTtlSeconds` whenever defined
  (the server keeps the old value when a param is missing, so turning caching off never stuck). Keep and dispose
  the ontology completion provider (`ontology-autocomplete.provider.ts`), on the route page and Add route.

## Phase 5: Metrics page onto `qtt-*`

Replace the page-local card, tile, table, banner and state styles in `metrics.component.scss` (613 lines) with
the shared `qtt-*` classes; keep chart-specific styles. Reuse `qtt-sparkline` where the page draws small trends.
No behaviour change; screenshots before/after to confirm.

## Phase 6: verify and document

- Unit specs for `routes-derive`, `RouteActionsService`, `RoutesLiveService`, `RouteDraft` (dirty, baseline,
  raw-value save, degraded mode), the guard, sparkline; route list/page component specs.
- Full web suite + production build (initial bundle must not grow by Monaco); Java: persistence, health, route-builder suites for the stop reason.
- Browser: mock API harness (metrics + datasources topics, ~40 routes incl. blocked and stopped), `ui_check.mjs`
  for list / add / route page at desktop and 390px, light and dark; motion capture for live pushes, status change,
  bulk actions; reduced motion.
- Docs: `user-guide.md` (Routes, route page, Add route), screenshots (`docs/config-route-screen.png`).

## Open items to watch

- The `metrics` topic emits nearly every tick because of `uptime`; fine for this page, but rows must not
  re-render when values are unchanged (track by route ID, compare before tweening).
- Test sends layer IRIs instead of titles: agreed, but verify against a real Graph Studio which form the test
  route needs before changing it.
- Order of delivery: the cache-off fix is a live bug independent of the redesign and can ship first on its own.
  Phase 4 is the largest and least tested part; land it as its own PR after the list.
- Deep links to `/routes/config-route/:id` are built by hand in recents, the quick actions bar and
  config-datasource; centralise in a `routeLink(id, tab?)` helper.

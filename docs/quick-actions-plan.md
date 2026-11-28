# Quick actions bar

Goal: a search field in the top toolbar, in the style of GitLab's and GitHub's, that lets a user jump to any page, route or datasource and run common actions from the keyboard.

Status: Phases 1–3 built on `feature/quick-actions` (from `refactor/optimize` at `08c8ae8`), 2026-11-28; Phase 4 (two-step actions) not started. Each phase passed `ng test`, `ng build` and a browser check against an isolated stack before the next began; results are under [Checks](#checks).

Where the build differs from the plan below:
- **Keyboard listener on `window`, not `document`.** zone.js shares one native listener per target, event and phase, with the options of the first one added. The CDK's focus tracking adds a *passive* capturing `keydown` listener to the document first, so ours inherited `passive` and `preventDefault()` was silently ignored (Ctrl+K would still focus the browser's address bar). A spec pins this.
- **No `ActiveDescendantKeyManager`.** The highlighted row is a plain index over the flattened results; the ARIA wiring (`aria-activedescendant`, `aria-selected`) is the same.
- **Refetching the lists:** also after any navigation, not only after 30 seconds, so a route just added or deleted shows correctly the next time the bar opens.
- **Empty bar:** the page's own actions are listed in full (other groups show 5); Settings sections are listed only once something is typed; groups go *This page, Recent, Go to, Actions*.
- **Closing the bar clears** its text and narrowing.
- **Clear all caches** says how many cached *results* will go (the server can't count affected routes), and skips the question when the cache is off or empty.
- **More page actions:** Save datasource and Show usage on the datasource page.
- **Fixed on the way:** `CacheService.clearAllCache()` called `DELETE /routes/cache/all`, which doesn't exist (the server has `DELETE /routes/cache`); the datasource page read its route list from `camelRouteTemplate`, but the API sends `camelRouteTemplateNames`, so the list was always undefined (and the delete dialog read `.length` of undefined).
- **Test harness:** `scripts/stack.sh` takes `QTT_STACK` (name suffix) and `QTT_PORT_OFFSET`, and `gate.sh`, `tls_check.sh` and `encryption_check.sh` follow them, so a second stack can run beside another session's. `proxy.conf.js` takes `QTT_PROXY_TARGET`.

## What the user gets

```
┌──────────────────────────────────────────────────────┐
│ ☰ QTT   [ 🔍 Search or jump to…        / ]    👤 ben │
└─────────┬──────────────────────────────┬─────────────┘
          │ This page                    │
          │  ▸ Clear this route's cache  │
          │ Routes                       │
          │  ▸ people-search    Started  │
          │  ▸ people-by-org    Stopped  │
          │ Datasources                  │
          │  ▸ anzo-prod                 │
          │ ──────────────────────────── │
          │ ↑↓ move  ↵ open  esc close   │
          └──────────────────────────────┘
```

- **Open it:** click the field, press `/` (when not typing in a field or editor), or press `Cmd+K` / `Ctrl+K` anywhere, including inside the Monaco editor.
- **Empty input:** shows the current page's actions, then recently visited routes and datasources, then page navigation.
- **Typing:** fuzzy-matches across actions, routes, datasources and pages, grouped, with the matched characters highlighted.
- **Prefixes narrow the search:** `>` actions only, `#` routes only, `@` datasources only, `?` shows the shortcut list.
- **Keys:** `↑`/`↓` move, `Enter` runs, `Esc` closes and returns focus to where it was.
- **Cheat sheet:** `?` outside a field opens a small dialog listing every shortcut.

## Decisions

| Topic | Decision | Why |
|---|---|---|
| Form | A visible field in the toolbar that opens a panel anchored under it | Chosen 2026-11-28. Discoverable without knowing a shortcut |
| Scope of v1 | Page navigation, route/datasource search, contextual actions, prefixes, recents and the cheat sheet | Chosen 2026-11-28. Built in three phases (below) so each is usable alone |
| Actions that change state | Allowed, hidden from roles that can't run them, and destructive ones ask for confirmation | Chosen 2026-11-28. The server enforces roles regardless, as with `*qttIfRole` |
| Look | The current Material theme and its `--mat-sys-*` variables; no new visual direction | Chosen 2026-11-28. The redesign (`feature/beautification`) restyles it later through the same variables |
| Panel mechanics | CDK Overlay + `ActiveDescendantKeyManager`, with the ARIA combobox/listbox pattern. Not `mat-autocomplete` | `mat-autocomplete` writes the chosen option into the input and has no place for the footer, prefix modes or per-row hints. `@angular/cdk` 22 is already a dependency |
| Matching | A small in-house scorer: prefix match, then word-start match, then in-order subsequence | The lists are tens to hundreds of items. A fuzzy-search library isn't worth a dependency |
| Where actions live | A registry service. Pages register their own actions on init and remove them on destroy | A page's action then calls the page's existing method, so it reuses the existing confirm dialog, snackbar and refresh logic |
| Entity data | Fetch routes and datasources when the bar first opens; reuse for 30 seconds, then refetch on the next open | No polling, and no request at all for users who never open the bar |
| Recents | Last 5 routes/datasources visited, in `localStorage` under a key that includes the username | Recorded from the router, so visits made by clicking a table row count too. Entries that no longer exist are dropped |
| `Cmd/Ctrl+K` in Monaco | The bar wins everywhere: a capture-phase `keydown` listener on `document` takes the key before Monaco does | Confirmed 2026-11-28. Monaco uses `Cmd+K` as the first key of two-key shortcuts (e.g. `Cmd+K Cmd+C`); those stop working in QTT's editors. `Cmd+/` still toggles comments |
| While a dialog is open | Both shortcuts are ignored | The bar would open behind, or steal focus from, a modal dialog |
| No-access users | The bar is not rendered | Same rule as the drawer button |
| Narrow screens | Below about 720px the field collapses to a search icon button | The toolbar already holds the logo, title and user menu |

## Actions

Roles come from the existing `*qttIfRole` gates in the templates.

### Always available

| Action | Role | Notes |
|---|---|---|
| Go to Metrics / Routes / Datasources / Settings | viewer | |
| Go to a Settings section (Sign-in, Query access, Caching, SPARQi, …) | viewer | Uses the existing `?section=` links |
| Add datasource | admin | Navigates to `/datasources/add-datasource` |
| Add route | editor | Navigates to `/routes` and opens the add-route dialog |
| Create API key | admin | Navigates to Settings → Query access |
| Clear all caches | admin | New: `CacheService.clearAllCache()` exists but no page calls it. Gets a new confirm dialog that says how many routes are affected. The only action reachable from the bar and nowhere else |
| Keyboard shortcuts | viewer | Opens the cheat sheet |
| Sign out | any | Only when sign-in is on |
| Open route `<id>` / Open datasource `<id>` | viewer | The search results |

### On a route's page (`/routes/config-route/:routeId`)

| Action | Role | Notes |
|---|---|---|
| Save route | editor | Calls `putRoute()` |
| Clear this route's cache | editor | Calls `clearRouteCache()`, which already confirms |
| Turn route on / off | editor | New on this page: today only the Routes table offers it. "On" is disabled while the datasource is down or disabled, as in the table |
| Run test request | editor | Calls `executeTest()` |
| Toggle SPARQi chat | editor | |
| Toggle editor fullscreen | viewer | |
| Copy query endpoint URL | viewer | |

### On a datasource's page (`/datasources/config-datasource/:dataSourceId`)

| Action | Role | Notes |
|---|---|---|
| Test connection | admin | |
| Run health check | editor | |
| Enable / Disable datasource | admin | Disable asks for confirmation (new) |
| Delete datasource | admin | Opens the existing delete dialog |

### Left out of v1

- **Delete route, clone route, start/stop a route from anywhere:** these live on Routes table rows. Doing them from the bar needs a two-step flow (pick the action, then pick the route). Decided 2026-11-28: a follow-up (Phase 4) after the three phases below are in use.
- **`g r` / `g d` go-to chords.**
- **Searching inside templates or ontology terms.**

## Design

New code goes under `app/shared/quick-actions/`:

| File | Purpose |
|---|---|
| `quick-action.ts` | The `QuickAction` type: `id`, `label`, `icon`, `group`, `keywords`, `minRole`, optional `hint`, `disabled` (with a reason), and `run()` |
| `quick-actions.service.ts` | The registry. `register(actions)` returns a function that removes them. Exposes the current list filtered by `AuthService.hasRole()` |
| `entity-index.service.ts` | Routes and datasources as search results, from `RoutesService` and `DatasourceService`, with the 30-second reuse rule |
| `recents.service.ts` | Listens to `NavigationEnd`, records visits to the two config pages, reads and writes `localStorage` (and tolerates it being unavailable) |
| `match.ts` | The scorer: `(query, text) → { score, ranges } \| null`. Pure functions |
| `quick-actions-bar.component.*` | The toolbar field, the overlay panel, keyboard handling, prefix parsing, result grouping |
| `shortcuts-dialog.component.ts` | The cheat sheet |
| `global-shortcuts.service.ts` | The `document` listeners for `/`, `Cmd/Ctrl+K` and `?`, with the "is the user typing?" and "is a dialog open?" checks |

Changes to existing code:

- `app.component.html` / `.ts` / `.scss`: place `<app-quick-actions-bar>` in the toolbar between the title and the spacer, inside the `!auth.noAccess` condition.
- `config-route.component.ts`, `config-datasource.component.ts`: register the page's actions in `ngOnInit`, remove them in `ngOnDestroy`. Add turn on/off to the route page.
- `routes.component.ts`: open the add-route dialog when navigated to with the "add" request from the bar.
- `scripts/ui_check.mjs`: a quick-actions check (below).
- `docs/user-guide.md`: a short section on the bar and its shortcuts.

Results show at most 5 per group when unscoped, and scroll when a prefix narrows to one group. The route list response includes each route's full template; that is the same request the Routes page makes today, so it is acceptable, but if it proves slow on large installs the fix is a slim server endpoint (not planned).

## Phases

Each phase ends with `ng test`, `ng build` and the headless UI check passing.

### Phase 1: the bar, navigation and search

1. `match.ts` with its spec (ranking order, highlighting ranges, no match).
2. Registry, entity index and the static navigation actions.
3. The bar component: field, overlay panel, grouping, keyboard movement, ARIA roles, focus return on close.
4. Global shortcuts, including the Monaco capture and the dialog-open rule.
5. Toolbar placement and narrow-screen collapse.

Done when: from any page, `Cmd+K`, typing part of a route ID and `Enter` lands on that route's page; the same works from inside the template editor.

### Phase 2: actions

1. Role filtering and the disabled-with-reason state.
2. Route page and datasource page actions, with turn on/off added to the route page.
3. The always-available actions (add route, add datasource, create API key, sign out).
4. Confirmation for disabling a datasource.
5. Clear all caches (admin), with its new confirm dialog. Check which role the server requires for `DELETE /queryrest/api/routes/cache/all` and match it.

Done when: a viewer sees no editor or admin actions; an editor can clear a route's cache from the bar and gets the same confirm dialog as the button.

### Phase 3: prefixes, recents and the cheat sheet

1. Prefix parsing (`>`, `#`, `@`, `?`) with a chip showing the active scope.
2. Recents service and the empty-input layout.
3. The shortcuts dialog and the `?` key.
4. User guide section.

## Checks

- **Unit (Vitest):** scorer; registry add/remove and role filtering; entity index reuse; recents (cap of 5, removed entities dropped, storage unavailable); bar keyboard behavior; shortcut rules (`/` ignored in inputs and in Monaco, `Cmd+K` not ignored, both ignored under a dialog).
- **Monaco:** a spec in `third-party-integration.spec.ts` that `Cmd+K` with focus in a real editor opens the bar and the editor does not enter chord mode.
- **Headless UI check (`scripts/ui_check.mjs`):** open with the keyboard, search for the seeded route, press `Enter`, land on its page; as a viewer, confirm that editor actions are absent.
- **Accessibility:** combobox/listbox roles, `aria-activedescendant`, visible focus, and a screen-reader announcement of the result count.
- **Full gate (`scripts/gate.sh`)** before merging.

Results, 2026-11-28, image `localhost/qtt:quick-actions` built from this branch, run as `QTT_STACK=qa QTT_PORT_OFFSET=2000` beside another session's stack:
- `ng test`: 331 tests pass (252 before); `ng build`: no warnings, initial bundle 1.65 MB (1.64 MB before).
- Gate: IT suites pass on Derby, PostgreSQL+Redis, MSSQL, Derby with sign-in, closed-by-default and Derby+Redis (93–94 tests each); restart persistence, custom TLS keystore and encryption at rest pass. The first UI check run failed only the new quick-actions checks: keys typed straight after Ctrl+K were lost, because the field was focused one tick late (fixed: focus at once), and the OIDC checks expected the sign-in round trip to keep the `#fragment` (it doesn't; the checks now navigate after signing in). After the fix, both UI checks pass: 17/17 with sign-in (viewer and editor see only their actions), 11/11 without.
- Browser checks per phase (scratch scripts against `ng serve` on port 4410 and the offset stack): opening from `/`, Cmd+K and Ctrl+K, including from inside Monaco with the editor's text unchanged; Escape returning focus to the editor; every route and datasource page action against the real server; the confirmations; clearing a route's cache and all caches with Redis; narrow screens; prefixes, recents across reloads and the shortcut list.

## After v1

**Phase 4, two-step actions:** an action can ask for a target. Choosing "Delete route…", "Clone route…", "Turn route on…" or "Turn route off…" switches the panel to a route picker; `Enter` on a route runs the action (delete through the existing dialog), `Backspace` on an empty input goes back a step. This needs a `target` field on `QuickAction` and a second panel state, so the registry type in Phase 1 should leave room for it.

# Web upgrade log

Results of the checks in [web-upgrade-plan.md](../web-upgrade-plan.md), one entry per step. Builds and tests ran on the
development Mac (arm64, Chrome 158). Bundle sizes come from `ng build` (production): "initial" is raw / estimated
transfer. The gate (`scripts/gate.sh`) ran in a separate worktree for each phase's last commit, after a full
`mvn install` (1210 Java unit and app tests each time) and an image build.

## Summary

| Step | Node | Angular | TS | Unit tests | Coverage S / B / F / L | Initial bundle | Output | Build warnings | Gate |
|---|---|---|---|---|---|---|---|---|---|
| Baseline (`3bd270b`) | 20.18.1 | 18.2.14 | 5.5.4 | 225 (Karma) | 46.8 / 31.5 / 47.3 / 46.3 | 5.51 MB / 1.08 MB | 21 MB | 99 CommonJS + 1 budget | pass |
| 0 Safety net (`886cb94`) | 20.18.1 | 18.2.14 | 5.5.4 | 245 | 50.2 / 37.9 / 49.1 / 49.9 | 5.51 MB / 1.08 MB | 21 MB | 99 + 1 | pass |
| 1 Node 26 (`c3cd8fe`) | 26.13.1 | 18.2.14 | 5.5.4 | 245 | same | same | 21 MB | 99 + 1 | pass |
| 2 Angular 19 (`58cfe24`) | 26.13.1 | 19.2.25 | 5.8 | 245 | same | 5.56 MB / 1.09 MB | | 101 + 1 | UI: JSON editor worker (see below) |
| 3 Angular 20 (`d569d96`) | 26.13.1 | 20.3.35 | 5.9 | 245 | same | 5.57 MB / 1.10 MB | | 101 + 1 | UI: same |
| 4 Angular 21 (`cd7c8bb`) | 26.13.1 | 21.2.26 | 5.9 | 245 | same | 6.20 MB / 1.23 MB | | 104 + 2 | pass |
| 5 Angular 22 (`5d47780`) | 26.13.1 | 22.3.0 | 6.0 | 245 | same | 6.52 MB / 1.29 MB | | 104 + 2 | pass |
| 6 esbuild, one Monaco (`57fd6c8`) | 26.13.1 | 22.3.0 | 6.0 | 252 (Karma) | 51.4 / 39.9 / 50.0 / 51.1 | 5.96 MB / 1.15 MB | 8.5 MB | 1 budget | pass |
| 7 Vitest (`0949233`) | 26.13.1 | 22.3.0 | 6.0 | 252 (Vitest) | 53.4 / 39.4 / 49.9 / 52.4 | 5.96 MB / 1.15 MB | 8.5 MB | 1 | pass |
| 8 Standalone, lazy editors (`2cb99e2`) | 26.13.1 | 22.3.0 | 6.0 | 252 | 54.9 / 39.9 / 50.9 / 54.0 | 1.67 MB / 329 kB | | 1 | pass |
| 9 Theming (`58f9857`) | 26.13.1 | 22.3.0 | 6.0 | 252 | same | 1.62 MB / 325 kB | | 0 | pass |
| 10 Hardening (`3453949`) | 26.13.1 | 22.3.0 | 6.0 | 252 | 54.9 / 39.9 / 50.9 / 54.0 | 1.62 MB / 325 kB | 8.7 MB | 0 | pass |

Coverage numbers change instrument between Karma (istanbul through webpack, then esbuild) and Vitest; within one
runner they only went up. The floor is enforced in the build (Karma `check`, then Vitest `coverageThresholds`).

## Before and after

| | Before | After |
|---|---|---|
| Node / npm | 20.18.1 (end of life) / 10.8.2 | 26.13.1 LTS / 11.20.0 |
| Angular / Material | 18.2 / 18.2 | 22.3 / 22.3 |
| TypeScript | 5.5 | 6.0 |
| Builder | webpack (`@angular-builders/custom-webpack:browser`) | esbuild (`@angular/build:application`) |
| Test runner | Karma + Jasmine | Vitest (browser mode, Chrome) |
| npm packages installed | 1118 (with `--legacy-peer-deps`) | 396 (`npm ci`, strict peers) |
| `npm audit` | not checked | 0 vulnerabilities |
| Production build | 26 s | 9 s |
| Unit tests | ~40 s | ~12 s |
| Initial bundle | 5.51 MB / 1.08 MB transfer | 1.62 MB / 325 kB transfer |
| Build output | 21 MB (14 MB was a second Monaco copy) | 8.7 MB |
| Build warnings | 100 | 0 |
| Unit tests | 225 (6 without expectations) | 252 |

## Notes per phase

**Baseline.** 225 specs pass; six `OntologyService` specs had no expectations. 99 "CommonJS or AMD dependencies"
warnings (Monaco CSS through `style-loader`) and a component-style budget warning (`config-route.component.scss`
19.39 kB against 15.36 kB).

**Phase 0.** 20 new characterization specs (markdown, charts, breadcrumbs, Monaco autocomplete). One found a bug:
after a prefix (`ex:`), autocomplete inserted the whole URI for slash URIs. `ui_check.mjs` now also checks chart and
breadcrumb DOM, and that both editors offer suggestions with codicon icons from a loaded font.

**Phase 1.** npm 11 blocks dependency install scripts by default; none are needed (esbuild, lmdb and msgpackr use
their prebuilt platform packages), so the denials are recorded in `allowScripts`.

**Phases 2–3, known UI regression.** For Angular 19 and 20 the matching `ngx-monaco-editor-v2` needed Monaco 0.52,
whose AMD build (loaded from `assets/monaco`) could not resolve the JSON worker's relative URL inside a worker ("Failed
to parse URL from ./assets/monaco/min/vs/language/json/jsonWorker.js"). The page error broke the test-body JSON editor
on the route page; it is the only failure in those two gates (every IT suite and every other UI check passed). Monaco
0.55 and 0.56 (Phases 4–5) load the worker again, and Phase 6a removes the AMD build altogether.

**Phase 4.** Angular 21's `ng update` converts templates to `@if`/`@for` (no longer optional). Two follow-ups:
duplicate `#stoppedBlock` templates in config-route stopped the migration there; and "track by identity" on
`statusLines`, a getter that builds new objects every time, recreated the Settings status rows on every change
detection (NG0100 in dev mode). TestBed defaults to zoneless since 21; the tests now get zone change detection like
the app.

**Phase 5.** Angular 22's migrations keep pre-22 behavior: `ChangeDetectionStrategy.Eager` on every component (22
defaults to OnPush), `withXhr()`, and `$safeNavigationMigration()` wrappers (removed again in Phase 8). monaco 0.56's
exports map dropped the `esm/vs/editor/edcore.main.js` paths config-route imported (redundant anyway). TypeScript 6
deprecates `baseUrl` and `downlevelIteration`.

**Phase 6.** `app-code-editor` replaces `ngx-monaco-editor-v2`; one Monaco (ESM) with real web workers. This also
fixed add-route's ontology autocomplete, which had been registered on the other Monaco copy. With esbuild, CSS that
Monaco's JavaScript imports is written to a file nothing links, so Monaco's own stylesheet is built as a separate
bundle and linked when an editor opens.

**Phase 7.** Same commit, both runners: 252 tests pass under Karma and under Vitest, with identical per-file counts
(41 files). Vitest doesn't support zone `fakeAsync`; that spec uses Vitest fake timers (without
`shouldAdvanceTime`, which the schematic chose and which lets real time leak into "not yet after 399 ms" checks).

**Phase 8.** The standalone migration's bootstrap step dropped `provideZoneChangeDetection()`, which would have made
the app zoneless; `app.config.ts` is written by hand. The route editor page and the add-route dialog are lazy, and a
small error-message helper moved out of the Monaco-based validation service, which had kept Monaco in the main bundle.

**Phase 9.** `mat.theme()`; snack bar variants global (they were only styled after a route page had been opened).

**Phase 10.** `npm ci`, strict peers, monaco 0.58 (DOMPurify advisories), Dependabot, Node version check in CI.

## Gate results

Every gate: `mvn install` (1210 Java tests), then on the image: the black-box IT suite on Derby, PostgreSQL+Redis and
MSSQL (93 tests each), with sign-in (93), with `QUERY_AUTH_DEFAULT=authenticated` (14), Derby+Redis with slow tests
(94), restart persistence, the UI check without and with sign-in (10 and 14 checks), custom TLS keystore and password
encryption at rest.

| Gate | Result |
|---|---|
| Baseline, 0, 1 | all pass |
| 2, 3 | all pass except the UI checks (7/10 and 10/14): the JSON editor worker page error above |
| 4, 5, 6, 7, 8, 9, 10 | all pass |
| Final (`d9987cb` header and font, `3929922` table shadow) | all pass |

**Visual review (G6).** Screenshots against the baseline: Phases 1–8 differ only in per-run values (generated names,
timestamps; under 0.4% of pixels). Phase 9 dropped the data tables' shadow (`mat-elevation-z8` draws nothing under
`mat.theme()`), restored afterwards with `--mat-sys-level4`. After the upgrade, the datasource page header was
reworked (title and audit line aligned) and plain text now uses Roboto instead of the browser's serif.

**Found outside the gates.** `ng serve` failed from Phase 6 to the end (Vite's dev pre-bundler and ngx-markdown's
optional katex import); the gates only exercise production builds. Fixed with `externalDependencies`.

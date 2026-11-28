# Upgrading the web UI toolchain (Node, Angular and the rest)

Goal: move `query-service-web` from Node 20 / Angular 18 to the current LTS Node and the current Angular. Every step is a small change that must pass a fixed set of checks before the next one starts. Styling may change along the way (Angular Material moves on), but redesign work belongs to the next project; here we only keep the UI correct and consistent.

Status: plan, 2026-11-27. Nothing implemented. Work happens on `chore/web-upgrade`, branched from `refactor/optimize`.

## Where we are

Measured on `refactor/optimize` at `b5d1b68`:

| Area | Today | Problem |
|---|---|---|
| Node | `v20.18.1` in `.nvmrc` and in the `frontend-maven-plugin` config (`pom.xml`), npm 10.8.2 | Node 20 reached end of life on 2026-04-30 and gets no more security fixes |
| Angular | 18.2.14 (CLI 18.2.21), TypeScript 5.5.4, zone.js 0.14 | Four majors behind (22.3.0 is current) and out of support |
| Build | `@angular-builders/custom-webpack:browser`, the legacy webpack builder. `webpack.config.js` exists only to load Monaco's CSS and fonts | Angular's esbuild/Vite builder has been the default since v17, and the webpack path gets fewer fixes with each release. The prod build prints 99 "CommonJS or AMD" warnings from Monaco CSS and one component-style budget warning |
| Bundle | Initial 5.51 MB raw / 1.08 MB transfer (`main` 5.41 MB); 21 MB output, of which 14 MB is `assets/monaco` | Monaco is shipped **twice**: four files use `import * as monaco from 'monaco-editor'` (bundled into `main`), and `ngx-monaco-editor-v2` loads its own AMD copy from `assets/`. `template-validation.service.ts` works around this by setting markers on both copies |
| App structure | One `AppModule` declares 22 components; `main.ts` calls `bootstrapModule` | `app.config.ts` and `app.routes.ts` are dead code that nothing imports |
| Templates | 188 uses of `*ngIf` / `*ngFor` / `*ngSwitch`, 22 `@Input`/`@Output`/`@ViewChild` decorators | `NgIf`/`NgFor` are deprecated in Angular 22 (still shipped). `@angular/animations` is deprecated, with removal planned for v23 |
| Tests | Karma + Jasmine, 225 specs, all passing. Coverage: statements 46.8%, branches 31.5%, functions 47.3%, lines 46.3% | Karma is deprecated upstream. Six `OntologyService` specs have no expectations, so they pass whatever the code does |
| Styling | Material 3 theme via `mat.define-theme` + `mat.core()`; 4.4k lines of SCSS with 48 `::ng-deep` / `.mat-*` / `.mdc-*` overrides | Theming APIs changed in Material 19. Overrides that target internal class names are what breaks on upgrade |
| Dependencies | `bootstrap` and `@popperjs/core` are declared but nothing imports them. Maven runs `npm install --legacy-peer-deps` | The build is not reproducible from the lockfile, and peer-dependency conflicts are hidden |
| Docs | `README.md` and `docs/development-setup.md` say Node 20.18.1 (one README line says "14.20+") | Out of date |

## Decisions

| Topic | Decision | Why |
|---|---|---|
| Node target | **26.13.1** (Lithium, Active LTS; supported until April 2029), with the npm it ships with (11.20.0) | Newest LTS, so the longest support window. Angular 22 supports it (`^22.22.3 \|\| ^24.15.0 \|\| >=26.0.0`). Node 24 (maintenance LTS) is the fallback if a tool breaks on 26 |
| Order: Node first or Angular first | **Node first**, on Angular 18 | Angular 18's engine range (`>=22.0.0`) already allows Node 26, so the runtime change can be tested by itself with the code unchanged. If something breaks on 26, **Node 22.23.5** is a bridge that every Angular major from 18 to 22 accepts |
| Angular target | **22.3.x** (current; 23 is expected around May 2027) | Latest stable |
| How to climb | One major at a time, 18 → 19 → 20 → 21 → 22, with `ng update` | `ng update` only migrates one major per run, and its automatic migrations assume that order |
| TypeScript | Whatever each Angular major requires; **6.0.x** at the end (Angular 22 accepts `>=6.0 <6.1`) | TypeScript 7 is out, but Angular 22 does not support it yet |
| Builder | Move to `@angular/build:application` (esbuild) and `@angular/build:dev-server` **after** reaching 22. Delete `webpack.config.js`, `css-loader`, `style-loader` and `custom-webpack` | Until then, each version step changes only versions; the builder switch is its own step with its own checks. The new test runner (below) needs the application builder |
| Monaco | Load **one** copy: the AMD copy that `ngx-monaco-editor-v2` already loads. Code changes to `import type * as monaco` and gets the runtime from `window.monaco` via a small `MonacoLoader` service | Removes about 4 MB from `main`, removes the custom webpack rules, and removes the "set markers on both copies" workaround. This changes behavior, so the Monaco tests below come first |
| Test runner | Keep **Karma** for the whole version climb, then move to **Vitest** (`@angular/build:unit-test`) as a separate step | Changing the framework and the test runner at the same time would leave nothing trusted to compare against. Keeping the runner fixed means a failure points at the code |
| Zone.js / zoneless | **Keep zone.js** (0.16 at the end). Zoneless change detection is out of scope | It changes runtime behavior across the whole app and doesn't block anything. Revisit after the redesign |
| NgModule → standalone, control flow, `inject()` | Do it, as schematic-driven steps **after** the climb and builder switch | `*ngIf` is deprecated and `@angular/animations` is due to be removed in v23, so this keeps v23 from being a big upgrade. Schematics make it mechanical, and the suite checks it |
| Styling | Move the theme to the current Material API (`mat.theme`, system tokens) and fix or delete overrides that broke. No redesign | The user accepted styling changes; the redesign comes next and builds on a clean theme |
| Peer dependencies | End with `npm ci` and **no** `--legacy-peer-deps` | Reproducible builds; real conflicts show up |

### Third-party versions by step

Each step uses the library major whose peer range includes that Angular major. Check with `npm view <pkg>@<major> peerDependencies` before bumping.

| Angular | TS | ngx-monaco-editor-v2 | ngx-markdown (marked) | ngx-charts | xng-breadcrumb | custom-webpack |
|---|---|---|---|---|---|---|
| 18 (today) | 5.5 | 18.1 | 18.1 (marked 12) | 20.5 | 12 | 18 |
| 19 | 5.8 | 19.0 | 19.1 | 22.0 | 13 | 19 |
| 20 | 5.8–5.9 | 20.3 | 20.1 | 23.1 | 14.1 | 20 |
| 21 | 5.9 | 21.1 | 21.3 | 24.0 | 14.1 (check) | 21 |
| 22 | 6.0 | 22.0.5 + **monaco 0.56.x** (peer `^0.56.0`, so not 0.58) | 22.1 (marked 17–18) | 25.0 | 15 | removed (step 6) |

The `marked` jump from 12 to 18 changes its API (renderer and token changes since v13), so the markdown tests in Phase 0 must cover what SPARQi chat renders.

## Checks

Every step passes the gates for its level before it is committed. Results go in a log (`docs/perf/web-upgrade-log.md`) so drift shows up across steps.

| Gate | What runs | Pass criteria |
|---|---|---|
| **G0 Install** | `nvm use && rm -rf node_modules && npm ci` (with `--legacy-peer-deps` only until Phase 10) | Clean install; record any new peer warnings |
| **G1 Build** | `npx ng build` (production) and `npx ng build -c development` | Exits 0. Warnings no worse than the last step (count them). Record bundle sizes: initial raw/transfer, `main`, total output size |
| **G2 Unit** | `npx ng test --watch=false --browsers=ChromeHeadless --code-coverage` (later `ng test` on Vitest) | Every spec passes; spec count never goes down unless a spec is removed on purpose. Coverage at least the **Phase 0 floor**, which `karma.conf.js` enforces through `coverageReporter.check` |
| **G3 Type & template** | `npx tsc -p tsconfig.spec.json --noEmit`; the build already runs `strictTemplates` | No errors |
| **G4 Maven** | `mvn -pl query-service-web -am clean install` (uses the plugin's own Node, exactly like CI) | Passes. This proves the `pom.xml` Node version and the `target/` install match `.nvmrc` |
| **G5 UI smoke** | Build the image, `scripts/stack.sh up`, run `scripts/ui_check.mjs` (with sign-in off, then with `QTT_OIDC=true`), saving screenshots to `target/web-upgrade/<step>/` | Every page loads with no console errors, page errors or failed requests; the API key and query-access flows pass |
| **G6 Visual review** | `scripts/ui_diff.mjs` (new, Phase 0) compares each page's screenshot to the Phase 0 baseline and writes an HTML side-by-side with a diff overlay | **Reviewed, not a hard gate**: styling may change. Every visible change gets a one-line note in the log ("expected: M3 button shape") or a fix |
| **G7 Manual** | The checklist below, about 10 minutes | Everything works |
| **G8 Full regression** | `scripts/gate.sh <image>` (all databases, OIDC, query auth, restart, TLS, encryption, UI check) | Same summary as the Phase 0 run |

Which gates each step needs:

- **Small commit inside a phase:** G0–G3.
- **End of each phase:** G0–G7.
- **Milestones** (after Phase 1, Phase 5, Phase 7 and Phase 10): G0–G8.

### Manual checklist (G7)

Covers what automated tests touch least:

1. **Route editor (Monaco):** syntax highlighting, codicon icons visible (the webpack rule exists because of a font path bug), template error markers appear while typing, ontology autocomplete suggestions, resizing, light theme.
2. **Add route:** the editor in the add-route flow works the same way.
3. **SPARQi chat:** markdown (headings, lists, code blocks, tables, links), the ontology visualization dialog, the metrics modal.
4. **Metrics page:** charts render, tooltips, resizing, empty state.
5. **Breadcrumbs** on nested pages (datasource → config, route → config).
6. **Tables:** sort, paginate, filter on Routes and Datasources.
7. **Dialogs and snackbars:** delete confirmations, cache clear, API key reveal/copy.
8. **Forms:** add datasource and add route validation, autocomplete chips, slide toggles, checkboxes.
9. **Sign-in:** the Keycloak login, a viewer role hiding editor controls, signing out, the session-expiry redirect.
10. **Narrow window (~400 px):** the sidenav and the Settings section tabs.

## Phases

Each phase is one or more commits on `chore/web-upgrade`. Open a PR into `refactor/optimize` after each milestone, so no single review is huge.

### Phase 0: Baseline and safety net (no version changes)

The suite has to be strong enough to catch regressions before anything changes.

1. Record the baseline: the G1 sizes and warning counts, G2 counts and coverage, the G5 screenshots, and a G8 run. Numbers so far: 225 specs; coverage 46.8 / 31.5 / 47.3 / 46.3; initial 5.51 MB / 1.08 MB; 99 CommonJS warnings + 1 budget warning.
2. Fix the six `OntologyService` specs that have no expectations (they currently prove nothing).
3. Add characterization tests where an upgrade is most likely to break things and coverage is thinnest:
   - `TemplateValidationService` and `OntologyAutocompleteProvider`: markers reach the editor's model, and the completion items' shape (guards the Monaco change).
   - `SparqiChatComponent`: rendered HTML for a fixed markdown sample (headings, code, tables, links). Snapshot the `innerHTML` (guards the jump to `marked` 18).
   - `MetricsComponent`: chart inputs (`results`, `scheme`, `ScaleType`) for sample data (guards ngx-charts 20 → 25).
   - `QsBreadcrumbComponent`: labels for nested routes.
   - `AuthInterceptor` / `AuthService` / `IfRoleDirective`: 401 handling, role gating (guards the change to functional interceptors and `provideAppInitializer`).
4. Extend `ui_check.mjs` with DOM checks the screenshots can't make: the Monaco editor mounts and a codicon glyph has a nonzero width; a SPARQi markdown heading renders as `<h*>`; the metrics page has an `svg` with data marks; breadcrumbs show the expected text.
5. Add `scripts/ui_diff.mjs` (puppeteer-core + pixelmatch in `UI_TOOLS`) for G6.
6. Set Karma coverage thresholds at the new floor, rounded down, so coverage can't drop silently.
7. Add `"engines": { "node": "..." }` to `package.json` and `engine-strict=true` to `.npmrc`, so the wrong Node fails fast.
8. Remove the unused `bootstrap` and `@popperjs/core`. Delete the dead `app.config.ts` / `app.routes.ts`, or move them aside if Phase 8 will adopt them.

Exit: G0–G8 green; the log has the baseline.

### Phase 1: Node 20 → 26 (Angular 18 unchanged) · milestone

1. `.nvmrc` → `v26.13.1`; `nvm install && nvm use`.
2. `pom.xml`: `nodeVersion` → `v26.13.1`, `npmVersion` → `11.20.0`; `frontend-maven-plugin` 1.13.4 → 1.15.1.
3. `npm ci` on npm 11, then commit the lockfile npm 11 rewrites (expect a large lockfile diff with no version changes; check it with `npm ls`).
4. Check the tools under Node 26: Karma + ChromeHeadless, webpack 5, `ui_check.mjs` (puppeteer-core in `UI_TOOLS`).
5. Update the Node version in `README.md` and `docs/development-setup.md`.

Fallback: if Karma or webpack fails on 26 and can't be fixed in an hour, use Node 22.23.5 for now and come back to 26 after Phase 6, when webpack is gone.

Exit: G0–G8 green.

### Phases 2–5: Angular 18 → 19 → 20 → 21 → 22

The same recipe for each major N:

1. `npx ng update @angular/core@N @angular/cli@N`, then `npx ng update @angular/material@N` (it updates CDK too).
2. Bump the third-party libraries to the row for N in the table above, and TypeScript and zone.js to what N requires.
3. Read every migration `ng update` ran and every `TODO` it left. Keep the generated changes in their own commit, separate from manual fixes, so review stays easy.
4. Fix new deprecation warnings in our own code right away while the cause is known. Leave larger optional migrations for Phase 8.
5. Run G0–G7 and write the log entry.

What to expect at each step:

| Step | Expected changes |
|---|---|
| **18 → 19** | Components become standalone by default: the migration adds `standalone: false` to our 22 declarations. `APP_INITIALIZER` is deprecated in favor of `provideAppInitializer`. Material 19 theming: `mat.theme()` arrives and `mat.core()` is deprecated, but `define-theme` still works (theme changes wait for Phase 9). TS 5.8 may report new errors under `strict` |
| **19 → 20** | Node must be ≥ 20.19 (already done in Phase 1). `NgIf`/`NgFor`/`NgSwitch` become deprecated: lots of warnings, no breakage. Signal APIs (`effect`, `linkedSignal`, `toSignal`) become stable. Material 20 changes some token names and default styles (expect G6 diffs on buttons, form fields, chips). `@angular/animations` is deprecated in 20.2 |
| **20 → 21** | Vitest becomes the default runner for **new** projects only; our Karma setup keeps working. Zoneless becomes the default for new apps only; existing apps keep zone.js. Check that `@angular-builders/custom-webpack` 21 works with our `webpack.config.js` |
| **21 → 22** | TypeScript 6.0: expect errors about deprecated `tsconfig` options (`baseUrl`, `downlevelIteration`). Remove them if `ng update` doesn't. zone.js 0.16. Monaco 0.50 → 0.56 (check the codicon and font assets, and AMD loader paths under `assets/monaco/min/vs`). `marked` 18 via ngx-markdown 22. ngx-charts 25 |

Phase 5 is a milestone: G8.

### Phase 6: Webpack → esbuild application builder

1. Fix Monaco first (see Decisions): add a `MonacoLoader` service that waits for `ngx-monaco-editor-v2`'s loader and returns `window.monaco`. Change the four `import * as monaco` lines to `import type` and route runtime calls through the service. Delete `setMarkersEverywhere`. Run G2 (the Phase 0 Monaco tests must still pass) and G7 item 1.
2. `angular.json`: `build` → `@angular/build:application`, `serve` → `@angular/build:dev-server`, `extract-i18n` → `buildTarget` (`browserTarget` is deprecated).
3. **Output path:** the application builder writes to `<outputPath>/browser/` by default. Quarkus serves `META-INF/resources/` directly, so set `"outputPath": { "base": "target/classes/META-INF/resources", "browser": "" }`. G4 and G5 catch it if this is wrong (the UI would return 404).
4. `main` → `browser`; polyfills stay `zone.js` + `@angular/localize/init`.
5. Delete `webpack.config.js` and remove `css-loader`, `style-loader` and `@angular-builders/custom-webpack`.
6. Fix the `config-route.component.scss` budget warning, or raise the budget on purpose.
7. Check that `proxy.conf.js` works with the Vite dev server (`/queryrest` → 8080). Add `/query` and the OIDC paths if local dev with sign-in needs them.

Expect: `main` down by about 4 MB, the CommonJS warnings gone, the prod build faster. Record all three.

### Phase 7: Karma/Jasmine → Vitest · milestone

1. `npx ng g @schematics/angular:refactor-jasmine-vitest` (converts `jasmine.createSpy`, `spyOn`, `toHaveBeenCalledWith` and similar), then switch `test` to `@angular/build:unit-test` with `runner: vitest`, `jsdom` environment.
2. Specs that need a real browser (layout, Monaco, `ResizeObserver`) either get the DOM API mocked in a setup file or run in Vitest browser mode (Playwright Chromium).
3. Before deleting Karma, run both runners on the same commit: the **same number of specs must pass** under each. Compare the per-file spec counts and list any differences.
4. Move the coverage floor to Vitest's `coverage.thresholds`.
5. Update the `pom.xml` test execution (`npm test -- --watch=false`; there's no `--browsers` flag any more) and remove the Karma packages and `karma.conf.js` / `test.ts`.
6. Have CI publish a JUnit report (`reporters: ['default', 'junit']`), so the GitHub summary can count Angular tests the way it counts Java tests.

Exit: G0–G8 green.

### Phase 8: Modernize the code (schematic-driven, one commit per schematic)

Each schematic gets its own commit and its own G0–G3 run; G5 runs at the end of the phase.

1. `ng g @angular/core:control-flow`: the 188 `*ngIf`/`*ngFor`/`*ngSwitch` uses become `@if`/`@for`/`@switch`. Check the generated `track` expressions by hand.
2. `ng g @angular/core:standalone` (three passes: convert declarations, remove `AppModule`, switch to `bootstrapApplication`). Build `app.config.ts` from what `AppModule` provides today, including Monaco `forRoot` → `importProvidersFrom` or the library's provider function, `MarkdownModule.forRoot` → `provideMarkdown`, and `provideHttpClient(withInterceptors([authInterceptor]))`.
3. `APP_INITIALIZER` → `provideAppInitializer(() => inject(AuthService).load())`.
4. `AuthInterceptor` (class, DI) → a functional interceptor.
5. Remove `@angular/animations` (due to be removed in v23): `BrowserAnimationsModule` → `provideAnimationsAsync()` for now, then remove it once Material no longer needs it, using `animate.enter`/`animate.leave` for any custom animations.
6. Optional, if there's time: `ng g @angular/core:inject` (constructor injection → `inject()`), `signal-input-migration`, `output-migration`, `signal-queries-migration`.
7. Optional: add `angular-eslint` with the recommended rules and fix what it finds, so new code doesn't bring back deprecated patterns.

### Phase 9: Bring theming up to date (prep for the redesign, not the redesign)

1. Replace `mat.core()` + `mat.define-theme` + `mat.all-component-themes` with `html { @include mat.theme((color: ..., typography: ..., density: ...)); }` and use the `--mat-sys-*` CSS variables.
2. Check the 48 `::ng-deep` / `.mat-*` / `.mdc-*` overrides one at a time: replace each with a component **override mixin** (`mat.button-overrides(...)` and similar) or a `--mat-sys-*` variable, or delete it if the new defaults are fine. Every override that's left gets a comment saying why.
3. Have component SCSS use system tokens instead of hard-coded colors where that's cheap. This is the base the redesign will build on.
4. G6 is the main check for this phase. Review the side-by-side for every page.

### Phase 10: Hardening and cleanup · milestone

1. `pom.xml`: `npm install --legacy-peer-deps` → `npm ci`. Resolve every peer conflict for real.
2. `npm audit --omit=dev` with no high or critical issues; record any that are left and why.
3. Add Dependabot (or Renovate) for `query-service-web/package.json`, grouping `@angular/*` together, so we don't fall four majors behind again.
4. Have CI warn when `.nvmrc`, `package.json` `engines` and the `pom.xml` `nodeVersion` disagree (one `grep`-based check in `pull-request.yml`).
5. Final pass on the docs: README, development setup, and this plan's status.
6. Run G8 and write the final log entry with before/after numbers (versions, bundle size, build time, test time, spec count, coverage).

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Monaco breaks: fonts, the AMD loader path, markers, autocomplete | High (two changes touch it: 0.50→0.56 and the single-copy rework) | Phase 0 tests and DOM checks, G7 item 1 at every phase. The single-copy change is its own commit and easy to revert |
| `marked` 12 → 18 changes rendered SPARQi markdown | Medium | Phase 0 `innerHTML` snapshot for a fixed sample. Review snapshot diffs instead of overwriting them |
| Material style changes in 19–22 cause visual regressions | High, accepted | G6 review every phase; the real cleanup happens in Phase 9 |
| A third-party lib lags an Angular major | Low (all four have v22 releases today) | Keep `--legacy-peer-deps` during the climb; if a library breaks for real, pin it and record why |
| The application builder's output layout breaks Quarkus serving | Medium | Set `outputPath.browser: ""` explicitly; G4 and G5 fail loudly |
| Vitest/jsdom differs from real Chrome | Medium | Run both runners in parallel and compare per-file counts. Use browser mode for specs that need real layout |
| Lockfile churn hides an unintended version bump | Medium | Each commit records `npm ls --depth=0` before and after in the log |

## Rough effort

| Phase | Estimate |
|---|---|
| 0 Baseline and safety net | 1–1.5 days |
| 1 Node 26 | 0.5 day |
| 2–5 Angular 18 → 22 | 0.5–1 day each, 2–4 days total |
| 6 esbuild + Monaco single copy | 1 day |
| 7 Vitest | 1 day |
| 8 Modernize | 1–1.5 days |
| 9 Theming | 1 day |
| 10 Hardening | 0.5 day |
| **Total** | **about 8–11 days** |

## Out of scope

- Visual redesign (the next project).
- Zoneless change detection.
- SSR, prerendering and service workers.
- Upgrading the Java/Quarkus side. CI's JDK version only matters here if the Maven/frontend plugin needs it.

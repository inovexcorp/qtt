# Authentication for the query endpoints

Goal: the query endpoints that applications call (today `:8888/<routeId>`) get the same protection as the admin UI and management API. Each route can be open or require a caller identity. Callers prove who they are with an OIDC bearer token from the organization's identity provider (IdP), or with an API key that QTT issues.

Status: implemented on branch `feature/query-auth` (from `refactor/optimize`), 2026-11-27; not yet merged. A spike checked performance, runtime route changes and OIDC enforcement first; results are below. The three open decisions (default access, per-route roles, API key scope) were settled on 2026-11-27 and are in the table. Where the implementation differs from the first draft of this plan, the text below describes what was built and says why it changed. This builds on [oidc-sso-plan.md](oidc-sso-plan.md), which deliberately left `:8888` out of scope. Setup for operators: [administration.md](administration.md#securing-the-query-endpoints).

## Decisions

| Topic | Decision | Why |
|---|---|---|
| Where query routes listen | On the Quarkus HTTP server (8080/8443) at `/query/<routeId>`, through `camel-quarkus-platform-http` | One TLS configuration, one security stack (OIDC, role mapping, path permissions) for the whole application. The spike measured it faster than `netty-http` (below) |
| Port 8888 | Removed in the same release; no compatibility listener | Every deployment can migrate its clients and proxies. A compatibility listener would either stay unauthenticated or need a second security stack |
| How callers authenticate | OIDC bearer tokens, or QTT-issued API keys sent as `Authorization: Bearer qtt_...` | Tokens suit organizations with an IdP; API keys suit simple integrations and deployments without an IdP |
| Browser sessions on `/query` | Not accepted: no login redirect and no session cookie | Query callers are programs. A missing or bad credential gets **401**, not a 302 to the IdP. Without cookies there is no CSRF exposure on these paths |
| Per-route setting | Each route is `default`, `open` or `authenticated`; an authenticated route can also list required roles | Lets an operator lock routes down one at a time, and limit sensitive ones to particular callers |
| Default access (`QUERY_AUTH_DEFAULT`) | `open` | Upgrades need only the URL change. Operators close routes one at a time, or set `QUERY_AUTH_DEFAULT=authenticated` to close every route left at `default` |
| Who may call authenticated routes | A new role, `qtt-query`, separate from viewer/editor/admin, plus the route's required roles if it lists any | Being allowed to query data and being allowed to administer QTT are different permissions, usually held by different principals (applications vs. people) |
| What API keys can do | Call `/query` routes only; they never authenticate the management API | A leaked key can read data but can't change routes or datasources. Management automation uses OIDC client credentials |
| Identity sent to Anzo | Unchanged: the datasource's stored credentials | QTT decides whether a call is allowed; Anzo sees QTT. Passing the caller's identity to Anzo would need per-caller cache keys and Anzo token support, and is out of scope |
| Result cache | Unchanged: shared by every caller of a route | Authorization is per route, not per caller, so every authorized caller gets the same data |

## Scope

| Surface | Today | After this change |
|---|---|---|
| Query endpoints | `:8888/<routeId>`, Camel `netty-http`, unauthenticated, plain HTTP | `/query/<routeId>` on 8080/8443, the route's access setting enforced |
| Temporary test routes | `:8888/test-<uuid>`, called by QTT over HTTP | In-process `direct:` endpoints, never reachable over the network |
| Admin UI and management API | Covered by [oidc-sso-plan.md](oidc-sso-plan.md) | Unchanged, plus an API key screen and endpoints (admin) |

## Spike results

Branch `spike/platform-http`: `QUERY_ENDPOINT_URL` changed to `platform-http:/query/%s?%s`, the `camel-quarkus-platform-http` dependency added, and `/query` excluded from `SpaFallback`.

**Performance** (`scripts/measure.py`, same machine and day, 3 cold starts each, plain HTTP, 8 keep-alive clients for 30 s):

| | Uncached rps / p50 / p95 ms | Cached rps / p50 / p95 ms | API ready ms | RSS idle / after load MB |
|---|---|---|---|---|
| `netty-http` on 8888 (OIDC build, `a900bb2`) | 1058 / 7.4 / 11.6 | 8972 / 0.8 / 1.3 | 2320 | 315 / 369 |
| `platform-http` at `/query` on 8080 | **1281 / 6.3 / 8.3** | 8993 / 0.9 / 1.3 | 2208 | 313 / 367 |

The uncached path gains about 21% throughput, and p95 falls below the Karaf baseline (10.1 ms). That closes the p95 gap left open by the Quarkus migration. The cached path, startup and memory are unchanged. TLS and API keys were measured on the finished build (below).

**Finished build** (`feature/query-auth`, same conditions: PostgreSQL + Redis, 8 keep-alive clients, 30 s each, one stack):

| Path | rps / p50 / p95 ms |
|---|---|
| Uncached route, plain HTTP (8080) | 1223 / 6.6 / 8.7 |
| Uncached route, TLS (8443) | 1203 / 6.7 / 8.7 |
| Uncached authenticated route with an API key | 1207 / 6.6 / 8.5 |
| Cached route, plain HTTP | 8659 / 0.9 / 1.3 |

TLS and the API key check cost nothing measurable next to the Anzo round trip.

**Behavior:**

- Routes can be added, deleted and re-created at runtime; query parameters still reach templates as `headers.*`; `httpMethodRestrict` is honored.
- A method a route doesn't allow returns **404**; `netty-http` returned **405**. Fix it or record it in the migration notes (rollout step 2).
- With OIDC on, unchanged configuration: a valid bearer token gets 200, a bad token or a token for another audience gets 401, and the route works over 8443. A request with no credential gets a **302 to the IdP**, because the application is `hybrid`. Hence the decision to make `/query` bearer-only.

**Problems found:**

1. **`SpaFallback` hid every query route.** Its regex rerouted any extension-less GET outside `/queryrest` and `/q` to the Angular `index.html`, with a 200. The first measurement reported 11,000 rps against the UI page. The fix excludes `/query`; a test must lock it in.
2. **Templates can read credentials.** All request headers become Camel headers, and templates read `headers.*`. A template containing `${headers.Authorization}` put the caller's bearer token into the SPARQL sent to Anzo. The `anzo.query` response header then returned it to the caller. An editor could use this to harvest callers' tokens through Anzo's query logs.
3. **Request headers are echoed in the response.** A request header `X-Api-Key: SECRET` came back in the response unchanged. `Authorization` and `Cookie` were not echoed, but anything custom is. API keys must not travel in a custom header, and the route must not reflect request headers.
4. **`anzo.query` exposes the generated SPARQL to every caller.** This already happened on 8888. It helps debugging, but it discloses template logic and, per item 2, whatever a template interpolates.

## How it works

### URL and listener

- Each route is `from("platform-http:/query/<routeId>?<routeParams>")`. It is served on 8080 and 8443 like the rest of the application. Operators who want plain HTTP off already have `quarkus.http.insecure-requests`.
- `SpaFallback` excludes `/query`. `QueryPathFallback` answers any `/query` path no route serves with a JSON 404 in every mode (Quarkus's own 404 page is HTML in dev and test mode). It is registered `last()`, after every Camel route, including routes added at runtime.
- Route IDs must match `[A-Za-z0-9._-]{1,128}` (`RouteIds` in core), because `platform-http` treats `{...}` in a path as a parameter and `/` as a separator. Create and clone validate them (400 `Invalid route ID`); at startup a stored route whose ID fails is logged by name and skipped, and the other routes still load.
- `RoutesController` builds endpoint URLs from `QUERY_BASE_URL`; unset, the base is `/query`, which the UI makes absolute with its own origin. `API_BASE_URL` is **not** used as a fallback, as first planned: every existing value points at port 8888 without `/query`, so it would produce wrong URLs. If it is set, startup logs a WARN naming `QUERY_BASE_URL`.

### Authentication on `/query/*`

Mechanisms, in the order Quarkus consults them:

1. **`AuthDisabledMechanism`** (priority 2000; only with OIDC off) grants the `anonymous` identity with the three management roles, but never on `/query/*`, and never `qtt-query`. Otherwise turning OIDC off would open every authenticated route. With OIDC off, authenticated routes accept API keys only.
2. **`QueryAuthMechanism`** (priority 1500, new) checks `Authorization: Bearer qtt_...` on `/query/*` only, off the event loop (the lookup reads the database, cached), and fails with 401 on a bad key instead of passing it to OIDC. Other tokens it leaves alone.
3. **OIDC** (the existing `hybrid` tenant) validates JWT bearer tokens as before.

No second OIDC tenant was needed, as first planned. Quarkus sends the challenge of the highest-priority mechanism that offers one, and `QueryAuthMechanism` offers `401` with `WWW-Authenticate: Bearer realm="qtt"` on `/query/*` only. So a refused request there never gets the browser redirect, the UI's XHRs there get 401 rather than 499, and everywhere else the OIDC challenge (redirect or 499) is unchanged.

Quarkus authenticates every request that carries a credential before the path policy runs, so an invalid token or key gets 401 even on an open route. That is deliberate: callers learn their credential is wrong. Route-level code never sees the credential (see credential hygiene).

### Authorization per route

A route has an access setting, stored on the route:

| Setting | Who may call |
|---|---|
| `default` | Follows `QUERY_AUTH_DEFAULT` |
| `open` | Anyone who can reach the server; credentials are ignored |
| `authenticated` | An OIDC principal with `qtt-query`, or an API key whose scope includes this route; in both cases the caller must also hold every role in the route's `requiredRoles` |

An `authenticated` route can list required roles in an optional `requiredRoles` field (for example, only `finance-apps` may call `revenue-by-region`). An empty list means `qtt-query` is enough.

- For OIDC callers, the names are QTT roles or IdP role/group names, matched after `QttRoleAugmentor` has mapped groups.
- An API key holds no IdP groups. It may call a route with `requiredRoles` only if the route is named explicitly in its scope; `*` doesn't count. Naming the route is the admin's deliberate grant.

A named `HttpSecurityPolicy`, `RouteAccessPolicy` (`quarkus.http.auth.permission.query.policy=qtt-query-route`), applies to `/query` and `/query/*`. It reads the access setting from the running Camel route itself, as route properties (`qtt.access`, `qtt.requiredRoles`) that `CamelRouteTemplateBuilder` sets. There is no separate map to keep in step: the setting always matches what the route serves, including after changes at runtime. The decision is `QueryAccessDecision`, a pure function with its own unit tests. Results:

- open route → served;
- no credential on an authenticated route or a route that doesn't exist → 401 (the same answer, so anonymous callers can't probe for route names);
- a valid credential on a route that doesn't exist → 404, from `QueryPathFallback`;
- a stopped route → 404 for everyone allowed to call it (the Camel route exists but serves nothing);
- a credential without access → 403.

The access setting is sent as a pair on modify. With `accessPolicy` given, `requiredRoles` is taken as given, and absent means none. With neither given, both are kept, so old clients and template-only changes don't reset them. The first draft used an empty `requiredRoles` to clear the list, but an empty query parameter arrives as null, so a list could never have been cleared that way. Modify, template-only modify, clone and the datasource-change rebuild all carry the setting across.

`QUERY_AUTH_DEFAULT` decides what `default` means, and it defaults to `open`:

- Existing routes are given `default` on upgrade, so they stay open, as they were on 8888. Clients only change the URL.
- An operator closes routes one at a time, or sets `QUERY_AUTH_DEFAULT=authenticated` to close every route left at `default`. Routes explicitly set to `open` stay open either way.
- When any route is open, startup logs one WARN with the count and the effective default. The route names go to INFO, so a deployment with many open routes doesn't flood the log. The routes list in the UI marks open routes.

### Roles

- `qtt-query` joins the role set. It is not implied by `qtt-viewer`, `qtt-editor` or `qtt-admin`, and does not imply them.
- `OIDC_QUERY_GROUPS` maps IdP groups to it, like the other `OIDC_*_GROUPS`. A role literally named `qtt-query` also works.
- A service account usually holds only `qtt-query`. A person who also wants to call routes from their own scripts gets both roles.

### API keys

- **Format:** `qtt_<id>_<secret>`. `<id>` is 12 random base62 characters and identifies the key in logs and the UI. `<secret>` is 32 random bytes, base62-encoded. The `qtt_` prefix lets secret scanners and the authentication mechanism recognize keys.
- **Storage:** a new `ApiKey` entity with these fields: `id`, `name`, `description`, `secretHash` (SHA-256 of the secret; enough for a high-entropy random value), `routes` (route IDs, or `*` for all authenticated routes), `expiresAt` (optional), `createdBy`, `createdAt`, `lastUsedAt`, `revokedAt`. Hibernate's `update` strategy creates the table on Derby, PostgreSQL and MSSQL. The secret itself is never stored.
- **Shown once:** the create response returns the full key. Later reads return the ID, name, scope and dates only.
- **Lookup:** compare the hash in constant time. Cache key records for 30 s (a small map, `ApiKeyAuthenticator`), so a revocation takes effect within 30 s on every instance, and at once on the instance that revoked it, without a database read per request. Unknown ids are not cached, so random ids can't grow the cache. `lastUsedAt` is written at most once a minute per key.
- **Identity:** principal `apikey:<id>`, role `qtt-query`, and the route scope as an attribute that `RouteAccessPolicy` checks. The audit log shows the key name.
- **Management:** `GET/POST /queryrest/api/apikeys` and `DELETE /queryrest/api/apikeys/{id}` (revoke), all admin, in `ApiKeysResource` in the app module (next to the other security resources, not in route-builder as first planned). Revoked keys stay listed with who revoked them. A Settings page section lists keys, creates one (showing the secret once with a copy button), and revokes.
- **Expiry:** the default comes from `QUERY_API_KEY_MAX_DAYS` (365 days; `0` for no expiry). Rotation means creating a second key, switching the client over, then revoking the first.
- **Query only:** keys are checked on `/query/*` only, and a key's identity never holds viewer, editor or admin. Management automation (for example, a CI pipeline that deploys templates) uses OIDC client credentials. With sign-in on, a `qtt_` key sent to `/queryrest/api` gets 401 (OIDC can't validate it). With sign-in off, the management API needs no credentials, and a key sent there changes nothing.

### Credential hygiene inside routes

Both directions are handled by one Camel `HeaderFilterStrategy` on the endpoint (`QueryHeaderFilterStrategy`, referenced with `#class:`):

- **Inbound** (request headers and query parameters becoming Camel headers): `Authorization`, `Proxy-Authorization`, `Cookie`, `Forwarded` and `X-Forwarded-*` are dropped, so templates never see credentials (spike problem 2). So are headers starting with `Camel`, so callers can't set Camel's control headers. Other request headers and query parameters reach templates as before.
- **Outbound** (Camel headers becoming response headers): only an allow list leaves, so request headers are never echoed (spike problem 3). That list is `Allow` (with a 405), plus `anzo.query`, `anzo.query_duration` and `anzo.graphmart` in debug mode (`QueryDebugHeaderFilterStrategy`, `QUERY_DEBUG_HEADERS=true`; spike problem 4). `Content-Type` is written by platform-http itself.
- platform-http also passes the server's authenticated user (the caller's security identity) in the `CamelVertxPlatformHttpAuthenticatedUser` header. The route removes it before templating; the spike didn't show this, because nobody was signed in.
- The 405 for a method outside `httpMethodRestrict` comes from the route itself (with `Allow`), because platform-http would answer 404. The restriction is no longer passed to the endpoint.
- The route test screen still shows the generated query: test routes run in-process and read it internally.

### Browser callers (CORS)

Front-end applications that call `/query` from another origin need CORS. Quarkus's CORS filter is global, and [oidc-sso-plan.md](oidc-sso-plan.md) relies on CORS being off for the management API (CSRF). So CORS for `/query/*` comes from `QueryCorsFilter`, a Quarkus HTTP filter at priority 310, ahead of Quarkus's CORS (300) and authentication (200). It answers preflights itself (204), because a preflight carries no credentials and would otherwise get 401 on an authenticated route. It allows:

- the origins in `QUERY_CORS_ORIGINS` (comma-separated, or `*`; empty means CORS stays off);
- `GET`, `POST` and `OPTIONS`;
- the `Authorization` and `Content-Type` headers.

It never allows credentials (cookies). Refused responses (401, 403) to an allowed origin carry the CORS header too, so a browser app can read them. Browser applications send OIDC access tokens; API keys don't belong in browsers, and the documentation says so.

### Test routes run in-process

Before, `TestRouteServiceImpl` started a `netty-http` route on 8888, and `RouteTestExecutor` called it at `http://localhost:8888/test-<uuid>`. Now:

- `TestRouteBuilder` consumes from `direct:test-<uuid>`.
- `TestRouteService.invokeTestRoute` sends to it with a reused `ProducerTemplate`. Test parameters become headers and the request body becomes the body, which is what `platform-http` produces from a real request. It also sets `CamelHttpMethod` and `CamelHttpQuery` for the debug panel.
- `RouteTestExecutor` no longer makes HTTP calls. `camel-quarkus-netty-http` is gone from the application.
- The 5-minute failsafe cleanup stays. Test routes are never reachable from the network.

## Migration

This release changes every client URL.

| Before | After |
|---|---|
| `http://host:8888/<routeId>` | `https://host:8443/query/<routeId>` (or `http://host:8080/query/<routeId>`) |
| No credentials | Still none by default (`QUERY_AUTH_DEFAULT=open`). Once a route is `authenticated`: `Authorization: Bearer <OIDC access token or qtt_ API key>` |
| `API_BASE_URL=http://host:8888` | `QUERY_BASE_URL=https://host:8443/query` (or unset) |
| Expose 8888 to applications, keep 8443 internal (`docs/advanced-topics.md` advises this today) | Expose `/query/*` through the reverse proxy and keep `/` and `/queryrest/*` internal. A path rule replaces a port rule |
| 405 for a disallowed method | 405, now with `Allow` |
| Request headers echoed in the response | Not echoed |
| `anzo.query` response header always present | Only with `QUERY_DEBUG_HEADERS=true` |

`routeParams` held raw `netty-http` URI options. The UI only ever writes `httpMethodRestrict=...`; `QueryRouteParams` reads it and the route enforces it. Any other option is logged at WARN and ignored, so a route created through the API with Netty-only options still starts.

## Configuration

New `.env.example` section:

```
# --- Query endpoints (/query/<routeId>) ---
# Access for routes left at "default": open, or authenticated (OIDC token with qtt-query, or an API key).
QUERY_AUTH_DEFAULT=open
# Public base URL shown in the UI for route endpoints (default: the UI's origin + /query).
QUERY_BASE_URL=
# IdP role or group names granting qtt-query (comma-separated).
OIDC_QUERY_GROUPS=
# Longest lifetime for new API keys, in days (0 = no limit).
QUERY_API_KEY_MAX_DAYS=365
# Origins allowed to call /query from a browser (comma-separated; empty = CORS off).
QUERY_CORS_ORIGINS=
# Return the generated SPARQL in anzo.query / anzo.query_duration response headers.
QUERY_DEBUG_HEADERS=false
```

`API_BASE_URL` is ignored, with a startup WARN if set (see URL and listener).

## Changes by module

### `query-service-app`

- `camel-quarkus-platform-http` and `camel-quarkus-direct` replace `camel-quarkus-netty-http`.
- `application.properties`: the `/query` path permission with the `qtt-query-route` policy; the settings above.
- `SpaFallback` excludes `/query`; `QueryPathFallback` (JSON 404).
- `security`: `QueryAuthMechanism` (API keys and the 401 challenge on /query), `ApiKeyFormat`, `ApiKeyAuthenticator`, `ApiKeysResource`, `RouteAccessPolicy`, `QueryAccessDecision`, `QueryPaths`, `QueryCorsFilter`.
  - `QttRoleAugmentor` gains `OIDC_QUERY_GROUPS`.
  - `AuthDisabledMechanism` skips `/query/*` and never grants `qtt-query`.
  - `AuthSettings` reads `QUERY_AUTH_DEFAULT` and fails startup on an invalid value.
  - `AuthStartupCheck` warns about open routes.
  - `MeResource` lists `qtt-query` when held.
  - `SsoInfoResource` adds the query-group mapping, `queryAuthDefault` and the open-route count.

### `query-service-core`

- `QttRoles.QUERY`; `RouteIds` (route-ID rule); `QueryAccess` (the access values, the default, the role list format).

### `query-service-persistence`

- `CamelRouteTemplate`: `accessPolicy` (`default`/`open`/`authenticated`) and `requiredRoles` (comma-separated). Both are nullable, so existing rows need no backfill: a `null` policy means `default`, and a `null` list means no required roles.
- `ApiKey` entity (`api_keys` table) and `ApiKeyService`.
- Hibernate's `update` strategy adds the columns and the table on Derby, PostgreSQL and MSSQL; the gate's databases and the upgrade check exercise that.

### `query-service-route-builder`

- `CamelRouteTemplateBuilder`: the `platform-http` endpoint with `QueryHeaderFilterStrategy`; the 405; the authenticated-user header removal; the access route properties. `CamelKarafComponent.routeBuilder` is now the one place routes are built (startup, route changes, datasource changes).
- `QueryRouteParams`, `QueryHeaderFilterStrategy`, `QueryDebugHeaderFilterStrategy`.
- `RoutesController`: route-ID validation; `accessPolicy`/`requiredRoles` on create and modify (editor, like the rest of the route); endpoint URLs from `QUERY_BASE_URL`.
- `RouteManagementService`: create and modify take the access setting and carry it across modify, template change and clone.
- `TestRouteBuilder` / `TestRouteServiceImpl`: `direct:` endpoints and `invokeTestRoute`.

### `query-service-testing`

- `RouteTestExecutor`: `ProducerTemplate` instead of HTTP to 8888.

### `query-service-web` (Angular)

- `app-route-access` on the add and config pages: an "Access" select (server default / open / authenticated) and, for authenticated routes, required roles.
- `app-endpoint-panel` on the config page: the absolute endpoint URL and a `curl` command (with the `Authorization` header for authenticated routes), each with a copy button.
- Settings: a "Query Access" card with the default, the open-route count and, for admins, `app-api-keys` (list, create with the key shown once, revoke). The SSO card lists the `qtt-query` group mapping and no longer mentions port 8888.
- The routes list shows absolute endpoint URLs and a globe (open) or lock (authenticated) on each route.
- `AuthService.noAccess` counts only the management roles, so a user holding only `qtt-query` gets the "No access" page rather than a UI full of 403s.

## Testing

### Unit tests

- core: `RouteIdsTest`, `QueryAccessTest`.
- route-builder:
  - `QueryRouteParamsTest`, `QueryHeaderFilterStrategyTest` (inbound credential and Camel headers dropped, outbound allow list, debug mode);
  - `RouteErrorHandlingTest` (405 with `Allow`; the running route carries its access properties);
  - `TestRouteServiceImplTest` (test routes are `direct:` endpoints, invoked in-process);
  - `CamelKarafComponentTest` (invalid stored route IDs are skipped), `RoutesControllerTest` (route IDs, access parameters, `QUERY_BASE_URL`).
- testing: `RouteTestExecutorTest` (in-process invocation instead of HTTP).
- app: `QueryAccessDecisionTest` (every combination of access, server default, OIDC roles, API key scope and required roles), `QueryPathsTest`, `ApiKeyFormatTest`, `ApiKeyAuthenticatorTest` (cache, expiry, revocation, last-use writes), `QttRoleAugmentorTest`, `SpaFallbackTest`, `RoleTableTest` (the API key endpoints).
- web: `query-endpoint.spec`, `RouteAccessComponent`, `ApiKeysComponent`, `ApiKeysService`, and `AuthService` (a `qtt-query`-only user gets "No access").

### `query-service-app` (Quarkus tests; a WireMock stand-in for Anzo, and `quarkus-test-oidc-server`)

- `QueryEndpointTest`:
  - routes served under `/query`;
  - credentials (`Authorization`, `Cookie`, `Proxy-Authorization`, `X-Forwarded-For`, the authenticated-user header) never reach the SPARQL sent to Anzo;
  - request headers and the Anzo metadata are not sent back;
  - 405 with `Allow`;
  - unknown paths are 401 for anonymous callers and never the UI page;
  - modify, delete and re-create while running;
  - invalid route IDs;
  - no CORS by default.
- `QueryAuthOffTest`:
  - routes follow their access setting with sign-in off; bearer tokens can't help there;
  - the setting is stored normalized and kept across modify, template change and clone, and changed as a pair;
  - unknown values are refused;
  - the open-route count.
- `QueryAuthClosedTest`: `QUERY_AUTH_DEFAULT=authenticated`.
- `QueryAuthOnTest`:
  - 401 and never a redirect (also for the UI's XHR header);
  - `qtt-query` required, admin alone gets 403;
  - `OIDC_QUERY_GROUPS` mapping;
  - required roles;
  - bad tokens 401;
  - unknown routes 401 or 404;
  - `/me` and the SSO info.
- `ApiKeysTest`:
  - scope, `*` and required roles;
  - bad and revoked keys 401, also on open routes;
  - the list never shows secrets and records use and revocation;
  - expiry limits and request validation;
  - keys do nothing on the management API.
- `ApiKeysOnTest`: admin-only management; keys valid on `/query`, 401 on the management API.
- `QueryCorsTest`: preflight answered before authentication; unlisted origins and the management API get no CORS headers.
- `SignInOnTest`, `SignInOffTest` as before, plus `/query` never redirecting.

### Harness

- `stack.sh` maps 8080 to `localhost:18082` (query routes at `http://localhost:18082/query/<id>`) and no longer maps 8888. `QTT_PUBLISH_8888=true` maps it again, for upgrade checks from images that still serve there.
- `measure.py` uses `/query` (`QTT_QUERY_URL`). `LoadGen` accepts https URLs (any certificate) and an `Authorization` header (`LOADGEN_AUTHORIZATION`).
- `query-service-it`:
  - all suites use `/query`;
  - `QueryPathIT` adds the leak, echo, 405 and JSON-404 cases;
  - `QueryAuthIT` (access settings; with sign-in, Keycloak tokens for `qtt-reader`, `qtt-finance`, `qtt-ci` and `other-app`; both server defaults);
  - `ApiKeyIT` (keys created through the API, scope, revocation, the management API).
- `qtt-realm.json` adds the `qtt-query` and `finance-apps` roles and the `qtt-reader` and `qtt-finance` service clients.
- `ui_check.mjs` checks query access end to end, with and without sign-in:
  - the endpoint panel and access select on a route's page;
  - closing the route in the UI, after which an anonymous call gets 401;
  - the lock in the routes list;
  - an API key created on the Settings page, used against `/query`, then revoked.
- `gate.sh` adds a configuration with `QUERY_AUTH_DEFAULT=authenticated` (`QueryAuthIT` and `ApiKeyIT`).
- `upgrade_check.sh` takes `OLD_QUERY_URL` for images that serve routes on 8888.

## Documentation

- `docs/administration.md`: a "Securing the query endpoints" section covering route access settings, API keys, the `qtt-query` role and IdP setup for service clients (client credentials, audience mapper). Replace "SSO does not cover port 8888".
- `docs/user-guide.md`, `docs/api-reference.md`, `README.md`: every `:8888` URL, plus `curl` examples with a bearer token and with an API key.
- `docs/advanced-topics.md`: replace "expose only the query port" with reverse proxy path rules (nginx and Traefik examples).
- `docs/configuration.md`, `.env.example`: the new variables; remove 8888 from the port table; deprecate `API_BASE_URL`.
- `docs/troubleshooting.md`: 401 vs 403 on `/query`; the UI page returned instead of data (a proxy rule sending `/query` to the wrong place); CORS.
- Release notes: the URL change, `QUERY_AUTH_DEFAULT`, and that `anzo.query` is now opt-in.
- Dockerfiles, `compose.yml`, GitHub workflows, module READMEs: remove `EXPOSE 8888` and the port mappings.

## Rollout

All eight steps are implemented on `feature/query-auth`; each was followed by the full build (unit, Quarkus and Angular
tests) and `scripts/gate.sh` (the integration suite on Derby, Derby + Redis, PostgreSQL + Redis, SQL Server and with
Keycloak sign-in, plus restart, UI, TLS and encryption checks) before the next began.

1. **Spike.** `spike/platform-http`: performance, runtime routes, OIDC enforcement and header behavior. The two open
   questions were settled in step 4: a key mechanism ahead of OIDC with its own challenge, and no second tenant.
2. **Move to `platform-http`.** Endpoint, `SpaFallback` and `QueryPathFallback`, route-ID validation, `routeParams`,
   header filtering both ways, `QUERY_DEBUG_HEADERS`, `QUERY_BASE_URL`, 405. The first gate run found the OIDC stack
   refusing anonymous query calls (fixed with the `/query` permission) and a template test relying on Netty's channel
   object (now Vert.x's socket address).
3. **Test routes in-process.** `direct:` endpoints; `camel-quarkus-netty-http` removed.
4. **Route access settings and `qtt-query`.** `RouteAccessPolicy`, `QueryAuthMechanism`'s challenge, the persistence
   fields, `QUERY_AUTH_DEFAULT`, the startup warning, `AuthDisabledMechanism` off `/query`.
5. **API keys.** Entity, authenticator, resource.
6. **Angular.** Access select, endpoint panel with `curl`, access markers, API key card.
7. **CORS for `/query`.**
8. **Documentation.** Administration guide (Securing the Query Endpoints, ports, upgrading), configuration reference,
   API reference (access parameters, API keys, query errors), user guide, troubleshooting, `.env.example`, READMEs,
   Dockerfiles and workflows (no `8888`).

## Risks and open questions

| Item | Risk | Status |
|---|---|---|
| Two mechanisms on `Authorization: Bearer` | Quarkus picks OIDC for a `qtt_` key, or refuses the configuration | Resolved: `QueryAuthMechanism` (priority 1500) claims `qtt_` tokens on `/query` and leaves the rest to OIDC; covered by `ApiKeysOnTest` and `ApiKeyIT` on the Keycloak stack |
| Routes stay open by default | People assume SSO now protects their data, as they did before the OIDC work | Startup WARN with the open-route count; a globe on open routes in the routes list and a warning on the route page; the Settings page shows the open-route count; the administration guide says routes are open until closed |
| Per-route `requiredRoles` | Typos in role names silently lock callers out | Open: the 403 doesn't name the missing role (Quarkus's denial has no body) and the route page doesn't list the known roles yet. The Settings page shows the group mappings |
| Query and admin traffic share one server | A query surge slows the UI, and port-level network separation is gone | Measured no regression at 8 clients (Spike results, Finished build); proxy path rules documented ("Exposing only the query routes") |
| Proxies or clients hard-coded to 8888 | Breakage on upgrade | Migration table in the administration guide; startup WARN when `API_BASE_URL` is set |
| Templates relying on credential headers | A template that read `Authorization` or `Cookie` would now see nothing | Documented in the administration guide (what templates can see) |
| A route change is a delete and re-create | During the change the route is briefly missing: callers get 401 (anonymous) or 404 | Not new (the same window existed on 8888); `ui_check.mjs` tolerates it |

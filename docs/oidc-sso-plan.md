# Single sign-on (OIDC) for the admin UI and management API

Goal: people sign in to the QTT admin UI through the organization's OpenID Connect identity provider (IdP), what they can do depends on their role, and scripts can call the management API with a bearer token.

Status: implemented on branch `feature/oidc` (from `refactor/optimize`, PR #17), 2026-11-27; not yet merged. Where the implementation differs from the first draft of this plan, the text below describes what was built and says why it changed.

## Decisions

| Topic | Decision | Why |
|---|---|---|
| Mechanism | `quarkus-oidc` with `application-type=hybrid` | Browsers use the authorization code flow with the session held server-side; scripts send bearer tokens to the same API |
| Where tokens live | In an encrypted session cookie set by Quarkus; the Angular code never sees a token | The UI and API share an origin, and the UI runs Monaco editors on user-written templates. Keeping tokens out of the browser lowers XSS exposure, and the Angular changes stay small |
| When OIDC is not configured | Same as today, with no authentication, plus a prominent startup warning | 2.0 upgrades must not break; secure-by-default can follow in a later release |
| Roles | `qtt-viewer`, `qtt-editor`, `qtt-admin`, each including the one before | Editors can change routes but not datasources, which hold the Anzo credentials |
| Bearer tokens for automation | Supported in v1 | The documented `curl` commands and the harness scripts keep working |
| Identity provider tested | Keycloak only for v1 | Claim paths are configurable, so other IdPs work through configuration; we test those when a customer needs one |
| Break-glass local login | None | To recover from an IdP outage, restart with `OIDC_ENABLED=false` |

## Scope

| Surface | Served by | In scope |
|---|---|---|
| Angular UI, `/` on 8080/8443 | Quarkus static resources and `SpaFallback` | Yes |
| Management API, `/queryrest/api/*` | Quarkus REST | Yes |
| Query endpoints, `:8888/<routeId>` | Camel `netty-http` | **No** |
| Temporary test routes, `:8888/<random>` | Camel `netty-http` (`TestRouteBuilder`) | **No** |

The `:8888` listener sits outside Quarkus's HTTP stack, so nothing in this plan protects it. Anyone who can reach 8888 can still query Anzo through any running route, using the credentials stored for its datasource. Securing the query endpoints is a separate design, for example per-route API keys or JWT validation in a Camel processor against the same issuer. Release notes and `docs/administration.md` must say clearly that SSO covers the admin plane only.

## How it works

### Browser flow

1. A request without a session reaches any secured path.
2. If it is a page navigation, Quarkus redirects to the IdP. After login, the IdP redirects back to the original URL and Quarkus sets the encrypted session cookie (`q_session`).
3. If it is an XHR from Angular, the interceptor has sent `X-Requested-With: JavaScript`. With `quarkus.oidc.authentication.java-script-auto-redirect=false`, Quarkus answers **499** instead of a 302 to the IdP (the browser can't follow that cross-origin redirect anyway). The interceptor then does a full-page navigation to the current URL, which starts step 2.
4. When the session expires, Quarkus uses the refresh token silently (`authentication.refresh-expired=true`). If refresh fails, step 3 applies.
5. Logging out is a link to `/logout`, a full-page navigation. Quarkus clears the cookie and calls the IdP's `end_session_endpoint` when the IdP has one (RP-initiated logout). Without one, only the QTT session ends.

### Bearer flow (automation)

A request with `Authorization: Bearer <jwt>` is validated as a service request: signature against the IdP's JWKS, issuer, expiry and audience. It needs no cookie, no redirect and no CSRF header. Scripts get tokens through the client-credentials grant from a separate IdP client (for example `qtt-ci`) whose service account is given a QTT role.

Keycloak's access tokens have `aud=account` unless an audience mapper adds the QTT client. The setup doc must include that mapper, and `quarkus.oidc.token.audience` is set to the QTT client id.

### Session cookie

- The cookie keeps all three tokens (the Quarkus default). Dropping the access token (`strategy=id-refresh-tokens`) was the plan, but Keycloak puts realm roles only in the access token by default, so roles are read from it (`OIDC_ROLES_SOURCE=accesstoken`) and it has to stay.
- `quarkus.oidc.token-state-manager.split-tokens=true` stores each token in its own cookie, keeping each under proxy header limits such as nginx's 8 KB default. Sites whose tokens carry many groups should grant QTT access through a few roles rather than raw group lists.
- `quarkus.oidc.token-state-manager.encryption-secret` comes from `OIDC_SESSION_SECRET`. Every instance behind a load balancer must use the same value. If it is unset, Quarkus derives the key from the client secret, which is also shared, so this is mainly so operators can rotate one without the other.
- Cookie attributes: `HttpOnly`, `Secure` (on HTTPS), `SameSite=Lax`.

### CSRF

Cookie sessions bring CSRF back as a risk.

- `SameSite=Lax` keeps the cookie off cross-site POST, PUT, PATCH and DELETE requests in current browsers.
- As a second layer, a small server filter rejects with **403** any request that is cookie-authenticated, uses an unsafe method, and lacks `X-Requested-With`. Bearer-authenticated requests are exempt. The Angular interceptor already sends the header for the 499 behavior.
- CORS stays off, so other origins can't set custom headers on requests to QTT.

### Behind a reverse proxy

TLS often terminates at a proxy, and Quarkus then builds `redirect_uri` as `http://...`, which the IdP rejects. These settings are exposed:

- `quarkus.http.proxy.proxy-address-forwarding`, `allow-x-forwarded` / `allow-forwarded`, and `trusted-proxies`, from `QTT_BEHIND_PROXY` and `QTT_TRUSTED_PROXIES`.
- `quarkus.oidc.authentication.force-redirect-https-scheme`, from `OIDC_FORCE_HTTPS_REDIRECT`.

### Private CA for the identity provider

IdPs on private networks often use an internal CA. The OIDC client uses a named TLS configuration (`quarkus.oidc.tls.tls-configuration-name=oidc`, plus `quarkus.tls.oidc.trust-store.*`), set from `OIDC_TRUSTSTORE_FILE`, `OIDC_TRUSTSTORE_PASSWORD` and `OIDC_TRUSTSTORE_TYPE`.

## Authorization

### Roles and endpoints

Each role includes everything allowed to the roles above it in the table. "Any authenticated user" means a valid session or token with none of the three roles; such a user gets **403** on every API call and the UI shows a "no access" page.

| Endpoint | Viewer | Editor | Admin |
|---|---|---|---|
| All `GET` under `/queryrest/api/**` except SPARQi session reads | ✓ | ✓ | ✓ |
| `GET /api/sparqi/session/{id}/history`, `/context` | owner only | owner only | ✓ |
| `PATCH /api/routes/{id}` (start/stop) | | ✓ | ✓ |
| `POST`/`PUT`/`DELETE /api/routes/{id}`, `POST /api/routes/clone/{id}` | | ✓ | ✓ |
| `DELETE /api/routes/{id}/cache` | | ✓ | ✓ |
| `DELETE /api/routes/cache` (clears every route's cache) | | | ✓ |
| `POST`/`DELETE /api/layers/{routeId}` | | ✓ | ✓ |
| `POST /api/routes/template/validate`, `POST /api/testing/variables`, `POST /api/ontology/{routeId}/warm` | ✓ | ✓ | ✓ |
| `POST /api/ontology/{routeId}/refresh`; `DELETE /api/ontology/{routeId}` | | ✓ | ✓ |
| `POST /api/testing/execute` | | ✓ | ✓ |
| `POST /api/sparqi/session`, `/session/{id}/message`, `/generate-test-request`; `DELETE /api/sparqi/session/{id}` | | ✓ (own sessions) | ✓ |
| `POST /api/datasources/{id}/healthcheck` | | ✓ | ✓ |
| `POST`/`PUT /api/datasources`, `DELETE /api/datasources/{id}` | | | ✓ |
| `PUT /api/datasources/{id}/disable`, `/enable` | | | ✓ |
| `POST /api/datasources/test` | | | ✓ |
| `GET /queryrest/api/me` | any authenticated user | | |

Three POSTs are viewer because they change nothing and the route page calls them when it opens: the template syntax check, template variable extraction and the ontology cache warm-up (which loads what `GET /api/ontology/{routeId}` would).

Why some endpoints sit higher than "read vs write" would suggest:

- **`POST /api/testing/execute`** is editor, not viewer, because it runs an arbitrary template against Anzo using the stored credentials.
- **`POST /api/datasources/test`** is admin because the server connects to a URL and credentials supplied by the caller, which makes it a server-side request forgery (SSRF) primitive.
- **`DELETE /api/routes/cache`** is admin because it affects every route.

### Enforcement

1. **Path policy, deny by default.** When OIDC is on, `/queryrest/*` and the UI under `/*` require an authenticated user (`quarkus.http.auth.permission.authenticated.paths=/*`). That covers anything new that someone forgets to annotate.
2. **Role annotations on each resource method.** `@RolesAllowed` uses constants from a shared `QttRoles` class in `query-service-core`. Annotations sit next to the code, rely on no path wildcards, and are easy to unit test. Set `quarkus.security.jaxrs.deny-unannotated-endpoints=true` so a new endpoint without an annotation fails closed. A unit test lists every resource method and checks that each has a role annotation, so a missing one is caught at build time rather than by a 403 in production.
3. **Ownership checks in code** for SPARQi sessions, covered below.

### When OIDC is off

The annotations would deny everything, so a custom `HttpAuthenticationMechanism`, active only when `OIDC_ENABLED=false`, gives every request a synthetic identity named `anonymous` with all three roles. This keeps one code path; the alternative of not enforcing would fork the logic. Startup logs at WARN:

```
QTT management API and UI are UNAUTHENTICATED (OIDC_ENABLED=false). Anyone who can reach port 8080/8443 can change datasources, routes and templates.
```

Confirmed in rollout step 1: with `quarkus.oidc.tenant-enabled=false` and no `auth-server-url`, the application starts and this mechanism (priority 2000, ahead of OIDC) authenticates every request; with OIDC on it returns nothing and the OIDC mechanism takes over. `SignInOffTest` and `SignInOnTest` boot the application both ways.

### Mapping IdP claims to roles

- `quarkus.oidc.roles.role-claim-path` comes from `OIDC_ROLES_CLAIM`. The default reads Keycloak's realm roles (`realm_access/roles`) and client roles (`resource_access/<client-id>/roles`). Examples for other IdPs: `groups` for Okta, `roles` for Entra app roles.
- `quarkus.oidc.roles.source` comes from `OIDC_ROLES_SOURCE` (`idtoken`, `accesstoken` or `userinfo`). The default is `accesstoken` for Keycloak. Entra needs `idtoken`, because the access token from an `openid` login is meant for Microsoft Graph and QTT can't validate it.
- IdP role or group names map to QTT roles through `OIDC_VIEWER_GROUPS`, `OIDC_EDITOR_GROUPS` and `OIDC_ADMIN_GROUPS` (comma-separated, case-sensitive), so customers don't need groups named after QTT. A role literally named `qtt-viewer`, `qtt-editor` or `qtt-admin` in the token also works. `QttRoleAugmentor` applies the mapping and then adds implied roles (admin → editor → viewer). This replaces the planned `quarkus.http.auth.roles-mapping`, whose environment-variable form is awkward for group names with spaces, slashes or GUIDs.
- The display name and username come from `OIDC_USERNAME_CLAIM` (default `preferred_username`) through `quarkus.oidc.token.principal-claim`.

## Configuration

New `.env.example` section. Everything else is still reachable through the standard `QUARKUS_OIDC_*` variables.

```
# --- Single sign-on (OIDC) for the admin UI and management API ---
# Off by default: without it the UI and API are unauthenticated.
OIDC_ENABLED=false
OIDC_AUTH_SERVER_URL=https://keycloak.example.com/realms/qtt
OIDC_CLIENT_ID=qtt
OIDC_CLIENT_SECRET=
# Where role or group names are found in the token, and which token holds them.
OIDC_ROLES_CLAIM=realm_access/roles
OIDC_ROLES_SOURCE=accesstoken
OIDC_USERNAME_CLAIM=preferred_username
# IdP role or group names granting each QTT role (comma-separated). Roles named qtt-viewer/-editor/-admin need none.
OIDC_VIEWER_GROUPS=
OIDC_EDITOR_GROUPS=
OIDC_ADMIN_GROUPS=
# Audience required in bearer tokens (default: OIDC_CLIENT_ID).
OIDC_TOKEN_AUDIENCE=
# Shared by every QTT instance behind a load balancer (32+ characters).
OIDC_SESSION_SECRET=
# Behind a TLS-terminating proxy.
QTT_BEHIND_PROXY=false
QTT_TRUSTED_PROXIES=
OIDC_FORCE_HTTPS_REDIRECT=false
# Private CA for the IdP.
OIDC_TRUSTSTORE_FILE=
OIDC_TRUSTSTORE_PASSWORD=
OIDC_TRUSTSTORE_TYPE=PKCS12
```

## Changes by module

### `query-service-app`

- Add the `quarkus-oidc` dependency.
- `application.properties`: the OIDC block mapped from the variables above; `quarkus.oidc.devservices.enabled=false`; logout path `/logout`; post-logout redirect to `/`.
- `security` package: `AuthDisabledMechanism` (identity when OIDC is off), `AuthStartupCheck` (the warning), `QttRoleAugmentor` (group mapping and implied roles), `CsrfHeaderFilter`, `OidcTlsConfigSource` (`OIDC_TRUSTSTORE_*`), `MeResource` (`GET /queryrest/api/me`), `SsoInfoResource` (`GET /queryrest/api/settings/sso`) and `AuditInterceptor` (below).
- `SpaFallback` needed no change: security handlers run before its reroute, so `/logout` and the login callback (which returns to the page that was asked for, for example `/routes`) reach the OIDC handler. `SecurityIT` covers both.

### `query-service-core`

- `QttRoles` constants. (The planned `CurrentUser` helper wasn't needed: the SPARQi controller uses JAX-RS's `SecurityContext`, and auditing has its own interceptor.)

### Every resource class (`query-service-route-builder`, `-testing`, `-sparqi`, `-ontology`)

- `@RolesAllowed` on every method, following the table above.
- `GET /queryrest/api/me` (`MeResource`, in the app module) returns `{ "username", "displayName", "roles": [...], "authEnabled": bool, "logoutUrl" }`.
- `GET /queryrest/api/settings/sso` (`SsoInfoResource`, in the app module, viewer) returns the non-secret sign-in configuration for the Settings page: issuer, client ID, claims, audience, group mappings, and whether the client secret, session secret and private-CA trust store are set (never their values).

### `query-service-sparqi`

- `POST /session` ignores the `userId` query parameter when OIDC is on; the owner is the signed-in user (JAX-RS `SecurityContext`). The parameter is kept for compatibility when OIDC is off.
- Sessions record their owner. History, context, message and delete check that the caller is the owner or an admin, and otherwise return **404** rather than 403, so callers can't probe which session ids exist.
- `docs/sparqi.md` is updated to describe this.

### `query-service-persistence`

- Routes and datasources embed an `AuditInfo` (`createdBy`, `createdAt`, `modifiedBy`, `modifiedAt`), returned read-only as `audit`. Hibernate's `update` strategy adds the nullable columns on Derby, PostgreSQL and MSSQL. Existing rows leave them empty and the UI shows nothing for them.
- A plain entity listener couldn't tell a user's change from a health check, which also updates the datasource row. So only controller methods marked `@Audited` (create and update of routes and datasources, route start/stop and clone, datasource enable/disable) count: `AuditInterceptor` runs them as the signed-in user, and `AuditListener` stamps entities saved during that call and leaves every other save alone.
- `createdBy`/`createdAt` are `updatable = false`, so merging a datasource sent by the UI (which has no audit fields) can't erase them. A route update deletes and re-creates the row, so `RouteManagementServiceImpl` carries the original audit record across. Route start/stop uses a bulk JPQL update that bypasses entity listeners, so it doesn't change `modifiedBy`.
- `upgrade_check.sh` must still pass: a 1.0.x or 2.0 database opens unchanged and gains the columns.

### `query-service-web` (Angular)

- `AuthInterceptor` adds `X-Requested-With: JavaScript` to requests to QTT. On a **499** it reloads the page to start login again. The UI uses hash routes (`/#/routes`), and the fragment is lost on the round trip to the IdP, so it saves the hash in `sessionStorage` and `AuthService` restores it after sign-in. A second 499 within 15 seconds shows a message instead of reloading again. It handles **403** with a snackbar ("You don't have permission to do that.").
- `AuthService` loads `/queryrest/api/me` once at startup through `APP_INITIALIZER` and exposes `hasRole()`.
- The toolbar shows the user's display name and a "Sign out" link to `/logout`; neither appears when `authEnabled=false`.
- A `*qttIfRole="'editor'"` structural directive hides write controls (create, edit, delete, start/stop, test, SPARQi, including the SPARQi panel, which opens a session as soon as it renders) from users without the role. This is cosmetic; the server enforces the roles.
- The route and datasource pages show "Created by … · Last changed by …" (`app-audit-line`).
- The Settings page has a "Single Sign-On" card: whether sign-in is on, who is signed in with which role, and the non-secret configuration from `GET /api/settings/sso`.
- A "No access" page for authenticated users with no QTT role.
- Local development (`ng serve` with `proxy.conf.js`) keeps OIDC off. To work on the login flow, use the UI served by Quarkus on 8080, because the IdP redirects back to the Quarkus origin.

## Testing

### Unit tests (each module)

- `RoleTableTest` (app module): every REST method's annotation against `role-table.txt`. The library modules' controllers are plain jars tested with mocks, so this replaces the per-module `@TestSecurity` tests in the first draft.
- SPARQi ownership (`SparqiControllerTest`): an owner, another editor (gets 404) and an admin.
- `AuditListenerTest` (persistence).
- Angular: `AuthService`, `AuthInterceptor` and `IfRoleDirective` specs.

### `query-service-app` with `quarkus-test-oidc-server`

These run in CI (`mvn install`), which does not run the podman harness. They use `OidcWiremockTestResource`, with no container. The library modules' controllers are plain jars tested with mocks, so the role-per-endpoint check lives here as `RoleTableTest`, which reads `src/test/resources/security/role-table.txt`.

- `SignInOnTest`: a page request gets a 302 to the IdP, and an XHR with the header gets a 499. A bearer token is accepted; a wrong audience, a wrong issuer or a malformed token gets 401. Roles and group mapping are enforced.
- `SignInOffTest`: every endpoint works anonymously, including admin-only writes without the CSRF header, and the UI is served.
- `CsrfHeaderFilterTest`, `QttRoleAugmentorTest`, `OidcTlsConfigSourceTest`: unit tests for the filter, the role mapping and the trust-store settings.
- The cookie-session paths (login callback, CSRF on a real session, `/logout`) need a real login form, so they are covered by `SecurityIT` against Keycloak, below.

### Harness (`scripts/stack.sh`, `query-service-it`, gate)

- `QTT_OIDC=true scripts/stack.sh up` starts Keycloak 26.0.7 on `localhost:18180`, importing `scripts/keycloak/qtt-realm.json`:
  - a confidential client `qtt` with an audience mapper;
  - a confidential client `qtt-ci` with a service account holding `qtt-admin`;
  - `other-app`, whose tokens lack QTT's audience (must get 401);
  - users `viewer`, `editor`, `editor2`, `admin`, `norole`, and `mapped` (holds `legacy-admins`, mapped to admin with `OIDC_ADMIN_GROUPS`), with test-only passwords.
- Readiness needed no change: an unauthenticated request gets a 302, which `curl -f` treats as success.
- With `-Dqtt.oidc=true`, the whole existing suite runs as the `qtt-ci` service account, so every API test also passes through sign-in. `SecurityIT` (skipped without OIDC) checks the browser flow (Keycloak form, callback, CSRF on the session cookie, sign-out), bearer tokens (roles, group mapping, wrong audience), a role matrix, SPARQi ownership and the audit record.
- `ui_check.mjs` with `QTT_OIDC=true` walks every page signed in as admin through the Keycloak form, then checks in a real browser: a viewer sees no write controls and opening pages causes no 403; a user with no role gets the "No access" page; after the session cookie is deleted, the next API call signs the user in again and lands back on the same page; sign-out ends at the Keycloak login page.
- `gate.sh` adds one configuration: Derby with sign-in on (the full suite, `SecurityIT` and the OIDC UI check). The other configurations run with sign-in off, as before.

## Documentation

- `docs/administration.md`: a new "Single sign-on" section covering Keycloak setup step by step (realm, clients, audience mapper, roles, group mapping), plus short notes for Entra ID (`OIDC_ROLES_SOURCE=idtoken`, app roles rather than groups) and Okta (a `groups` claim on the authorization server). The notes say plainly that only Keycloak is tested.
- `docs/administration.md`: state that SSO does not cover the `:8888` query endpoints.
- `docs/configuration.md` and `.env.example`: the new variables.
- `docs/api-reference.md`: the roles each endpoint needs, plus how to get a bearer token with client credentials and call the API with curl.
- `docs/troubleshooting.md`: `redirect_uri` mismatch behind a proxy, a 401 on bearer tokens because of the audience, cookies too large, and IdP TLS errors.

## Rollout

All eight steps are implemented on `feature/oidc`.

1. **Spike.** Prove that `quarkus-oidc` with the tenant disabled at runtime and the anonymous-identity mechanism can coexist, that hybrid mode and the 499 behavior work, and that `SpaFallback` and `/logout` interact correctly. Throw the code away if it doesn't fit.
2. **Backend core.** Add the dependency, configuration, roles, the off-mode mechanism and warning, `@RolesAllowed` everywhere, the coverage test, and `/me`. OIDC stays off by default, so behavior is unchanged.
3. **Keycloak in the harness.** Add the realm file, `QTT_OIDC=true` in `stack.sh`, and `SecurityIT`.
4. **Angular.** Add the interceptor, `AuthService`, toolbar, role directive and "No access" page, and extend `ui_check.mjs`.
5. **CSRF filter and proxy/TLS settings.**
6. **SPARQi ownership.**
7. **Audit columns,** with `upgrade_check.sh` on all three databases.
8. **Documentation.**

## Risks and open questions

| Item | Risk | How we find out |
|---|---|---|
| Off-mode identity mechanism | Quarkus may pick the OIDC mechanism over ours, or reject a disabled tenant without `auth-server-url` | Resolved in step 1: works as designed |
| Cookie size | Large ID tokens from IdPs that put many groups in them | `split-tokens`, plus the Entra/Okta notes recommending app roles over raw groups |
| Keycloak audience | Bearer tokens fail with 401 until the audience mapper is added | Included in the realm file and the setup doc |
| `:8888` is unprotected | People assume SSO protects the data | Called out in docs and release notes; follow-up design for data-plane auth |
| SPARQi sessions from before this change | None in practice | Sessions live in memory (Caffeine, 30 minutes idle) and don't survive the restart that enables OIDC |

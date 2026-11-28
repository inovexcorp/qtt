# Administration Guide

This guide covers deploying and administering QTT in production environments.

## Quick Start with Docker

The fastest way to get QTT running is using the official Docker image.

### Step 1: Pull the Image

```bash
docker pull docker.io/inovexis/qtt:latest
```

### Step 2: Run the Container

**Basic deployment with embedded Derby database:**

```bash
docker run -d \
  --name qtt \
  -p 8443:8443 \
  docker.io/inovexis/qtt:latest
```

**With environment variables for configuration:**

```bash
docker run -d \
  --name qtt \
  -p 8443:8443 \
  -e DB_DRIVER_NAME=derby \
  docker.io/inovexis/qtt:latest
```

### Step 3: Access the Application

- **Web UI**: https://localhost:8443
- **API Base**: https://localhost:8443/queryrest/api/
- **Query Endpoints**: https://localhost:8443/query/{route-id}

The image ships with a self-signed certificate (see [SSL/TLS Configuration](#ssltls-configuration)). The UI and API
are also served over plain HTTP on port 8080 if you publish it (`-p 8080:8080`).

**Note**: Typically you'll want to use a reverse proxy to expose the application to the outside world.

### Step 4: Verify Installation

```bash
# Check container status
docker ps

# View logs
docker logs qtt

# Check API health
curl -k https://localhost:8443/queryrest/api/settings
```

## Quick Start with Podman

Podman is a daemonless container engine that's Docker-compatible and can run rootless containers.

### Install Podman

**On macOS:**

```bash
brew install podman
podman machine init
podman machine start
```

**On RHEL/CentOS/Fedora:**

```bash
sudo dnf install podman
```

**On Ubuntu/Debian:**

```bash
sudo apt-get install podman
```

### Pull and Run

```bash
podman pull docker.io/inovexis/qtt:latest

# Rootless mode (recommended)
podman run -d \
  --name qtt \
  -p 8443:8443 \
  docker.io/inovexis/qtt:latest
```

**With volume mount for persistent data:**

```bash
podman run -d --name qtt -p 8443:8443 -v qtt-data:/opt/qtt/data:Z docker.io/inovexis/qtt:latest
```

**Note**: The `:Z` flag is important for SELinux systems (RHEL, CentOS, Fedora) to properly label the volume.

### Podman-Specific Considerations

**Port Mapping in Rootless Mode:**

- Rootless Podman can map ports 1024+ directly
- For ports <1024, you need root or configure `net.ipv4.ip_unprivileged_port_start`

**SELinux Context:**

- Use `:Z` for private volume mounts
- Use `:z` for shared volume mounts

## Docker Compose Setup

For production-like deployments with PostgreSQL or SQL Server, use Docker Compose.

### PostgreSQL Setup

Create `compose.yml`:

```yaml
services:
  postgresql:
    image: postgres:18-alpine
    container_name: qtt-postgres
    environment:
      POSTGRES_DB: qtt
      POSTGRES_USER: qttuser
      POSTGRES_PASSWORD: SecurePassword123
    ports:
      - "5432:5432"
    volumes:
      - qtt-postgres-data:/var/lib/postgresql
    networks:
      - qtt-network

  qtt:
    image: docker.io/inovexis/qtt:latest
    container_name: qtt
    depends_on:
      - postgresql
    environment:
      DB_DRIVER_NAME: "PostgreSQL JDBC Driver"
      DB_URL: "jdbc:postgresql://postgresql:5432/qtt"
      DB_USER: qttuser
      DB_PASSWORD: SecurePassword123
    ports:
      - "8443:8443"
    networks:
      - qtt-network

volumes:
  qtt-postgres-data:

networks:
  qtt-network:
    driver: bridge
```

**Start services:**

```bash
docker compose up -d
```

### SQL Server Setup

Create `docker-compose.yml`:

```yaml
services:
  mssql:
    image: mcr.microsoft.com/mssql/server:2019-latest
    container_name: qtt-mssql
    environment:
      ACCEPT_EULA: "Y"
      SA_PASSWORD: "VerySecure123!"
      MSSQL_PID: Express
    ports:
      - "1433:1433"
    volumes:
      - qtt-mssql-data:/var/opt/mssql
    networks:
      - qtt-network

  qtt:
    image: docker.io/inovexis/qtt:latest
    container_name: qtt
    depends_on:
      - mssql
    environment:
      DB_DRIVER_NAME: "Microsoft JDBC Driver for SQL Server"
      DB_URL: "jdbc:sqlserver://mssql:1433;databaseName=qtt;encrypt=true;trustServerCertificate=true"
      DB_USER: sa
      DB_PASSWORD: "VerySecure123!"
    ports:
      - "8443:8443"
    networks:
      - qtt-network

volumes:
  qtt-mssql-data:

networks:
  qtt-network:
    driver: bridge
```

### Redis Cache Setup

For production deployments with query result caching, add Redis to your stack:

```yaml
services:
  postgresql:
    image: postgres:18-alpine
    container_name: qtt-postgres
    environment:
      POSTGRES_DB: qtt
      POSTGRES_USER: qttuser
      POSTGRES_PASSWORD: SecurePassword123
    ports:
      - "5432:5432"
    volumes:
      - qtt-postgres-data:/var/lib/postgresql/data
    networks:
      - qtt-network
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U qttuser" ]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: qtt-redis
    command: redis-server --requirepass RedisSecure123 --maxmemory 256mb --maxmemory-policy allkeys-lru
    ports:
      - "6379:6379"
    volumes:
      - qtt-redis-data:/data
    networks:
      - qtt-network
    healthcheck:
      test: [ "CMD", "redis-cli", "--raw", "incr", "ping" ]
      interval: 10s
      timeout: 5s
      retries: 5

  qtt:
    image: docker.io/inovexis/qtt:latest
    container_name: qtt
    depends_on:
      postgresql:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      # Database Configuration
      DB_DRIVER_NAME: "PostgreSQL JDBC Driver"
      DB_URL: "jdbc:postgresql://postgresql:5432/qtt"
      DB_USER: qttuser
      DB_PASSWORD: SecurePassword123

      # Redis Cache Configuration
      REDIS_ENABLED: "true"
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: RedisSecure123
      REDIS_DATABASE: 0

      # Cache Settings
      CACHE_DEFAULT_TTL: 3600
      CACHE_KEY_PREFIX: "qtt:cache:"
      CACHE_COMPRESSION_ENABLED: "true"
      CACHE_FAIL_OPEN: "true"
      CACHE_STATS_ENABLED: "true"
    ports:
      - "8443:8443"
    networks:
      - qtt-network

volumes:
  qtt-postgres-data:
  qtt-redis-data:

networks:
  qtt-network:
    driver: bridge
```

**Key Configuration Notes:**

- **Redis Memory Management**: `maxmemory 256mb` with `allkeys-lru` eviction policy ensures cache stays within bounds
- **Redis Password**: Set via `--requirepass` flag and `REDIS_PASSWORD` environment variable
- **Health Checks**: Ensures QTT only starts after PostgreSQL and Redis are ready
- **Compression**: Enabled by default to reduce memory usage
- **Fail-Open**: Cache errors don't break queries; system falls back to direct database queries

### Managing the Stack

```bash
# Start all services
docker compose up -d

# View logs
docker compose logs -f qtt

# Stop all services
docker compose down

# Stop and remove volumes (CAUTION: deletes data)
docker compose down -v

# Restart a specific service
docker compose restart qtt
```

## Podman Compose Setup

Podman Compose provides Docker Compose compatibility for Podman.

### Install Podman Compose

```bash
pip install podman-compose
```

### Using the Same Compose Files

The same `docker-compose.yml` files work with `podman-compose`:

```bash
# Start services
podman-compose up -d

# View logs
podman-compose logs -f qtt

# Stop services
podman-compose down
```

### Using Podman Pods (Alternative)

Podman supports Kubernetes-style pods for grouping containers:

```bash
# Create a pod with port mappings
podman pod create --name qtt-pod \
  -p 8443:8443 \
  -p 5432:5432

# Run PostgreSQL in the pod
podman run -d \
  --pod qtt-pod \
  --name qtt-postgres \
  -e POSTGRES_DB=qtt \
  -e POSTGRES_USER=qttuser \
  -e POSTGRES_PASSWORD=SecurePassword123 \
  postgres:latest

# Run QTT in the pod
podman run -d \
  --pod qtt-pod \
  --name qtt \
  -e DB_DRIVER_NAME="PostgreSQL JDBC Driver" \
  -e DB_URL="jdbc:postgresql://localhost:5432/qtt" \
  -e DB_USER=qttuser \
  -e DB_PASSWORD=SecurePassword123 \
  docker.io/inovexis/qtt:latest

# Manage the pod
podman pod ps
podman pod stop qtt-pod
podman pod start qtt-pod
podman pod rm qtt-pod
```

## Single Sign-On (OIDC)

QTT can require users to sign in through your organization's OpenID Connect identity provider (IdP) before they can
use the UI or the management API (`/queryrest/api`). What each user can do then depends on their QTT role.

**Sign-in is off by default.** Without it, the UI and management API are open to anyone who can reach ports
8080/8443, and QTT logs a warning at every startup. Turn it on for any shared or production deployment.

**Sign-in does not decide who can call the query routes.** Each route at `/query/<route-id>` has its own access
setting, and routes are open unless you close them; see [Securing the Query Endpoints](#securing-the-query-endpoints).

### Roles

| Role         | Can                                                                                                   |
|--------------|-------------------------------------------------------------------------------------------------------|
| `qtt-viewer` | See everything: metrics, routes and templates, datasources (never their passwords), settings          |
| `qtt-editor` | Everything a viewer can, plus create, change, start/stop, clone and delete routes; clear a route's cache; run template tests; use SPARQi; trigger a datasource health check |
| `qtt-admin`  | Everything an editor can, plus create, change, enable/disable and delete datasources (they hold the Anzo credentials), test a datasource connection, clear every route's cache |

A signed-in user with none of these roles sees a "No access" page. Users can only see and use their own SPARQi
sessions (admins can see all). Routes and datasources record who created and last changed them, shown on their
configuration pages and returned as `audit` by the API.

You can grant roles in two ways:

- Give users (or their groups) IdP roles named exactly `qtt-viewer`, `qtt-editor` or `qtt-admin`.
- Or map roles or groups you already have: `OIDC_VIEWER_GROUPS`, `OIDC_EDITOR_GROUPS` and `OIDC_ADMIN_GROUPS` each
  take a comma-separated list of names, matched exactly (case-sensitive).

### Turning it on

```bash
docker run -d \
  --name qtt \
  -p 8443:8443 \
  -e OIDC_ENABLED=true \
  -e OIDC_AUTH_SERVER_URL=https://keycloak.example.com/realms/example \
  -e OIDC_CLIENT_ID=qtt \
  -e OIDC_CLIENT_SECRET="$(cat ./secrets/qtt_client_secret)" \
  -e OIDC_ADMIN_GROUPS=platform-admins \
  docker.io/inovexis/qtt:latest
```

`OIDC_AUTH_SERVER_URL` is the issuer URL; QTT reads the rest from its `/.well-known/openid-configuration`. All
sign-in settings are listed in [Configuration](configuration.md#sign-in-oidc-configuration).

Startup logs `Sign-in required for the UI and management API (OIDC issuer ...)` when it is on.
The **Single Sign-On** card on the Settings page shows whether sign-in is on, who you are signed in as and with
which role, and the settings in effect: issuer, client ID, roles claim, accepted audience, group mappings, and whether
the client secret, session secret and private-CA trust store are set (never their values).

### Setting up Keycloak

Keycloak is the identity provider QTT is tested against. `scripts/keycloak/qtt-realm.json` is a complete test realm
(it contains test passwords, so use it as a reference only).

1. **Client for QTT**: create an OpenID Connect client, for example `qtt`.
   - Client authentication: on (confidential). Copy its secret into `OIDC_CLIENT_SECRET`.
   - Standard flow: on.
   - Valid redirect URIs: `https://qtt.example.com/*` (the URL users open QTT at).
   - Valid post logout redirect URIs: `+` (same as the redirect URIs).
2. **Audience**: add a mapper to the client (Client scopes → `qtt-dedicated` → Add mapper → By configuration →
   Audience) with *Included Client Audience* `qtt` and *Add to access token* on. Without it, bearer tokens are
   rejected (401) because they are not issued for QTT.
3. **Roles**: create realm roles `qtt-viewer`, `qtt-editor` and `qtt-admin` and assign them to users or groups. Or
   keep your existing roles and map them with `OIDC_*_GROUPS`.
4. **Automation** (optional): for scripts and CI, create a second confidential client (for example `qtt-ci`) with
   *Service accounts roles* on and standard flow off, give its service account a QTT role, and add the same audience
   mapper.

Keycloak puts realm roles in the access token, which is where QTT reads them by default
(`OIDC_ROLES_SOURCE=accesstoken`). To use Keycloak groups instead of roles, add a *Group Membership* mapper (claim
`groups`, *Full group path* off, added to the access token), set `OIDC_ROLES_CLAIM=groups` and map the group names
with `OIDC_*_GROUPS`.

### Other identity providers

Any OpenID Connect provider should work, but only Keycloak is tested. Points to watch:

- **Microsoft Entra ID**: `OIDC_AUTH_SERVER_URL=https://login.microsoftonline.com/<tenant-id>/v2.0`. Define app
  roles on the app registration and assign them; set `OIDC_ROLES_CLAIM=roles` and `OIDC_ROLES_SOURCE=idtoken` (the
  access token Entra issues at sign-in is meant for Microsoft Graph, not QTT). Prefer app roles to groups: group
  claims are object IDs and are left out entirely for users in more than 200 groups.
  `OIDC_USERNAME_CLAIM=preferred_username` works.
- **Okta**: add a `groups` claim to the authorization server (filter it to the groups QTT needs), then
  `OIDC_ROLES_CLAIM=groups` and map the group names with `OIDC_*_GROUPS`.
- Providers without RP-initiated logout (for example Google) end only the QTT session on sign-out, not the
  provider's.

### Behind a reverse proxy

If TLS ends at a proxy, QTT sees plain-HTTP requests and builds `http://` redirect URIs, which the IdP rejects. Set:

| Variable                    | Value                                                          |
|-----------------------------|----------------------------------------------------------------|
| `QTT_BEHIND_PROXY`          | `true`: trust `X-Forwarded-*` / `Forwarded` headers            |
| `QTT_TRUSTED_PROXIES`       | the proxy's address(es), so other clients can't forge headers  |
| `OIDC_FORCE_HTTPS_REDIRECT` | `true` if the proxy doesn't send `X-Forwarded-Proto`           |

Proxies must allow request headers of at least 8 KB: the session is kept in encrypted cookies, split one per
token.

### Live updates through a reverse proxy

The datasource and metrics pages get health changes and route metrics over a long-lived server-sent events stream
(`/queryrest/api/events`). QTT sends `X-Accel-Buffering: no`, a `: keep-alive` comment every 20 s
(`QTT_EVENTS_KEEP_ALIVE`) and ends each stream after 10 minutes (`QTT_EVENTS_MAX_AGE`). The proxy must pass the
stream through unbuffered and keep idle connections open longer than the keep-alive interval. With nginx:

```nginx
location /queryrest/api/events {
    proxy_pass https://qtt:8443;
    proxy_http_version 1.1;
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 60s;   # above the 20 s keep-alive
}
```

Don't compress `text/event-stream` responses at the proxy (compression buffers them). If the stream can't get
through, the UI falls back to polling, so pages still update, only more slowly.

### Identity provider with a private CA

```bash
-v /path/to/idp-ca.p12:/opt/qtt/etc/idp-ca.p12:ro \
-e OIDC_TRUSTSTORE_FILE=/opt/qtt/etc/idp-ca.p12 \
-e OIDC_TRUSTSTORE_PASSWORD=changeit \
-e OIDC_TRUSTSTORE_TYPE=PKCS12     # or JKS, or PEM (a certificate file; no password)
```

### More than one QTT instance

Sessions live in encrypted cookies, so any instance can serve any user. All instances need the same
`OIDC_CLIENT_SECRET` and, if you set it, the same `OIDC_SESSION_SECRET` (at least 32 characters).

### Calling the API from scripts

With sign-in on, scripts send a bearer token from the IdP. With Keycloak's client-credentials grant:

```bash
TOKEN=$(curl -s https://keycloak.example.com/realms/example/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=qtt-ci -d client_secret="$QTT_CI_SECRET" | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" https://qtt.example.com:8443/queryrest/api/routes
```

Requests with a bearer token need no other headers. (The browser UI uses a session cookie; state-changing requests
made with that cookie must carry an `X-Requested-With` header, which the UI adds, as protection against cross-site
request forgery.)

### If the identity provider is down

Users can't sign in while the IdP is unreachable. To get back in, restart QTT with `OIDC_ENABLED=false`, which
opens the UI and API to anyone who can reach them, so do that only on a network you control, and turn sign-in back on
as soon as the IdP returns.

## Securing the Query Endpoints

Applications call routes at `/query/<route-id>` on the same server as the UI: `https://<host>:8443/query/<route-id>`
(or port 8080 over plain HTTP). Each route has an **access** setting, edited on the route's page or sent as
`accessPolicy` to the routes API:

| Access          | Who can call the route                                                                                   |
|-----------------|----------------------------------------------------------------------------------------------------------|
| `default`       | Follows `QUERY_AUTH_DEFAULT` (`open` unless you change it)                                               |
| `open`          | Anyone who can reach the server; credentials are ignored                                                 |
| `authenticated` | A caller with an OIDC access token that carries the `qtt-query` role, or a QTT API key that names the route (or `*`) |

**Routes are open by default** (`QUERY_AUTH_DEFAULT=open`), as they were on port 8888, so upgrading only changes the
URL. Close routes one at a time, or set `QUERY_AUTH_DEFAULT=authenticated` to close every route left at `default`;
routes set explicitly to `open` stay open. At startup QTT logs a warning with the number of open routes (their names at
INFO), and the routes list marks each route as open (globe) or authenticated (lock).

Callers send their credential as a bearer token:

```bash
curl -H "Authorization: Bearer $QTT_TOKEN" https://qtt.example.com:8443/query/people-search
```

What callers get back:

| Situation                                                                       | Response                                  |
|---------------------------------------------------------------------------------|-------------------------------------------|
| Open route                                                                      | 200                                       |
| Authenticated route, no credential                                              | 401 with `WWW-Authenticate: Bearer` (never a redirect to the sign-in page) |
| Invalid, expired or revoked token or key, on any route (open ones too)          | 401                                       |
| Valid credential without access to the route                                    | 403, the body naming the missing roles (`missingRoles`) or the API key's scope |
| Route being changed right now (a save in the UI or API, or its datasource changed) | 503 with `Retry-After: 1`, for callers the route's access setting admits |
| Route that doesn't exist: anonymous caller / caller with a valid credential     | 401 / 404 (anonymous callers can't tell a missing route from a protected one) |
| A method the route doesn't accept                                               | 405 with `Allow`                          |

### The `qtt-query` role

Callers with an OIDC token need the `qtt-query` role. It is separate from `qtt-viewer`, `qtt-editor` and `qtt-admin`:
an admin can't call authenticated routes without it, and `qtt-query` alone gives no access to the UI. Grant it like
the other roles, as an IdP role named `qtt-query` or by mapping existing roles or groups with `OIDC_QUERY_GROUPS`
(comma-separated).

For an application, create a confidential client with a service account in the IdP, give the service account
`qtt-query`, and add the same audience mapper as for scripts ([Setting up Keycloak](#setting-up-keycloak)) so its
tokens are issued for QTT. The application then uses the client-credentials grant:

```bash
TOKEN=$(curl -s https://keycloak.example.com/realms/example/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=reporting-app -d client_secret="$SECRET" | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" https://qtt.example.com:8443/query/people-search
```

### Required roles

An authenticated route can also list **required roles** (comma-separated, for example `finance-apps`). An OIDC caller
then needs `qtt-query` and every listed role (IdP role or group names, matched after `OIDC_*_GROUPS` mapping). An API
key holds no IdP roles, so it can call such a route only if the key names the route; a `*` key does not reach it.

### API keys

API keys suit applications that don't use your identity provider, and deployments without sign-in. Admins manage them
under **Settings › Query Access** (or `GET/POST /queryrest/api/apikeys`, `DELETE /queryrest/api/apikeys/{id}`).

- A key looks like `qtt_<id>_<secret>`. It is shown **once**, when it is created; QTT stores only a SHA-256 hash of
  the secret, and lists keys by their id, name, routes and dates.
- A key names the routes it may call, or `*` for every authenticated route that requires no roles. Open routes accept
  any valid key.
- Keys expire after `QUERY_API_KEY_MAX_DAYS` days (365 by default) unless created with a shorter `expiresInDays`.
  With `QUERY_API_KEY_MAX_DAYS=0`, keys can be created without an expiry.
- Revoking a key takes effect within 30 seconds on every instance (at once on the instance that revoked it).
  To rotate a key, create a second one, switch the application over, then revoke the first.
- Keys work on `/query` only, never on the management API. Automation that manages routes and datasources uses an
  OIDC service account ([Calling the API from scripts](#calling-the-api-from-scripts)).
- Send keys only in the `Authorization` header, never in a URL, and keep them out of browser code.

### Calling routes from a browser

A web application on another origin needs CORS. List the allowed origins in `QUERY_CORS_ORIGINS` (comma-separated, or
`*` for any). QTT then answers preflight requests for `/query/*` and allows `GET`, `POST`, the `Authorization` and
`Content-Type` headers, and no cookies. The management API never allows cross-origin requests. Browser applications
should send an OIDC access token from their own sign-in, not an API key.

### What templates and callers can see

Templates read request headers and query parameters (`headers.*`), but QTT removes `Authorization`, `Cookie`,
`Proxy-Authorization`, `Forwarded` and `X-Forwarded-*` before a template runs, so a template can't copy a caller's
credential into the query sent to Anzo. Responses carry `Content-Type` and the route's own headers only; request
headers are not echoed back. The `anzo.query`, `anzo.query_duration` and `anzo.graphmart` response headers, which show
the generated SPARQL, are sent only with `QUERY_DEBUG_HEADERS=true`.

All callers of a route share its cache: access decides who may call a route, not what data each caller sees. Anzo
is always queried with the datasource's stored credentials.

### Exposing only the query routes

Port 8888 is gone, so separate the query routes from the UI by path instead: expose `/query/` to the networks your
applications use, and keep `/` and `/queryrest/` internal if only your staff use the UI. With nginx:

```nginx
location /query/ {
    proxy_pass https://qtt:8443;
}
location / {
    allow 10.0.0.0/8;   # staff network
    deny all;
    proxy_pass https://qtt:8443;
}
```

Set `QUERY_BASE_URL` to the public URL of the query routes (for example `https://data.example.com/query`) so the UI
shows the right endpoint URLs.

## SSL/TLS Configuration

### Using Custom SSL Certificates

Typically, it's a better practice to sit behind a reverse proxy, such as nginx, but the container does support runtime
SSL configuration via environment variables:

```bash
docker run -d \
  --name qtt \
  -p 8443:8443 \
  -v /path/to/keystore.jks:/opt/qtt/etc/custom-keystore:ro \
  -e KEYSTORE=/opt/qtt/etc/custom-keystore \
  -e PASSWORD=your-keystore-password \
  docker.io/inovexis/qtt:latest
```

`KEYSTORE` is the path of a JKS keystore inside the container and `PASSWORD` its password. Without them, QTT uses the
bundled self-signed keystore at `/opt/qtt/etc/keystore`. The container runs as uid 1001, so the mounted keystore must
be readable by that user.

### Generating a Self-Signed Certificate

```bash
keytool -genkey \
  -keyalg RSA \
  -alias qtt \
  -keystore keystore.jks \
  -storepass changeit \
  -validity 365 \
  -keysize 2048
```

## Persistent Data and Volumes

### Important Data Directories

| Path in Container         | Purpose              | Persistence Needed |
|---------------------------|----------------------|--------------------|
| `/opt/qtt/data/database`  | Derby database files | Yes                |
| `/opt/qtt/data/templates` | Freemarker templates | Yes                |
| `/opt/qtt/etc/keystore`   | Default TLS keystore | No                 |

Logs go to the console; read them with `docker logs qtt` / `podman logs qtt`.

The container runs as the non-root user `qtt` (uid 1001), so mounted volumes must be writable by that user.

### Docker Volume Mounting

```bash
docker run -d \
  --name qtt \
  -p 8443:8443 \
  -v qtt-data:/opt/qtt/data \
  -v qtt-templates:/opt/qtt/data/templates \
  docker.io/inovexis/qtt:latest
```

### Podman Volume Mounting with SELinux

```bash
podman run -d \
  --name qtt \
  -p 8443:8443 \
  -v qtt-data:/opt/qtt/data:Z \
  -v qtt-templates:/opt/qtt/data/templates:Z \
  docker.io/inovexis/qtt:latest
```

## Port Configuration

| Port | Protocol | Purpose                                                         |
|------|----------|-----------------------------------------------------------------|
| 8443 | HTTPS    | Web UI, management API (`/queryrest/api`), query routes (`/query`) |
| 8080 | HTTP     | The same, over plain HTTP                                       |

Port 8888 (the query endpoints before 2.0) is no longer used. Sign-in ([Single Sign-On](#single-sign-on-oidc))
protects the UI and management API; each query route follows its own access setting
([Securing the Query Endpoints](#securing-the-query-endpoints)).

To expose QTT on other host ports, change the host side of the `-p` mapping (for example `-p 443:8443`).

## Upgrading from QTT 1.x

QTT now runs on Camel Quarkus instead of Apache Karaf. Environment variables and the management API are unchanged,
but **the query endpoints moved** from `http://<host>:8888/<route-id>` to `https://<host>:8443/query/<route-id>`
(or `http://<host>:8080/query/<route-id>`), so applications and proxies that call routes must change their URLs:

| Before (1.x)                                   | From 2.0                                                                      |
|------------------------------------------------|-------------------------------------------------------------------------------|
| `http://<host>:8888/<route-id>`                | `https://<host>:8443/query/<route-id>`                                         |
| Publish port 8888 (`-p 8888:8888`)             | Not needed; the query routes share 8443/8080 with the UI                       |
| `API_BASE_URL=http://<host>:8888`              | `QUERY_BASE_URL=https://<host>:8443/query` (or unset); `API_BASE_URL` is ignored with a warning |
| Proxy rule "expose port 8888"                  | Proxy rule on the path `/query/` ([Exposing only the query routes](#exposing-only-the-query-routes)) |
| No credentials                                 | Still none: routes stay open until you close them ([Securing the Query Endpoints](#securing-the-query-endpoints)) |
| 405 for a method the route doesn't accept      | 405, now with an `Allow` header                                                |
| `anzo.query` / `anzo.query_duration` / `anzo.graphmart` response headers | Only with `QUERY_DEBUG_HEADERS=true`                      |
| Any request header echoed in the response      | Not echoed                                                                     |

Route IDs must now be letters, digits, `.`, `_` and `-` (up to 128 characters), since each is one URL path segment.
A stored route whose ID breaks that rule is not loaded; startup logs it by name, and it can be recreated under a
valid ID. Of the route parameters (`routeParams`), only `httpMethodRestrict` (what the UI writes) is kept; other Netty
options set through the API are ignored with a warning.

Otherwise an existing deployment upgrades by switching the image:

- **Derby**: mount the same `/opt/qtt/data` volume. The container now runs as uid 1001, while the 1.x container ran
  as root, so hand the volume over to that user once before starting the new image:

  ```bash
  docker run --rm -u 0 -v qtt-data:/opt/qtt/data --entrypoint chown docker.io/inovexis/qtt:latest -R 1001:1001 /opt/qtt/data
  ```

  The embedded Derby is now version 10.16; it opens databases created by 1.x (Derby 10.14) in soft-upgrade mode.
- **PostgreSQL / SQL Server**: the existing database is reused as is. On SQL Server the template column is converted
  from `varchar(max)` to `nvarchar(max)` at first start, so non-Latin template text is stored correctly from then on
  (text that was already stored as `?` cannot be recovered).
- **Configuration**: settings that were edited in `etc/*.cfg` files under Karaf are now set with environment
  variables or Java system properties (see the [Configuration Reference](configuration.md)). Changes require a
  restart.
- **Logs**: QTT logs to the console instead of `data/log/karaf.log`.
- **Sign-in**: 2.0 can require sign-in through an OpenID Connect provider ([Single Sign-On](#single-sign-on-oidc)).
  It is off unless `OIDC_ENABLED=true`, so upgrading changes nothing until you turn it on. Routes and datasources
  gain `createdBy`/`createdAt`/`modifiedBy`/`modifiedAt` columns, added automatically at first start; existing rows
  leave them empty.
- **Query access**: routes gain `accessPolicy` and `requiredRoles` columns and there is a new `api_keys` table, all
  created at first start. Existing routes get the `default` access, which is open unless `QUERY_AUTH_DEFAULT` says
  otherwise.

## Docker vs Podman Command Reference

| Action           | Docker                                                                             | Podman                                                                             |
|------------------|------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| Pull image       | `docker pull docker.io/inovexis/qtt:latest`                                        | `podman pull docker.io/inovexis/qtt:latest`                                        |
| Run container    | `docker run -d --name qtt -p 8443:8443 docker.io/inovexis/qtt:latest` | `podman run -d --name qtt -p 8443:8443 docker.io/inovexis/qtt:latest` |
| List containers  | `docker ps`                                                                        | `podman ps`                                                                        |
| View logs        | `docker logs qtt`                                                                  | `podman logs qtt`                                                                  |
| Follow logs      | `docker logs -f qtt`                                                               | `podman logs -f qtt`                                                               |
| Execute command  | `docker exec -it qtt bash`                                                         | `podman exec -it qtt bash`                                                         |
| Stop container   | `docker stop qtt`                                                                  | `podman stop qtt`                                                                  |
| Start container  | `docker start qtt`                                                                 | `podman start qtt`                                                                 |
| Remove container | `docker rm qtt`                                                                    | `podman rm qtt`                                                                    |
| Volume mount     | `docker run -v qtt-data:/opt/qtt/data ...`                                         | `podman run -v qtt-data:/opt/qtt/data:Z ...`                                       |
| Compose up       | `docker compose up -d`                                                             | `podman-compose up -d`                                                             |
| Compose down     | `docker compose down`                                                              | `podman-compose down`                                                              |
| Create pod       | N/A                                                                                | `podman pod create --name qtt-pod -p 8443:8443`                                    |
| Generate systemd | N/A                                                                                | `podman generate systemd --name qtt --files --new`                                 |

**Key Differences:**

- Podman requires `:Z` or `:z` for SELinux volume labels on RHEL/CentOS/Fedora
- Podman supports pods (Kubernetes-style container groups)
- Podman can generate systemd unit files
- Podman runs rootless by default
- Use `host.docker.internal` for host access in Docker; `host.containers.internal` in Podman

# Advanced Topics

This guide covers advanced deployment scenarios, database migration, high availability, and security hardening.

## Database Migration

### From Derby to PostgreSQL

**Step 1: Export Data from Derby**

```bash
# Use custom export script or JDBC dump
# (Implementation-specific)
```

**Step 2: Create PostgreSQL Database**

```sql
CREATE
DATABASE qtt;
CREATE
USER qttuser WITH PASSWORD 'SecurePassword123';
GRANT ALL PRIVILEGES ON DATABASE
qtt TO qttuser;
```

**Step 3: Update Configuration**

```bash
docker run -d \
  --name qtt \
  -e DB_DRIVER_NAME="PostgreSQL JDBC Driver" \
  -e DB_URL="jdbc:postgresql://db-host:5432/qtt" \
  -e DB_USER=qttuser \
  -e DB_PASSWORD=SecurePassword123 \
  ...
```

**Step 4: Import Data**

JPA will auto-create schema on first startup. Import data via:

- SQL INSERT statements
- Custom migration scripts
- Manual UI recreation (for small datasets)

## High Availability Setup

### Active-Passive Configuration

```yaml
services:
  postgresql-primary:
    image: postgres:18-alpine
    environment:
      POSTGRES_DB: qtt
      POSTGRES_USER: qttuser
      POSTGRES_PASSWORD: SecurePassword123
    volumes:
      - pg-primary-data:/var/lib/postgresql/data

  postgresql-standby:
    image: postgres:latest
    environment:
      POSTGRES_DB: qtt
      POSTGRES_USER: qttuser
      POSTGRES_PASSWORD: SecurePassword123
    volumes:
      - pg-standby-data:/var/lib/postgresql/data
    # Configure streaming replication

  qtt-primary:
    image: docker.io/inovexis/qtt:latest
    environment:
      DB_URL: "jdbc:postgresql://postgresql-primary:5432/qtt"
      #...
    ports:
      - "8443:8443"

  qtt-standby:
    image: docker.io/inovexis/qtt:latest
    environment:
      DB_URL: "jdbc:postgresql://postgresql-standby:5432/qtt"
      #...
    # Standby instance for failover
```

**Load Balancer Configuration:**

Use HAProxy or nginx to route traffic to active instance.

## Backup and Restore

### Database Backup

**PostgreSQL:**

```bash
# Backup
docker exec qtt-postgres pg_dump -U qttuser qtt > qtt-backup.sql

# Restore
cat qtt-backup.sql | docker exec -i qtt-postgres psql -U qttuser qtt
```

**SQL Server:**

```bash
# Backup
docker exec qtt-mssql /opt/mssql-tools/bin/sqlcmd \
  -S localhost -U sa -P password \
  -Q "BACKUP DATABASE qtt TO DISK='/var/opt/mssql/backup/qtt.bak'"

# Copy backup out
docker cp qtt-mssql:/var/opt/mssql/backup/qtt.bak ./qtt-backup.bak
```

### Template Backup

```bash
# Backup templates directory
docker cp qtt:/opt/qtt/data/templates ./templates-backup

# Restore
docker cp ./templates-backup qtt:/opt/qtt/data/templates
```

### Configuration Backup

QTT's configuration is packaged into the application and overridden with environment variables, so there are no
configuration files to back up inside the container. Keep your compose files or `docker run` settings (and any custom
keystore) under version control or in your secrets store instead.

### Full System Backup

```bash
#!/bin/bash
BACKUP_DIR=./qtt-backup-$(date +%Y%m%d-%H%M%S)
mkdir -p $BACKUP_DIR

# Backup database
docker exec qtt-postgres pg_dump -U qttuser qtt > $BACKUP_DIR/database.sql

# Backup templates
docker cp qtt:/opt/qtt/data/templates $BACKUP_DIR/templates

# Create archive
tar -czf $BACKUP_DIR.tar.gz $BACKUP_DIR
rm -rf $BACKUP_DIR

echo "Backup created: $BACKUP_DIR.tar.gz"
```

## Security Hardening

### 1. SSL/TLS Best Practices

- Use CA-signed certificates (not self-signed)
- Enforce TLS 1.2+ only
- Disable weak cipher suites

### 2. Network Segmentation

- Place QTT in private network
- Only expose necessary ports through reverse proxy
- Use firewall rules to restrict datasource access

**Example docker-compose with network isolation:**

```yaml
services:
  qtt:
    networks:
      - internal
      - external

  postgresql:
    networks:
      - internal

  nginx:
    networks:
      - external
    ports:
      - "443:443"

networks:
  internal:
    internal: true
  external:
```

### 3. Route Templates

Without sign-in, anyone who can reach the management API can save route templates, so turn on
[Single Sign-On](administration.md#single-sign-on-oidc) for shared deployments. Templates are rendered with
FreeMarker's safeguards for untrusted templates switched on: no `?new` or `?api`, no `<#include>`/`<#import>`, and no
Java methods on request objects (see [What Templates Cannot Do](template-development.md#what-templates-cannot-do)).
Templates never see a caller's `Authorization`, `Cookie` or proxy headers, so they can't pass credentials on to Anzo.

The query routes share ports 8443/8080 with the UI. To let applications reach only the query routes, route the path
`/query/` to them through your reverse proxy and keep `/` and `/queryrest/` internal
([Exposing only the query routes](administration.md#exposing-only-the-query-routes)), and close routes that should
not be public ([Securing the Query Endpoints](administration.md#securing-the-query-endpoints)).

### 4. Secrets Management

Keep secrets out of images and compose files and pass them to the container as environment variables at
start-up, for example from a Kubernetes Secret:

```yaml
env:
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: qtt-secrets
        key: db-password
  - name: SPARQI_LLM_API_KEY
    valueFrom:
      secretKeyRef:
        name: qtt-secrets
        key: sparqi-api-key
```

QTT reads these values from environment variables only; it does not support `*_FILE` variables that point to
secret files.

### 5. Least Privilege Database Access

Grant only required permissions:

```sql
-- PostgreSQL
CREATE
USER qttuser WITH PASSWORD 'SecurePassword123';
GRANT CONNECT
ON DATABASE qtt TO qttuser;
GRANT USAGE ON SCHEMA
public TO qttuser;
GRANT
SELECT,
INSERT
,
UPDATE,
DELETE
ON ALL TABLES IN SCHEMA public TO qttuser;
GRANT
USAGE
ON
ALL
SEQUENCES IN SCHEMA public TO qttuser;
```

### 6. Rate Limiting

Use nginx or HAProxy for rate limiting:

```nginx
http {
  limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;

  server {
    location /queryrest/api/ {
      limit_req zone=api_limit burst=20;
      proxy_pass http://qtt:8080;
    }
  }
}
```

## Cache Internals

### Cache Key Generation Strategy

Understanding how cache keys are generated helps optimize cache hit rates.

**Cache Key Format:**

```
{prefix}{routeId}:{hash}
```

**Example:**

```
qtt:cache:people-search:a3f8b9c2e1d4f6a8b1c3d5e7f9a0b2c4d6e8f0a2b4c6d8e0f2a4b6c8d0e2f4a6
```

**Components:**

- `prefix`: Configurable prefix (default: `qtt:cache:`), allows namespace separation
- `routeId`: The route identifier (e.g., `people-search`)
- `hash`: SHA-256 hash of query inputs (64 hex characters)

**Hash Input Components:**

The SHA-256 hash is computed from:

1. **SPARQL Query** (after Freemarker template processing)
2. **GraphMart URI** (target graph database)
3. **Layer URIs** (comma-separated list of layers)

**Why This Matters:**

Different parameter values create different hashes, even if the query result would be identical:

```bash
# Query 1: name=John
GET /people-search?name=John
# Cache key: qtt:cache:people-search:abc123...

# Query 2: name=john (different case)
GET /people-search?name=john
# Cache key: qtt:cache:people-search:def456...
# Different hash, cache MISS even if results would be identical
```

**Optimizing Cache Hit Rate:**

Normalize parameters in Freemarker template:

```freemarker
<#-- Normalize to lowercase for better cache hit rate -->
<#assign normalizedName = (name!"")?lower_case>

SELECT * WHERE {
  ?person foaf:name ?name .
  FILTER(LCASE(?name) = "${normalizedName}")
}
```

### Cache Invalidation Patterns

**Time-Based Invalidation (Automatic):**

```bash
# All cache entries expire after TTL
CACHE_DEFAULT_TTL=3600  # 1 hour
```

**Manual Invalidation:**

```bash
# Clear specific route cache after data update
curl -X DELETE "http://localhost:8080/queryrest/api/routes/people-search/cache"

# Clear all cache after bulk data import
curl -X DELETE "http://localhost:8080/queryrest/api/routes/cache"
```

### Fail-Open vs Fail-Closed Strategy

**Fail-Open (Default):** `CACHE_FAIL_OPEN=true`

```
Cache Error → Log warning → Continue without cache → Query executes normally

Pros:
✓ Service stays operational during Redis outages
✓ No user-facing errors
✓ Graceful degradation

Cons:
✗ Higher load on backend during Redis downtime
✗ May not notice cache failures immediately
```

**Fail-Closed:** `CACHE_FAIL_OPEN=false`

```
Cache Error → Throw exception → Return 500 error to client

Pros:
✓ Forces immediate attention to cache problems
✓ Prevents overwhelming backend with uncached traffic

Cons:
✗ Service unavailable during Redis outages
✗ User-facing errors
```

**Recommendation:**

- **Production**: Use fail-open for resilience
- **Development**: Use fail-closed to catch configuration issues early

## Useful Links

**Documentation:**

- Apache Camel: https://camel.apache.org/manual/
- Camel Quarkus: https://camel.apache.org/camel-quarkus/latest/
- Quarkus: https://quarkus.io/guides/
- Freemarker: https://freemarker.apache.org/docs/
- SPARQL: https://www.w3.org/TR/sparql11-query/
- JSON-LD: https://json-ld.org/

**LLM Providers:**

- OpenAI: https://platform.openai.com/
- Anthropic Claude: https://console.anthropic.com/
- LiteLLM: https://docs.litellm.ai/
- Azure OpenAI: https://azure.microsoft.com/en-us/products/ai-services/openai-service
- Ollama: https://ollama.ai/

**Tools:**

- Docker: https://docs.docker.com/
- Podman: https://podman.io/
- PostgreSQL: https://www.postgresql.org/docs/
- SQL Server: https://docs.microsoft.com/en-us/sql/

## Glossary

**Anzo**: Cambridge Semantics graph database platform

**Apache Camel**: Integration framework for routing and mediation

**Cache Aside Pattern**: Caching strategy where the application checks cache first, then queries database on miss and
updates cache

**Cache Eviction**: Removal of cache entries due to memory limits (LRU policy) or TTL expiration

**Cache Hit**: Request served from cache without querying the database

**Cache Hit Ratio**: Percentage of requests served from cache (hits / (hits + misses)). Target: > 70%

**CONSTRUCT**: SPARQL query form that builds RDF graphs

**DataSource**: Configuration for connecting to an Anzo instance

**Freemarker**: Java-based template engine

**GraphMart**: Anzo's logical data container

**JAX-RS**: Java API for RESTful web services

**JSON-LD**: JSON format for linked data

**Layer**: Subset of data within an Anzo GraphMart

**LRU (Least Recently Used)**: Cache eviction policy that removes least recently accessed entries

**Monaco Editor**: Code editor component (used by VS Code)

**Quarkus**: Java application framework that QTT runs on (via Camel Quarkus)

**RDF**: Resource Description Framework

**Redis**: In-memory data store used for query result caching

**Route**: Query endpoint with associated template

**SPARQL**: Query language for RDF graphs

**SPARQi**: AI assistant for SPARQL template development

**Template**: Freemarker file that generates SPARQL queries

**TTL (Time To Live)**: Duration that cache entries remain valid before expiration

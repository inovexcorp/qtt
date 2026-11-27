# Configuration Reference

This guide covers all configuration options for QTT.

## Environment Variables Reference

All configuration values can be overridden using environment variables. The application configuration
(`query-service-app/src/main/resources/application.properties`, packaged into the application) follows the pattern:
`${VARIABLE_NAME:default}`

### Core Database Configuration

This configuration controls the database connection for the persistence layer.

| Variable         | Default                                | Description            | Required |
|------------------|----------------------------------------|------------------------|----------|
| `DB_URL`         | `jdbc:derby:data/database;create=true` | JDBC connection string | Optional |
| `DB_USER`        | `user`                                 | Database username      | Optional |
| `DB_PASSWORD`    | `password`                             | Database password      | Optional |
| `QTT_DB_KIND`    | (detected from `DB_URL`)               | Database type override | Optional |
| `DB_DRIVER_NAME` | (none)                                 | Legacy; see below      | Optional |

**Database selection:** the database type is detected from the `DB_URL` prefix:

- `jdbc:derby:` - Apache Derby (embedded)
- `jdbc:postgresql:` - PostgreSQL
- `jdbc:sqlserver:` - SQL Server

`QTT_DB_KIND` (`derby`, `postgresql` or `mssql`) overrides the detection. `DB_DRIVER_NAME` no longer selects the
driver or sets the Settings page's database type (which shows the database actually in use); it is still accepted,
and only used to pick the database when neither `QTT_DB_KIND` nor `DB_URL` is set.

### API Configuration

This configuration controls the UI base URL when displaying the route endpoints.

| Variable       | Default                 | Description                                    |
|----------------|-------------------------|------------------------------------------------|
| `API_BASE_URL` | `http://localhost:8888` | Base URL for query endpoints exposed to the UI |

### SPARQi AI Assistant Configuration

| Variable                   | Default | Description                    | Required     |
|----------------------------|---------|--------------------------------|--------------|
| `SPARQI_ENABLED`           | `false` | Enable/disable SPARQi service  | Optional     |
| `SPARQI_LLM_BASE_URL`      | None    | OpenAI-compatible endpoint URL | Conditional* |
| `SPARQI_LLM_API_KEY`       | None    | API key for LLM provider       | Conditional* |
| `SPARQI_LLM_MODEL`         | None    | Model name (e.g., gpt-4o-mini) | Conditional* |
| `SPARQI_LLM_TIMEOUT`       | `90`    | Request timeout in seconds     | Optional     |
| `SPARQI_LLM_TEMPERATURE`   | `0.7`   | Model temperature (0.0-1.0)    | Optional     |
| `SPARQI_LLM_MAX_TOKENS`    | `4000`  | Maximum tokens per response    | Optional     |
| `SPARQI_SESSION_TIMEOUT`   | `60`    | Session timeout in minutes     | Optional     |
| `SPARQI_MAX_CONVO_HISTORY` | `50`    | Max conversation messages      | Optional     |

*Required if `SPARQI_ENABLED=true`

### Ontology Service Configuration

| Variable                   | Default | Description                     |
|----------------------------|---------|---------------------------------|
| `ONTO_CACHE_TTL`           | `60`    | Cache TTL in minutes            |
| `ONTO_CACHE_MAX_ENTRIES`   | `20`    | Max routes to cache             |
| `ONTO_CACHE_ENABLED`       | `true`  | Enable/disable caching          |
| `ONTO_CACHE_QUERY_TIMEOUT` | `30`    | SPARQL query timeout (seconds)  |
| `ONTO_CACHE_QUERY_LIMIT`   | `1000`  | Max ontology elements per query |

### Redis Cache Configuration

Configuration for Redis-backed query result caching to improve performance and reduce backend load.

| Variable                    | Default      | Description                               | Required     |
|-----------------------------|--------------|-------------------------------------------|--------------|
| `REDIS_ENABLED`             | `false`      | Enable/disable Redis caching globally     | Optional     |
| `REDIS_HOST`                | `localhost`  | Redis server hostname                     | Conditional* |
| `REDIS_PORT`                | `6379`       | Redis server port                         | Conditional* |
| `REDIS_PASSWORD`            | (empty)      | Redis authentication password             | Optional     |
| `REDIS_DATABASE`            | `0`          | Redis database number (0-15)              | Optional     |
| `REDIS_TIMEOUT`             | `5000`       | Connection timeout in milliseconds        | Optional     |
| `REDIS_POOL_MAX_TOTAL`      | `20`         | Maximum connections in pool               | Optional     |
| `REDIS_POOL_MAX_IDLE`       | `10`         | Maximum idle connections in pool          | Optional     |
| `REDIS_POOL_MIN_IDLE`       | `5`          | Minimum idle connections in pool          | Optional     |
| `CACHE_DEFAULT_TTL`         | `3600`       | Default cache TTL in seconds              | Optional     |
| `CACHE_KEY_PREFIX`          | `qtt:cache:` | Prefix for all cache keys                 | Optional     |
| `CACHE_COMPRESSION_ENABLED` | `true`       | Enable gzip compression for cached values | Optional     |
| `CACHE_FAIL_OPEN`           | `true`       | Continue on cache errors (vs fail closed) | Optional     |
| `CACHE_STATS_ENABLED`       | `true`       | Track cache statistics                    | Optional     |
| `CACHE_STATS_TTL`           | `5`          | Cache statistics TTL in seconds           | Optional     |

*Required if `REDIS_ENABLED=true`

### Health Check Configuration

| Variable                         | Default         | Description                                      | Required |
|----------------------------------|-----------------|--------------------------------------------------|----------|
| `HEALTH_CHECK_ENABLED`           | `true`          | Enable/disable health checks globally            | Optional |
| `HEALTH_CHECK_INTERVAL_CRON`     | `0 0/2 * * * ?` | Cron expression for health check interval        | Optional |
| `HEALTH_CHECK_FAILURE_THRESHOLD` | `-1`            | Consecutive failures before auto-stopping routes | Optional |

### Password Encryption Configuration

Configuration for AES-256-GCM encryption of datasource passwords stored in the database.

| Variable                       | Default             | Description                                      | Required     |
|--------------------------------|---------------------|--------------------------------------------------|--------------|
| `PASSWORD_ENCRYPTION_ENABLED`  | `false`             | Enable/disable password encryption globally      | Optional     |
| `PASSWORD_ENCRYPTION_KEY`      | (empty)             | Base encryption key (32+ characters recommended) | Conditional* |
| `PASSWORD_ENCRYPTION_SALT`     | (empty)             | Salt for key derivation (16+ characters)         | Conditional* |
| `ENCRYPTION_PBKDF2_ITERATIONS` | `65536`             | PBKDF2 iteration count for key derivation        | Optional     |
| `ENCRYPTION_ALGORITHM`         | `AES/GCM/NoPadding` | Cipher algorithm specification                   | Optional     |
| `ENCRYPTION_KEY_LENGTH`        | `256`               | Key length in bits (128, 192, or 256)            | Optional     |
| `ENCRYPTION_GCM_TAG_LENGTH`    | `128`               | GCM authentication tag length in bits            | Optional     |
| `ENCRYPTION_GCM_IV_LENGTH`     | `12`                | GCM IV length in bytes                           | Optional     |
| `ENCRYPTION_FAIL_ON_ERROR`     | `false`             | Fail operations on encryption/decryption errors  | Optional     |

*Required if `PASSWORD_ENCRYPTION_ENABLED=true`

**Security Recommendations:**

- **Generate Strong Keys**: Use cryptographically secure random data for key and salt:
  ```bash
  export PASSWORD_ENCRYPTION_KEY=$(openssl rand -base64 32)
  export PASSWORD_ENCRYPTION_SALT=$(openssl rand -base64 16)
  ```
- **Environment Variables Only**: Never commit encryption keys to version control
- **Key Rotation**: There is no automatic re-encryption with a new key. After changing the key or salt, re-enter the
  password of each datasource (its configuration page, or `PUT /api/datasources` with the password).

**Behaviour:**

- Passwords are encrypted on every write to the database and decrypted when read; the REST API never returns them.
- At startup, passwords stored in plain text (created before encryption was enabled, or rewritten in plain text by
  earlier builds, which did that on the first health check) are encrypted.
- Values that look encrypted but do not decrypt with the configured key are left unchanged and reported in the log,
  so starting once with a wrong key does no damage: restart with the right key.

## Application Properties

All other settings live in `query-service-app/src/main/resources/application.properties`, which is packaged into the
application. Property names keep the prefixes of the former `etc/*.cfg` files (for example,
`com.inovexcorp.queryservice.cache.redis.enabled=${REDIS_ENABLED:false}`).

Any property can be overridden without rebuilding:

- **Java system property**: `-Dcom.inovexcorp.queryservice.scheduler.CleanMetrics.minutesToLive=60`
- **Environment variable**: Quarkus maps a property name to an environment variable by replacing every character
  that is not a letter or digit with `_` and upper-casing it, e.g.
  `COM_INOVEXCORP_QUERYSERVICE_SCHEDULER_CLEANMETRICS_MINUTESTOLIVE=60`

Configuration is read at startup; changes take effect after a restart.

### Core Service Configuration

```properties
# Freemarker template directory (relative to the working directory, /opt/qtt in containers)
com.inovexcorp.queryservice.routebuilder.templateLocation=data/templates/
# API base URL exposed to UI
com.inovexcorp.queryservice.routebuilder.querycontrollers.RoutesController.baseUrl=${API_BASE_URL:http://localhost:8888}
```

### Database Configuration

One datasource is configured per supported database; the one matching `DB_URL` (or `QTT_DB_KIND`) is activated at
startup. For example, PostgreSQL:

```properties
qtt.db.url=${DB_URL:jdbc:derby:data/database;create=true}
quarkus.datasource.postgresql.jdbc.url=${qtt.db.url}
quarkus.datasource.postgresql.username=${DB_USER:user}
quarkus.datasource.postgresql.password=${DB_PASSWORD:password}
# Connection pool size
quarkus.datasource.postgresql.jdbc.max-size=10
```

### Scheduler Configuration

```properties
# Collect metrics every minute
com.inovexcorp.queryservice.scheduler.QueryMetrics.scheduler.expression=0 0/1 * * * ?

# Clean old metrics every minute, keeping metrics for 30 minutes
com.inovexcorp.queryservice.scheduler.CleanMetrics.scheduler.expression=0 0/1 * * * ?
com.inovexcorp.queryservice.scheduler.CleanMetrics.minutesToLive=30

# Datasource health checks
com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.enabled=${HEALTH_CHECK_ENABLED:true}
com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.scheduler.expression=${HEALTH_CHECK_INTERVAL_CRON:0 0/2 * * * ?}
# Auto-stop routes after N consecutive failures (-1 = disabled)
com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.consecutiveFailureThreshold=${HEALTH_CHECK_FAILURE_THRESHOLD:-1}

# Clean old health records daily at midnight, keeping them for 7 days
com.inovexcorp.queryservice.scheduler.CleanHealthRecords.scheduler.expression=0 0 0 * * ?
com.inovexcorp.queryservice.scheduler.CleanHealthRecords.daysToLive=7
```

### SPARQi Configuration

```properties
# Enable/disable SPARQi
com.inovexcorp.queryservice.sparqi.enableSparqi=${SPARQI_ENABLED:false}
# LLM provider configuration
com.inovexcorp.queryservice.sparqi.llmBaseUrl=${SPARQI_LLM_BASE_URL:}
com.inovexcorp.queryservice.sparqi.llmApiKey=${SPARQI_LLM_API_KEY:}
com.inovexcorp.queryservice.sparqi.llmModelName=${SPARQI_LLM_MODEL:}
# Performance tuning
com.inovexcorp.queryservice.sparqi.llmTimeout=${SPARQI_LLM_TIMEOUT:90}
com.inovexcorp.queryservice.sparqi.llmTemperature=${SPARQI_LLM_TEMPERATURE:0.7}
com.inovexcorp.queryservice.sparqi.llmMaxTokens=${SPARQI_LLM_MAX_TOKENS:4000}
# Session management
com.inovexcorp.queryservice.sparqi.sessionTimeoutMinutes=${SPARQI_SESSION_TIMEOUT:60}
com.inovexcorp.queryservice.sparqi.maxConversationHistory=${SPARQI_MAX_CONVO_HISTORY:50}
```

### Cache Configuration

```properties
# Redis Connection Settings
com.inovexcorp.queryservice.cache.redis.enabled=${REDIS_ENABLED:false}
com.inovexcorp.queryservice.cache.redis.host=${REDIS_HOST:localhost}
com.inovexcorp.queryservice.cache.redis.port=${REDIS_PORT:6379}
com.inovexcorp.queryservice.cache.redis.password=${REDIS_PASSWORD:}
com.inovexcorp.queryservice.cache.redis.database=${REDIS_DATABASE:0}
com.inovexcorp.queryservice.cache.redis.timeout=${REDIS_TIMEOUT:5000}
# Redis Connection Pool Settings
com.inovexcorp.queryservice.cache.redis.pool.maxTotal=${REDIS_POOL_MAX_TOTAL:20}
com.inovexcorp.queryservice.cache.redis.pool.maxIdle=${REDIS_POOL_MAX_IDLE:10}
com.inovexcorp.queryservice.cache.redis.pool.minIdle=${REDIS_POOL_MIN_IDLE:5}
# Global Cache Settings
com.inovexcorp.queryservice.cache.cache.keyPrefix=${CACHE_KEY_PREFIX:qtt:cache:}
com.inovexcorp.queryservice.cache.cache.defaultTtlSeconds=${CACHE_DEFAULT_TTL:3600}
com.inovexcorp.queryservice.cache.cache.compressionEnabled=${CACHE_COMPRESSION_ENABLED:true}
# Cache Behavior
com.inovexcorp.queryservice.cache.cache.failOpen=${CACHE_FAIL_OPEN:true}
com.inovexcorp.queryservice.cache.cache.statsEnabled=${CACHE_STATS_ENABLED:true}
com.inovexcorp.queryservice.cache.cache.statsTtlSeconds=${CACHE_STATS_TTL:5}
```

### Web Server Configuration

```properties
# Plain HTTP and HTTPS ports for the UI and REST API
quarkus.http.port=8080
quarkus.http.ssl-port=8443
# SSL/TLS settings
quarkus.http.ssl.certificate.key-store-file=${KEYSTORE:etc/keystore}
quarkus.http.ssl.certificate.key-store-password=${PASSWORD:p@ssword1}
quarkus.http.ssl.certificate.key-store-file-type=JKS
```

`KEYSTORE` is the path of a JKS keystore (default `etc/keystore`, i.e. `/opt/qtt/etc/keystore` in containers) and
`PASSWORD` its password.

**WARNING**: Default SSL passwords are insecure and MUST be changed for production deployments.

### Logging Configuration

QTT logs to the console (in containers: `docker logs qtt` / `podman logs qtt`). Defaults:

```properties
# Root logger level
quarkus.log.level=INFO
quarkus.log.category."org.hibernate".level=WARN
```

Change levels with environment variables, either globally or per category:

```bash
QUARKUS_LOG_LEVEL=DEBUG
QUARKUS_LOG_CATEGORY__COM_INOVEXCORP__LEVEL=DEBUG
```

or with system properties, e.g. `-Dquarkus.log.category."com.inovexcorp".level=DEBUG`. Per-query log lines (query
duration, cache hits) are logged at DEBUG.

## Database Backend Configuration

### Derby (Default - Development Only)

**Pros:**

- Zero configuration
- Embedded (no separate database server)
- Automatic schema creation

**Cons:**

- Single-user mode
- File locking issues in containers
- Not suitable for production
- Container loss will lose your data

**Configuration:**

```bash
DB_DRIVER_NAME=derby
DB_URL=jdbc:derby:data/database;create=true
DB_USER=user
DB_PASSWORD=password
```

### PostgreSQL (Recommended for Production)

**Pros:**

- Multi-user, concurrent access
- Excellent performance
- Open source
- Wide adoption

**Setup:**

1. Create database and user:

```sql
CREATE
DATABASE qtt;
CREATE
USER qttuser WITH PASSWORD 'SecurePassword123';
GRANT ALL PRIVILEGES ON DATABASE
qtt TO qttuser;
```

2. Configure QTT:

```bash
DB_DRIVER_NAME=PostgreSQL JDBC Driver
DB_URL=jdbc:postgresql://db-host:5432/qtt
DB_USER=qttuser
DB_PASSWORD=SecurePassword123
```

### SQL Server (Enterprise Environments)

**Pros:**

- Enterprise-grade features
- Windows authentication support
- High availability options

**Setup:**

1. Create database and login:

```sql
CREATE
DATABASE qtt;
CREATE
LOGIN qttuser WITH PASSWORD = 'SecurePassword123';
USE
qtt;
CREATE
USER qttuser FOR LOGIN qttuser;
GRANT CONTROL
ON DATABASE::qtt TO qttuser;
```

2. Configure QTT:

```bash
DB_DRIVER_NAME=Microsoft JDBC Driver for SQL Server
DB_URL=jdbc:sqlserver://db-host:1433;databaseName=qtt;encrypt=true;trustServerCertificate=true
DB_USER=qttuser
DB_PASSWORD=SecurePassword123
```

On SQL Server, a database created by QTT 1.x stores templates in a `varchar(max)` column; QTT converts it to
`nvarchar(max)` automatically at first start so non-Latin template text is preserved. Text that was already stored as
`?` cannot be recovered.

## Security Configuration

### Changing Default SSL Passwords

**CRITICAL**: The default keystore password is `p@ssword1` and MUST be changed for production.

1. Generate a new keystore:

```bash
keytool -genkey \
  -keyalg RSA \
  -alias qtt \
  -keystore /path/to/keystore.jks \
  -storepass YourStrongPassword \
  -validity 365 \
  -keysize 2048
```

2. Mount and configure:

```bash
docker run -d \
  --name qtt \
  -p 8443:8443 \
  -p 8888:8888 \
  -v /path/to/keystore.jks:/opt/qtt/etc/custom-keystore:ro \
  -e KEYSTORE=/opt/qtt/etc/custom-keystore \
  -e PASSWORD=YourStrongPassword \
  docker.io/inovexis/qtt:latest
```

### Database Credential Management

**Never hardcode credentials in configuration files.** Always use environment variables:

```bash
# Read the secret from a file at start-up and pass it as an environment variable
# (QTT does not support *_FILE variables)
docker run -d \
  --name qtt \
  -e DB_PASSWORD="$(cat ./secrets/db_password)" \
  docker.io/inovexis/qtt:latest
```

## Scheduler Cron Expressions

| Scheduler               | Default Expression | Description                  | Configurable |
|-------------------------|--------------------|------------------------------|--------------|
| Query Metrics           | `0 0/1 * * * ?`    | Every 1 minute               | Yes          |
| Clean Metrics           | `0 0/1 * * * ?`    | Every 1 minute               | Yes          |
| Datasource Health Check | `0 0/2 * * * ?`    | Every 2 minutes (configurable) | Yes          |
| Clean Health Records    | `0 0 0 * * ?`      | Daily at midnight            | Yes          |

**Cron Format:** `second minute hour day month weekday`

**Examples:**

- Every 5 minutes: `0 0/5 * * * ?`
- Every hour: `0 0 * * * ?`
- Every day at 2 AM: `0 0 2 * * ?`
- Every Monday at 8 AM: `0 0 8 ? * MON`

## Supported Database Drivers

| Database     | `QTT_DB_KIND` | JDBC URL Format                                    | Default Port   |
|--------------|---------------|----------------------------------------------------|----------------|
| Apache Derby | `derby`       | `jdbc:derby:data/database;create=true`             | N/A (embedded) |
| PostgreSQL   | `postgresql`  | `jdbc:postgresql://host:port/database`             | 5432           |
| SQL Server   | `mssql`       | `jdbc:sqlserver://host:port;databaseName=database` | 1433           |

**Driver Versions (bundled):**

- Derby: 10.16.1.1 (opens databases created by Derby 10.14 in soft-upgrade mode)
- PostgreSQL: 42.7.13
- SQL Server: 13.4.0.jre11

## Port Reference

| Port  | Protocol | Service          | Purpose                     | Exposed by Default |
|-------|----------|------------------|-----------------------------|--------------------|
| 8443  | HTTPS    | Quarkus HTTP     | REST API and Web UI         | Yes                |
| 8080  | HTTP     | Quarkus HTTP     | REST API and Web UI (plain) | Yes                |
| 8888  | HTTP     | Camel Netty HTTP | Dynamic query endpoints     | Yes                |
| 5005  | TCP      | JVM Debug        | Remote debugging (dev mode) | No                 |

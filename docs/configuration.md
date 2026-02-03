# Configuration Reference

This guide covers all configuration options for QTT.

## Environment Variables Reference

All configuration values can be overridden using environment variables. The system follows the pattern:
`$[env:VARIABLE_NAME;default=value]`

### Core Database Configuration

This configuration controls the database connection for the persistence layer.

| Variable         | Default                                | Description            | Required |
|------------------|----------------------------------------|------------------------|----------|
| `DB_DRIVER_NAME` | `derby`                                | JDBC driver identifier | Optional |
| `DB_URL`         | `jdbc:derby:data/database;create=true` | JDBC connection string | Optional |
| `DB_USER`        | `user`                                 | Database username      | Optional |
| `DB_PASSWORD`    | `password`                             | Database password      | Optional |

**Supported `DB_DRIVER_NAME` values:**

- `derby` - Apache Derby (embedded)
- `PostgreSQL JDBC Driver` - PostgreSQL
- `Microsoft JDBC Driver for SQL Server` - SQL Server

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
- **Key Rotation**: Periodically rotate encryption keys (requires re-encrypting all stored passwords)

## Configuration Files in Distribution

All configuration files are located in `/opt/qtt/etc/` (in containers) or
`query-service-distribution/src/main/resources/etc/` (in source).

### Core Service Configuration

**`com.inovexcorp.queryservice.routebuilder.cfg`**

```properties
# Freemarker template directory (relative to Karaf home)
templateLocation=data/templates/
```

**`com.inovexcorp.queryservice.routebuilder.querycontrollers.RoutesController.cfg`**

```properties
# API base URL exposed to UI
baseUrl=$[env:API_BASE_URL;default=http://localhost:8888]
```

### Database Configuration

**`org.ops4j.datasource-qtt.cfg`**

```properties
osgi.jdbc.driver.name=$[env:DB_DRIVER_NAME;default=derby]
url=$[env:DB_URL;default=jdbc:derby:data/database;create=true]
user=$[env:DB_USER;default=user]
password=$[env:DB_PASSWORD;default=password]
# Connection pool settings
jdbc.pool.maxTotal=10
jdbc.pool.maxIdle=10
jdbc.pool.minIdle=10
jdbc.factory.maxConnLifetimeMillis=-1
pool=dbcp2
xa=false
dataSourceName=qtt-ds
```

### Scheduler Configuration

**`com.inovexcorp.queryservice.scheduler.QueryMetrics.cfg`** - Schedule for metrics collection

```properties
# Collect metrics every minute
scheduler.expression=0 0/1 * * * ?
scheduler.concurrent=false
```

**`com.inovexcorp.queryservice.scheduler.CleanMetrics.cfg`** - Schedule for metrics cleanup

```properties
# Clean old metrics every minute
scheduler.expression=0 0/1 * * * ?
scheduler.concurrent=false
# Keep metrics for 30 minutes
minutesToLive=30
```

**`com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.cfg`** - Schedule for datasource health checks

```properties
# Enable/disable health checks globally
enabled=$[env:HEALTH_CHECK_ENABLED;default=true]
# Cron expression for health check interval (default: every 2 hours)
scheduler.expression=$[env:HEALTH_CHECK_INTERVAL_CRON;default=0 0/2 * * * ?]
scheduler.concurrent=false
# Auto-stop routes after N consecutive failures (-1 = disabled)
consecutiveFailureThreshold=$[env:HEALTH_CHECK_FAILURE_THRESHOLD;default=-1]
```

**`com.inovexcorp.queryservice.scheduler.CleanHealthRecords.cfg`** - Schedule for health record cleanup

```properties
# Clean old health records daily at midnight
scheduler.expression=0 0 0 * * ?
scheduler.concurrent=false
# Keep health records for 7 days
daysToLive=7
```

### SPARQi Configuration

**`com.inovexcorp.queryservice.sparqi.cfg`**

```properties
# Enable/disable SPARQi
enableSparqi=$[env:SPARQI_ENABLED;default=false]
# LLM provider configuration
llmBaseUrl=$[env:SPARQI_LLM_BASE_URL]
llmApiKey=$[env:SPARQI_LLM_API_KEY]
llmModelName=$[env:SPARQI_LLM_MODEL]
# Performance tuning
llmTimeout=$[env:SPARQI_LLM_TIMEOUT;default=90]
llmTemperature=$[env:SPARQI_LLM_TEMPERATURE;default=0.7]
llmMaxTokens=$[env:SPARQI_LLM_MAX_TOKENS;default=4000]
# Session management
sessionTimeoutMinutes=$[env:SPARQI_SESSION_TIMEOUT;default=60]
maxConversationHistory=$[env:SPARQI_MAX_CONVO_HISTORY;default=50]
```

### Cache Configuration

**`com.inovexcorp.queryservice.cache.cfg`**

```properties
# Redis Connection Settings
redis.enabled=$[env:REDIS_ENABLED;default=false]
redis.host=$[env:REDIS_HOST;default=localhost]
redis.port=$[env:REDIS_PORT;default=6379]
redis.password=$[env:REDIS_PASSWORD;default=]
redis.database=$[env:REDIS_DATABASE;default=0]
redis.timeout=$[env:REDIS_TIMEOUT;default=5000]
# Redis Connection Pool Settings
redis.pool.maxTotal=$[env:REDIS_POOL_MAX_TOTAL;default=20]
redis.pool.maxIdle=$[env:REDIS_POOL_MAX_IDLE;default=10]
redis.pool.minIdle=$[env:REDIS_POOL_MIN_IDLE;default=5]
# Global Cache Settings
cache.keyPrefix=$[env:CACHE_KEY_PREFIX;default=qtt:cache:]
cache.defaultTtlSeconds=$[env:CACHE_DEFAULT_TTL;default=3600]
cache.compressionEnabled=$[env:CACHE_COMPRESSION_ENABLED;default=true]
# Cache Behavior
cache.failOpen=$[env:CACHE_FAIL_OPEN;default=true]
cache.statsEnabled=$[env:CACHE_STATS_ENABLED;default=true]
cache.statsTtlSeconds=$[env:CACHE_STATS_TTL;default=5]
```

### Web Server Configuration

**`org.ops4j.pax.web.cfg`**

```properties
# HTTPS configuration
org.osgi.service.http.secure.enabled=true
org.osgi.service.http.port.secure=8080
# SSL/TLS settings
org.ops4j.pax.web.ssl.keystore=${karaf.etc}/keystore
org.ops4j.pax.web.ssl.keystore.password=p@ssword1
org.ops4j.pax.web.ssl.key.password=p@ssword1
org.ops4j.pax.web.ssl.keystore.type=JKS
org.ops4j.pax.web.ssl.clientauthneeded=false
# Jetty thread pool
org.ops4j.pax.web.server.maxThreads=200
org.ops4j.pax.web.server.minThreads=10
org.ops4j.pax.web.server.idleTimeout=30000
```

**WARNING**: Default SSL passwords are insecure and MUST be changed for production deployments.

### Logging Configuration

**`org.ops4j.pax.logging.cfg`**

```properties
# Root logger level
log4j2.rootLogger.level=INFO
# Log file location
log4j2.appender.rolling.fileName=${karaf.log}/karaf.log
log4j2.appender.rolling.filePattern=${karaf.log}/karaf-%i.log
# Rolling policy
log4j2.appender.rolling.policies.size.size=16MB
# Component-specific levels
log4j2.logger.camel.level=INFO
log4j2.logger.camel.name=org.apache.camel
log4j2.logger.queryservice.level=INFO
log4j2.logger.queryservice.name=com.inovexcorp.queryservice
```

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
  -p 8080:8080 \
  -p 8888:8888 \
  -v /path/to/keystore.jks:/opt/qtt/etc/custom-keystore:ro \
  -e KEYSTORE=/opt/qtt/etc/custom-keystore \
  -e PASSWORD=YourStrongPassword \
  docker.io/inovexis/qtt:latest
```

### Database Credential Management

**Never hardcode credentials in configuration files.** Always use environment variables:

```bash
# Use environment variables
export DB_PASSWORD=$(cat /run/secrets/db_password)

# Or Docker secrets
docker run -d \
  --name qtt \
  --secret db_password \
  -e DB_PASSWORD_FILE=/run/secrets/db_password \
  docker.io/inovexis/qtt:latest
```

## Scheduler Cron Expressions

| Scheduler               | Default Expression | Description                  | Configurable |
|-------------------------|--------------------|------------------------------|--------------|
| Query Metrics           | `0 0/1 * * * ?`    | Every 1 minute               | Yes          |
| Clean Metrics           | `0 0/1 * * * ?`    | Every 1 minute               | Yes          |
| Datasource Health Check | `0 0/2 * * * ?`    | Every 2 hours (configurable) | Yes          |
| Clean Health Records    | `0 0 0 * * ?`      | Daily at midnight            | Yes          |

**Cron Format:** `second minute hour day month weekday`

**Examples:**

- Every 5 minutes: `0 0/5 * * * ?`
- Every hour: `0 0 * * * ?`
- Every day at 2 AM: `0 0 2 * * ?`
- Every Monday at 8 AM: `0 0 8 ? * MON`

## Supported Database Drivers

| Database     | Driver Name                            | JDBC URL Format                                    | Default Port   |
|--------------|----------------------------------------|----------------------------------------------------|----------------|
| Apache Derby | `derby`                                | `jdbc:derby:data/database;create=true`             | N/A (embedded) |
| PostgreSQL   | `PostgreSQL JDBC Driver`               | `jdbc:postgresql://host:port/database`             | 5432           |
| SQL Server   | `Microsoft JDBC Driver for SQL Server` | `jdbc:sqlserver://host:port;databaseName=database` | 1433           |

**Driver Versions (bundled):**

- Derby: 10.14.2.0
- PostgreSQL: 42.5.0
- SQL Server: 9.4.1.jre11

## Port Reference

| Port  | Protocol | Service          | Purpose                 | Exposed by Default |
|-------|----------|------------------|-------------------------|--------------------|
| 8080  | HTTP     | PAX Web          | JAX-RS API and Web UI   | Yes                |
| 8888  | HTTP     | Camel Jetty      | Dynamic query endpoints | Yes                |
| 5005  | TCP      | JVM Debug        | Remote debugging        | No                 |
| 1099  | TCP      | JMX RMI Registry | Management              | No                 |
| 44444 | TCP      | JMX RMI Server   | Management              | No                 |

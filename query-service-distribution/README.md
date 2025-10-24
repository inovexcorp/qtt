# Query Service Distribution

This module produces the complete Apache Karaf distribution for the Query Templating Tool (QTT) application. It packages all OSGi bundles, configuration files, and runtime dependencies into a ready-to-run Karaf instance.

## Overview

The distribution uses Maven's `karaf-assembly` packaging to create a standalone Karaf 4.4.1 runtime with:

- Pre-installed OSGi bundles for all Query Service modules
- Pre-configured settings for database, caching, schedulers, and services
- Custom Karaf branding with QTT welcome screen
- Docker image build capability
- JVM runtime optimizations

**Build Output Location**: `query-service-distribution/target/assembly/`

## Building the Distribution

### Maven Build Commands

```bash
# Build entire project including distribution
mvn clean install

# Build from parent directory (recommended)
cd /path/to/query-service
make build_and_run

# Build distribution module only (after building dependencies)
cd query-service-distribution
mvn clean install
```

### What Happens During Build

1. **Dependency Resolution**: Downloads all required Karaf features, OSGi bundles, and dependencies
2. **Assembly Creation**: Creates complete Karaf directory structure in `target/assembly/`
3. **Configuration Processing**: Copies and filters configuration files from `src/main/resources/`
4. **Docker Image**: Builds Docker image `inovexis/qtt` with version tags
5. **Feature Installation**: Pre-installs configured features into `system/` directory

**Build Time**: ~2-5 minutes (first build), ~30 seconds (subsequent builds without web module)

### Output Directory Structure

```
target/assembly/
├── bin/              # Karaf startup scripts (karaf, start, stop, client)
├── data/             # Runtime data (derby database, templates, caches)
├── deploy/           # Hot-deploy directory for bundles
├── etc/              # Configuration files
├── lib/              # Boot classpath libraries
├── system/           # OSGi bundle repository
├── Dockerfile        # Container image definition
└── run.sh            # Container startup script
```

## Source Directory Structure

```
src/
├── main/
│   ├── filtered-resources/     # Maven-filtered files (version substitution)
│   │   ├── Dockerfile          # OpenJDK 17 based container image
│   │   └── run.sh              # Container entrypoint script
│   └── resources/
│       ├── bin/
│       │   └── setenv          # JVM configuration (heap size, stack size)
│       └── etc/                # Application configuration files
│           ├── *.cfg           # OSGi Config Admin configuration
│           ├── keystore        # SSL/TLS certificate
│           └── branding.properties  # Karaf shell customization
└── test/
    └── resources/              # Test Freemarker templates
```

## Configuration Files Reference

All configuration files use OSGi Config Admin format (`key=value`) and support environment variable substitution via `$[env:VAR_NAME;default=value]` syntax.

### Core Application Configuration

| File                                                                             | Purpose                      | Key Settings                                   |
|----------------------------------------------------------------------------------|------------------------------|------------------------------------------------|
| `org.ops4j.datasource-qtt.cfg`                                                   | Database connection pooling  | JDBC URL, driver, credentials, pool size       |
| `com.inovexcorp.queryservice.routebuilder.cfg`                                   | Freemarker template location | `templateLocation` (relative to `$KARAF_HOME`) |
| `com.inovexcorp.queryservice.routebuilder.querycontrollers.RoutesController.cfg` | API base URL                 | `baseUrl` for route endpoint generation        |
| `com.inovexcorp.queryservice.jsonldSerializer.cfg`                               | JSON-LD output format        | Base URI, compaction mode, optimization flags  |

### Redis Cache Configuration

**File**: `com.inovexcorp.queryservice.cache.cfg`

| Setting                    | Environment Variable        | Default      | Description                           |
|----------------------------|-----------------------------|--------------|---------------------------------------|
| `redis.enabled`            | `REDIS_ENABLED`             | `false`      | Enable/disable Redis caching globally |
| `redis.host`               | `REDIS_HOST`                | `localhost`  | Redis server hostname                 |
| `redis.port`               | `REDIS_PORT`                | `6379`       | Redis server port                     |
| `redis.password`           | `REDIS_PASSWORD`            | _(empty)_    | Redis authentication password         |
| `redis.database`           | `REDIS_DATABASE`            | `0`          | Redis database number (0-15)          |
| `redis.timeout`            | `REDIS_TIMEOUT`             | `5000`       | Connection timeout (milliseconds)     |
| `redis.pool.maxTotal`      | `REDIS_POOL_MAX_TOTAL`      | `20`         | Maximum connections in pool           |
| `redis.pool.maxIdle`       | `REDIS_POOL_MAX_IDLE`       | `10`         | Maximum idle connections              |
| `redis.pool.minIdle`       | `REDIS_POOL_MIN_IDLE`       | `5`          | Minimum idle connections              |
| `cache.keyPrefix`          | `CACHE_KEY_PREFIX`          | `qtt:cache:` | Prefix for all cache keys             |
| `cache.defaultTtlSeconds`  | `CACHE_DEFAULT_TTL`         | `3600`       | Default cache TTL (1 hour)            |
| `cache.compressionEnabled` | `CACHE_COMPRESSION_ENABLED` | `true`       | Enable gzip compression               |
| `cache.failOpen`           | `CACHE_FAIL_OPEN`           | `true`       | Continue on cache errors vs fail      |
| `cache.statsEnabled`       | `CACHE_STATS_ENABLED`       | `true`       | Track hit/miss statistics             |

### Scheduler Configuration

All schedulers use Quartz cron expressions in `scheduler.expression` property.

| File                                                              | Purpose                           | Default Schedule  | Retention  |
|-------------------------------------------------------------------|-----------------------------------|-------------------|------------|
| `com.inovexcorp.queryservice.scheduler.QueryMetrics.cfg`          | Collect route performance metrics | Every 1 minute    | N/A        |
| `com.inovexcorp.queryservice.scheduler.CleanMetrics.cfg`          | Purge old query metrics           | Every 1 minute    | 30 minutes |
| `com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.cfg` | Check Anzo backend health         | Every 30 seconds  | 7 days     |
| `com.inovexcorp.queryservice.scheduler.CleanHealthRecords.cfg`    | Purge old health records          | Daily at midnight | 7 days     |
| `com.inovexcorp.queryservice.scheduler.CleanSparqiMetrics.cfg`    | Purge old SPARQi metrics          | Daily at 2:00 AM  | 7 days     |

**Cron Expression Format**: `second minute hour day month weekday`

### Ontology Service Configuration

**File**: `com.inovexcorp.queryservice.ontology.cfg`

| Setting                | Environment Variable       | Default | Description                         |
|------------------------|----------------------------|---------|-------------------------------------|
| `cacheEnable`          | `ONTO_CACHE_ENABLED`       | `true`  | Enable ontology data caching        |
| `cacheTtlMinutes`      | `ONTO_CACHE_TTL`           | `60`    | Cache time-to-live (minutes)        |
| `cacheMaxEntries`      | `ONTO_CACHE_MAX_ENTRIES`   | `20`    | Maximum cached routes               |
| `ontologyQueryTimeout` | `ONTO_CACHE_QUERY_TIMEOUT` | `30`    | SPARQL query timeout (seconds)      |
| `ontologyMaxResults`   | `ONTO_CACHE_QUERY_LIMIT`   | `1000`  | Maximum ontology elements per query |

### SPARQi AI Assistant Configuration

**File**: `com.inovexcorp.queryservice.sparqi.cfg`

SPARQi provides AI-powered SPARQL template assistance using LangChain4j with OpenAI-compatible LLM endpoints.

#### LLM Connection Settings

| Setting          | Environment Variable     | Description                                                     |
|------------------|--------------------------|-----------------------------------------------------------------|
| `llmBaseUrl`     | `SPARQI_LLM_BASE_URL`    | LLM API endpoint (OpenAI, LiteLLM, Azure, etc.)                 |
| `llmApiKey`      | `SPARQI_LLM_API_KEY`     | API authentication key                                          |
| `llmModelName`   | `SPARQI_LLM_MODEL`       | Model identifier (e.g., `gpt-4o`, `claude-3-5-sonnet-20241022`) |
| `llmTimeout`     | `SPARQI_LLM_TIMEOUT`     | Request timeout in seconds (default: 90)                        |
| `llmTemperature` | `SPARQI_LLM_TEMPERATURE` | Response creativity 0.0-1.0 (default: 0.7)                      |
| `llmMaxTokens`   | `SPARQI_LLM_MAX_TOKENS`  | Maximum response length (default: 4000)                         |

#### Session and Behavior Settings

| Setting                  | Environment Variable       | Default | Description                    |
|--------------------------|----------------------------|---------|--------------------------------|
| `enableSparqi`           | `SPARQI_ENABLED`           | `false` | Enable/disable SPARQi service  |
| `sessionTimeoutMinutes`  | `SPARQI_SESSION_TIMEOUT`   | `60`    | Inactive session cleanup time  |
| `maxConversationHistory` | `SPARQI_MAX_CONVO_HISTORY` | `50`    | Messages retained per session  |
| `metricsEnabled`         | `SPARQI_METRICS_ENABLED`   | `true`  | Track token usage and costs    |
| `inputTokenCostPer1M`    | `SPARQI_INPUT_TOKEN_COST`  | `2.50`  | Input token cost (USD per 1M)  |
| `outputTokenCostPer1M`   | `SPARQI_OUTPUT_TOKEN_COST` | `10.00` | Output token cost (USD per 1M) |

**Supported LLM Providers**:
- OpenAI: `https://api.openai.com/v1`
- LiteLLM Gateway: `http://localhost:4000` (proxies Anthropic, Google, etc.)
- Azure OpenAI: `https://<resource>.openai.azure.com/openai/deployments/<deployment-id>`
- Ollama (via LiteLLM): Local models

### Web Server Configuration

| File                                             | Purpose                 | Key Settings                                        |
|--------------------------------------------------|-------------------------|-----------------------------------------------------|
| `org.ops4j.pax.web.cfg`                          | HTTPS/SSL configuration | Port 8443, keystore path and password               |
| `com.eclipsesource.jaxrs.connector.cfg`          | JAX-RS REST API root    | `/rest` (full path: `https://localhost:8443/rest/`) |
| `org.apache.aries.jax.rs.whiteboard.default.cfg` | Aries JAX-RS Whiteboard | Service registration settings                       |

**Default Ports**:
- **8443**: HTTPS REST API (`/rest/api/routes`, `/rest/api/datasources`, etc.)
- **8888**: HTTP query endpoints (dynamically created per route)

### Database Configuration

**File**: `org.ops4j.datasource-qtt.cfg`

| Setting                 | Environment Variable | Default                                | Description                    |
|-------------------------|----------------------|----------------------------------------|--------------------------------|
| `osgi.jdbc.driver.name` | `DB_DRIVER_NAME`     | `derby`                                | JDBC driver name               |
| `url`                   | `DB_URL`             | `jdbc:derby:data/database;create=true` | JDBC connection URL            |
| `user`                  | `DB_USER`            | `user`                                 | Database username              |
| `password`              | `DB_PASSWORD`        | `password`                             | Database password              |
| `pool`                  | N/A                  | `dbcp2`                                | Connection pool implementation |
| `jdbc.pool.maxTotal`    | N/A                  | `10`                                   | Maximum pool connections       |

**Supported Databases**:
- **Apache Derby** (embedded, default): No external server required
- **PostgreSQL**: Set `DB_DRIVER_NAME=PostgreSQL JDBC Driver`
- **Microsoft SQL Server**: Set `DB_DRIVER_NAME=Microsoft JDBC Driver for SQL Server`

### Karaf Branding

**File**: `branding.properties`

Customizes the Karaf console welcome message with QTT ASCII art logo and custom prompt (`${USER}@QTT>`).

## Installed Features

The distribution uses the `karaf-maven-plugin` to pre-install Karaf features at build time.

### Karaf Standard Features

Installed as **startup features** (loaded immediately on boot):

- `eventadmin` - OSGi Event Admin service
- `scr` - Declarative Services runtime
- `http`, `http-whiteboard` - HTTP service and whiteboard pattern
- `pax-web-karaf` - Pax Web integration
- `jdbc`, `jndi`, `transaction` - JDBC/JTA support
- `pax-jdbc-*` - JDBC drivers (Derby, PostgreSQL, SQL Server)
- `jpa`, `hibernate` - JPA 2.2 with Hibernate 5.x
- `camel`, `camel-jetty`, `camel-freemarker` - Apache Camel 3.20.5
- `scheduler` - Quartz scheduler integration
- `shell`, `ssh`, `management` - Karaf management features

### Custom Query Service Features

Installed as **boot features** (via `query-service-feature` module):

#### `qs-rdf4j`
RDF4J 4.x runtime and dependencies for SPARQL query processing and JSON-LD serialization.

**Key Libraries**: Eclipse RDF4J, JSON-LD Java, Jackson, Guava, Apache HTTP Client

#### `qs-persistence`
JPA/JDBC persistence layer dependencies.

**Dependencies**: jndi, jdbc, transaction, pax-jdbc-*, jpa, hibernate

#### `qs-camel`
Apache Camel integration framework.

**Dependencies**: camel, camel-jetty, camel-freemarker

#### `qs-redis-cache`
Optional Redis caching layer for query results.

**Key Libraries**: Lettuce 6.8.1 (Redis client), Netty 4.1.94, Reactor Core 3.4.29, Apache Commons Pool2

**Activation**: Set `redis.enabled=true` in `com.inovexcorp.queryservice.cache.cfg`

#### `qs-sparqi`
SPARQi AI assistant for SPARQL template development.

**Key Libraries**: LangChain4j 1.7.1, LangGraph4j 1.7.0-beta2, OkHttp 4.12.0, Retrofit 2.9.0, Gson 2.10.1

**Activation**: Set `enableSparqi=true` in `com.inovexcorp.queryservice.sparqi.cfg`

#### `query-service` (Main Feature)
Bundles all Query Service modules and dependencies.

**Bundles Installed**:
- `query-service-persistence` - JPA entities and services
- `camel-anzo` - Custom Camel component for Anzo integration
- `query-service-cache` - Redis caching processors
- `query-service-core` - JSON-LD serialization utilities
- `query-service-metrics` - Metrics collection via JMX
- `query-service-health` - Datasource health monitoring
- `query-service-route-builder` - Dynamic Camel route creation
- `query-service-scheduler` - Cron job services
- `query-service-ontology` - Ontology autocomplete service
- `query-service-sparqi` - AI assistant service
- `query-service-web` - Angular 15 web UI

**Bundle Start Levels**:
- 82: persistence, camel-anzo
- 84: cache
- 86: all other application bundles

For complete bundle dependency details, see `query-service-feature/src/main/resources/feature.xml`.

## Runtime Configuration

### Environment Variables

Set these environment variables before starting Karaf to override configuration defaults:

#### Database
- `DB_DRIVER_NAME` - JDBC driver ("derby", "PostgreSQL JDBC Driver", "Microsoft JDBC Driver for SQL Server")
- `DB_URL` - JDBC connection URL
- `DB_USER`, `DB_PASSWORD` - Database credentials

#### Redis Cache
- `REDIS_ENABLED` - Enable Redis caching (`true`/`false`)
- `REDIS_HOST`, `REDIS_PORT` - Redis server address
- `REDIS_PASSWORD` - Redis authentication
- `REDIS_DATABASE` - Redis DB number (0-15)
- `CACHE_DEFAULT_TTL` - Default cache TTL in seconds

#### SPARQi AI Assistant
- `SPARQI_ENABLED` - Enable SPARQi service
- `SPARQI_LLM_BASE_URL` - LLM API endpoint
- `SPARQI_LLM_API_KEY` - API key
- `SPARQI_LLM_MODEL` - Model name

#### Ontology Service
- `ONTO_CACHE_ENABLED` - Enable ontology caching
- `ONTO_CACHE_TTL` - Cache TTL in minutes

#### Application
- `API_BASE_URL` - Base URL for route endpoints (default: `http://localhost:8888`)

### JVM Configuration

**File**: `src/main/resources/bin/setenv`

```bash
export JAVA_MIN_MEM=512M      # Minimum heap size
export JAVA_MAX_MEM=1024M     # Maximum heap size
export EXTRA_JAVA_OPTS=-Xss4m # Thread stack size (4MB)
```

**Customization**: Edit `setenv` before building, or modify `target/assembly/bin/setenv` after building.

### SSL/TLS Configuration

**Keystore Location**: `$KARAF_HOME/etc/keystore`
**Default Password**: `p@ssword1`

**⚠️ Security Warning**: Replace the default keystore with your own certificate for production deployments!

To replace the keystore:

1. Generate or obtain a Java keystore (`.jks` or `.p12` file)
2. Copy to `src/main/resources/etc/keystore` before building
3. Update passwords in `org.ops4j.pax.web.cfg`

For Docker deployments, use environment variables:

```bash
docker run -e KEYSTORE=/path/to/keystore -e PASSWORD=yourpassword inovexis/qtt
```

## Running the Distribution

### Local Karaf Instance

After building, the distribution is located at `query-service-distribution/target/assembly/`.

#### Console Mode (Interactive)

```bash
cd target/assembly
bin/karaf
```

This starts Karaf in foreground mode with an interactive shell. Press `Ctrl+D` or type `shutdown` to stop.

**Useful Commands**:
```
karaf@QTT> feature:list                      # List all features
karaf@QTT> bundle:list                       # List installed bundles
karaf@QTT> log:tail                          # Tail log output
karaf@QTT> bundle:watch *                    # Auto-reload changed bundles
karaf@QTT> camel:route-list                  # List Camel routes
karaf@QTT> jdbc:datasources                  # Show configured datasources
```

#### Background Mode (Daemon)

```bash
cd target/assembly
bin/start                                    # Start in background
bin/status                                   # Check status
bin/client                                   # Connect to running instance
bin/stop                                     # Stop gracefully
```

#### Using Makefile (from project root)

```bash
make run                                     # Build and run with Derby
make postgres_run                            # Build and run with PostgreSQL
make mssql_run                               # Build and run with SQL Server
```

### Health Checks and Verification

#### Verify Karaf is Running

```bash
# Check HTTP ports are listening
curl -k https://localhost:8443/rest/health   # REST API health endpoint
curl http://localhost:8888/                  # Query endpoint base
```

#### Verify Bundles are Active

```bash
bin/client "bundle:list | grep query-service"
```

All bundles should show `Active` state.

#### Verify Database Connection

```bash
bin/client "jdbc:query qtt-ds 'SELECT COUNT(*) FROM camel_route_template'"
```

Should return a count of configured routes (0 for fresh install).

#### Verify Routes are Loaded

```bash
bin/client "camel:route-list"
```

Should show dynamically created routes from database.

#### Check Logs

```bash
tail -f target/assembly/data/log/karaf.log   # Main application log
```

Look for:
- `Bundle started` messages for all `com.inovexcorp.queryservice.*` bundles
- `CamelContext started` messages
- No ERROR or WARN messages about bundle resolution

## Docker Deployment

The distribution includes Docker build integration for containerized deployments.

### Dockerfile Structure

**Location**: `src/main/filtered-resources/Dockerfile`

```dockerfile
FROM openjdk:17.0.2-jdk
ENV VERSION ${project.version}               # Maven-filtered version
ENV KARAF_HOME /opt/qtt
EXPOSE 8443 8888
COPY ./bin/ /opt/qtt/bin/                    # Karaf scripts
COPY ./etc/ /opt/qtt/etc/                    # Configuration
COPY ./system/ /opt/qtt/system/              # OSGi bundles
CMD /opt/qtt/bin/run.sh                      # Startup script
```

The `run.sh` script:
1. Applies SSL keystore configuration from environment variables (`$KEYSTORE`, `$PASSWORD`)
2. Starts Karaf with `karaf run` (foreground mode)

### Building Docker Image

```bash
# Using Maven (builds image during package phase)
mvn clean install
cd query-service-distribution
mvn docker:build

# Using Docker CLI (after building distribution)
cd target/assembly
docker build -t inovexis/qtt:1.0.39-SNAPSHOT .
```

**Image Tags**:
- `inovexis/qtt:latest` - Latest build
- `inovexis/qtt:${project.version}` - Specific version (e.g., `1.0.39-SNAPSHOT`)

### Running Docker Container

#### Basic Run

```bash
docker run -d \
  --name qtt \
  -p 8443:8443 \
  -p 8888:8888 \
  inovexis/qtt:latest
```

#### With Environment Variables

```bash
docker run -d \
  --name qtt \
  -p 8443:8443 \
  -p 8888:8888 \
  -e DB_DRIVER_NAME="PostgreSQL JDBC Driver" \
  -e DB_URL="jdbc:postgresql://postgres:5432/qtt" \
  -e DB_USER="qttuser" \
  -e DB_PASSWORD="qttpass" \
  -e REDIS_ENABLED=true \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  inovexis/qtt:latest
```

#### With Persistent Data Volume

```bash
docker run -d \
  --name qtt \
  -p 8443:8443 \
  -p 8888:8888 \
  -v qtt-data:/opt/qtt/data \
  inovexis/qtt:latest
```

The `data/` directory contains:
- Derby database files (if using embedded Derby)
- Freemarker template files
- Karaf cache and temporary files

#### With Custom Keystore

```bash
docker run -d \
  --name qtt \
  -p 8443:8443 \
  -p 8888:8888 \
  -v /path/to/custom-keystore:/opt/qtt/etc/custom-keystore:ro \
  -e KEYSTORE=/opt/qtt/etc/custom-keystore \
  -e PASSWORD=mykeystorepassword \
  inovexis/qtt:latest
```

### Docker Compose Integration

**Location**: `<project-root>/docker-compose.yml`

Example multi-container setup with PostgreSQL and Redis:

```yaml
services:
  qtt:
    image: inovexis/qtt:latest
    ports:
      - "8443:8443"
      - "8888:8888"
    environment:
      DB_DRIVER_NAME: "PostgreSQL JDBC Driver"
      DB_URL: "jdbc:postgresql://postgres:5432/qtt"
      DB_USER: "qttuser"
      DB_PASSWORD: "qttpass"
      REDIS_ENABLED: "true"
      REDIS_HOST: "redis"
    depends_on:
      - postgres
      - redis
    volumes:
      - qtt-data:/opt/qtt/data

  postgres:
    image: postgres:14
    environment:
      POSTGRES_DB: qtt
      POSTGRES_USER: qttuser
      POSTGRES_PASSWORD: qttpass
    volumes:
      - postgres-data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes:
      - redis-data:/data

volumes:
  qtt-data:
  postgres-data:
  redis-data:
```

Start all services:

```bash
docker-compose up -d
```

### Container Management

```bash
# View logs
docker logs -f qtt

# Access Karaf console
docker exec -it qtt /opt/qtt/bin/client

# Restart container
docker restart qtt

# Stop and remove
docker stop qtt
docker rm qtt
```

### Image Registry

Push to Docker Hub:

```bash
mvn docker:build docker:push
```

**Configuration**: See `docker-maven-plugin` in `pom.xml` for registry settings.

## Customization

### Modifying Configuration Before Building

1. Edit configuration files in `src/main/resources/etc/`
2. Rebuild the distribution: `mvn clean install`
3. New configuration will be in `target/assembly/etc/`

**Common Customizations**:
- **Template location**: Edit `com.inovexcorp.queryservice.routebuilder.cfg`
- **Database settings**: Edit `org.ops4j.datasource-qtt.cfg`
- **JVM memory**: Edit `bin/setenv`
- **SSL keystore**: Replace `etc/keystore` and update `org.ops4j.pax.web.cfg`

### Adding Custom Karaf Features

1. Edit `pom.xml` in this module
2. Add feature repository to `<startupRepositories>` or `<bootRepositories>`
3. Add feature name to `<startupFeatures>` or `<bootFeatures>`
4. Rebuild

Example (add Camel JSON component):

```xml
<startupFeatures>
    ...
    <feature>camel-jackson</feature>
</startupFeatures>
```

### Switching Databases

#### PostgreSQL

Set environment variables before starting:

```bash
export DB_DRIVER_NAME="PostgreSQL JDBC Driver"
export DB_URL="jdbc:postgresql://localhost:5432/qtt"
export DB_USER="qttuser"
export DB_PASSWORD="qttpass"
bin/karaf
```

Or use Makefile:

```bash
make postgres_run
```

#### Microsoft SQL Server

```bash
export DB_DRIVER_NAME="Microsoft JDBC Driver for SQL Server"
export DB_URL="jdbc:sqlserver://localhost:1433;databaseName=qtt"
export DB_USER="sa"
export DB_PASSWORD="YourPassword123"
bin/karaf
```

Or use Makefile:

```bash
make mssql_run
```

**Note**: JDBC drivers for all three databases (Derby, PostgreSQL, SQL Server) are pre-installed via `pax-jdbc-*` features.

### Enabling Redis Cache

1. Start Redis server (standalone or via Docker Compose)
2. Set environment variables:

```bash
export REDIS_ENABLED=true
export REDIS_HOST=localhost
export REDIS_PORT=6379
bin/karaf
```

3. Configure per-route caching in the web UI or via API:

```bash
curl -X PUT https://localhost:8443/rest/api/routes/my-route \
  -H "Content-Type: application/json" \
  -d '{"cacheEnabled": true, "cacheTtlSeconds": 3600}'
```

### Enabling SPARQi AI Assistant

1. Obtain API key from OpenAI or configure LiteLLM gateway
2. Set environment variables:

```bash
export SPARQI_ENABLED=true
export SPARQI_LLM_BASE_URL="https://api.openai.com/v1"
export SPARQI_LLM_API_KEY="sk-..."
export SPARQI_LLM_MODEL="gpt-4o-mini"
bin/karaf
```

3. Access SPARQi via web UI or REST API:

```bash
curl -X POST https://localhost:8443/rest/api/sparqi/session \
  -H "Content-Type: application/json" \
  -d '{"routeId": "my-route", "message": "Help me build a SPARQL query"}'
```

## Troubleshooting

### Common Build Issues

#### Problem: Build fails with "Could not resolve dependencies"

**Solution**: Ensure parent POM is built first:

```bash
cd /path/to/query-service
mvn clean install -pl '!query-service-web'   # Build without web (faster)
cd query-service-distribution
mvn clean install
```

#### Problem: Docker build fails with "No such file or directory"

**Solution**: Build the assembly first before building Docker image:

```bash
mvn clean install                             # Build assembly
mvn docker:build                              # Then build image
```

#### Problem: "Template location does not exist" error on startup

**Solution**: Create template directory or update configuration:

```bash
mkdir -p target/assembly/data/templates
# OR update com.inovexcorp.queryservice.routebuilder.cfg
```

### Configuration Validation

#### Check Configuration Files are Loaded

```bash
bin/client "config:list | grep queryservice"
```

Should show all `com.inovexcorp.queryservice.*` configurations.

#### View Active Configuration

```bash
bin/client "config:property-list com.inovexcorp.queryservice.cache"
```

Shows all properties in cache configuration (useful for debugging environment variable substitution).

### Bundle Resolution Problems

#### Problem: Bundle shows "Installed" instead of "Active"

**Solution**: Check why bundle failed to start:

```bash
bin/client "bundle:diag <bundle-id>"         # Show resolution errors
bin/client "log:display | grep ERROR"       # Check logs for exceptions
```

Common causes:
- Missing OSGi service dependency (wait for other bundles to start)
- Configuration file syntax error
- Database connection failure

#### Problem: "Unsatisfied Requirements" error

**Solution**: Missing feature dependency. Install manually:

```bash
bin/client "feature:install <feature-name>"
```

### Database Connection Issues

#### Problem: "Cannot create PoolableConnectionFactory"

**Causes**:
- Database server not running (PostgreSQL/SQL Server)
- Incorrect credentials in `org.ops4j.datasource-qtt.cfg`
- Network connectivity to database

**Solution**:

```bash
# Test database connection
bin/client "jdbc:datasources"                # Should show "qtt-ds"
bin/client "jdbc:query qtt-ds 'SELECT 1'"    # Should return 1

# Check configuration
bin/client "config:property-list org.ops4j.datasource-qtt"
```

#### Problem: Derby database locked

**Cause**: Another Karaf instance is running.

**Solution**: Stop other instances or delete lock files:

```bash
rm -f data/database/*.lck
```

### Redis Connection Issues

#### Problem: Routes fail after enabling Redis

**Solution**: Check Redis connectivity:

```bash
# Test Redis connection (requires redis-cli)
redis-cli -h localhost -p 6379 ping          # Should return PONG

# Check cache configuration
bin/client "config:property-list com.inovexcorp.queryservice.cache"

# View cache statistics
curl https://localhost:8443/rest/api/routes/cache/info
```

If Redis is unavailable but `cache.failOpen=true`, routes should still work (bypassing cache).

### Log Locations

- **Main log**: `data/log/karaf.log`
- **Console output**: `data/log/karaf.out` (when using `bin/start`)
- **Audit log**: `data/log/audit.log`

Increase log level for debugging:

```bash
bin/client "log:set DEBUG com.inovexcorp.queryservice"
bin/client "log:tail"
```

### Performance Issues

#### Problem: Out of memory errors

**Solution**: Increase heap size in `bin/setenv`:

```bash
export JAVA_MIN_MEM=1024M
export JAVA_MAX_MEM=2048M
```

#### Problem: High CPU usage

**Causes**:
- Scheduler jobs running too frequently
- Large result sets without caching
- Excessive logging

**Solution**:
- Adjust cron expressions in scheduler configs
- Enable Redis caching for expensive queries
- Reduce log level: `log:set WARN`

### Getting Help

- **Project Documentation**: See `CLAUDE.md` in repository root
- **Karaf Documentation**: https://karaf.apache.org/manual/latest/
- **Apache Camel Documentation**: https://camel.apache.org/components/latest/
- **Issue Tracking**: Contact project maintainers

---

## Quick Reference

**Build**: `mvn clean install`
**Run**: `target/assembly/bin/karaf`
**Stop**: Press `Ctrl+D` or type `shutdown`
**Logs**: `data/log/karaf.log`
**Web UI**: `https://localhost:8443/`
**REST API**: `https://localhost:8443/rest/api/`
**Query Endpoints**: `http://localhost:8888/<route-id>`

**Default Credentials**: SSL keystore password is `p@ssword1` (change for production!)

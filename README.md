# Query Templating Tool (QTT) - User Guide

## Table of Contents

1. [Introduction & Overview](#1-introduction--overview)
2. [Installation & Deployment](#2-installation--deployment)
3. [Configuration Guide](#3-configuration-guide)
4. [Getting Started Tutorial](#4-getting-started-tutorial)
5. [SPARQi AI Assistant](#5-sparqi-ai-assistant)
6. [Web UI Guide](#6-web-ui-guide)
7. [Template Development](#7-template-development)
8. [API Reference](#8-api-reference)
9. [Monitoring & Troubleshooting](#9-monitoring--troubleshooting)
10. [Advanced Topics](#10-advanced-topics)
11. [Appendices](#11-appendices)

---

## 1. Introduction & Overview

### What is Query Templating Tool (QTT)?

Query Templating Tool (QTT) is a microservice platform that translates domain-specific search requests into optimized SPARQL queries for graph database systems (specifically Altair Graph Studio). It acts as a middleware layer between your applications and your knowledge graph, simplifying data access through templated queries.

### How It Works

```
HTTP Request + Freemarker Template → SPARQL Query → Graph Studio Backend → JSON-LD Results
```

**Example Flow:**

1. Your application sends a simple JSON request:
```json
{
  "search_type": "email",
  "from": "joe@example.com",
  "limit": 500
}
```

2. QTT uses a Freemarker template to generate an optimized SPARQL query
3. The query executes against your Graph Studio graph database
4. Results are streamed back as JSON-LD

### Key Features

- **Template-Based Queries**: Write SPARQL queries once as Freemarker templates, reuse with different parameters
- **Dynamic Route Creation**: Create, modify, and deploy query endpoints without code changes
- **Multiple Database Backends**: Supports Derby (embedded), PostgreSQL, and SQL Server for metadata/route persistence
- **Real-Time Metrics**: Built-in performance monitoring and route analytics
- **Health Monitoring**: Automatic datasource health checks with configurable failure handling
- **Query Result Caching**: Optional Redis-backed caching layer to reduce database load and improve response times
- **AI-Powered Assistance**: Optional SPARQi assistant helps develop SPARQL templates using LLMs
- **Rich Web UI**: Angular-based interface for managing datasources, routes, and monitoring performance
- **RESTful API**: Complete programmatic access to all features
- **OSGi Runtime**: Built on Apache Karaf for modular, hot-deployable components

### Use Cases

- **Simplified Knowledge Graph Access**: Hide SPARQL complexity from frontend developers
- **Multi-Tenant Graph Queries**: Route different clients to different graph layers
- **Query Performance Optimization**: Centralized template management and caching
- **Graph Data API Gateway**: Single endpoint for all graph database interactions
- **Semantic Search Services**: Build search APIs backed by ontology-driven queries

### Architecture Overview

QTT is built on a multi-module Maven project with these key components:

**Backend Modules:**
- **query-service-core**: RDF/JSON-LD serialization utilities
- **camel-anzo**: Custom Apache Camel component for Anzo integration
- **query-service-route-builder**: Dynamic Camel route creation from database templates
- **query-service-persistence**: JPA entities and services for routes, datasources, metrics
- **query-service-scheduler**: Cron jobs for metrics collection and cleanup
- **query-service-metrics**: JMX-based metrics collection
- **query-service-cache**: Redis-backed caching layer for query results
  - `CacheService`: Interface for caching operations (get, put, delete, stats)
  - `RedisCacheService`: Redis implementation using Lettuce client with connection pooling
  - `NoOpCacheService`: Fallback when Redis is disabled
  - `CacheCheckProcessor`, `CacheStoreProcessor`: Camel processors for cache integration
  - Per-route cache configuration (enable/disable, TTL, key strategy)
  - Automatic cache key generation using SHA-256 hashing
- **query-service-sparqi**: Optional AI assistant for template development
- **query-service-feature**: Karaf feature descriptor for OSGi deployment
- **query-service-distribution**: Complete Karaf distribution with all bundles

**Frontend Module:**
- **query-service-web**: Angular 15.2.2 web application (builds to OSGi bundle)

**Runtime Architecture:**

The application runs in Apache Karaf 4.4.1 and provides two separate HTTP applications:

1. **JAX-RS Application** (Port 8080 - HTTP)
   - Static CRUD endpoints for routes, datasources, layers
   - SPARQi AI assistant API
   - Metrics and settings endpoints

2. **Camel Jetty Application** (Port 8888 - HTTP)
   - Dynamically created query endpoints based on database route definitions
   - Endpoint pattern: `http://localhost:8888/{route-id}?param=value`

### System Requirements

**For Running from Source:**
- Java 17 (JDK)
- Apache Maven 3.6+
- Node.js v20.18.1
- Angular CLI 18.2.21

**For Running with Docker/Podman:**
- Docker 20.10+ or Podman 3.0+
- 2GB RAM minimum (4GB recommended)
- 2GB disk space

**For Production Deployment:**
- PostgreSQL 12+ or SQL Server 2019+ (recommended over embedded Derby)
- SSL certificates for HTTPS
- Optional: Redis 6.0+ for query result caching (recommended for improved performance)
- Optional: LLM provider access for SPARQi (OpenAI, Azure OpenAI, LiteLLM, etc.)

---

## 2. Installation & Deployment

### 2.1 Quick Start with Docker

The fastest way to get QTT running is using the official Docker image.

#### Step 1: Pull the Image

```bash
docker pull docker.io/inovexis/qtt:latest
```

#### Step 2: Run the Container

**Basic deployment with embedded Derby database:**

```bash
docker run -d \
  --name qtt \ -p 8080:8080 \
  -p 8888:8888 \
  docker.io/inovexis/qtt:latest
```

**With environment variables for configuration:**

```bash
docker run -d \
  --name qtt \
  -p 8080:8080 \
  -p 8888:8888 \
  -e API_BASE_URL=http://localhost:8888 \
  -e DB_DRIVER_NAME=derby \
  docker.io/inovexis/qtt:latest
```

#### Step 3: Access the Application

- **Web UI**: http://localhost:8080
- **API Base**: http://localhost:8080/queryrest/api/
- **Query Endpoints**: http://localhost:8888/{route-id}

**Note**: Typically you'll want to use a reverse proxy to expose the application to the outside world.

#### Step 4: Verify Installation

```bash
# Check container status
docker ps

# View logs
docker logs qtt

# Check API health
curl -k http://localhost:8080/queryrest/api/settings
```

### 2.2 Quick Start with Podman

Podman is a daemonless container engine that's Docker-compatible and can run rootless containers.

#### Step 1: Install Podman

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

#### Step 2: Pull the Image

```bash
podman pull docker.io/inovexis/qtt:latest
```

#### Step 3: Run the Container

**Rootless mode (recommended):**

```bash
podman run -d \
  --name qtt \
  -p 8080:8080 \
  -p 8888:8888 \
  docker.io/inovexis/qtt:latest
```

**With volume mount for persistent data:**

```bash
podman run -d \
  --name qtt \
  -p 8080:8080 \
  -p 8888:8888 \
  -v qtt-data:/opt/qtt/data:Z \
  docker.io/inovexis/qtt:latest
```

**Note**: The `:Z` flag is important for SELinux systems (RHEL, CentOS, Fedora) to properly label the volume.

#### Step 4: Access the Application

- **Web UI**: http://localhost:8080/queryrest/app/
- **API Base**: http://localhost:8080/queryrest/api/

#### Podman-Specific Considerations

**Port Mapping in Rootless Mode:**
- Rootless Podman can map ports 1024+ directly
- For ports <1024, you need root or configure `net.ipv4.ip_unprivileged_port_start`

**SELinux Context:**
- Use `:Z` for private volume mounts
- Use `:z` for shared volume mounts

### 2.3 Docker Compose Setup

For production-like deployments with PostgreSQL or SQL Server, use Docker Compose.

#### PostgreSQL Setup

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  postgresql:
    image: postgres:16-alpine
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
      API_BASE_URL: http://localhost:8888
    ports:
      - "8080:8080"
      - "8888:8888"
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

#### SQL Server Setup

Create `docker-compose.yml`:

```yaml
version: '3.8'

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
      API_BASE_URL: http://localhost:8888
    ports:
      - "8080:8080"
      - "8888:8888"
    networks:
      - qtt-network

volumes:
  qtt-mssql-data:

networks:
  qtt-network:
    driver: bridge
```

**Start services:**

```bash
docker compose up -d
```

#### Redis Cache Setup

For production deployments with query result caching, add Redis to your stack. This significantly improves performance by reducing load on the graph database backend.

**docker-compose.yml with PostgreSQL + Redis:**

```yaml
version: '3.8'

services:
  postgresql:
    image: postgres:16-alpine
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
      test: ["CMD-SHELL", "pg_isready -U qttuser"]
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
      test: ["CMD", "redis-cli", "--raw", "incr", "ping"]
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
      API_BASE_URL: http://localhost:8888

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
      - "8080:8080"
      - "8888:8888"
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
- **Cache TTL**: Default 3600 seconds (1 hour); can be overridden per-route in the UI
- **Compression**: Enabled by default to reduce memory usage (adds minimal CPU overhead)
- **Fail-Open**: Cache errors don't break queries; system falls back to direct database queries

**Start services:**

```bash
docker compose up -d
```

**Verify cache is working:**

```bash
# Check Redis connection
docker exec qtt-redis redis-cli -a RedisSecure123 ping
# Expected output: PONG

# View cache statistics (after making some queries)
curl http://localhost:8080/queryrest/api/routes/cache/info
```

#### Managing the Stack

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

### 2.4 Podman Compose Setup

Podman Compose provides Docker Compose compatibility for Podman.

#### Install Podman Compose

```bash
pip install podman-compose
```

#### Using the Same Compose Files

The same `docker-compose.yml` files work with `podman-compose`:

```bash
# Start services
podman-compose up -d

# View logs
podman-compose logs -f qtt

# Stop services
podman-compose down
```

#### Using Podman Pods (Alternative)

Podman supports Kubernetes-style pods for grouping containers:

```bash
# Create a pod with port mappings
podman pod create --name qtt-pod \
  -p 8080:8080 \
  -p 8888:8888 \
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

### 2.5 SSL/TLS Configuration

#### Using Custom SSL Certificates

Typically, it's a better practice to sit behind a reverse proxy, such as nginx, but the container does support runtime SSL configuration via environment variables:

```bash
docker run -d \
  --name qtt \
  -p 8080:8080 \
  -p 8888:8888 \
  -v /path/to/keystore.jks:/opt/qtt/etc/custom-keystore:ro \
  -e KEYSTORE=/opt/qtt/etc/custom-keystore \
  -e PASSWORD=your-keystore-password \
  docker.io/inovexis/qtt:latest
```

The `run.sh` startup script automatically updates the PAX Web configuration with your keystore settings.

#### Generating a Self-Signed Certificate

```bash
keytool -genkey \
  -keyalg RSA \
  -alias qtt \
  -keystore keystore.jks \
  -storepass changeit \
  -validity 365 \
  -keysize 2048
```

### 2.6 Persistent Data and Volumes

#### Important Data Directories

| Path in Container         | Purpose              | Persistence Needed |
|---------------------------|----------------------|--------------------|
| `/opt/qtt/data/database`  | Derby database files | Yes                |
| `/opt/qtt/data/templates` | Freemarker templates | Yes                |
| `/opt/qtt/data/log`       | Application logs     | Optional           |
| `/opt/qtt/etc`            | Configuration files  | Optional           |

#### Docker Volume Mounting

```bash
docker run -d \
  --name qtt \
  -p 8080:8080 \
  -p 8888:8888 \
  -v qtt-data:/opt/qtt/data \
  -v qtt-templates:/opt/qtt/data/templates \
  docker.io/inovexis/qtt:latest
```

#### Podman Volume Mounting with SELinux

```bash
podman run -d \
  --name qtt \
  -p 8080:8080 \
  -p 8888:8888 \
  -v qtt-data:/opt/qtt/data:Z \
  -v qtt-templates:/opt/qtt/data/templates:Z \
  docker.io/inovexis/qtt:latest
```

### 2.7 Building from Source

If you need to build from source or customize the application:

#### Prerequisites

- Java 17
- Maven 3.6+
- Node.js v20.18.1
- Angular CLI 18.2.21

#### Build Commands

```bash
# Clone the repository
git clone https://github.com/inovexcorp/qtt.git
cd qtt

# Full build and run (includes Angular frontend)
make build_and_run

# Build without frontend (faster for backend-only changes)
make build_no_web

# Build Docker image
make build_docker

# Run locally built distribution (sources .env file if present)
make run
```

**Note**: For local development workflows with PostgreSQL and Redis, see [Section 2.8: Local Development with Makefile](#28-local-development-with-makefile).

#### Configuration Before Building

**Important**: Configure the template location before building.

Edit `query-service-distribution/src/main/resources/etc/com.inovexcorp.queryservice.routebuilder.cfg`:

```properties
templateLocation=data/templates/
```

This path is relative to the Karaf home directory (`/opt/qtt` in containers).

### 2.8 Local Development with Makefile

The project includes a comprehensive Makefile that simplifies common development workflows. This section covers local development using the Makefile commands with PostgreSQL and Redis for a production-like environment.

#### Prerequisites

- Java 17
- Maven 3.6+
- Node.js v20.18.1
- Docker or Podman (for PostgreSQL and Redis containers)

#### Quick Reference: Makefile Commands

**Build Commands:**

| Command | Description |
|---------|-------------|
| `make build` | Build entire project with Maven |
| `make build_no_web` | Build excluding query-service-web module |
| `make refresh_bundles` | Rebuild bundles (excludes web and distribution) |
| `make build_docker` | Build Docker image |

**Local Run Commands:**

| Command | Description |
|---------|-------------|
| `make run` | Run Karaf (builds if needed, uses Derby by default) |
| `make build_and_run` | Build then run Karaf |
| `make postgres_run` | Start PostgreSQL and run Karaf with PostgreSQL config |
| `make mssql_run` | Start MSSQL and run Karaf with MSSQL config |
| `make stop` | Stop running Karaf instance |

**Database Management Commands:**

| Command | Description |
|---------|-------------|
| `make start_redis` | Start Redis container |
| `make start_postgres` | Start PostgreSQL container |
| `make start_mssql` | Start MSSQL container |
| `make stop_redis` | Stop Redis container |
| `make stop_postgres` | Stop PostgreSQL container |
| `make stop_mssql` | Stop MSSQL container |
| `make stop_databases` | Stop all database containers |
| `make logs_redis` | View Redis logs |
| `make logs_postgres` | View PostgreSQL logs |
| `make logs_mssql` | View MSSQL logs |

**Utility Commands:**

| Command | Description |
|---------|-------------|
| `make clean` | Remove build artifacts |
| `make test` | Run Maven tests |
| `make help` | Show help message with all commands |

#### Recommended Setup: PostgreSQL + Redis

This workflow sets up a production-like local environment with PostgreSQL for persistence and Redis for query result caching.

**Step 1: Initial Build**

```bash
# Clone and build the project (first time only)
git clone https://github.com/inovexcorp/qtt.git
cd qtt
make build
```

**Step 2: Start Dependencies**

Start PostgreSQL and Redis containers (handled automatically by docker/podman compose):

```bash
# Start both PostgreSQL and Redis
make start_postgres start_redis

# Verify containers are running
docker ps
# or: podman ps
```

The containers will start with these default credentials (from `compose.yml`):

- **PostgreSQL**: `postgres:verYs3cret@localhost:5432/qtt`
- **Redis**: `localhost:6379` (no password)

**Step 3: Run with PostgreSQL**

```bash
make postgres_run
```

This command:
1. Starts PostgreSQL container if not already running
2. Sets PostgreSQL environment variables automatically
3. Sources your `.env` file if present (for Redis or other config)
4. Launches Karaf with the configured settings

**Step 4: Enable Redis Caching (Optional)**

Create a `.env` file in the project root to enable Redis:

```bash
# Redis Cache Configuration
REDIS_ENABLED=true
REDIS_HOST=localhost
REDIS_PORT=6379
CACHE_DEFAULT_TTL=3600
CACHE_COMPRESSION_ENABLED=true
```

Then restart:

```bash
make stop
make postgres_run
```

**Step 5: Verify Installation**

```bash
# Check Karaf is running
# Access Web UI at: http://localhost:8080

# Check API
curl http://localhost:8080/queryrest/api/settings

# Check cache info (if Redis enabled)
curl http://localhost:8080/queryrest/api/routes/cache/info
```

**Step 6: Stopping Services**

```bash
# Stop Karaf
make stop

# Stop database containers
make stop_databases
```

#### Alternative Workflows

**Simple Development (Derby embedded database):**

```bash
# Quick start with no external dependencies
make build_and_run
```

**PostgreSQL without Redis:**

```bash
# Just PostgreSQL, no caching
make postgres_run
# (Don't create .env file or set REDIS_ENABLED=false)
```

**SQL Server instead of PostgreSQL:**

```bash
make mssql_run
```

#### Managing Services

**View logs for debugging:**

```bash
# View PostgreSQL logs
make logs_postgres

# View Redis logs
make logs_redis

# View Karaf logs (in separate terminal)
tail -f query-service-distribution/target/assembly/data/log/karaf.log
```

**Rebuild after code changes:**

```bash
# For backend changes (faster, skips Angular build)
make refresh_bundles

# Restart Karaf
make stop
make postgres_run
```

**Start containers individually:**

```bash
# Start only what you need
make start_redis     # Just Redis
make start_postgres  # Just PostgreSQL
make start_mssql     # Just MSSQL
```

---

## 3. Configuration Guide

### 3.1 Environment Variables Reference

All configuration values can be overridden using environment variables. The system follows the pattern: `$[env:VARIABLE_NAME;default=value]`

#### Core Database Configuration
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

#### API Configuration
This configuration controls the UI base URL when displaying the route endpoints.

| Variable       | Default                 | Description                                    |
|----------------|-------------------------|------------------------------------------------|
| `API_BASE_URL` | `http://localhost:8888` | Base URL for query endpoints exposed to the UI |

#### SPARQi AI Assistant Configuration
This configuration controls the SPARQi AI assistant, which is used to generate responses to user queries.

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

#### Ontology Service Configuration

| Variable                   | Default | Description                     |
|----------------------------|---------|---------------------------------|
| `ONTO_CACHE_TTL`           | `60`    | Cache TTL in minutes            |
| `ONTO_CACHE_MAX_ENTRIES`   | `20`    | Max routes to cache             |
| `ONTO_CACHE_ENABLED`       | `true`  | Enable/disable caching          |
| `ONTO_CACHE_QUERY_TIMEOUT` | `30`    | SPARQL query timeout (seconds)  |
| `ONTO_CACHE_QUERY_LIMIT`   | `1000`  | Max ontology elements per query |

#### Redis Cache Configuration

Configuration for Redis-backed query result caching to improve performance and reduce backend load.

| Variable                     | Default       | Description                              | Required |
|------------------------------|---------------|------------------------------------------|----------|
| `REDIS_ENABLED`              | `false`       | Enable/disable Redis caching globally    | Optional |
| `REDIS_HOST`                 | `localhost`   | Redis server hostname                    | Conditional* |
| `REDIS_PORT`                 | `6379`        | Redis server port                        | Conditional* |
| `REDIS_PASSWORD`             | (empty)       | Redis authentication password            | Optional |
| `REDIS_DATABASE`             | `0`           | Redis database number (0-15)             | Optional |
| `REDIS_TIMEOUT`              | `5000`        | Connection timeout in milliseconds       | Optional |
| `REDIS_POOL_MAX_TOTAL`       | `20`          | Maximum connections in pool              | Optional |
| `REDIS_POOL_MAX_IDLE`        | `10`          | Maximum idle connections in pool         | Optional |
| `REDIS_POOL_MIN_IDLE`        | `5`           | Minimum idle connections in pool         | Optional |
| `CACHE_DEFAULT_TTL`          | `3600`        | Default cache TTL in seconds             | Optional |
| `CACHE_KEY_PREFIX`           | `qtt:cache:`  | Prefix for all cache keys                | Optional |
| `CACHE_COMPRESSION_ENABLED`  | `true`        | Enable gzip compression for cached values| Optional |
| `CACHE_FAIL_OPEN`            | `true`        | Continue on cache errors (vs fail closed)| Optional |
| `CACHE_STATS_ENABLED`        | `true`        | Track cache statistics                   | Optional |
| `CACHE_STATS_TTL`            | `5`           | Cache statistics TTL in seconds          | Optional |

*Required if `REDIS_ENABLED=true`

**Important Notes:**

- **Redis is Optional**: Routes work without Redis using `NoOpCacheService` as a fallback. Enable only if you have Redis infrastructure.
- **Per-Route Control**: Caching can be enabled/disabled per route in the UI, even when Redis is globally enabled.
- **Cache TTL Strategy**: Routes can override the global default TTL. Shorter TTLs for frequently changing data, longer for static data.
- **Compression Trade-Off**: Compression saves memory but adds CPU overhead. Disable for small result sets or if CPU is constrained.
- **Fail-Open Mode**: Recommended for production. Cache errors don't break queries; system falls back to direct database access.
- **Key Prefix**: Useful for multi-tenant deployments or namespace separation. Change if running multiple QTT instances against same Redis.

### 3.2 Configuration Files in Distribution

All configuration files are located in `/opt/qtt/etc/` (in containers) or `query-service-distribution/src/main/resources/etc/` (in source).

#### Core Service Configuration

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

#### Database Configuration
Database configuration for the runtime.

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

#### Scheduler Configuration
Various configuration in support of scheduled activities on the platform.

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
# Check datasource health every 60 seconds
scheduler.expression=0/60 * * * * ?
scheduler.concurrent=false
# Auto-stop routes after N consecutive failures (-1 = disabled)
consecutiveFailureThreshold=-1
```

**`com.inovexcorp.queryservice.scheduler.CleanHealthRecords.cfg`** - Schedule for health record cleanup
```properties
# Clean old health records daily at midnight
scheduler.expression=0 0 0 * * ?
scheduler.concurrent=false
# Keep health records for 7 days
daysToLive=7
```

#### SPARQi Configuration
Configuration for the SPARQi AI assistant.

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

#### Ontology Service Configuration
Configuration for the ontology service, which caches ontology elements for things like autocomplete and context in the SPARQi agent.

**`com.inovexcorp.queryservice.ontology.cfg`**
```properties
cacheTtlMinutes=$[env:ONTO_CACHE_TTL;default=60]
cacheMaxEntries=$[env:ONTO_CACHE_MAX_ENTRIES;default=20]
cacheEnabled=$[env:ONTO_CACHE_ENABLED;default=true]
queryTimeoutSeconds=$[env:ONTO_CACHE_QUERY_TIMEOUT;default=30]
queryLimit=$[env:ONTO_CACHE_QUERY_LIMIT;default=1000]
```

#### Cache Configuration
Configuration for Redis-backed query result caching. This is separate from the ontology cache and caches actual query results to improve performance.

**`com.inovexcorp.queryservice.cache.cfg`**
```properties
# Redis Connection Settings
# Enable/disable Redis caching globally (if false, queries bypass cache)
redis.enabled=$[env:REDIS_ENABLED;default=false]

# Redis server hostname
redis.host=$[env:REDIS_HOST;default=localhost]

# Redis server port
redis.port=$[env:REDIS_PORT;default=6379]

# Redis authentication password (leave empty if no auth required)
redis.password=$[env:REDIS_PASSWORD;default=]

# Redis database number (0-15)
redis.database=$[env:REDIS_DATABASE;default=0]

# Redis connection timeout in milliseconds
redis.timeout=$[env:REDIS_TIMEOUT;default=5000]

# Redis Connection Pool Settings
# Maximum number of connections in pool
redis.pool.maxTotal=$[env:REDIS_POOL_MAX_TOTAL;default=20]

# Maximum number of idle connections in pool
redis.pool.maxIdle=$[env:REDIS_POOL_MAX_IDLE;default=10]

# Minimum number of idle connections in pool
redis.pool.minIdle=$[env:REDIS_POOL_MIN_IDLE;default=5]

# Global Cache Settings
# Prefix for all cache keys (helps with multi-tenant or namespacing)
cache.keyPrefix=$[env:CACHE_KEY_PREFIX;default=qtt:cache:]

# Default cache TTL in seconds (used when route doesn't specify a TTL)
cache.defaultTtlSeconds=$[env:CACHE_DEFAULT_TTL;default=3600]

# Enable gzip compression for cached values (saves memory, adds CPU overhead)
cache.compressionEnabled=$[env:CACHE_COMPRESSION_ENABLED;default=true]

# Cache Behavior
# Continue on cache errors (true) vs fail and return error to client (false)
cache.failOpen=$[env:CACHE_FAIL_OPEN;default=true]

# Track cache statistics (hit/miss counts, memory usage)
cache.statsEnabled=$[env:CACHE_STATS_ENABLED;default=true]

# Cache statistics TTL in seconds (prevents Redis stampedes on stats endpoint)
cache.statsTtlSeconds=$[env:CACHE_STATS_TTL;default=5]
```

#### JSON-LD Serialization
Simple properties that control how the JSON-LD serialization will be performed.

**`com.inovexcorp.queryservice.jsonldSerializer.cfg`**
```properties
baseUri=http://inovexcorp.com/
jsonLdMode=COMPACT
optimize=true
useNativeTypes=true
compactArrays=true
```

#### Web Server Configuration
Base web configuration for the platform.

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

**`org.apache.aries.jax.rs.whiteboard.default.cfg`**
```properties
# JAX-RS application base path
default.application.base=/queryrest

# Enable multipart file uploads
jersey.multipart.enabled=true
```

#### Logging Configuration

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

### 3.3 Database Backend Configuration

#### Derby (Default - Development Only)

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

#### PostgreSQL (Recommended for Production)

**Pros:**
- Multi-user, concurrent access
- Excellent performance
- Open source
- Wide adoption

**Setup:**

1. Create database and user:
```sql
CREATE DATABASE qtt;
CREATE USER qttuser WITH PASSWORD 'SecurePassword123';
GRANT ALL PRIVILEGES ON DATABASE qtt TO qttuser;
```

2. Configure QTT:
```bash
DB_DRIVER_NAME=PostgreSQL JDBC Driver
DB_URL=jdbc:postgresql://db-host:5432/qtt
DB_USER=qttuser
DB_PASSWORD=SecurePassword123
```

#### SQL Server (Enterprise Environments)

**Pros:**
- Enterprise-grade features
- Windows authentication support
- High availability options

**Setup:**

1. Create database and login:
```sql
CREATE DATABASE qtt;
CREATE LOGIN qttuser WITH PASSWORD = 'SecurePassword123';
USE qtt;
CREATE USER qttuser FOR LOGIN qttuser;
GRANT CONTROL ON DATABASE::qtt TO qttuser;
```

2. Configure QTT:
```bash
DB_DRIVER_NAME=Microsoft JDBC Driver for SQL Server
DB_URL=jdbc:sqlserver://db-host:1433;databaseName=qtt;encrypt=true;trustServerCertificate=true
DB_USER=qttuser
DB_PASSWORD=SecurePassword123
```

### 3.4 Security Configuration

#### Changing Default SSL Passwords
This is only necessary if you want to configure native HTTPS on the container. A better practice is a reverse proxy with SSL termination.

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

#### Database Credential Management

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

#### SPARQi API Key Security

```bash
# Store API keys in files
echo "sk-your-api-key" > /secure/path/openai-key

# Mount as read-only
docker run -d \
  --name qtt \
  -v /secure/path/openai-key:/run/secrets/openai_key:ro \
  -e SPARQI_LLM_API_KEY_FILE=/run/secrets/openai_key \
  docker.io/inovexis/qtt:latest
```

### 3.5 Port Configuration

| Port | Protocol | Purpose                 | Configurable                |
|------|----------|-------------------------|-----------------------------|
| 8080 | HTTP     | JAX-RS API and Web UI   | Yes (org.ops4j.pax.web.cfg) |
| 8888 | HTTP     | Dynamic query endpoints | Yes (Jetty configuration)   |

To change ports, edit the Jetty endpoint configuration in your route templates or override the PAX Web configuration.

---

## 4. Getting Started Tutorial

This tutorial walks you through creating your first query route from start to finish.

### Prerequisites

- QTT is running and accessible at http://localhost:8080
- You have access to an Altair Graph Studio graph database instance
- Basic familiarity with SPARQL and RDF

#### Running QTT Locally (Recommended for Development)

If you're developing locally, follow these steps to set up QTT with PostgreSQL and Redis for a production-like environment:

**1. Clone and build the project:**

```bash
git clone https://github.com/inovexcorp/qtt.git
cd qtt
make build
```

**2. Start PostgreSQL and Redis:**

```bash
make start_postgres start_redis
```

**3. Configure Redis caching and Postgres connection (optional but recommended):**

Create a `.env` file in the project root:

```bash
# .env
REDIS_ENABLED=true
REDIS_HOST=localhost
REDIS_PORT=6379
CACHE_DEFAULT_TTL=3600

DB_USER=postgres
DB_PASSWORD=verYs3cret
DB_DRIVER_NAME='PostgreSQL JDBC Driver'
DB_URL=jdbc:postgresql://127.0.0.1:5432/qtt
```

**4. Run QTT (.env will be sourced automatically):**

```bash
make run
```

**5. Verify QTT is running:**

- Web UI: http://localhost:8080
- API: http://localhost:8080/queryrest/api/settings

For more details on local development, see [Section 2.8: Local Development with Makefile](#28-local-development-with-makefile).

#### Alternative: Using Docker

If you prefer using Docker:

```bash
# Pull and run the official image
docker pull docker.io/inovexis/qtt:latest
docker run -d --name qtt -p 8080:8080 -p 8888:8888 docker.io/inovexis/qtt:latest

# Access at http://localhost:8080
```

### Step 1: Access the Web UI

1. Open your browser and navigate to: **http://localhost:8080/**
   - If using the Docker image with HTTPS enabled, use **https://localhost:8443/** and accept the self-signed certificate warning
2. You should see the Query Templating Tool dashboard

### Step 2: Create Your First Datasource

A datasource represents your Altair Graph Studio graph database connection.

1. **Navigate to Datasources**
   - Click "DataSources" in the left sidebar

2. **Click "Add DataSource" button** (top right)

3. **Fill in the form:**
   - **DataSource ID**: `my-anzo-instance` (unique identifier)
   - **URL**: Your Anzo GraphStudio URL (e.g., `https://anzo-server`)
   - **Timeout**: `30` (seconds)
   - **Max Query Header Length**: `1024`
   - **Username**: Your Anzo username
   - **Password**: Your Anzo password
   - **Validate Certificate**: Check/uncheck based on your SSL setup

4. **Test Connection**
   - Click the "Test Connection" button
   - Wait for success message

5. **Save**
   - Click "Save" to create the datasource

The datasource card will appear showing status indicators.

### Step 3: Create Your First Route

A route defines a query endpoint with an associated Freemarker template.

1. **Navigate to Routes**
   - Click "Routes" in the left sidebar

2. **Click "Add Route" button**

3. **Fill in Basic Information:**
   - **Route ID**: `people-search` (this becomes your endpoint: `/people-search`)
   - **HTTP Methods**: Check **GET** (and optionally **POST**)
   - **Description**: `Search for people by name`

4. **Configure Data Source:**
   - **Datasource**: Select `my-anzo-instance` from dropdown
   - **GraphMart**: Start typing and select your GraphMart from autocomplete
   - **GraphMart Layers** (optional): Select relevant layers if applicable

5. **Write Your Freemarker Template:**

Click in the template editor and paste this example:

```freemarker
PREFIX foaf: <http://xmlns.com/foaf/0.1/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

CONSTRUCT {
  ?person a foaf:Person ;
    foaf:name ?name ;
    foaf:mbox ?email .
}
WHERE {
  ?person a foaf:Person ;
    foaf:name ?name .

  <#if headers.name??>
  FILTER(CONTAINS(LCASE(STR(?name)), "${headers.name?lower_case}"))
  </#if>

  OPTIONAL { ?person foaf:mbox ?email . }
}
<#if headers.limit??>
LIMIT ${headers.limit}
</#if>
```

This template:
- Searches for `foaf:Person` instances
- Filters by name if `name` parameter is provided
- Optionally limits results if `limit` parameter is provided
- Returns person data as JSON-LD

6. **Save the Route**
   - Click "Save"

### Step 4: Test Your Route

Your new endpoint is now live at: `http://localhost:8888/people-search`

**Test without parameters:**
```bash
curl -X GET "http://localhost:8888/people-search"
```

**Test with parameters:**
```bash
curl -X GET "http://localhost:8888/people-search?name=john&limit=10"
```

### Step 5: Monitor Performance

1. **Navigate to Metrics**
   - Click "Metrics" in the left sidebar

2. **View Your Route's Performance:**
   - Find `people-search` in the metrics table
   - See processing times, exchange counts, success rate

3. **Analyze Trends:**
   - Click on the "Trends" tab
   - Select your route from the dropdown
   - View historical performance data

### Step 6: Modify and Iterate

1. **Edit the Route:**
   - Go back to Routes page
   - Click the menu (three dots) on your route card
   - Select "Configure"

2. **Update the template:**
   - Modify the SPARQL query or Freemarker logic
   - Click "Save"
   - Changes take effect immediately (no restart required)

3. **Toggle Route Status:**
   - Use the menu to "Turn Off" or "Turn On" the route
   - Stopped routes are inaccessible but remain configured

### Next Steps

- **Clone your route**: Use the "Clone" option to create variations
- **Add more datasources**: Connect to multiple Anzo instances
- **Explore SPARQi**: Enable the AI assistant to help write templates
- **Monitor health**: Set up health check thresholds for automatic failover

---

## 5. SPARQi AI Assistant

SPARQi is an optional AI-powered assistant that helps you develop and refine Freemarker-templated SPARQL queries.

### What is SPARQi?

SPARQi stands for **SPARQL Query Intelligence**. It's a conversational AI agent that understands:
- Your route's ontology (classes, properties, individuals)
- Your current Freemarker template
- SPARQL query syntax and best practices
- Freemarker templating techniques

### When to Use SPARQi

SPARQi is particularly helpful when you need to:
- **Explore an unfamiliar ontology**: "What classes are available?"
- **Find relationships**: "How do I connect Person to Organization?"
- **Write complex queries**: "Help me create a query that finds all documents modified in the last week"
- **Debug templates**: "Why isn't my FILTER clause working?"
- **Optimize performance**: "How can I make this query faster?"

### 5.1 Setting Up SPARQi

#### Option 1: Using OpenAI Directly

1. **Get an OpenAI API key** from https://platform.openai.com

2. **Configure environment variables:**
```bash
docker run -d \
  --name qtt \
  -p 8080:8080 \
  -p 8888:8888 \
  -e SPARQI_ENABLED=true \
  -e SPARQI_LLM_BASE_URL=https://api.openai.com/v1 \
  -e SPARQI_LLM_API_KEY=sk-your-openai-key \
  -e SPARQI_LLM_MODEL=gpt-4o-mini \
  docker.io/inovexis/qtt:latest
```

#### Option 2: Using LiteLLM (Recommended)

[LiteLLM](https://docs.litellm.ai/) provides a unified gateway to 100+ LLM providers with enterprise features like caching, fallbacks, and load balancing.

**Step 1: Install LiteLLM**
```bash
pip install litellm[proxy]
```

**Step 2: Create configuration file** (`litellm_config.yaml`):
```yaml
model_list:
  # Claude from Anthropic
  - model_name: claude-3-5-sonnet-20241022
    litellm_params:
      model: anthropic/claude-3-5-sonnet-20241022
      api_key: os.environ/ANTHROPIC_API_KEY

  # GPT-4o from OpenAI
  - model_name: gpt-4o
    litellm_params:
      model: openai/gpt-4o
      api_key: os.environ/OPENAI_API_KEY

  # Gemini from Google
  - model_name: gemini-pro
    litellm_params:
      model: gemini/gemini-1.5-pro
      api_key: os.environ/GEMINI_API_KEY

  # Local Ollama model
  - model_name: llama3
    litellm_params:
      model: ollama/llama3.1
      api_base: http://localhost:11434
```

**Step 3: Start LiteLLM proxy**
```bash
export ANTHROPIC_API_KEY=your-key
export OPENAI_API_KEY=your-key
litellm --config litellm_config.yaml --port 4000
```

**Step 4: Configure QTT to use LiteLLM**
```bash
docker run -d \
  --name qtt \
  -p 8080:8080 \
  -p 8888:8888 \
  -e SPARQI_ENABLED=true \
  -e SPARQI_LLM_BASE_URL=http://host.docker.internal:4000 \
  -e SPARQI_LLM_API_KEY=anything \
  -e SPARQI_LLM_MODEL=claude-3-5-sonnet-20241022 \
  docker.io/inovexis/qtt:latest
```

**Note**: Use `host.docker.internal` to access LiteLLM running on your host machine from within Docker.

#### Option 3: Azure OpenAI

```bash
docker run -d \
  --name qtt \
  -p 8080:8443 \
  -p 8888:8888 \
  -e SPARQI_ENABLED=true \
  -e SPARQI_LLM_BASE_URL=https://your-resource.openai.azure.com/openai/deployments/your-deployment \
  -e SPARQI_LLM_API_KEY=your-azure-key \
  -e SPARQI_LLM_MODEL=your-deployment-name \
  docker.io/inovexis/qtt:latest
```

#### Option 4: Ollama (via LiteLLM)

Run Ollama locally and proxy through LiteLLM for consistent API interface:

```bash
# Start Ollama
ollama serve

# Pull a model
ollama pull llama3.1

# Configure in litellm_config.yaml (see Option 2)
# Then use model name in QTT configuration
-e SPARQI_LLM_MODEL=llama3
```

### 5.2 Performance Tuning

Configure SPARQi behavior with these environment variables:

```bash
# Timeout for LLM requests (seconds)
SPARQI_LLM_TIMEOUT=120

# Model temperature (0.0 = deterministic, 1.0 = creative)
SPARQI_LLM_TEMPERATURE=0.7

# Maximum tokens per response
SPARQI_LLM_MAX_TOKENS=4000

# Session timeout (minutes of inactivity)
SPARQI_SESSION_TIMEOUT=60

# Max conversation history messages
SPARQI_MAX_CONVO_HISTORY=50
```

**Recommendations:**
- **For code generation**: Lower temperature (0.3-0.5)
- **For explanations**: Higher temperature (0.7-0.9)
- **For long conversations**: Increase max history to 100
- **For slow networks**: Increase timeout to 180 seconds

### 5.3 Using SPARQi in the Web UI

#### Accessing SPARQi

1. **Navigate to Routes** → **Configure an existing route**
2. Look for the **SPARQi** chat icon in the top-right corner
3. Click to open the chat panel

The chat panel will:
- Slide in from the right side
- Show your current route context
- Display a welcome message
- Be ready for your questions

#### SPARQi Features

**Context Display:**
- Click "View Context" to see what SPARQi knows about your route:
  - Route ID and description
  - Current Freemarker template
  - GraphMart URI and layers
  - Number of ontology elements cached

**Ontology Visualization:**
- Click the ontology element count to open a dialog
- See all classes, properties, and individuals
- Organized by type with descriptions

**Conversation Features:**
- **Markdown rendering**: Formatted responses with code blocks
- **Code copy buttons**: One-click copy of SPARQL/Freemarker snippets
- **Message history**: Scroll through past conversations
- **Clear chat**: Start fresh while keeping context
- **Persistent sessions**: Conversations saved in browser localStorage

#### Example Conversations

**Exploring the Ontology:**
```
You: What classes are available in this ontology?

SPARQi: Based on your GraphMart, here are the available classes:

- foaf:Person - Represents people
- foaf:Organization - Represents organizations
- foaf:Document - Represents documents
...

You can use these in your CONSTRUCT queries to specify what types of entities you're querying for.
```

**Finding Relationships:**
```
You: How can I connect a Person to an Organization?

SPARQi: There are several properties you can use:

1. **foaf:member** - Indicates a person is a member of an organization
   Domain: foaf:Person
   Range: foaf:Organization

2. **foaf:worksFor** - Indicates employment relationship
   Domain: foaf:Person
   Range: foaf:Organization

Here's an example query:
... [code block with query]
```

**Template Assistance:**
```
You: Help me add a date filter to this query

SPARQi: I can see your current template. To add a date filter, you can use Freemarker's conditional logic with SPARQL FILTER:

... [provides specific code for your template]
```

### 5.4 SPARQi Tools and Capabilities

SPARQi has access to intelligent tools that it automatically uses to answer your questions:

#### Tool 1: lookupOntologyElements
Searches ontology for specific concepts.

**Example triggers:**
- "Find properties related to dates"
- "Search for organization-related classes"
- "What properties connect documents to people?"

#### Tool 2: getAllClasses
Lists all available OWL/RDFS classes.

**Example triggers:**
- "What entity types exist?"
- "Show me all classes"
- "What can I query for?"

#### Tool 3: getAllProperties
Retrieves all properties with domain/range information.

**Example triggers:**
- "What properties are available?"
- "Show me object properties"
- "List all datatype properties"

#### Tool 4: getIndividuals
Retrieves named individuals (instances).

**Example triggers:**
- "Show me example data"
- "What individuals exist?"
- "Give me sample instances"

#### Tool 5: getPropertyDetails
Deep dive into specific properties.

**Example triggers:**
- "Tell me more about foaf:knows"
- "What's the domain and range of this property?"
- "Property details for http://example.org/hasMember"

**Note**: Tool usage is transparent—SPARQi automatically decides when to use them.

### 5.5 SPARQi API Reference

For programmatic access, SPARQi provides a REST API.

**Base Path:** `/api/sparqi`

#### Start a Session

```bash
curl -X POST "http://localhost:8080/queryrest/api/sparqi/session?routeId=people-search&userId=alice" -k
```

**Response:**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "routeId": "people-search",
  "userId": "alice",
  "createdAt": "2025-10-18T10:00:00.000Z",
  "welcomeMessage": "Hi! I'm SPARQi, your SPARQL query assistant..."
}
```

#### Send a Message

```bash
curl -X POST "http://localhost:8080/queryrest/api/sparqi/session/{sessionId}/message" \
  -H "Content-Type: application/json" \
  -d '{"message": "Help me query all Person instances"}' \
  -k
```

**Response:**
```json
{
  "role": "ASSISTANT",
  "content": "I can help you with that! Here's a SPARQL CONSTRUCT query...",
  "timestamp": "2025-10-18T10:01:00.000Z"
}
```

#### Get Conversation History

```bash
curl -X GET "http://localhost:8080/queryrest/api/sparqi/session/{sessionId}/history" -k
```

#### Get Route Context

```bash
curl -X GET "http://localhost:8080/queryrest/api/sparqi/session/{sessionId}/context" -k
```

#### End Session

```bash
curl -X DELETE "http://localhost:8080/queryrest/api/sparqi/session/{sessionId}" -k
```

#### Health Check

```bash
curl -X GET "http://localhost:8080/queryrest/api/sparqi/health" -k
```

### 5.6 Troubleshooting SPARQi

**SPARQi button not appearing:**
- Verify `SPARQI_ENABLED=true`
- Check health endpoint: `/api/sparqi/health`
- Review Karaf logs: `docker logs qtt | grep sparqi`

**LLM timeouts:**
- Increase `SPARQI_LLM_TIMEOUT`
- Check network connectivity to LLM provider
- Try a different model (smaller/faster)

**Incorrect or irrelevant responses:**
- Lower temperature for more deterministic output
- Clear cache and restart session
- Ensure ontology is cached (check context display)

**Session lost:**
- Sessions timeout after inactivity (default: 60 minutes)
- Refresh page to start new session
- Check browser localStorage (key: `sparqi-session-{routeId}`)

---

## 6. Web UI Guide

The Query Templating Tool provides a rich Angular-based web interface for managing all aspects of your query routes and datasources.

### 6.1 Navigation Overview

The application uses a left sidebar navigation with four main sections:

- **Metrics**: Performance monitoring and analytics dashboard
- **Routes**: Query route management
- **DataSources**: Graph database connection management  
- **Settings**: System information and statistics

### 6.2 DataSources Page

The DataSources page displays all configured Anzo graph database connections as cards.

#### DataSource Card Information

Each card shows:
- **Title**: DataSource ID
- **Status Badge**: Health indicator with color coding
  - 🟢 **UP** (Green checkmark) - Healthy and accessible
  - 🔴 **DOWN** (Red X) - Unhealthy, connection failed
  - 🔴 **DISABLED** (Red block) - Manually disabled
  - 🔄 **CHECKING** (Rotating sync) - Health check in progress
  - ⚪ **UNKNOWN** (Gray help icon) - Status not yet determined
- **URL**: Link to the GraphStudio interface
- **Timeout**: Connection timeout in seconds
- **Max Query Header Length**: Amount of the generated query that will be returned as a header on the response
- **Last Health Check**: Timestamp of last health verification

#### Adding a DataSource

1. Click **"Add DataSource"** button (top-right)
2. Fill in the modal form:
   - DataSource ID (unique identifier)
   - URL (Anzo GraphStudio endpoint)
   - Timeout (seconds)
   - Max Query Header Length
   - Username and Password
   - Validate Certificate checkbox
3. Click **"Test Connection"** to verify connectivity
4. Click **"Save"** to create the datasource

#### Configuring a DataSource

Click the menu button on any datasource card to access configuration.

**Configuration Tab:**
- Edit connection details
- Update credentials
- Modify timeout settings
- Enable/Disable datasource
- Trigger manual health check
- Delete datasource (with impact warnings)

**Usage Tab:**
- View all routes using this datasource
- See aggregate metrics:
  - Total Routes
  - Total Exchanges
  - Average Latency
  - Success Rate
  - Failed Exchanges
- Searchable/sortable route table with performance data

#### Health Monitoring

Datasources are automatically health-checked based on scheduler configuration (default: every 60 seconds).

**Health indicators include:**
- Last check timestamp
- Consecutive failure count
- Error messages from failed checks
- Routes affected by unhealthy datasources

**Health Actions:**
- **Enable**: Re-enable a manually disabled datasource
- **Disable**: Manually disable a datasource (stops associated routes)
- **Trigger Health Check**: Force immediate health verification

### 6.3 Routes Page

The Routes page displays all configured query endpoints in a searchable, sortable table.

#### Route Table Columns

- **Status Icon**: Visual indicator of route and datasource status
- **Route ID**: Unique identifier (sortable)
- **Endpoint URL**: Full query path (http://localhost:8888/{route-id})
- **Description**: User-friendly description (sortable)
- **DataSource ID**: Associated datasource with health warnings
- **Actions Menu**: Three-dot menu for route operations

#### Route Status Indicators

- ✅ **Green checkmark** - Route started and healthy
- 🛑 **Red stop circle** - Route stopped
- ⚠️ **Orange warning** - Datasource down (route may fail)
- 🚫 **Red block** - Datasource disabled (route operations restricted)

#### Route Actions Menu

Each route provides these actions:

1. **Configure** - Open route editor with template and settings
2. **Turn On/Off** - Start or stop the route dynamically
3. **Clone** - Duplicate route for quick template variations
4. **Delete** - Remove route with confirmation dialog

**Note**: Start/stop actions are disabled if the datasource is unhealthy or disabled.

#### Adding a Route

1. Click **"Add Route"** button (top-right)
2. Fill in the full-screen dialog:
   - **Route ID**: Endpoint name (becomes `/{route-id}`)
   - **HTTP Methods**: Select GET, POST, PUT, PATCH, DELETE
   - **Description**: User-friendly description
   - **Datasource**: Select from dropdown
   - **GraphMart**: Autocomplete selector (fetched from datasource)
   - **GraphMart Layers** (optional): Chip-based multi-select
   - **Template**: Freemarker SPARQL template (Monaco editor)

3. **Monaco Editor Features:**
   - Freemarker2 syntax highlighting
   - Dark theme
   - Smart autocomplete for ontology elements
   - File upload button for existing templates
   - Context-aware suggestions (triggered by typing)

4. Click **"Save"** to create the route

#### Configuring a Route

Click **"Configure"** from the route's action menu.

**Configuration Screen Features:**

- **Datasource Status Warning Banner**:
  - Shows if datasource is DOWN or DISABLED
  - Displays error details and last check time
  - Provides link to datasource configuration

- **SPARQi AI Assistant** (if enabled):
  - Chat panel button in top-right corner
  - Opens resizable side panel (300-800px width)
  - Provides natural language help with templates
  - Shows route context and ontology information

- **Template Editor**:
  - Full Monaco editor with Freemarker2 support
  - Fullscreen mode toggle
  - File upload for templates
  - Ontology autocomplete provider
  - Syntax highlighting

- **Form Fields**:
  - All fields from Add Route dialog
  - Editable without stopping the route
  - Changes take effect immediately on save

- **Cache Settings** (expandable panel):
  - **Cache Enabled**: Toggle to enable/disable caching for this specific route
  - **Cache TTL**: Time-to-live in seconds (leave blank to use global default of 3600s)
  - **Cache Statistics** (live display when cache enabled):
    - 📊 **Entries**: Number of cached results for this route
    - 📈 **Hit Rate**: Percentage of requests served from cache
    - 🌐 **Host:Port**: Redis server connection information
    - 🗜️ **Compression**: Whether gzip compression is enabled
  - **Clear Cache**: Button to invalidate all cached results for this route
    - Shows confirmation dialog before deletion
    - Displays count of deleted cache entries
    - Automatically refreshes cache statistics

**Cache Behavior Notes:**
- Cache is checked AFTER Freemarker template processing, BEFORE Anzo query execution
- Cache key is generated using SHA-256 hash of: `query + graphmart + layers`
- Format: `{prefix}{routeId}:{hash}` (e.g., `qtt:cache:my-route:a3f8...`)
- Cache misses execute normal query flow and store result for future requests
- Cache hits skip Anzo query entirely, returning cached JSON-LD result
- Per-route caching requires global `REDIS_ENABLED=true` and Redis connection

#### Cloning a Route

The Clone feature creates a copy of an existing route:

1. Click **Clone** from the route's action menu
2. System creates new route with ID: `{original-id}-copy`
3. All configuration and template content is duplicated
4. New route starts in "Stopped" state
5. Edit the cloned route to customize

### 6.4 Metrics Page

The Metrics page provides comprehensive performance monitoring and analytics.

#### Summary Cards (Top Row)

- **Total Endpoints**: Count of all configured routes
- **Avg Processing Time**: Mean processing time across all routes
- **Busiest Route**: Route with highest exchange count
- **Success Rate Gauge**: Linear gauge showing overall API success percentage

#### Analytics Tabs

**Exchanges Tab:**
- Stacked bar chart visualization
- Shows successful vs failed exchanges per route
- Filters:
  - Datasource autocomplete filter
  - Route multi-select filter
- Color-coded: Green (success), Red (failure)

**Latency Tab:**
- 2D vertical grouped bar chart
- Displays Min/Max/Avg processing times per route
- Same filtering as Exchanges tab
- Helps identify performance bottlenecks

**Trends Tab:**
- Line chart with historical metric tracking
- Metric selector dropdown (8 options):
  - Total Processing Time
  - Min Processing Time
  - Max Processing Time
  - Mean Processing Time
  - Completed Exchanges
  - Total Exchanges
  - Inflight Exchanges
  - Failed Exchanges
- Time-series data visualization
- Useful for spotting performance trends

**Table Tab:**
- Comprehensive sortable/searchable data table
- Columns:
  - Route Name (sortable)
  - Processing Time (Min/Max/Avg)
  - Successful Exchanges
  - Failed Exchanges
  - State (Started/Stopped)
  - Uptime (formatted duration)
- Search box for filtering
- Pagination (5/10/25/50 rows per page)
- Export-friendly view

### 6.5 Settings Page

The Settings page displays system information and statistics.

#### System Information Card

- **Version**: Application version number
- **System Uptime**: Formatted uptime duration
- **Database Type**: Active database driver (Derby/PostgreSQL/SQL Server)
- **Remote Database Address**: JDBC connection URL

#### Application Stats Card

- **Total DataSources**: Count of configured datasources
- **Total Routes**: Count of configured routes

#### Ontology Cache Statistics Card

- **Cache Size**: Number of cached route ontologies
- **Hit Count**: Cache hits (ontology found in cache)
- **Miss Count**: Cache misses (ontology fetched from source)
- **Hit Rate**: Percentage of requests served from cache
- **Eviction Count**: Number of evicted cache entries
- **Total Load Time**: Cumulative time spent loading ontologies
  - Formatted in ms or seconds based on magnitude

This information helps understand ontology service performance and cache effectiveness.

#### Query Result Cache Statistics Card

Displays real-time Redis cache performance metrics for query result caching (separate from ontology caching):

- **Cache Status**: Connected/Disconnected with Redis host:port information
- **Cache Type**: "redis" when connected, "noop" when disabled or unavailable
- **Total Entries**: Number of cached query results across all routes
- **Hit Rate**: Percentage of queries served from cache vs database
- **Hit Count**: Total number of successful cache retrievals
- **Miss Count**: Total number of cache misses requiring database queries
- **Error Count**: Number of cache operation failures (should be 0)
- **Evictions**: Number of cache entries evicted due to memory constraints
- **Compression**: Whether gzip compression is enabled for cached values
- **Fail-Open Mode**: Whether cache errors allow queries to proceed (recommended: true)

**Key Performance Indicators:**

- **High Hit Rate (>70%)**: Cache is effective, reducing database load
- **Low Hit Rate (<30%)**: Consider increasing cache TTL or investigating query patterns
- **High Evictions**: Consider increasing Redis memory limit or reducing TTL
- **Non-Zero Errors**: Investigate Redis connectivity or configuration issues

**Cache Not Available:**

If cache shows as "Not Available", check:
1. `REDIS_ENABLED=true` environment variable is set
2. Redis server is running and accessible
3. Redis credentials are correct (if authentication enabled)
4. Network connectivity between QTT and Redis

### 6.6 UI Tips and Best Practices

**Searching and Filtering:**
- All tables support instant search filtering
- Search is case-insensitive and searches all columns
- Use datasource filters in metrics to isolate performance by backend

**Performance Optimization:**
- Monitor the Metrics page regularly
- Use the Latency tab to identify slow queries
- Clone working routes rather than starting from scratch
- Test connection before saving datasources

**Health Management:**
- Set up health check thresholds for critical datasources
- Monitor consecutive failures to catch intermittent issues
- Disable datasources during maintenance windows

**Template Development:**
- Use SPARQi for ontology exploration
- Start with simple templates and iterate
- Test templates with curl before deploying to production
- Use fullscreen mode for complex template editing

---

## 7. Template Development

Freemarker templates are the heart of QTT, transforming simple requests into complex SPARQL queries.

### 7.1 Freemarker Basics for SPARQL

Freemarker is a template engine that processes directives and expressions to generate text output. In QTT, templates generate SPARQL queries.

#### Template Structure

```freemarker
PREFIX declarations

CONSTRUCT or SELECT {
  template content
}
WHERE {
  query patterns
  
  <#if parameter??>
  conditional SPARQL
  </#if>
}
<#if limit??>
LIMIT ${limit}
</#if>
```

#### Key Concepts

**1. Parameter Access**: `${paramName}`
```freemarker
FILTER(CONTAINS(?name, "${searchTerm}"))
```

**2. Conditional Blocks**: `<#if>...</#if>`
```freemarker
<#if startDate??>
FILTER(?date >= "${startDate}"^^xsd:date)
</#if>
```

**3. Parameter Existence Check**: `paramName??`
```freemarker
<#if email??>
  ?person foaf:mbox "${email}" .
</#if>
```

**4. String Transformations**
```freemarker
${name?lower_case}    # Convert to lowercase
${name?upper_case}    # Convert to uppercase
${name?trim}          # Remove whitespace
${name?url}           # URL encode
```

**5. Default Values**
```freemarker
${limit!'10'}         # Use '10' if limit not provided
```

### 7.2 Common Template Patterns

#### Pattern 1: Simple Search with Optional Filter

```freemarker
PREFIX foaf: <http://xmlns.com/foaf/0.1/>

CONSTRUCT {
  ?person foaf:name ?name .
}
WHERE {
  ?person a foaf:Person ;
    foaf:name ?name .
  
  <#if searchTerm??>
  FILTER(CONTAINS(LCASE(STR(?name)), "${searchTerm?lower_case}"))
  </#if>
}
<#if limit??>
LIMIT ${limit}
</#if>
```

**Usage:**
```bash
# All people
GET /people-search

# Filtered search
GET /people-search?searchTerm=john&limit=5
```

#### Pattern 2: Multi-Parameter Filtering

```freemarker
PREFIX ex: <http://example.org/>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

CONSTRUCT {
  ?doc ex:title ?title ;
    ex:author ?author ;
    ex:date ?date .
}
WHERE {
  ?doc a ex:Document ;
    ex:title ?title .
  
  <#if author??>
  ?doc ex:author ?author .
  FILTER(CONTAINS(LCASE(STR(?author)), "${author?lower_case}"))
  </#if>
  
  <#if startDate??>
  ?doc ex:date ?date .
  FILTER(?date >= "${startDate}"^^xsd:date)
  </#if>
  
  <#if endDate??>
  ?doc ex:date ?date .
  FILTER(?date <= "${endDate}"^^xsd:date)
  </#if>
}
<#if limit??>
LIMIT ${limit}
</#if>
```

#### Pattern 3: List Parameters

```freemarker
PREFIX ex: <http://example.org/>

CONSTRUCT {
  ?item ex:name ?name ;
    ex:type ?type .
}
WHERE {
  ?item a ex:Item ;
    ex:name ?name ;
    ex:type ?type .
  
  <#if types??>
  <#assign typeList = types?split(",")>
  FILTER(?type IN (<#list typeList as type>"${type}"<#if type_has_next>, </#if></#list>))
  </#if>
}
```

**Usage:**
```bash
GET /items?types=TypeA,TypeB,TypeC
```

#### Pattern 4: Optional Properties

```freemarker
PREFIX foaf: <http://xmlns.com/foaf/0.1/>

CONSTRUCT {
  ?person foaf:name ?name .
  <#if includeEmail?? && includeEmail == "true">
  ?person foaf:mbox ?email .
  </#if>
  <#if includePhone?? && includePhone == "true">
  ?person foaf:phone ?phone .
  </#if>
}
WHERE {
  ?person a foaf:Person ;
    foaf:name ?name .
  
  <#if includeEmail?? && includeEmail == "true">
  OPTIONAL { ?person foaf:mbox ?email . }
  </#if>
  
  <#if includePhone?? && includePhone == "true">
  OPTIONAL { ?person foaf:phone ?phone . }
  </#if>
}
```

### 7.3 Using Ontology Autocomplete

The Monaco editor provides context-aware autocomplete for ontology elements.

**Trigger Autocomplete:**
- Type any character in the editor
- Press `Ctrl+Space`

**Autocomplete Provides:**
- **Classes**: All OWL/RDFS classes from your GraphMart
- **Properties**: Object properties, datatype properties, annotation properties
- **Individuals**: Named instances

**Example:**
```
Type: PREFIX foaf: <http://xmlns.com/foaf/
[Autocomplete suggests URIs]

Type: ?person a foaf:
[Autocomplete suggests: Person, Organization, Agent, etc.]

Type: ?person foaf:
[Autocomplete suggests: name, mbox, knows, member, etc.]
```

### 7.4 Parameter Handling

#### GET Parameters (Query String)

Parameters from URL query string are automatically available:

```bash
GET /my-route?name=John&age=30
```

Template access:
```freemarker
${headers.name}  # "John"
${headers.age}   # "30"
```

#### POST Parameters (JSON Body)

Parameters from JSON body are automatically extracted:

```bash
POST /my-route
Content-Type: application/json

{
  "name": "John",
  "age": 30
}
```

Template access (same as GET):
```freemarker
${body.name}  # "John"
${body.age}   # 30
```

#### Parameter Type Considerations

**All parameters are strings by default.** For type-safe queries:

```freemarker
# Integer
FILTER(?age = ${age})

# String (needs quotes)
FILTER(?name = "${name}")

# Date (with type cast)
FILTER(?date >= "${startDate}"^^xsd:date)

# Boolean
<#if includeInactive == "true">
  # Include inactive items
</#if>
```

### 7.5 Best Practices

#### 1. Always Validate Parameters

```freemarker
<#if !limit?? || limit?number < 1 || limit?number > 1000>
  <#assign limit = "100">
</#if>
LIMIT ${limit}
```

#### 2. Escape User Input

Freemarker automatically escapes strings, but be cautious with direct injection:

```freemarker
# GOOD - Freemarker handles escaping
FILTER(CONTAINS(?name, "${searchTerm}"))

# BAD - Direct injection risk
FILTER(CONTAINS(?name, ${searchTerm}))  # Missing quotes!
```

#### 3. Use Conditional OPTIONAL Clauses

```freemarker
# Efficient - only includes OPTIONAL when needed
<#if includeEmail??>
OPTIONAL { ?person foaf:mbox ?email . }
</#if>

# Inefficient - always includes OPTIONAL
OPTIONAL { ?person foaf:mbox ?email . }
```

#### 4. Provide Sensible Defaults

```freemarker
<#assign maxResults = limit!'50'>
<#assign offset = offset!'0'>

LIMIT ${maxResults}
OFFSET ${offset}
```

#### 5. Document Your Templates

```freemarker
#
#  Route: people-search
#  Description: Searches for people by name, email, or organization
#  
#  Parameters:
#    - name (optional): Person name filter
#    - email (optional): Email address filter
#    - org (optional): Organization name filter
#    - limit (optional, default=50): Maximum results
#  
#  Example: /people-search?name=john&limit=10
#
PREFIX foaf: <http://xmlns.com/foaf/0.1/>
...
```

#### 6. Test Incrementally

1. Start with a basic CONSTRUCT query without parameters
2. Test manually with curl
3. Add one parameter at a time
4. Test each addition
5. Use SPARQi to validate complex logic

#### 7. Optimize Query Performance

```freemarker
# Use specific graph patterns
?person a foaf:Person .  # More specific than ?person a ?type

# Limit early in complex queries
{
  SELECT DISTINCT ?person WHERE {
    ?person a foaf:Person .
    <#if name??>
    ?person foaf:name ?name .
    FILTER(CONTAINS(?name, "${name}"))
    </#if>
  }
  LIMIT ${limit!'100'}
}

# Use OPTIONAL sparingly
OPTIONAL { ?person foaf:mbox ?email . }  # Only if truly optional
```

### 7.6 Debugging Templates

**Check Generated SPARQL:**

Enable Karaf logging to see the generated SPARQL queries:

```bash
# In Karaf console
log:set DEBUG com.inovexcorp.queryservice

# View logs
docker logs -f qtt
```

**Common Issues:**

1. **Missing quotes around string parameters**
   ```freemarker
   # Wrong
   FILTER(?name = ${name})
   
   # Correct
   FILTER(?name = "${name}")
   ```

2. **Unclosed Freemarker directives**
   ```freemarker
   # Wrong
   <#if param??>
   FILTER clause
   # Missing </#if>
   
   # Correct
   <#if param??>
   FILTER clause
   </#if>
   ```

3. **Type mismatches**
   ```freemarker
   # Wrong - limit is a string
   LIMIT ${limit}
   
   # Correct - convert to number if validating
   <#assign limitNum = limit?number>
   LIMIT ${limitNum}
   ```

---

## 8. API Reference

QTT provides comprehensive RESTful APIs for all functionality.

**Base URL:** `http://localhost:8080/queryrest/api`

**Authentication:** Currently no authentication (HTTP only)

**Content-Type:** `application/json` for request/response bodies

**SSL:** Self-signed certificate by default (use `-k` flag with curl)

### 8.1 DataSources API

#### List All DataSources

```bash
GET /datasources
```

**Response:**
```json
[
  {
    "id": "my-anzo",
    "url": "http://anzo:8080",
    "timeout": 30,
    "maxQueryHeaderLength": 8192,
    "username": "admin",
    "password": "***",
    "validateCertificate": true,
    "status": "UP",
    "lastHealthCheck": "2025-10-18T10:00:00.000Z",
    "consecutiveFailures": 0
  }
]
```

#### Get Single DataSource

```bash
GET /datasources/{id}
```

#### Create DataSource

```bash
POST /datasources
Content-Type: application/json

{
  "id": "my-anzo",
  "url": "http://anzo:8080",
  "timeout": 30,
  "maxQueryHeaderLength": 8192,
  "username": "admin",
  "password": "password",
  "validateCertificate": true
}
```

#### Update DataSource

```bash
PUT /datasources/{id}
Content-Type: application/json

{
  "url": "http://new-anzo:8080",
  "timeout": 60,
  ...
}
```

#### Delete DataSource

```bash
DELETE /datasources/{id}
```

#### Test Connection

```bash
POST /datasources/{id}/test
```

**Response:**
```json
{
  "success": true,
  "message": "Connection successful",
  "timestamp": "2025-10-18T10:00:00.000Z"
}
```

#### Enable/Disable DataSource

```bash
POST /datasources/{id}/enable
POST /datasources/{id}/disable
```

#### Trigger Health Check

```bash
POST /datasources/{id}/health-check
```

### 8.2 Routes API

#### List All Routes

```bash
GET /routes
```

**Response:**
```json
[
  {
    "id": "people-search",
    "description": "Search for people",
    "datasourceId": "my-anzo",
    "graphMartUri": "http://example.org/graphmart",
    "layerUris": ["http://example.org/layer1"],
    "template": "PREFIX foaf: ...",
    "httpMethods": ["GET", "POST"],
    "state": "Started",
    "uptime": 3600000
  }
]
```

#### Get Single Route

```bash
GET /routes/{id}
```

#### Create Route

```bash
POST /routes
Content-Type: application/json

{
  "id": "people-search",
  "description": "Search for people",
  "datasourceId": "my-anzo",
  "graphMartUri": "http://example.org/graphmart",
  "layerUris": ["http://example.org/layer1"],
  "template": "PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n\nCONSTRUCT { ?person foaf:name ?name . }\nWHERE { ?person a foaf:Person ; foaf:name ?name . }",
  "httpMethods": ["GET", "POST"]
}
```

#### Update Route

```bash
PUT /routes/{id}
Content-Type: application/json

{
  "description": "Updated description",
  "template": "...",
  ...
}
```

**Note:** Route changes take effect immediately without restart.

#### Delete Route

```bash
DELETE /routes/{id}
```

#### Start/Stop Route

```bash
POST /routes/{id}/start
POST /routes/{id}/stop
```

#### Clone Route

```bash
POST /routes/{id}/clone
```

Creates a new route with ID `{id}-copy`.

### 8.3 GraphMarts and Layers API

#### Get GraphMarts for DataSource

```bash
GET /datasources/{datasourceId}/graphmarts
```

**Response:**
```json
[
  {
    "uri": "http://example.org/graphmart1",
    "label": "GraphMart 1",
    "status": "active"
  }
]
```

#### Get Layers for GraphMart

```bash
GET /layers?graphMartUri={graphMartUri}&datasourceId={datasourceId}
```

**Response:**
```json
[
  {
    "uri": "http://example.org/layer1",
    "label": "Layer 1",
    "status": "active"
  }
]
```

#### Get Layers for Route

```bash
GET /routes/{routeId}/layers
```

### 8.4 Metrics API

#### Get All Route Metrics

```bash
GET /metrics
```

**Response:**
```json
[
  {
    "routeId": "people-search",
    "processingTime": {
      "min": 45,
      "max": 320,
      "mean": 127
    },
    "exchanges": {
      "completed": 1543,
      "failed": 12,
      "total": 1555,
      "inflight": 0
    },
    "state": "Started",
    "uptime": 86400000
  }
]
```

#### Get Metrics for Specific Route

```bash
GET /metrics/routes/{routeId}
```

#### Get Metrics for DataSource

```bash
GET /metrics/datasources/{datasourceId}
```

#### Get Historical Metrics

```bash
GET /metrics/history?routeId={routeId}&metric={metricName}&from={timestamp}&to={timestamp}
```

**Metric Names:**
- `totalProcessingTime`
- `minProcessingTime`
- `maxProcessingTime`
- `meanProcessingTime`
- `completedExchanges`
- `totalExchanges`
- `inflightExchanges`
- `failedExchanges`

### 8.5 Settings API

#### Get System Information

```bash
GET /settings
```

**Response:**
```json
{
  "version": "1.0.0",
  "uptime": 86400000,
  "database": {
    "type": "PostgreSQL JDBC Driver",
    "url": "jdbc:postgresql://localhost:5432/qtt"
  },
  "stats": {
    "totalDataSources": 3,
    "totalRoutes": 12
  },
  "ontologyCache": {
    "size": 12,
    "hitCount": 543,
    "missCount": 67,
    "hitRate": 0.89,
    "evictionCount": 3,
    "totalLoadTime": 45678
  }
}
```

### 8.6 Query Execution API

Dynamically created endpoints based on your routes.

**Base URL:** `http://localhost:8888`

#### Execute Query (GET)

```bash
GET /{route-id}?param1=value1&param2=value2
```

**Example:**
```bash
curl "http://localhost:8888/people-search?name=john&limit=10"
```

#### Execute Query (POST)

```bash
POST /{route-id}
Content-Type: application/json

{
  "param1": "value1",
  "param2": "value2"
}
```

**Example:**
```bash
curl -X POST "http://localhost:8888/people-search" \
  -H "Content-Type: application/json" \
  -d '{"name": "john", "limit": 10}'
```

**Response Format:** JSON-LD

```json
{
  "@context": {...},
  "@graph": [
    {
      "@id": "http://example.org/person/1",
      "@type": "http://xmlns.com/foaf/0.1/Person",
      "http://xmlns.com/foaf/0.1/name": "John Doe"
    }
  ]
}
```

### 8.7 SPARQi API

See [Section 5.5](#55-sparqi-api-reference) for complete SPARQi API documentation.

### 8.8 Cache Management API

Endpoints for managing Redis query result cache.

**Base URL:** `http://localhost:8080/queryrest/api/routes`

#### Clear Cache for Specific Route

Delete all cached query results for a specific route.

```bash
DELETE /queryrest/api/routes/{routeId}/cache
```

**Example:**
```bash
curl -X DELETE "http://localhost:8080/queryrest/api/routes/people-search/cache" -k
```

**Success Response (200 OK):**
```json
{
  "routeId": "people-search",
  "deletedCount": 42,
  "message": "Cache cleared successfully"
}
```

**Error Response (404 Not Found):**
```json
{
  "error": "Route not found: people-search"
}
```

**Error Response (503 Service Unavailable):**
```json
{
  "error": "Cache service not available"
}
```

#### Clear All Cache Entries

Delete all cached query results for all routes.

```bash
DELETE /queryrest/api/routes/cache
```

**Example:**
```bash
curl -X DELETE "http://localhost:8080/queryrest/api/routes/cache" -k
```

**Success Response (200 OK):**
```json
{
  "deletedCount": 157,
  "message": "All cache entries cleared successfully"
}
```

**Use Cases:**
- Clear cache after data updates in backend
- Reset cache during troubleshooting
- Free memory when Redis is reaching capacity

#### Get Cache Statistics for Specific Route

Retrieve cache statistics and entry count for a specific route.

```bash
GET /queryrest/api/routes/{routeId}/cache/stats
```

**Example:**
```bash
curl "http://localhost:8080/queryrest/api/routes/people-search/cache/stats" -k
```

**Success Response (200 OK):**
```json
{
  "routeId": "people-search",
  "cacheEnabled": true,
  "cacheTtlSeconds": 3600,
  "routeKeyCount": 42,
  "globalStats": {
    "hits": 1543,
    "misses": 234,
    "errors": 0,
    "evictions": 12,
    "keyCount": 157,
    "memoryUsageBytes": 0,
    "hitRatio": 0.8683
  }
}
```

**Response Fields:**
- `routeId`: The route identifier
- `cacheEnabled`: Whether caching is enabled for this route
- `cacheTtlSeconds`: Time-to-live for cache entries (seconds)
- `routeKeyCount`: Number of cached entries for this specific route
- `globalStats`: Overall cache statistics across all routes
  - `hits`: Total cache hits (queries served from cache)
  - `misses`: Total cache misses (queries executed against database)
  - `errors`: Number of cache operation errors
  - `evictions`: Number of entries evicted due to TTL or memory limits
  - `keyCount`: Total cached entries across all routes
  - `hitRatio`: Cache hit rate (0.0 to 1.0)

**Error Response (404 Not Found):**
```json
{
  "error": "Route not found: people-search"
}
```

#### Get Global Cache Information

Retrieve global cache connection information and statistics.

```bash
GET /queryrest/api/routes/cache/info
```

**Example:**
```bash
curl "http://localhost:8080/queryrest/api/routes/cache/info" -k
```

**Success Response (200 OK) - Cache Available:**
```json
{
  "available": true,
  "info": {
    "enabled": true,
    "connected": true,
    "type": "redis",
    "host": "localhost",
    "port": 6379,
    "database": 0,
    "keyPrefix": "qtt:cache:",
    "defaultTtlSeconds": 3600,
    "compressionEnabled": true,
    "failOpen": true,
    "errorMessage": null
  },
  "stats": {
    "hits": 1543,
    "misses": 234,
    "errors": 0,
    "evictions": 12,
    "keyCount": 157,
    "memoryUsageBytes": 0,
    "hitRatio": 0.8683
  }
}
```

**Response (200 OK) - Cache Not Configured:**
```json
{
  "available": false,
  "message": "Cache service not configured"
}
```

**Info Fields:**
- `enabled`: Whether Redis caching is enabled globally (from config)
- `connected`: Whether connection to Redis server is active
- `type`: Cache implementation type ("redis" or "noop")
- `host`: Redis server hostname
- `port`: Redis server port
- `database`: Redis database number (0-15)
- `keyPrefix`: Prefix for all cache keys (e.g., "qtt:cache:")
- `defaultTtlSeconds`: Default time-to-live for cache entries
- `compressionEnabled`: Whether GZIP compression is enabled for cached values
- `failOpen`: Whether cache failures allow queries to proceed (true) or fail (false)
- `errorMessage`: Last error message if connection failed (null if healthy)

**Use Cases:**
- Health monitoring and alerting
- Verifying cache configuration
- Troubleshooting connection issues
- Monitoring cache performance

---

## 9. Monitoring & Troubleshooting

### 9.1 Health Monitoring

#### DataSource Health Checks

**Automatic Checks:**
- Default interval: Every 60 seconds
- Configured in: `com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.cfg`

**Health States:**
- **UP**: Connection successful
- **DOWN**: Connection failed
- **DISABLED**: Manually disabled
- **CHECKING**: Health check in progress
- **UNKNOWN**: No check performed yet

**Consecutive Failure Handling:**

Configure automatic route stopping after N failures:

```properties
# In DatasourceHealthCheck.cfg
consecutiveFailureThreshold=5  # Stop routes after 5 consecutive failures
# or
consecutiveFailureThreshold=-1  # Disable automatic stopping
```

**Manual Health Checks:**

Via UI:
1. Navigate to DataSources
2. Click datasource menu
3. Select "Trigger Health Check"

Via API:
```bash
curl -X POST "http://localhost:8080/queryrest/api/datasources/{id}/health-check" -k
```

#### Route Health

Routes inherit health status from their datasource:
- **Healthy Datasource + Started Route** = Fully operational
- **Unhealthy Datasource + Started Route** = May fail
- **Disabled Datasource** = Route operations blocked

### 9.2 Metrics Collection

#### Automatic Collection

**Scheduler:** `com.inovexcorp.queryservice.scheduler.QueryMetrics.cfg`
- Default: Every 1 minute
- Collects via JMX from Apache Camel routes

**Collected Metrics:**
- Processing time (min/max/mean)
- Exchange counts (completed/failed/total/inflight)
- Route state (Started/Stopped)
- Uptime

#### Metrics Cleanup

**Scheduler:** `com.inovexcorp.queryservice.scheduler.CleanMetrics.cfg`
- Default: Every 1 minute
- Removes metrics older than TTL (default: 30 minutes)

**Configuration:**
```properties
minutesToLive=30  # Keep metrics for 30 minutes
```

For longer retention:
```properties
minutesToLive=1440  # 24 hours
minutesToLive=10080  # 7 days
```

### 9.3 Log Management

#### Log Files

**Location (in container):** `/opt/qtt/data/log/karaf.log`

**View logs:**
```bash
# Docker
docker logs qtt

# Podman
podman logs qtt

# Follow logs
docker logs -f qtt

# Inside container
docker exec -it qtt tail -f /opt/qtt/data/log/karaf.log
```

#### Log Levels

**Default:** INFO

**Change log level (runtime):**

Connect to Karaf console:
```bash
docker exec -it qtt bin/client
```

Set log levels:
```bash
# Set specific package to DEBUG
log:set DEBUG com.inovexcorp.queryservice

# Set Camel to WARN
log:set WARN org.apache.camel

# View current log levels
log:list

# Reset to default
log:set INFO
```

**Change log level (configuration):**

Edit `org.ops4j.pax.logging.cfg`:
```properties
log4j2.logger.queryservice.level=DEBUG
log4j2.logger.queryservice.name=com.inovexcorp.queryservice
```

#### Log Rotation

**Policy:** Size-based (16MB per file)

**Configuration in `org.ops4j.pax.logging.cfg`:**
```properties
log4j2.appender.rolling.policies.size.size=16MB
```

### 9.4 Common Issues and Solutions

#### Issue: Route Returns 404

**Symptoms:**
```bash
curl http://localhost:8888/my-route
# 404 Not Found
```

**Solutions:**
1. **Check route exists:**
   ```bash
   curl -k http://localhost:8080/queryrest/api/routes
   ```

2. **Check route is started:**
   ```bash
   curl -k http://localhost:8080/queryrest/api/routes/my-route
   # Look for "state": "Started"
   ```

3. **Start the route:**
   ```bash
   curl -X POST -k http://localhost:8080/queryrest/api/routes/my-route/start
   ```

4. **Check Karaf logs:**
   ```bash
   docker logs qtt | grep my-route
   ```

#### Issue: Route Returns 500 Internal Error

**Symptoms:**
```bash
curl http://localhost:8888/my-route
# 500 Internal Server Error
```

**Solutions:**
1. **Check datasource health:**
   - Via UI: DataSources page, look for DOWN status
   - Via API: `GET /datasources/{id}`

2. **Check template syntax:**
   - Look for unclosed Freemarker directives
   - Check SPARQL syntax
   - Use SPARQi to validate template

3. **Check Karaf logs for errors:**
   ```bash
   docker logs qtt | grep ERROR
   ```

4. **Test datasource connection:**
   ```bash
   curl -X POST -k http://localhost:8080/queryrest/api/datasources/{id}/test
   ```

#### Issue: Datasource Shows as DOWN

**Symptoms:**
- Red X icon on datasource card
- Routes using datasource fail

**Solutions:**
1. **Check datasource URL is accessible:**
   ```bash
   curl http://anzo-server:8080
   ```

2. **Verify credentials:**
   - Update username/password in datasource configuration

3. **Check network connectivity:**
   ```bash
   docker exec -it qtt ping anzo-server
   ```

4. **Review last error message:**
   - UI: Datasource card shows error tooltip
   - API: `GET /datasources/{id}` returns error details

5. **Manually trigger health check:**
   ```bash
   curl -X POST -k http://localhost:8080/queryrest/api/datasources/{id}/health-check
   ```

#### Issue: SPARQi Not Appearing

**Symptoms:**
- No chat button in route configuration
- Health endpoint returns disabled status

**Solutions:**
1. **Verify SPARQi is enabled:**
   ```bash
   docker exec -it qtt grep enableSparqi /opt/qtt/etc/com.inovexcorp.queryservice.sparqi.cfg
   # Should show: enableSparqi=true
   ```

2. **Check LLM configuration:**
   ```bash
   curl -X GET -k http://localhost:8080/queryrest/api/sparqi/health
   ```

3. **Restart container with correct env vars:**
   ```bash
   docker run -d --name qtt \
     -e SPARQI_ENABLED=true \
     -e SPARQI_LLM_BASE_URL=https://api.openai.com/v1 \
     -e SPARQI_LLM_API_KEY=sk-... \
     -e SPARQI_LLM_MODEL=gpt-4o-mini \
     ...
   ```

4. **Check Karaf logs:**
   ```bash
   docker logs qtt | grep -i sparqi
   ```

#### Issue: Query Performance Degradation

**Symptoms:**
- Increasing processing times in Metrics
- Latency trends show upward slope

**Solutions:**
1. **Check metrics trends:**
   - Navigate to Metrics → Trends tab
   - Select "Mean Processing Time"
   - Identify when degradation started

2. **Review query complexity:**
   - Check if template was recently modified
   - Look for missing FILTER optimizations
   - Consider adding LIMIT clauses

3. **Check datasource load:**
   - View datasource usage tab
   - Check if multiple routes overwhelming backend

4. **Optimize template:**
   - Use specific graph patterns
   - Add LIMIT to subqueries
   - Minimize OPTIONAL clauses
   - Use SPARQi for optimization suggestions

5. **Check Anzo backend:**
   - Monitor Anzo server resources
   - Review Anzo query logs
   - Consider index optimization

#### Issue: Database Connection Failed

**Symptoms:**
```
javax.persistence.PersistenceException: Unable to acquire connection
```

**Solutions:**
1. **Check database is running:**
   ```bash
   # PostgreSQL
   docker exec -it qtt-postgres pg_isready
   
   # SQL Server
   docker exec -it qtt-mssql /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P password -Q "SELECT 1"
   ```

2. **Verify JDBC URL:**
   ```bash
   docker exec -it qtt env | grep DB_
   ```

3. **Check network connectivity:**
   ```bash
   docker exec -it qtt ping postgresql
   ```

4. **Review credentials:**
   - Ensure DB_USER and DB_PASSWORD match database

5. **Check connection pool:**
   - Edit `org.ops4j.datasource-qtt.cfg`
   - Increase `jdbc.pool.maxTotal` if needed

#### Issue: Cache Not Available

**Symptoms:**
- Settings page shows "Cache: Disconnected"
- Cache API returns 503 Service Unavailable
- Routes still working but slower than expected

**Solutions:**
1. **Check Redis is running:**
   ```bash
   # Docker Compose
   docker ps | grep redis

   # Podman Compose
   podman ps | grep redis

   # Test connection manually
   docker exec -it qtt-redis redis-cli ping
   # Should return: PONG
   ```

2. **Verify Redis environment variables:**
   ```bash
   docker exec -it qtt env | grep REDIS_
   # Should show:
   # REDIS_ENABLED=true
   # REDIS_HOST=redis
   # REDIS_PORT=6379
   ```

3. **Check Redis health:**
   ```bash
   curl -k http://localhost:8080/queryrest/api/routes/cache/info
   # Look for "connected": false
   ```

4. **Review Karaf logs for cache errors:**
   ```bash
   docker logs qtt | grep -i "cache\|redis"
   # Look for connection errors or timeouts
   ```

5. **Test Redis connectivity from container:**
   ```bash
   docker exec -it qtt ping redis
   # If fails, check Docker network configuration
   ```

6. **Restart Redis container:**
   ```bash
   docker restart qtt-redis
   # Wait for health check to pass
   docker logs qtt-redis
   ```

7. **Check Redis authentication:**
   ```bash
   # If using password
   docker exec -it qtt-redis redis-cli -a YourPassword ping
   # Verify REDIS_PASSWORD env var matches
   ```

#### Issue: Cache Service Returns 503

**Symptoms:**
```bash
curl -X DELETE -k http://localhost:8080/queryrest/api/routes/my-route/cache
# {"error": "Cache service not available"}
```

**Solutions:**
1. **Verify Redis is enabled:**
   ```bash
   docker exec -it qtt cat /opt/qtt/etc/com.inovexcorp.queryservice.cache.cfg | grep redis.enabled
   # Should show: redis.enabled=true
   ```

2. **Check cache bundle is active:**
   ```bash
   docker exec -it qtt bin/client "bundle:list | grep cache"
   # Should show: Active state
   ```

3. **Verify configuration file exists:**
   ```bash
   docker exec -it qtt ls -la /opt/qtt/etc/com.inovexcorp.queryservice.cache.cfg
   ```

4. **Restart cache bundle:**
   ```bash
   docker exec -it qtt bin/client
   # In Karaf console:
   bundle:restart query-service-cache
   ```

5. **Check for fail-closed mode:**
   ```bash
   docker exec -it qtt grep cache.failOpen /opt/qtt/etc/com.inovexcorp.queryservice.cache.cfg
   # If cache.failOpen=false and Redis is down, queries will fail
   # Set to true for graceful degradation
   ```

#### Issue: High Cache Miss Rate

**Symptoms:**
- Settings page shows low hit rate (< 50%)
- Metrics show cache not improving performance
- Most queries still hit database

**Solutions:**
1. **Check cache statistics:**
   ```bash
   curl -k http://localhost:8080/queryrest/api/routes/cache/info
   # Review hits, misses, hitRatio
   ```

2. **Verify routes have caching enabled:**
   - UI: Check route configuration → Cache Settings → Cache Enabled
   - API: `GET /api/routes/{routeId}` → check `cacheEnabled` field

3. **Review TTL configuration:**
   - Too low TTL causes frequent evictions
   - Check route-specific TTL vs global default
   - Increase TTL if data doesn't change frequently:
   ```bash
   # Edit route or update global default
   docker exec -it qtt grep cache.defaultTtlSeconds /opt/qtt/etc/com.inovexcorp.queryservice.cache.cfg
   ```

4. **Check for query variations:**
   - Different parameters create different cache keys
   - `GET /route?name=John` ≠ `GET /route?name=john` (case-sensitive)
   - Normalize parameters in template for better hit rate

5. **Monitor evictions:**
   ```bash
   curl -k http://localhost:8080/queryrest/api/routes/cache/info | jq '.stats.evictions'
   # High evictions may indicate:
   # - TTL too short
   # - Redis maxmemory too low
   # - LRU eviction policy too aggressive
   ```

6. **Check Redis memory:**
   ```bash
   docker exec -it qtt-redis redis-cli info memory
   # Look for:
   # - used_memory_human
   # - maxmemory_human
   # - evicted_keys
   ```

#### Issue: Redis Connection Timeout

**Symptoms:**
```
io.lettuce.core.RedisConnectionException: Unable to connect to redis:6379
```

**Solutions:**
1. **Increase timeout value:**
   ```bash
   # Edit com.inovexcorp.queryservice.cache.cfg
   redis.timeout=10000  # Increase from default 5000ms to 10000ms
   ```

2. **Check network latency:**
   ```bash
   docker exec -it qtt ping -c 3 redis
   # Look for high latency (> 100ms)
   ```

3. **Verify Redis is not overloaded:**
   ```bash
   docker exec -it qtt-redis redis-cli --latency
   # Press Ctrl+C after a few seconds
   # Average latency should be < 1ms
   ```

4. **Check Redis slow log:**
   ```bash
   docker exec -it qtt-redis redis-cli slowlog get 10
   # Shows queries taking > 10ms (configurable)
   ```

5. **Review connection pool settings:**
   ```bash
   # In com.inovexcorp.queryservice.cache.cfg
   redis.pool.maxTotal=20    # Increase if needed
   redis.pool.maxIdle=10
   redis.pool.minIdle=5
   ```

6. **Monitor connection pool:**
   ```bash
   # Check logs for pool exhaustion
   docker logs qtt | grep "pool exhausted"
   ```

#### Issue: Cache Not Being Used (Queries Not Cached)

**Symptoms:**
- Routes configured with cache enabled
- Cache stats show 0 entries
- All queries show as cache misses

**Solutions:**
1. **Verify route-level cache is enabled:**
   ```bash
   curl -k http://localhost:8080/queryrest/api/routes/my-route | jq '.cacheEnabled'
   # Should return: true
   ```

2. **Check route state:**
   ```bash
   # Route must be Started for cache to work
   curl -k http://localhost:8080/queryrest/api/routes/my-route | jq '.state'
   ```

3. **Confirm route successfully executes:**
   ```bash
   # Test the query endpoint
   curl http://localhost:8888/my-route
   # Should return 200 OK with results
   ```

4. **Verify cache key pattern:**
   ```bash
   docker exec -it qtt-redis redis-cli KEYS "qtt:cache:*"
   # Should show cache keys if caching is working
   ```

5. **Check for cache errors in logs:**
   ```bash
   docker logs qtt | grep -A 5 "Error.*cache"
   # Look for cache store/retrieve failures
   ```

6. **Enable cache statistics:**
   ```bash
   # In com.inovexcorp.queryservice.cache.cfg
   cache.statsEnabled=true
   ```

7. **Test cache manually:**
   ```bash
   # Execute same query twice
   time curl http://localhost:8888/my-route?id=123
   time curl http://localhost:8888/my-route?id=123
   # Second request should be faster (cached)
   ```

#### Issue: Redis Memory Full

**Symptoms:**
```
OOM command not allowed when used memory > 'maxmemory'
```

**Solutions:**
1. **Check current memory usage:**
   ```bash
   docker exec -it qtt-redis redis-cli info memory | grep used_memory_human
   docker exec -it qtt-redis redis-cli info memory | grep maxmemory_human
   ```

2. **Clear old cache entries:**
   ```bash
   # Clear all cache
   curl -X DELETE -k http://localhost:8080/queryrest/api/routes/cache

   # Or clear specific route
   curl -X DELETE -k http://localhost:8080/queryrest/api/routes/{routeId}/cache
   ```

3. **Increase Redis memory limit:**
   ```yaml
   # In docker-compose.yml
   redis:
     command: redis-server --maxmemory 512mb  # Increase from 256mb
   ```

4. **Verify eviction policy:**
   ```bash
   docker exec -it qtt-redis redis-cli config get maxmemory-policy
   # Should show: allkeys-lru (recommended for caching)
   ```

5. **Adjust TTL to reduce memory usage:**
   ```bash
   # Lower default TTL to expire entries sooner
   # In com.inovexcorp.queryservice.cache.cfg
   cache.defaultTtlSeconds=1800  # Reduce from 3600 to 30 minutes
   ```

6. **Enable compression:**
   ```bash
   # In com.inovexcorp.queryservice.cache.cfg
   cache.compressionEnabled=true  # Reduces memory by ~60-80%
   ```

7. **Monitor evictions:**
   ```bash
   docker exec -it qtt-redis redis-cli info stats | grep evicted_keys
   # Track eviction rate over time
   ```

### 9.5 Karaf Console Commands

Access the Karaf console for advanced troubleshooting:

```bash
docker exec -it qtt bin/client
```

**Useful commands:**

```bash
# List all bundles
bundle:list

# View bundle details
bundle:info <bundle-id>

# Check bundle status
bundle:diag <bundle-id>

# Restart a bundle
bundle:restart <bundle-id>

# List OSGi services
service:list

# List Camel routes
camel:route-list

# View route details
camel:route-info <route-id>

# Check configuration
config:list

# Edit configuration
config:edit com.inovexcorp.queryservice.routebuilder

# View property
config:property-get templateLocation

# Set property
config:property-set templateLocation data/templates/

# Save config changes
config:update
```

### 9.6 Performance Tuning

#### JVM Memory Settings

**For containers:**
```bash
docker run -d \
  --name qtt \
  -e JAVA_OPTS="-Xms512m -Xmx2048m" \
  ...
```

#### Thread Pool Tuning

**Edit `org.ops4j.pax.web.cfg`:**
```properties
org.ops4j.pax.web.server.maxThreads=400
org.ops4j.pax.web.server.minThreads=20
```

#### Database Connection Pool

**Edit `org.ops4j.datasource-qtt.cfg`:**
```properties
jdbc.pool.maxTotal=20
jdbc.pool.maxIdle=20
jdbc.pool.minIdle=5
```

#### Ontology Cache Optimization

**Tune cache settings:**
```bash
# Increase cache size for more routes
ONTO_CACHE_MAX_ENTRIES=50

# Longer TTL for stable ontologies
ONTO_CACHE_TTL=120

# Faster timeout for responsive UX
ONTO_CACHE_QUERY_TIMEOUT=15
```

#### Redis Query Result Cache Optimization

**Connection Pool Tuning:**

Optimize Redis connection pool for your workload. Edit `com.inovexcorp.queryservice.cache.cfg`:

```properties
# High-traffic workload (many concurrent queries)
redis.pool.maxTotal=50      # Increase from default 20
redis.pool.maxIdle=25       # Increase from default 10
redis.pool.minIdle=10       # Increase from default 5

# Low-traffic workload (few concurrent queries)
redis.pool.maxTotal=10      # Decrease to save resources
redis.pool.maxIdle=5
redis.pool.minIdle=2
```

**Connection pool sizing formula:**
- `maxTotal` ≥ (Peak concurrent routes × Expected queries/second)
- `maxIdle` ≈ (Average concurrent queries)
- `minIdle` ≈ (Baseline steady-state connections)

**Timeout Settings:**

```properties
# Production environment (stable network)
redis.timeout=5000          # Default 5 seconds

# Distributed environment (higher network latency)
redis.timeout=10000         # Increase to 10 seconds

# Local development (Redis on same host)
redis.timeout=2000          # Decrease to 2 seconds
```

**Compression Trade-offs:**

```properties
# Enable compression (recommended for large result sets)
cache.compressionEnabled=true
# Pros: 60-80% memory reduction, more cache entries fit in Redis
# Cons: ~5-10ms CPU overhead per cache operation
# Best for: Results > 10KB, memory-constrained Redis

# Disable compression (recommended for small result sets)
cache.compressionEnabled=false
# Pros: Fastest cache operations, minimal CPU
# Cons: Higher memory usage
# Best for: Results < 10KB, CPU-constrained systems
```

**Measure compression benefit:**
```bash
# Check memory usage before
docker exec -it qtt-redis redis-cli info memory | grep used_memory_human

# Check individual key sizes
docker exec -it qtt-redis redis-cli --bigkeys
```

**TTL Strategy:**

Configure TTL based on data volatility:

```properties
# Static data (ontologies, reference data) - Long TTL
cache.defaultTtlSeconds=86400  # 24 hours

# Slowly changing data (user profiles, catalogs) - Medium TTL
cache.defaultTtlSeconds=3600   # 1 hour (default)

# Frequently updated data (real-time metrics, live feeds) - Short TTL
cache.defaultTtlSeconds=300    # 5 minutes

# Highly volatile data (personalized results) - Very short TTL or disable caching
cache.defaultTtlSeconds=60     # 1 minute
# Or set cacheEnabled=false for specific routes
```

**Per-route TTL override** (in route configuration):
```bash
# Via UI: Route configuration → Cache Settings → Cache TTL
# Via API:
curl -X PUT -k "http://localhost:8080/queryrest/api/routes/my-route?cacheTtlSeconds=7200"
```

**Redis Memory Management:**

Optimize Redis `maxmemory` and eviction policy in `docker-compose.yml`:

```yaml
redis:
  command: >
    redis-server
    --maxmemory 512mb                    # Set based on available RAM
    --maxmemory-policy allkeys-lru       # Evict least recently used keys
    --save ""                            # Disable RDB persistence (cache-only)
    --appendonly no                      # Disable AOF persistence (cache-only)
```

**Memory sizing guidelines:**
- **Small deployment:** 256MB (supports ~5K-10K cached queries)
- **Medium deployment:** 512MB (supports ~10K-20K cached queries)
- **Large deployment:** 1GB+ (supports 20K+ cached queries)

**Actual memory needed = (Average result size × Expected cache entries) / Compression ratio**

**Example calculation:**
- Average result size: 50KB
- Expected cache entries: 10,000
- Compression enabled (70% reduction): 0.3
- Memory needed: (50KB × 10,000 × 0.3) = ~150MB
- Add 20% overhead: 150MB × 1.2 = **180MB minimum**

**Eviction policies:**
```bash
# allkeys-lru (recommended for query caching)
# Evicts least recently used keys when maxmemory reached
--maxmemory-policy allkeys-lru

# volatile-lru (alternative)
# Only evicts keys with TTL set (all our cache keys have TTL)
--maxmemory-policy volatile-lru

# noeviction (not recommended)
# Returns errors when memory full - causes cache failures
--maxmemory-policy noeviction
```

**Monitoring Cache Performance:**

Track cache effectiveness:

```bash
# Check hit ratio (target: > 70%)
curl -k http://localhost:8080/queryrest/api/routes/cache/info | jq '.stats.hitRatio'

# Monitor evictions (should be low)
docker exec -it qtt-redis redis-cli info stats | grep evicted_keys

# Check cache memory usage
docker exec -it qtt-redis redis-cli info memory | grep used_memory_human

# Monitor connection pool usage (via logs)
docker logs qtt | grep "pool exhausted"
```

**Performance Benchmarking:**

Test cache impact:

```bash
# Warm up cache
for i in {1..100}; do
  curl -s "http://localhost:8888/my-route?id=$i" > /dev/null
done

# Benchmark with cache (should be fast)
time for i in {1..100}; do
  curl -s "http://localhost:8888/my-route?id=1" > /dev/null
done

# Clear cache
curl -X DELETE -k http://localhost:8080/queryrest/api/routes/my-route/cache

# Benchmark without cache (should be slower)
time for i in {1..100}; do
  curl -s "http://localhost:8888/my-route?id=1" > /dev/null
done
```

**Redis Persistence Tuning:**

For cache-only workload (recommended):

```yaml
redis:
  command: >
    redis-server
    --save ""           # Disable RDB snapshots (no disk I/O)
    --appendonly no     # Disable AOF log (no disk I/O)
```

If persistence required (durable cache):

```yaml
redis:
  command: >
    redis-server
    --save "900 1 300 10"  # Snapshot: 1 change in 15min OR 10 changes in 5min
    --appendonly yes        # Enable AOF
    --appendfsync everysec  # Fsync every second (balanced)
```

**Network Optimization:**

For Redis on separate host:

```properties
# Enable TCP keepalive to detect dead connections
# In docker-compose.yml
redis:
  command: >
    redis-server
    --tcp-keepalive 60

# In cache config
redis.timeout=10000  # Higher timeout for network latency
```

For Redis on same Docker network (recommended):

```yaml
# docker-compose.yml
networks:
  qtt-network:
    driver: bridge
    # Use default bridge settings for best performance
```

**Cache Statistics Tuning:**

Balance statistics accuracy vs. Redis load:

```properties
# Frequent stats updates (higher Redis load)
cache.statsTtlSeconds=5     # Refresh every 5 seconds

# Infrequent stats updates (lower Redis load)
cache.statsTtlSeconds=60    # Refresh every minute

# Disable stats collection (minimal Redis overhead)
cache.statsEnabled=false
# Note: Cache hit/miss counters still work, but keyCount and evictions won't update
```

**Recommended Configuration by Deployment Size:**

**Small (< 10 routes, < 100 req/min):**
```properties
redis.pool.maxTotal=10
redis.pool.maxIdle=5
redis.pool.minIdle=2
redis.timeout=5000
cache.defaultTtlSeconds=3600
cache.compressionEnabled=true
cache.statsTtlSeconds=30
```
```yaml
redis:
  command: redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru --save ""
```

**Medium (10-50 routes, 100-1000 req/min):**
```properties
redis.pool.maxTotal=20
redis.pool.maxIdle=10
redis.pool.minIdle=5
redis.timeout=5000
cache.defaultTtlSeconds=3600
cache.compressionEnabled=true
cache.statsTtlSeconds=15
```
```yaml
redis:
  command: redis-server --maxmemory 512mb --maxmemory-policy allkeys-lru --save ""
```

**Large (50+ routes, > 1000 req/min):**
```properties
redis.pool.maxTotal=50
redis.pool.maxIdle=25
redis.pool.minIdle=10
redis.timeout=7000
cache.defaultTtlSeconds=3600
cache.compressionEnabled=true
cache.statsTtlSeconds=10
```
```yaml
redis:
  command: redis-server --maxmemory 1024mb --maxmemory-policy allkeys-lru --save ""
```

---

## 10. Advanced Topics

### 10.1 Database Migration

#### From Derby to PostgreSQL

**Step 1: Export Data from Derby**

```bash
# Connect to Karaf console
docker exec -it qtt bin/client

# Use custom export script or JDBC dump
# (Implementation-specific)
```

**Step 2: Create PostgreSQL Database**

```sql
CREATE DATABASE qtt;
CREATE USER qttuser WITH PASSWORD 'SecurePassword123';
GRANT ALL PRIVILEGES ON DATABASE qtt TO qttuser;
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

### 10.2 High Availability Setup

#### Active-Passive Configuration

```yaml
version: '3.8'

services:
  postgresql-primary:
    image: postgres:latest
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
      - "8888:8888"

  qtt-standby:
    image: docker.io/inovexis/qtt:latest
    environment:
      DB_URL: "jdbc:postgresql://postgresql-standby:5432/qtt"
      #...
    # Standby instance for failover
```

**Load Balancer Configuration:**

Use HAProxy or nginx to route traffic to active instance.

### 10.3 Backup and Restore

#### Database Backup

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

#### Template Backup

```bash
# Backup templates directory
docker cp qtt:/opt/qtt/data/templates ./templates-backup

# Restore
docker cp ./templates-backup qtt:/opt/qtt/data/templates
```

#### Configuration Backup

```bash
# Backup all configuration
docker cp qtt:/opt/qtt/etc ./etc-backup

# Restore
docker cp ./etc-backup qtt:/opt/qtt/etc
```

#### Full System Backup

```bash
#!/bin/bash
BACKUP_DIR=./qtt-backup-$(date +%Y%m%d-%H%M%S)
mkdir -p $BACKUP_DIR

# Backup database
docker exec qtt-postgres pg_dump -U qttuser qtt > $BACKUP_DIR/database.sql

# Backup templates
docker cp qtt:/opt/qtt/data/templates $BACKUP_DIR/templates

# Backup configuration
docker cp qtt:/opt/qtt/etc $BACKUP_DIR/etc

# Create archive
tar -czf $BACKUP_DIR.tar.gz $BACKUP_DIR
rm -rf $BACKUP_DIR

echo "Backup created: $BACKUP_DIR.tar.gz"
```

### 10.4 Custom Karaf Features

You can extend QTT with custom OSGi bundles.

**Create custom feature:**

```xml
<!-- custom-feature.xml -->
<features xmlns="http://karaf.apache.org/xmlns/features/v1.3.0" name="custom-features">
  <feature name="custom-bundle" version="1.0.0">
    <bundle>mvn:com.example/custom-bundle/1.0.0</bundle>
  </feature>
</features>
```

**Deploy:**

```bash
# Copy feature to deploy
docker cp custom-feature.xml qtt:/opt/qtt/deploy/

# Or install via Karaf console
docker exec -it qtt bin/client

karaf@root()> feature:repo-add file:///opt/qtt/deploy/custom-feature.xml
karaf@root()> feature:install custom-bundle
```

### 10.5 Security Hardening

#### 1. SSL/TLS Best Practices

- Use CA-signed certificates (not self-signed)
- Enforce TLS 1.2+ only
- Disable weak cipher suites

**Configure in `org.ops4j.pax.web.cfg`:**
```properties
org.ops4j.pax.web.ssl.protocols.included=TLSv1.2,TLSv1.3
org.ops4j.pax.web.ssl.ciphersuites.excluded=.*NULL.*,.*RC4.*,.*MD5.*,.*DES.*,.*DSS.*
```

#### 2. Network Segmentation

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

#### 3. Secrets Management

Use Docker secrets or Kubernetes secrets:

```yaml
services:
  qtt:
    secrets:
      - db_password
      - sparqi_api_key
    environment:
      DB_PASSWORD_FILE: /run/secrets/db_password
      SPARQI_LLM_API_KEY_FILE: /run/secrets/sparqi_api_key

secrets:
  db_password:
    file: ./secrets/db_password.txt
  sparqi_api_key:
    file: ./secrets/sparqi_api_key.txt
```

#### 4. Least Privilege Database Access

Grant only required permissions:

```sql
-- PostgreSQL
CREATE USER qttuser WITH PASSWORD 'SecurePassword123';
GRANT CONNECT ON DATABASE qtt TO qttuser;
GRANT USAGE ON SCHEMA public TO qttuser;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO qttuser;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO qttuser;
```

#### 5. Rate Limiting

Use nginx or HAProxy for rate limiting:

```nginx
http {
  limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;
  
  server {
    location /queryrest/api/ {
      limit_req zone=api_limit burst=20;
      proxy_pass https://qtt:8443;
    }
  }
}
```

### 10.6 Cache Internals and Advanced Topics

#### Cache Key Generation Strategy

Understanding how cache keys are generated helps optimize cache hit rates and troubleshoot cache behavior.

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

**Code reference:** `query-service-cache/src/main/java/com/inovexcorp/queryservice/cache/CacheKey.java:45`

**Hash Generation Algorithm:**
```java
String toHash = query + "|" + graphmartUri + "|" + layerUris;
byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(toHash.getBytes(UTF_8));
String hash = bytesToHex(hashBytes).toLowerCase();
```

**Why This Matters:**

Different parameter values create different hashes, even if the query result would be identical:

```bash
# Query 1: name=John
GET /people-search?name=John
# Cache key: qtt:cache:people-search:abc123...
# Query generates: SELECT * WHERE { ?person foaf:name "John" }

# Query 2: name=john (different case)
GET /people-search?name=john
# Cache key: qtt:cache:people-search:def456...
# Query generates: SELECT * WHERE { ?person foaf:name "john" }
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

Now both `?name=John` and `?name=john` generate the same SPARQL query, resulting in the same cache key.

#### Cache Invalidation Patterns

**Time-Based Invalidation (Automatic):**

Default behavior using TTL:
```properties
# All cache entries expire after TTL
cache.defaultTtlSeconds=3600  # 1 hour

# Per-route override
# Set cacheTtlSeconds=7200 for specific route
```

**Manual Invalidation:**

Clear cache when source data changes:

```bash
# Pattern 1: Clear specific route cache after data update
curl -X POST "http://anzo:8080/update" -d "INSERT DATA { ... }"
curl -X DELETE -k "http://localhost:8080/queryrest/api/routes/people-search/cache"

# Pattern 2: Clear all cache after bulk data import
./import-data.sh
curl -X DELETE -k "http://localhost:8080/queryrest/api/routes/cache"

# Pattern 3: Selective clearing using Redis patterns
docker exec -it qtt-redis redis-cli --scan --pattern "qtt:cache:people-*" | \
  xargs docker exec -it qtt-redis redis-cli DEL
```

**Event-Driven Invalidation (Advanced):**

Integrate with data change events:

```bash
# Webhook approach (pseudo-code)
POST /webhook/data-changed
{
  "dataset": "people",
  "affectedRoutes": ["people-search", "people-by-org"]
}

# Implementation clears specific route caches
for route in affectedRoutes:
  DELETE /api/routes/{route}/cache
```

**Cache Aside Pattern:**

Current implementation uses "cache aside" (lazy loading):

```
1. Request arrives
2. Check cache → MISS
3. Execute query against Anzo
4. Store result in cache
5. Return result

Next identical request:
1. Request arrives
2. Check cache → HIT
3. Return cached result (skip steps 3-4)
```

**Write-Through Pattern (Not Implemented):**

Alternative pattern for frequently updated data:

```
On data update:
1. Update Anzo database
2. Immediately update or invalidate cache
3. Next read hits fresh cache

Pros: Always fresh data, no stale reads
Cons: More complex, requires change detection
```

#### Multi-Region Redis Deployment

For globally distributed deployments, consider Redis replication:

**Primary-Replica Setup:**

```yaml
# docker-compose.yml
services:
  redis-primary:
    image: redis:7-alpine
    command: redis-server --maxmemory 512mb --maxmemory-policy allkeys-lru
    ports:
      - "6379:6379"
    volumes:
      - redis-primary-data:/data

  redis-replica-1:
    image: redis:7-alpine
    command: redis-server --slaveof redis-primary 6379 --maxmemory 512mb
    depends_on:
      - redis-primary

  redis-replica-2:
    image: redis:7-alpine
    command: redis-server --slaveof redis-primary 6379 --maxmemory 512mb
    depends_on:
      - redis-primary

volumes:
  redis-primary-data:
```

**Geographic Distribution:**

```yaml
# US East deployment
qtt-us-east:
  environment:
    REDIS_HOST: redis-us-east

# EU deployment
qtt-eu:
  environment:
    REDIS_HOST: redis-eu

# Each region has local Redis for lower latency
# Cache entries may differ between regions (eventual consistency)
```

**Redis Sentinel for High Availability:**

```yaml
services:
  redis-master:
    image: redis:7-alpine

  redis-sentinel-1:
    image: redis:7-alpine
    command: redis-sentinel /etc/sentinel.conf
    volumes:
      - ./sentinel.conf:/etc/sentinel.conf

  qtt:
    environment:
      # Sentinel discovers current master automatically
      REDIS_HOST: redis-sentinel-1,redis-sentinel-2,redis-sentinel-3
      REDIS_SENTINEL_ENABLED: true
      REDIS_SENTINEL_MASTER_NAME: mymaster
```

**Note:** Current implementation doesn't support Sentinel. Manual failover required if Redis fails.

#### Cache Warming Strategies

Pre-populate cache before user requests:

**Strategy 1: Scheduled Warm-Up**

```bash
#!/bin/bash
# warm-cache.sh - Run via cron daily at 6 AM

# Popular queries that should always be cached
QUERIES=(
  "http://localhost:8888/people-search?limit=100"
  "http://localhost:8888/org-search?limit=100"
  "http://localhost:8888/recent-updates?days=7"
)

for query in "${QUERIES[@]}"; do
  echo "Warming cache: $query"
  curl -s "$query" > /dev/null
done

echo "Cache warming complete"
```

**Strategy 2: Post-Deployment Warm-Up**

```bash
#!/bin/bash
# After deploying new version, warm cache with actual traffic patterns

# Replay access logs from last hour
tail -n 1000 /var/log/nginx/access.log | \
  grep "GET /api/" | \
  awk '{print $7}' | \
  while read path; do
    curl -s "http://localhost:8888${path}" > /dev/null
  done
```

**Strategy 3: Application-Level Warm-Up**

```bash
# Call cache warming endpoint after route creation
curl -X POST -k "http://localhost:8080/queryrest/api/routes/my-route" ...
# Then immediately warm the cache
curl "http://localhost:8888/my-route?id=test"
```

**Strategy 4: Intelligent Warm-Up Based on Metrics**

```bash
#!/bin/bash
# Warm cache for high-traffic routes only

# Get routes sorted by request volume
curl -k "http://localhost:8080/queryrest/api/metrics" | \
  jq -r '.[] | select(.exchanges.completed > 100) | .routeId' | \
  while read routeId; do
    # Execute with default parameters
    curl -s "http://localhost:8888/${routeId}" > /dev/null
    echo "Warmed cache for $routeId"
  done
```

**Monitoring Cache Warming Effectiveness:**

```bash
# Before warm-up
curl -k http://localhost:8080/queryrest/api/routes/cache/info | jq '.stats.hitRatio'
# Output: 0.65 (65% hit rate)

# Run warm-up script
./warm-cache.sh

# After warm-up (check after some traffic)
curl -k http://localhost:8080/queryrest/api/routes/cache/info | jq '.stats.hitRatio'
# Output: 0.82 (82% hit rate) - improved!
```

#### Cache Bypass for Debugging

Temporarily bypass cache without disabling:

**Method 1: Clear specific cache before test**
```bash
curl -X DELETE -k "http://localhost:8080/queryrest/api/routes/my-route/cache"
curl "http://localhost:8888/my-route?id=123"  # Forces fresh query
```

**Method 2: Temporarily disable route cache**
```bash
# Via API (requires route modification)
curl -X PUT -k "http://localhost:8080/queryrest/api/routes/my-route?cacheEnabled=false"
# Test queries...
# Re-enable after testing
curl -X PUT -k "http://localhost:8080/queryrest/api/routes/my-route?cacheEnabled=true"
```

**Method 3: Use unique parameters** (not recommended for production)
```bash
# Add random parameter to force cache miss
curl "http://localhost:8888/my-route?id=123&_nocache=$RANDOM"
```

#### Compression Algorithm Deep Dive

Understanding GZIP compression for cache optimization:

**Compression Process:**
```java
// From RedisCacheService.java:442
private String compress(String data) throws IOException {
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
        gzipStream.write(data.getBytes(StandardCharsets.UTF_8));
    }
    return Base64.getEncoder().encodeToString(byteStream.toByteArray());
}
```

**Storage Format:**
```
Raw JSON-LD Result → GZIP Compress → Base64 Encode → Store in Redis
```

**Decompression Process:**
```
Redis Value → Base64 Decode → GZIP Decompress → JSON-LD Result
```

**Compression Effectiveness by Data Type:**

| Data Type | Original Size | Compressed Size | Compression Ratio | Recommended |
|-----------|--------------|-----------------|-------------------|-------------|
| JSON-LD (typical) | 50 KB | 12 KB | 76% reduction | Yes |
| JSON-LD (highly repetitive) | 100 KB | 8 KB | 92% reduction | Yes |
| JSON-LD (small) | 2 KB | 1.8 KB | 10% reduction | No (overhead) |
| JSON-LD (already compact) | 20 KB | 18 KB | 10% reduction | Maybe |

**When to Disable Compression:**
- Average result size < 5 KB (compression overhead not worth it)
- CPU-constrained environment
- Ultra-low latency requirements (< 10ms)
- Results already compressed/binary

**When to Enable Compression:**
- Average result size > 10 KB (significant memory savings)
- Memory-constrained Redis
- Network bandwidth limited
- Large result sets (> 100 KB)

#### Fail-Open vs Fail-Closed Strategy

**Current Default: Fail-Open** (`cache.failOpen=true`)

```
Cache Error → Log warning → Continue without cache → Query executes normally

Pros:
✓ Service stays operational during Redis outages
✓ No user-facing errors
✓ Graceful degradation

Cons:
✗ Higher load on Anzo during Redis downtime
✗ Slower response times
✗ May not notice cache failures immediately
```

**Alternative: Fail-Closed** (`cache.failOpen=false`)

```
Cache Error → Throw exception → Return 500 error to client → Query fails

Pros:
✓ Forces immediate attention to cache problems
✓ Prevents overwhelming Anzo with uncached traffic
✓ Clear failure signal

Cons:
✗ Service unavailable during Redis outages
✗ User-facing errors
✗ Complete service disruption
```

**Recommendation:**
- **Production**: Use fail-open for resilience
- **Development**: Use fail-closed to catch cache configuration issues early
- **High-traffic production**: Use fail-open with monitoring/alerting on cache errors

**Implementation Reference:** `query-service-cache/src/main/java/com/inovexcorp/queryservice/cache/RedisCacheService.java:181`

---

## 11. Appendices

### 11.1 Complete Environment Variables Reference

| Variable | Default | Type | Description | Section |
|----------|---------|------|-------------|---------|
| `API_BASE_URL` | `http://localhost:8888` | String | Base URL for query endpoints | Core |
| `DB_DRIVER_NAME` | `derby` | String | JDBC driver identifier | Database |
| `DB_URL` | `jdbc:derby:data/database;create=true` | String | JDBC connection string | Database |
| `DB_USER` | `user` | String | Database username | Database |
| `DB_PASSWORD` | `password` | String | Database password | Database |
| `SPARQI_ENABLED` | `false` | Boolean | Enable SPARQi AI assistant | SPARQi |
| `SPARQI_LLM_BASE_URL` | None | String | LLM API endpoint | SPARQi |
| `SPARQI_LLM_API_KEY` | None | String | LLM API key | SPARQi |
| `SPARQI_LLM_MODEL` | None | String | LLM model name | SPARQi |
| `SPARQI_LLM_TIMEOUT` | `90` | Integer | LLM request timeout (seconds) | SPARQi |
| `SPARQI_LLM_TEMPERATURE` | `0.7` | Double | LLM temperature (0.0-1.0) | SPARQi |
| `SPARQI_LLM_MAX_TOKENS` | `4000` | Integer | Max tokens per response | SPARQi |
| `SPARQI_SESSION_TIMEOUT` | `60` | Integer | Session timeout (minutes) | SPARQi |
| `SPARQI_MAX_CONVO_HISTORY` | `50` | Integer | Max conversation messages | SPARQi |
| `ONTO_CACHE_TTL` | `60` | Integer | Ontology cache TTL (minutes) | Ontology |
| `ONTO_CACHE_MAX_ENTRIES` | `20` | Integer | Max cached routes | Ontology |
| `ONTO_CACHE_ENABLED` | `true` | Boolean | Enable ontology caching | Ontology |
| `ONTO_CACHE_QUERY_TIMEOUT` | `30` | Integer | Ontology query timeout (seconds) | Ontology |
| `ONTO_CACHE_QUERY_LIMIT` | `1000` | Integer | Max ontology elements per query | Ontology |
| `REDIS_ENABLED` | `false` | Boolean | Enable Redis query result caching | Cache |
| `REDIS_HOST` | `localhost` | String | Redis server hostname | Cache |
| `REDIS_PORT` | `6379` | Integer | Redis server port | Cache |
| `REDIS_PASSWORD` | (empty) | String | Redis authentication password | Cache |
| `REDIS_DATABASE` | `0` | Integer | Redis database number (0-15) | Cache |
| `REDIS_TIMEOUT` | `5000` | Integer | Redis connection timeout (milliseconds) | Cache |
| `REDIS_POOL_MAX_TOTAL` | `20` | Integer | Max connections in pool | Cache |
| `REDIS_POOL_MAX_IDLE` | `10` | Integer | Max idle connections in pool | Cache |
| `REDIS_POOL_MIN_IDLE` | `5` | Integer | Min idle connections in pool | Cache |
| `CACHE_DEFAULT_TTL` | `3600` | Integer | Default cache TTL (seconds) | Cache |
| `CACHE_KEY_PREFIX` | `qtt:cache:` | String | Prefix for all Redis cache keys | Cache |
| `CACHE_COMPRESSION_ENABLED` | `true` | Boolean | Enable GZIP compression for cached values | Cache |
| `CACHE_FAIL_OPEN` | `true` | Boolean | Continue on cache errors (vs fail closed) | Cache |
| `CACHE_STATS_ENABLED` | `true` | Boolean | Track cache hit/miss statistics | Cache |
| `CACHE_STATS_TTL` | `5` | Integer | Cache statistics refresh interval (seconds) | Cache |
| `KEYSTORE` | None | String | Custom SSL keystore path | Security |
| `PASSWORD` | None | String | SSL keystore password | Security |

### 11.2 Scheduler Cron Expressions

| Scheduler | Default Expression | Description | Configurable |
|-----------|-------------------|-------------|--------------|
| Query Metrics | `0 0/1 * * * ?` | Every 1 minute | Yes |
| Clean Metrics | `0 0/1 * * * ?` | Every 1 minute | Yes |
| Datasource Health Check | `0/60 * * * * ?` | Every 60 seconds | Yes |
| Clean Health Records | `0 0 0 * * ?` | Daily at midnight | Yes |

**Cron Format:** `second minute hour day month weekday`

**Examples:**
- Every 5 minutes: `0 0/5 * * * ?`
- Every hour: `0 0 * * * ?`
- Every day at 2 AM: `0 0 2 * * ?`
- Every Monday at 8 AM: `0 0 8 ? * MON`

### 11.3 Supported Database Drivers

| Database | Driver Name | JDBC URL Format | Default Port |
|----------|-------------|-----------------|--------------|
| Apache Derby | `derby` | `jdbc:derby:data/database;create=true` | N/A (embedded) |
| PostgreSQL | `PostgreSQL JDBC Driver` | `jdbc:postgresql://host:port/database` | 5432 |
| SQL Server | `Microsoft JDBC Driver for SQL Server` | `jdbc:sqlserver://host:port;databaseName=database` | 1433 |

**Driver Versions (bundled):**
- Derby: 10.14.2.0
- PostgreSQL: 42.5.0
- SQL Server: 9.4.1.jre11

### 11.4 Port Reference

| Port | Protocol | Service | Purpose | Exposed by Default |
|------|----------|---------|---------|-------------------|
| 8443 | HTTPS | PAX Web | JAX-RS API and Web UI | Yes |
| 8888 | HTTP | Camel Jetty | Dynamic query endpoints | Yes |
| 5005 | TCP | JVM Debug | Remote debugging | No |
| 1099 | TCP | JMX RMI Registry | Management | No |
| 44444 | TCP | JMX RMI Server | Management | No |

**Changing Ports:**

Edit `org.ops4j.pax.web.cfg`:
```properties
org.osgi.service.http.port.secure=8443  # Change HTTPS port
```

For query endpoints (8888), modify Jetty configuration in route templates.

### 11.5 Docker vs Podman Command Reference

| Action | Docker | Podman |
|--------|--------|--------|
| Pull image | `docker pull docker.io/inovexis/qtt:latest` | `podman pull docker.io/inovexis/qtt:latest` |
| Run container | `docker run -d --name qtt -p 8443:8443 -p 8888:8888 docker.io/inovexis/qtt:latest` | `podman run -d --name qtt -p 8443:8443 -p 8888:8888 docker.io/inovexis/qtt:latest` |
| List containers | `docker ps` | `podman ps` |
| View logs | `docker logs qtt` | `podman logs qtt` |
| Follow logs | `docker logs -f qtt` | `podman logs -f qtt` |
| Execute command | `docker exec -it qtt bash` | `podman exec -it qtt bash` |
| Stop container | `docker stop qtt` | `podman stop qtt` |
| Start container | `docker start qtt` | `podman start qtt` |
| Remove container | `docker rm qtt` | `podman rm qtt` |
| Volume mount | `docker run -v qtt-data:/opt/qtt/data ...` | `podman run -v qtt-data:/opt/qtt/data:Z ...` |
| Compose up | `docker compose up -d` | `podman-compose up -d` |
| Compose down | `docker compose down` | `podman-compose down` |
| Create pod | N/A | `podman pod create --name qtt-pod -p 8443:8443` |
| Generate systemd | N/A | `podman generate systemd --name qtt --files --new` |

**Key Differences:**
- Podman requires `:Z` or `:z` for SELinux volume labels on RHEL/CentOS/Fedora
- Podman supports pods (Kubernetes-style container groups)
- Podman can generate systemd unit files
- Podman runs rootless by default
- Use `host.docker.internal` for host access in Docker; `host.containers.internal` in Podman

### 11.6 Useful Links

**Documentation:**
- Apache Karaf: https://karaf.apache.org/manual/latest/
- Apache Camel: https://camel.apache.org/manual/
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

### 11.7 Glossary

**Anzo**: Cambridge Semantics graph database platform

**Apache Camel**: Integration framework for routing and mediation

**Apache Karaf**: OSGi runtime environment

**Cache Aside Pattern**: Caching strategy where the application checks cache first, then queries database on miss and updates cache. Also called "lazy loading"

**Cache Eviction**: Removal of cache entries due to memory limits (LRU policy) or TTL expiration

**Cache Hit**: Request served from cache without querying the database. Indicates cached data was found and valid

**Cache Hit Ratio**: Percentage of requests served from cache (hits / (hits + misses)). Target: > 70% for effective caching

**Cache Key**: Unique identifier for cached data. Format: `{prefix}{routeId}:{hash}` where hash is SHA-256 of query parameters

**Cache Miss**: Request that requires database query because data not found in cache. Triggers cache population

**Compression (Cache)**: GZIP compression of cached JSON-LD results before storing in Redis. Reduces memory usage by ~60-80%

**CONSTRUCT**: SPARQL query form that builds RDF graphs

**DataSource**: Configuration for connecting to an Anzo instance

**Fail-Closed**: Cache error handling mode where Redis failures cause queries to fail with errors. Ensures cache problems are immediately visible

**Fail-Open**: Cache error handling mode where Redis failures allow queries to proceed without cache. Provides graceful degradation (default behavior)

**Freemarker**: Java-based template engine

**GraphMart**: Anzo's logical data container

**JAX-RS**: Java API for RESTful web services

**JSON-LD**: JSON format for linked data

**Layer**: Subset of data within an Anzo GraphMart

**LiteLLM**: Unified LLM API gateway

**LRU (Least Recently Used)**: Cache eviction policy that removes least recently accessed entries when memory limit reached. Default Redis policy for QTT

**Monaco Editor**: Code editor component (used by VS Code)

**OSGi**: Modular Java framework

**RDF**: Resource Description Framework

**Redis**: In-memory data store used for query result caching. Provides fast read/write operations with TTL-based expiration

**Route**: Query endpoint with associated template

**SPARQL**: Query language for RDF graphs

**SPARQi**: AI assistant for SPARQL template development

**Template**: Freemarker file that generates SPARQL queries

**TTL (Time To Live)**: Duration (in seconds) that cache entries remain valid before expiration. Default: 3600 seconds (1 hour)

---

## Conclusion

This user guide provides comprehensive documentation for installing, configuring, and using the Query Templating Tool (QTT). For additional support or to report issues, please consult the project repository or contact your system administrator.

**Quick Start Recap:**

1. **Deploy:** `docker pull docker.io/inovexis/qtt:latest && docker run -d --name qtt -p 8443:8443 -p 8888:8888 docker.io/inovexis/qtt:latest`
2. **Access:** http://localhost:8080/
3. **Create Datasource:** Add your Anzo connection
4. **Create Route:** Add query template with Freemarker
5. **Test:** `curl http://localhost:8888/{your-route-id}`
6. **Monitor:** View metrics and performance in UI

**Next Steps:**
- Explore SPARQi for AI-assisted template development
- Set up production database (PostgreSQL/SQL Server)
- Configure SSL certificates
- Implement monitoring and alerting
- Optimize query performance

Happy querying! 🚀

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Copyright (c) 2025 RealmOne

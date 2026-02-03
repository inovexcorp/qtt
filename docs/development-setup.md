# Development Setup

This guide covers building QTT from source and setting up a local development environment.

## Prerequisites

- Java 17 (JDK)
- Apache Maven 3.6+
- Node.js v20.18.1
- Angular CLI 18.2.21
- Docker or Podman (for PostgreSQL and Redis containers)

## Building from Source

### Clone and Build

```bash
# Clone the repository
git clone https://github.com/inovexcorp/qtt.git
cd qtt

# Full build (includes Angular frontend)
make build

# Build without frontend (faster for backend-only changes)
make build_no_web

# Build Docker image
make build_docker
```

### Configuration Before Building

Configure the template location before building.

Edit `query-service-distribution/src/main/resources/etc/com.inovexcorp.queryservice.routebuilder.cfg`:

```properties
templateLocation=data/templates/
```

This path is relative to the Karaf home directory (`/opt/qtt` in containers).

## Makefile Commands Reference

### Build Commands

| Command                | Description                                     |
|------------------------|-------------------------------------------------|
| `make build`           | Build entire project with Maven                 |
| `make build_no_web`    | Build excluding query-service-web module        |
| `make refresh_bundles` | Rebuild bundles (excludes web and distribution) |
| `make build_docker`    | Build Docker image                              |

### Local Run Commands

| Command              | Description                                           |
|----------------------|-------------------------------------------------------|
| `make run`           | Run Karaf (builds if needed, uses Derby by default)   |
| `make build_and_run` | Build then run Karaf                                  |
| `make postgres_run`  | Start PostgreSQL and run Karaf with PostgreSQL config |
| `make mssql_run`     | Start MSSQL and run Karaf with MSSQL config           |
| `make stop`          | Stop running Karaf instance                           |

### Database Management Commands

| Command               | Description                  |
|-----------------------|------------------------------|
| `make start_redis`    | Start Redis container        |
| `make start_postgres` | Start PostgreSQL container   |
| `make start_mssql`    | Start MSSQL container        |
| `make stop_redis`     | Stop Redis container         |
| `make stop_postgres`  | Stop PostgreSQL container    |
| `make stop_mssql`     | Stop MSSQL container         |
| `make stop_databases` | Stop all database containers |
| `make logs_redis`     | View Redis logs              |
| `make logs_postgres`  | View PostgreSQL logs         |
| `make logs_mssql`     | View MSSQL logs              |

### Utility Commands

| Command      | Description                         |
|--------------|-------------------------------------|
| `make clean` | Remove build artifacts              |
| `make test`  | Run Maven tests                     |
| `make help`  | Show help message with all commands |

## Recommended Setup: PostgreSQL + Redis

This workflow sets up a production-like local environment with PostgreSQL for persistence and Redis for query result
caching.

### Step 1: Initial Build

```bash
# Clone and build the project (first time only)
git clone https://github.com/inovexcorp/qtt.git
cd qtt
make build
```

### Step 2: Start Dependencies

Start PostgreSQL and Redis containers:

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

### Step 3: Run with PostgreSQL

```bash
make postgres_run
```

This command:

1. Starts PostgreSQL container if not already running
2. Sets PostgreSQL environment variables automatically
3. Sources your `.env` file if present (for Redis or other config)
4. Launches Karaf with the configured settings

### Step 4: Enable Redis Caching (Optional)

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

### Step 5: Verify Installation

```bash
# Check Karaf is running
# Access Web UI at: http://localhost:8080

# Check API
curl http://localhost:8080/queryrest/api/settings

# Check cache info (if Redis enabled)
curl http://localhost:8080/queryrest/api/routes/cache/info
```

### Step 6: Stopping Services

```bash
# Stop Karaf
make stop

# Stop database containers
make stop_databases
```

## Alternative Workflows

### Simple Development (Derby embedded database)

```bash
# Quick start with no external dependencies
make build_and_run
```

### PostgreSQL without Redis

```bash
# Just PostgreSQL, no caching
make postgres_run
# (Don't create .env file or set REDIS_ENABLED=false)
```

### SQL Server instead of PostgreSQL

```bash
make mssql_run
```

## Managing Services

### View logs for debugging

```bash
# View PostgreSQL logs
make logs_postgres

# View Redis logs
make logs_redis

# View Karaf logs (in separate terminal)
tail -f query-service-distribution/target/assembly/data/log/karaf.log
```

### Rebuild after code changes

```bash
# For backend changes (faster, skips Angular build)
make refresh_bundles

# Restart Karaf
make stop
make postgres_run
```

### Start containers individually

```bash
# Start only what you need
make start_redis     # Just Redis
make start_postgres  # Just PostgreSQL
make start_mssql     # Just MSSQL
```

## Project Structure

```
qtt/
├── camel-anzo/                    # Custom Camel component for Anzo
├── query-service-core/            # Core RDF/JSON-LD utilities
├── query-service-route-builder/   # Dynamic route creation
├── query-service-persistence/     # JPA entities and services
├── query-service-scheduler/       # Scheduled jobs
├── query-service-metrics/         # JMX metrics collection
├── query-service-cache/           # Redis caching layer
├── query-service-sparqi/          # AI assistant
├── query-service-feature/         # Karaf feature descriptor
├── query-service-distribution/    # Complete Karaf distribution
├── query-service-web/             # Angular frontend
├── Makefile                       # Build and run commands
├── compose.yml                    # Docker Compose for dependencies
└── docs/                          # Documentation
```

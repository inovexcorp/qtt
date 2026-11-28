# Development Setup

This guide covers building QTT from source and setting up a local development environment.

## Prerequisites

- Java 17 (JDK)
- Apache Maven 3.6+
- Node.js v26.13.1 (`query-service-web/.nvmrc`; with nvm: `nvm install && nvm use`)
- Angular CLI 22.3 (installed by `npm ci` in `query-service-web`; use `npx ng`)
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

The build produces a Quarkus application in `query-service-app/target/quarkus-app/`. `make build_docker` packages it
into the container image `inovexis/qtt:latest` using `query-service-app/src/main/docker/Dockerfile.jvm`.

### Configuration

The application configuration is `query-service-app/src/main/resources/application.properties` (see the
[Configuration Reference](configuration.md)). For example, the template location:

```properties
com.inovexcorp.queryservice.routebuilder.templateLocation=data/templates/
```

This path is relative to the working directory: `query-service-app/target` for `make run`, `/opt/qtt` in containers.

## Makefile Commands Reference

### Build Commands

| Command             | Description                                                    |
|---------------------|----------------------------------------------------------------|
| `make build`        | Build entire project with Maven (Java and Angular tests)       |
| `make build_no_web` | Build excluding query-service-web module (uses last UI build)  |
| `make build_docker` | Build the container image `inovexis/qtt:latest`                |

### Local Run Commands

| Command              | Description                                                         |
|----------------------|---------------------------------------------------------------------|
| `make run`           | Run QTT (builds if needed, uses Derby by default)                   |
| `make dev`           | Run QTT in Quarkus dev mode with live reload (`make build` first)   |
| `make build_and_run` | Build then run QTT                                                  |
| `make postgres_run`  | Start PostgreSQL and run QTT with PostgreSQL config                 |
| `make mssql_run`     | Start MSSQL and run QTT with MSSQL config                           |
| `make stop`          | Stop a QTT started with `make run`                                  |

`make run` starts `query-service-app/target/quarkus-app/quarkus-run.jar` from `query-service-app/target` (so the
Derby database and templates go to `query-service-app/target/data`) with the bundled development keystore. The UI is
at https://localhost:8443. Because `make build` runs `mvn clean install`, it deletes that local Derby database and
its templates. To run it by hand:

```bash
cd query-service-app/target && java -jar quarkus-app/quarkus-run.jar
```

### Container Commands

| Command               | Description                                   |
|-----------------------|-----------------------------------------------|
| `make run_docker`     | Build the image and run it as container `qtt` |
| `make logs_docker`    | Follow the `qtt` container logs               |
| `make destroy_docker` | Remove the `qtt` container                    |

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

| Command        | Description                                                                 |
|----------------|-----------------------------------------------------------------------------|
| `make clean`   | Remove build artifacts                                                      |
| `make test`    | Run Maven tests                                                             |
| `make it`      | Run the black-box regression suite against `inovexis/qtt:latest` (Derby)   |
| `make gate`    | Full regression gate: all databases, Redis, restart, UI, TLS                |
| `make measure` | Measure image size, cold start, memory and throughput                       |
| `make help`    | Show help message with all commands                                         |

### Regression Tests

`query-service-it` is an HTTP-only regression suite (JUnit 5 + RestAssured) that runs against a running QTT stack.
`scripts/stack.sh up` starts QTT with a mock Anzo (and optionally Redis, PostgreSQL or SQL Server, selected with
`QTT_DB` and `QTT_REDIS`); then run the suite with:

```bash
mvn -Pit -pl query-service-it verify
```

`make it` does both for the Derby stack. `scripts/gate.sh` (`make gate`) runs the suite on Derby, Derby + Redis,
PostgreSQL + Redis and SQL Server, with sign-in through a Keycloak container (`QTT_OIDC=true`), and with
`QUERY_AUTH_DEFAULT=authenticated`, plus restart, UI, TLS and real-Anzo checks. The stack serves query routes at
`http://localhost:18082/query/<route-id>`; the Keycloak test realm (`scripts/keycloak/qtt-realm.json`) has clients
for query callers (`qtt-reader` with `qtt-query`, `qtt-finance` with `qtt-query` and `finance-apps`).

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
4. Launches QTT with the configured settings

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
# Check QTT is running
# Access Web UI at: https://localhost:8443

# Check API
curl -k https://localhost:8443/queryrest/api/settings

# Check cache info (if Redis enabled)
curl -k https://localhost:8443/queryrest/api/routes/cache/info
```

### Step 6: Stopping Services

```bash
# Stop QTT
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
```

QTT logs to the console of the terminal running `make run` / `make postgres_run`. For more detail, start it with
e.g. `QUARKUS_LOG_CATEGORY__COM_INOVEXCORP__LEVEL=DEBUG make run`.

### Rebuild after code changes

```bash
# For backend changes (faster, skips Angular build)
make build_no_web

# Restart QTT
make stop
make postgres_run
```

Alternatively, `make dev` runs QTT in Quarkus dev mode, which reloads application changes without a restart.

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
├── query-service-derby/           # Embedded Derby support (left out of native builds)
├── query-service-sparqi/          # AI assistant
├── query-service-web/             # Angular frontend
├── query-service-app/             # Camel Quarkus application and container image
├── query-service-it/              # Black-box HTTP regression suite
├── scripts/                       # Test stack, regression gate and measurement scripts
├── Makefile                       # Build and run commands
├── compose.yml                    # Docker Compose for dependencies
└── docs/                          # Documentation
```

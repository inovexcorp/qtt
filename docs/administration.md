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
  -p 8080:8080 \
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

### Step 3: Access the Application

- **Web UI**: http://localhost:8080
- **API Base**: http://localhost:8080/queryrest/api/
- **Query Endpoints**: http://localhost:8888/{route-id}

**Note**: Typically you'll want to use a reverse proxy to expose the application to the outside world.

### Step 4: Verify Installation

```bash
# Check container status
docker ps

# View logs
docker logs qtt

# Check API health
curl -k http://localhost:8080/queryrest/api/settings
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

### SQL Server Setup

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

### Redis Cache Setup

For production deployments with query result caching, add Redis to your stack:

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

## SSL/TLS Configuration

### Using Custom SSL Certificates

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
| `/opt/qtt/data/log`       | Application logs     | Optional           |
| `/opt/qtt/etc`            | Configuration files  | Optional           |

### Docker Volume Mounting

```bash
docker run -d \
  --name qtt \
  -p 8080:8080 \
  -p 8888:8888 \
  -v qtt-data:/opt/qtt/data \
  -v qtt-templates:/opt/qtt/data/templates \
  docker.io/inovexis/qtt:latest
```

### Podman Volume Mounting with SELinux

```bash
podman run -d \
  --name qtt \
  -p 8080:8080 \
  -p 8888:8888 \
  -v qtt-data:/opt/qtt/data:Z \
  -v qtt-templates:/opt/qtt/data/templates:Z \
  docker.io/inovexis/qtt:latest
```

## Port Configuration

| Port | Protocol | Purpose                 | Configurable                |
|------|----------|-------------------------|-----------------------------|
| 8080 | HTTP     | JAX-RS API and Web UI   | Yes (org.ops4j.pax.web.cfg) |
| 8888 | HTTP     | Dynamic query endpoints | Yes (Jetty configuration)   |

To change ports, edit the Jetty endpoint configuration in your route templates or override the PAX Web configuration.

## Docker vs Podman Command Reference

| Action | Docker | Podman |
|--------|--------|--------|
| Pull image | `docker pull docker.io/inovexis/qtt:latest` | `podman pull docker.io/inovexis/qtt:latest` |
| Run container | `docker run -d --name qtt -p 8080:8080 -p 8888:8888 docker.io/inovexis/qtt:latest` | `podman run -d --name qtt -p 8080:8080 -p 8888:8888 docker.io/inovexis/qtt:latest` |
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
| Create pod | N/A | `podman pod create --name qtt-pod -p 8080:8080` |
| Generate systemd | N/A | `podman generate systemd --name qtt --files --new` |

**Key Differences:**
- Podman requires `:Z` or `:z` for SELinux volume labels on RHEL/CentOS/Fedora
- Podman supports pods (Kubernetes-style container groups)
- Podman can generate systemd unit files
- Podman runs rootless by default
- Use `host.docker.internal` for host access in Docker; `host.containers.internal` in Podman

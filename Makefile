# ==============================================================================
# Query Service Makefile
# ==============================================================================

.PHONY: help build build_no_web build_docker \
        run dev build_and_run stop postgres_run mssql_run \
        destroy_docker run_docker logs_docker \
        start_redis start_postgres start_mssql \
        stop_redis stop_postgres stop_mssql stop_databases \
        logs_redis logs_postgres logs_mssql \
        clean test it gate measure

# ------------------------------------------------------------------------------
# Variables
# ------------------------------------------------------------------------------

# Determine the container engine to use: podman if available, otherwise docker
ENGINE := $(shell command -v podman > /dev/null 2>&1 && echo podman || echo docker)

# Compose command using detected engine
COMPOSE := $(ENGINE) compose

# Define common Maven options: use 2 threads per core
MAVEN := mvn -T 2C

# The Quarkus application module and its build output
APP_DIR := query-service-app
RUN_DIR := $(APP_DIR)/target
RUN_JAR := quarkus-app/quarkus-run.jar
IMAGE := inovexis/qtt

# Local runs work in query-service-app/target (the Derby database and templates go to ./data there) and use the
# bundled development keystore unless KEYSTORE is set.
LOCAL_KEYSTORE := ../src/main/etc/keystore

# Detect operating system and set platform-specific variables
ifeq ($(OS),Windows_NT)
    DETECTED_OS := Windows
    # Force cmd.exe as shell on Windows (choco make uses bash by default)
    SHELL := cmd.exe
    .SHELLFLAGS := /c
    RUN_CHECK_PATH := $(subst /,\,$(RUN_DIR))\quarkus-app\quarkus-run.jar
else
    DETECTED_OS := Unix
    RUN_CHECK_PATH := $(RUN_DIR)/$(RUN_JAR)
endif

# ==============================================================================
# Help Target (default)
# ==============================================================================

help:
	@echo "Query Service Makefile Commands"
	@echo "================================"
	@echo ""
	@echo "Build Targets:"
	@echo "  make build              - Build entire project with Maven (Java and Angular tests included)"
	@echo "  make build_no_web       - Build excluding query-service-web (uses the last installed UI build)"
	@echo "  make build_docker       - Build the container image $(IMAGE):latest"
	@echo ""
	@echo "Local Run Targets:"
	@echo "  make run                - Run QTT (builds if needed); UI and API on https://localhost:8443"
	@echo "  make dev                - Run QTT in Quarkus dev mode (live reload; run 'make build' once first)"
	@echo "  make build_and_run      - Build then run QTT"
	@echo "  make stop               - Stop a QTT started with 'make run'"
	@echo "  make postgres_run       - Start PostgreSQL and run QTT against it"
	@echo "  make mssql_run          - Start MSSQL and run QTT against it"
	@echo ""
	@echo "Docker Targets:"
	@echo "  make destroy_docker     - Remove QTT container"
	@echo "  make run_docker         - Build and run QTT in a container"
	@echo "  make logs_docker        - View QTT container logs"
	@echo ""
	@echo "Database Targets:"
	@echo "  make start_redis        - Start Redis container"
	@echo "  make start_postgres     - Start PostgreSQL container"
	@echo "  make start_mssql        - Start MSSQL container"
	@echo "  make stop_redis         - Stop Redis container"
	@echo "  make stop_postgres      - Stop PostgreSQL container"
	@echo "  make stop_mssql         - Stop MSSQL container"
	@echo "  make stop_databases     - Stop all database containers"
	@echo "  make logs_redis         - View Redis logs"
	@echo "  make logs_postgres      - View PostgreSQL logs"
	@echo "  make logs_mssql         - View MSSQL logs"
	@echo ""
	@echo "Test Targets:"
	@echo "  make test               - Run Maven unit tests"
	@echo "  make it                 - Run the black-box regression suite against $(IMAGE):latest (Derby)"
	@echo "  make gate               - Full regression gate: all databases, Redis, restart, UI, TLS"
	@echo "  make measure            - Measure image size, cold start, memory and throughput (LABEL=name)"
	@echo ""
	@echo "Utility Targets:"
	@echo "  make clean              - Remove build artifacts"
	@echo "  make help               - Show this help message"

# ==============================================================================
# Build Targets
# ==============================================================================

# Build the entire project using Maven
build:
	$(MAVEN) clean install

# Build the project excluding the query-service-web module
build_no_web:
	$(MAVEN) -pl '!query-service-web' clean install

# Build the container image (JVM mode) after a full Maven build
build_docker: build
	$(ENGINE) build -f $(APP_DIR)/src/main/docker/Dockerfile.jvm -t $(IMAGE):latest $(APP_DIR)

# ==============================================================================
# Local Run Targets
# ==============================================================================

# Run the service: build first if the application has not been built
run:
ifeq ($(DETECTED_OS),Windows)
	@if not exist "$(RUN_CHECK_PATH)" ( \
		echo Application not found. Building... & \
		$(MAKE) build \
	)
	@if exist .env ( for /f "usebackq tokens=1,* delims==" %%A in (".env") do @set "%%A=%%B" ) & \
	cd $(RUN_DIR) && set "KEYSTORE=$(LOCAL_KEYSTORE)" && java -jar $(RUN_JAR)
else
	@if [ ! -f $(RUN_CHECK_PATH) ]; then \
		echo "Application not found. Building..."; \
		$(MAKE) build; \
	fi
	set -a; [ -f .env ] && . ./.env; set +a; cd $(RUN_DIR) && KEYSTORE=$${KEYSTORE:-$(LOCAL_KEYSTORE)} java -jar $(RUN_JAR)
endif

# Quarkus dev mode (needs the other modules installed: run 'make build' once)
dev:
	mvn -pl $(APP_DIR) quarkus:dev -Dquarkus.http.ssl.certificate.key-store-file=src/main/etc/keystore

# Build the project with Maven, then launch QTT
build_and_run: build run

# Stop a QTT started with 'make run'
stop:
ifeq ($(DETECTED_OS),Windows)
	@echo Stop QTT with Ctrl+C in the window where it runs.
else
	@pkill -f '$(RUN_JAR)' && echo "Stopped QTT" || echo "QTT is not running"
endif

# Run with PostgreSQL: start PostgreSQL container, then run QTT against it
postgres_run: start_postgres
	@echo "Starting QTT with PostgreSQL configuration..."
ifeq ($(DETECTED_OS),Windows)
	@if not exist "$(RUN_CHECK_PATH)" ( \
		echo Application not found. Building... & \
		$(MAKE) build \
	)
	@set "DB_URL=jdbc:postgresql://localhost:5432/qtt" & \
	set "DB_USER=postgres" & \
	set "DB_PASSWORD=verYs3cret" & \
	if exist .env ( for /f "usebackq tokens=1,* delims==" %%A in (".env") do @set "%%A=%%B" ) & \
	cd $(RUN_DIR) && set "KEYSTORE=$(LOCAL_KEYSTORE)" && java -jar $(RUN_JAR)
else
	@if [ ! -f $(RUN_CHECK_PATH) ]; then \
		echo "Application not found. Building..."; \
		$(MAKE) build; \
	fi
	@export DB_URL="jdbc:postgresql://localhost:5432/qtt"; \
	export DB_USER="postgres"; \
	export DB_PASSWORD="verYs3cret"; \
	set -a; [ -f .env ] && . ./.env; set +a; \
	cd $(RUN_DIR) && KEYSTORE=$${KEYSTORE:-$(LOCAL_KEYSTORE)} java -jar $(RUN_JAR)
endif

# Run with MSSQL: start MSSQL container, then run QTT against it
mssql_run: start_mssql
	@echo "Starting QTT with MSSQL configuration..."
ifeq ($(DETECTED_OS),Windows)
	@if not exist "$(RUN_CHECK_PATH)" ( \
		echo Application not found. Building... & \
		$(MAKE) build \
	)
	@set "DB_URL=jdbc:sqlserver://localhost:1433;databaseName=qtt;encrypt=true;trustServerCertificate=true" & \
	set "DB_USER=sa" & \
	set "DB_PASSWORD=verYs3cret" & \
	if exist .env ( for /f "usebackq tokens=1,* delims==" %%A in (".env") do @set "%%A=%%B" ) & \
	cd $(RUN_DIR) && set "KEYSTORE=$(LOCAL_KEYSTORE)" && java -jar $(RUN_JAR)
else
	@if [ ! -f $(RUN_CHECK_PATH) ]; then \
		echo "Application not found. Building..."; \
		$(MAKE) build; \
	fi
	@export DB_URL="jdbc:sqlserver://localhost:1433;databaseName=qtt;encrypt=true;trustServerCertificate=true"; \
	export DB_USER="sa"; \
	export DB_PASSWORD="verYs3cret"; \
	set -a; [ -f .env ] && . ./.env; set +a; \
	cd $(RUN_DIR) && KEYSTORE=$${KEYSTORE:-$(LOCAL_KEYSTORE)} java -jar $(RUN_JAR)
endif

# ==============================================================================
# Docker Targets
# ==============================================================================

# Remove any existing container named 'qtt'
destroy_docker:
	-$(ENGINE) container rm -f qtt

# Build the image and run the container in detached mode, mapping required ports
run_docker: destroy_docker build_docker
	$(ENGINE) run -d --name qtt -p 8443:8443 -p 8888:8888 $(IMAGE):latest

# View logs from the QTT container
logs_docker:
	$(ENGINE) logs -f qtt

# ==============================================================================
# Database Targets
# ==============================================================================

# Start Redis container
start_redis:
	$(COMPOSE) up -d redis

# Start PostgreSQL container, wait until it accepts connections, and create the qtt database if it does not exist
# (POSTGRES_DB would only apply to a new volume). Runs inside the container and avoids $ and quotes the host shell
# would interpret, so the same command works from sh and cmd.exe. The TCP check skips the socket-only server the
# image runs while initializing a new volume.
start_postgres:
	$(COMPOSE) up -d postgresql
	$(ENGINE) exec postgresql sh -c "timeout 60 sh -c 'until pg_isready -q -h 127.0.0.1 -U postgres; do sleep 1; done' || { echo 'PostgreSQL did not start within 60 s'; exit 1; }; (psql -U postgres -tAc 'SELECT datname FROM pg_database' | grep -qx qtt || createdb -U postgres qtt) && echo 'PostgreSQL ready, database qtt exists'"

# Start MSSQL container
start_mssql:
	$(COMPOSE) up -d mssql

# Stop Redis container
stop_redis:
	$(COMPOSE) stop redis

# Stop PostgreSQL container
stop_postgres:
	$(COMPOSE) stop postgresql

# Stop MSSQL container
stop_mssql:
	$(COMPOSE) stop mssql

# Stop all database containers
stop_databases:
	$(COMPOSE) stop redis postgresql mssql

# View Redis logs
logs_redis:
	$(COMPOSE) logs -f redis

# View PostgreSQL logs
logs_postgres:
	$(COMPOSE) logs -f postgresql

# View MSSQL logs
logs_mssql:
	$(COMPOSE) logs -f mssql

# ==============================================================================
# Utility Targets
# ==============================================================================

# Remove all build artifacts
clean:
	$(MAVEN) clean

# Run unit tests
test:
	$(MAVEN) test

# Black-box regression suite against a fresh test stack (Derby, no Redis)
it:
	QTT_IMAGE=$(IMAGE):latest scripts/stack.sh up
	-scripts/stack.sh it
	scripts/stack.sh down

# Full regression gate (all databases, Redis, restart, UI, TLS); see docs/quarkus-migration-plan.md
gate:
	scripts/gate.sh $(IMAGE):latest

# Measure the image; results go to docs/perf/
measure:
	python3 scripts/measure.py --image $(IMAGE):latest --label "$${LABEL:-local}"

# ==============================================================================
# Query Service Makefile
# ==============================================================================

.PHONY: help build build_no_web refresh_bundles build_docker \
        run build_and_run stop postgres_run mssql_run \
        destroy_docker run_docker logs_docker \
        start_redis start_postgres start_mssql \
        stop_redis stop_postgres stop_mssql stop_databases \
        logs_redis logs_postgres logs_mssql \
        clean test

# ------------------------------------------------------------------------------
# Variables
# ------------------------------------------------------------------------------

# Determine the container engine to use: podman if available, otherwise docker
ENGINE := $(shell command -v podman > /dev/null 2>&1 && echo podman || echo docker)

# Compose command using detected engine
COMPOSE := $(ENGINE) compose

# Define common Maven options: use 2 threads per core
MAVEN := mvn -T 2C

# Define the target directory for the query service distribution
DIST_DIR := query-service-distribution/target

# Detect operating system and set platform-specific variables
ifeq ($(OS),Windows_NT)
    DETECTED_OS := Windows
    # Force cmd.exe as shell on Windows (choco make uses bash by default)
    SHELL := cmd.exe
    .SHELLFLAGS := /c
    KARAF_BIN := $(DIST_DIR)/assembly/bin/karaf.bat
    KARAF_STOP := $(DIST_DIR)/assembly/bin/stop.bat
    # Use backslash for Windows path in existence checks
    DIST_CHECK_PATH := $(subst /,\,$(DIST_DIR))\assembly
else
    DETECTED_OS := Unix
    KARAF_BIN := $(DIST_DIR)/assembly/bin/karaf
    KARAF_STOP := $(DIST_DIR)/assembly/bin/stop
    DIST_CHECK_PATH := $(DIST_DIR)/assembly
endif

# ==============================================================================
# Help Target (default)
# ==============================================================================

help:
	@echo "Query Service Makefile Commands"
	@echo "================================"
	@echo ""
	@echo "Build Targets:"
	@echo "  make build              - Build entire project with Maven"
	@echo "  make build_no_web       - Build excluding query-service-web module"
	@echo "  make refresh_bundles    - Rebuild bundles (excludes web and distribution)"
	@echo "  make build_docker       - Build Docker image"
	@echo ""
	@echo "Local Run Targets:"
	@echo "  make run                - Run Karaf (builds if needed)"
	@echo "  make build_and_run      - Build then run Karaf"
	@echo "  make stop               - Stop running Karaf instance"
	@echo "  make postgres_run       - Start PostgreSQL and run Karaf with PostgreSQL config"
	@echo "  make mssql_run          - Start MSSQL and run Karaf with MSSQL config"
	@echo ""
	@echo "Docker Targets:"
	@echo "  make destroy_docker     - Remove QTT container"
	@echo "  make run_docker         - Build and run QTT in Docker"
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
	@echo "Utility Targets:"
	@echo "  make clean              - Remove build artifacts"
	@echo "  make test               - Run Maven tests"
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

# Refresh bundles by building all modules except query-service-web and query-service-distribution
refresh_bundles:
	$(MAVEN) -pl '!query-service-web,!query-service-distribution' clean install

# Build the Docker image for query-service-distribution after running a full Maven build
build_docker: build
	$(MAVEN) -pl query-service-distribution docker:build

# ==============================================================================
# Local Run Targets
# ==============================================================================

# Run the service: if the distribution is already built, simply launch Karaf; otherwise, build first
run:
ifeq ($(DETECTED_OS),Windows)
	@if not exist "$(DIST_CHECK_PATH)" ( \
		echo Distribution not found. Building... & \
		$(MAKE) build \
	)
	@if exist .env ( for /f "usebackq tokens=1,* delims==" %%A in (".env") do @set "%%A=%%B" ) & "$(KARAF_BIN)"
else
	@if [ ! -d $(DIST_DIR)/assembly/ ]; then \
		echo "Distribution not found. Building..."; \
		$(MAKE) build; \
	fi
	set -a; [ -f .env ] && . .env; set +a; $(KARAF_BIN)
endif

# Build the project with Maven, then launch Karaf
build_and_run: build run

# Stop the running Karaf instance
stop:
ifeq ($(DETECTED_OS),Windows)
	@if exist "$(KARAF_STOP)" ( \
		echo Stopping Karaf... & \
		"$(KARAF_STOP)" \
	) else ( \
		echo Karaf stop script not found. Is the distribution built? \
	)
else
	@if [ -f $(KARAF_STOP) ]; then \
		echo "Stopping Karaf..."; \
		$(KARAF_STOP); \
	else \
		echo "Karaf stop script not found. Is the distribution built?"; \
	fi
endif

# Run with PostgreSQL: start PostgreSQL container, then run Karaf with PostgreSQL configuration
postgres_run: start_postgres
	@echo "Starting Karaf with PostgreSQL configuration..."
ifeq ($(DETECTED_OS),Windows)
	@if not exist "$(DIST_CHECK_PATH)" ( \
		echo Distribution not found. Building... & \
		$(MAKE) build \
	)
	@echo Setting PostgreSQL environment variables...
	@set "DB_DRIVER_NAME=PostgreSQL JDBC Driver" & \
	set "DB_URL=jdbc:postgresql://localhost:5432/qtt" & \
	set "DB_USER=postgres" & \
	set "DB_PASSWORD=verYs3cret" & \
	if exist .env ( for /f "usebackq tokens=1,* delims==" %%A in (".env") do @set "%%A=%%B" ) & \
	"$(KARAF_BIN)"
else
	@if [ ! -d $(DIST_DIR)/assembly/ ]; then \
		echo "Distribution not found. Building..."; \
		$(MAKE) build; \
	fi
	@echo "Setting PostgreSQL environment variables..."
	@export DB_DRIVER_NAME="PostgreSQL JDBC Driver"; \
	export DB_URL="jdbc:postgresql://localhost:5432/qtt"; \
	export DB_USER="postgres"; \
	export DB_PASSWORD="verYs3cret"; \
	set -a; [ -f .env ] && . ./.env; set +a; \
	$(KARAF_BIN)
endif

# Run with MSSQL: start MSSQL container, then run Karaf with MSSQL configuration
mssql_run: start_mssql
	@echo "Starting Karaf with MSSQL configuration..."
ifeq ($(DETECTED_OS),Windows)
	@if not exist "$(DIST_CHECK_PATH)" ( \
		echo Distribution not found. Building... & \
		$(MAKE) build \
	)
	@echo Setting MSSQL environment variables...
	@set "DB_DRIVER_NAME=Microsoft JDBC Driver for SQL Server" & \
	set "DB_URL=jdbc:sqlserver://localhost:1433;databaseName=qtt;encrypt=true;trustServerCertificate=true" & \
	set "DB_USER=sa" & \
	set "DB_PASSWORD=verYs3cret" & \
	if exist .env ( for /f "usebackq tokens=1,* delims==" %%A in (".env") do @set "%%A=%%B" ) & \
	"$(KARAF_BIN)"
else
	@if [ ! -d $(DIST_DIR)/assembly/ ]; then \
		echo "Distribution not found. Building..."; \
		$(MAKE) build; \
	fi
	@echo "Setting MSSQL environment variables..."
	@export DB_DRIVER_NAME="Microsoft JDBC Driver for SQL Server"; \
	export DB_URL="jdbc:sqlserver://localhost:1433;databaseName=qtt;encrypt=true;trustServerCertificate=true"; \
	export DB_USER="sa"; \
	export DB_PASSWORD="verYs3cret"; \
	set -a; [ -f .env ] && . ./.env; set +a; \
	$(KARAF_BIN)
endif

# ==============================================================================
# Docker Targets
# ==============================================================================

# Remove any existing container named 'qtt'
destroy_docker:
	-$(ENGINE) container rm -f qtt

# Build the Docker image and run the container in detached mode, mapping required ports
run_docker: destroy_docker build_docker
	$(ENGINE) run -d --name qtt -p 8443:8443 -p 8888:8888 inovexis/qtt:latest

# View logs from the QTT Docker container
logs_docker:
	$(ENGINE) logs -f qtt

# ==============================================================================
# Database Targets
# ==============================================================================

# Start Redis container
start_redis:
	$(COMPOSE) up -d redis

# Start PostgreSQL container
start_postgres:
	$(COMPOSE) up -d postgresql

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

# Run tests
test:
	$(MAVEN) test

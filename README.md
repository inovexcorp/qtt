# Query Templating Tool (QTT)

Query Templating Tool (QTT) is a microservice platform that translates domain-specific search requests into optimized
SPARQL queries for graph database systems. It acts as a middleware layer between your applications and your knowledge
graph, simplifying data access through templated queries.

## How It Works

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

## Key Features

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

## Use Cases

- **Simplified Knowledge Graph Access**: Hide SPARQL complexity from frontend developers
- **Multi-Tenant Graph Queries**: Route different clients to different graph layers
- **Query Performance Optimization**: Centralized template management and caching
- **Graph Data API Gateway**: Single endpoint for all graph database interactions
- **Semantic Search Services**: Build search APIs backed by ontology-driven queries

## Architecture Overview

QTT is built on a multi-module Maven project with these key components:

**Backend Modules:**

- **query-service-core**: RDF/JSON-LD serialization utilities
- **camel-anzo**: Custom Apache Camel component for Anzo integration
- **query-service-route-builder**: Dynamic Camel route creation from database templates
- **query-service-persistence**: JPA entities and services for routes, datasources, metrics
- **query-service-scheduler**: Cron jobs for metrics collection and cleanup
- **query-service-metrics**: JMX-based metrics collection
- **query-service-cache**: Redis-backed caching layer for query results
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

## Quick Start

The fastest way to get QTT running is using the official Docker image:

```bash
# Pull the image
docker pull docker.io/inovexis/qtt:latest

# Run the container
docker run -d \
  --name qtt \
  -p 8080:8080 \
  -p 8888:8888 \
  docker.io/inovexis/qtt:latest
```

Access the application:

- **Web UI**: http://localhost:8080
- **API Base**: http://localhost:8080/queryrest/api/
- **Query Endpoints**: http://localhost:8888/{route-id}

For detailed deployment options, see the [Administration Guide](docs/administration.md).

## Documentation

| Document                                             | Description                                                                   |
|------------------------------------------------------|-------------------------------------------------------------------------------|
| [Administration Guide](docs/administration.md)       | Docker, Podman, Docker Compose deployment, SSL/TLS, volumes, production setup |
| [Development Setup](docs/development-setup.md)       | Building from source, Makefile commands, local development environment        |
| [Configuration Reference](docs/configuration.md)     | Environment variables, configuration files, database backends                 |
| [User Guide](docs/user-guide.md)                     | Getting started tutorial, Web UI guide, creating routes and datasources       |
| [Template Development](docs/template-development.md) | Freemarker templates, SPARQL patterns, best practices                         |
| [API Reference](docs/api-reference.md)               | REST API documentation for all endpoints                                      |
| [SPARQi AI Assistant](docs/sparqi.md)                | Setup and usage of the AI-powered SPARQL assistant                            |
| [Troubleshooting](docs/troubleshooting.md)           | Common issues, monitoring, logging, performance tuning                        |
| [Advanced Topics](docs/advanced-topics.md)           | Database migration, high availability, backup/restore, security hardening     |

## System Requirements

**For Running with Docker/Podman:**

- Docker 20.10+ or Podman 3.0+
- 2GB RAM minimum (4GB recommended)
- 2GB disk space

**For Running from Source:**

- Java 17 (JDK)
- Apache Maven 3.6+
- Node.js v20.18.1
- Angular CLI 18.2.21

**For Production Deployment:**

- PostgreSQL 16+ or SQL Server 2019+ (recommended over embedded Derby)
- SSL certificates for HTTPS
- Optional: Redis 6.0+ for query result caching
- Optional: LLM provider access for SPARQi (OpenAI, Azure OpenAI, LiteLLM, etc.)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Copyright (c) 2025 RealmOne

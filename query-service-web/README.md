# Query Service Web

An Angular-based web interface for the Query Service, packaged as an OSGi bundle for deployment in Apache Karaf.

## Overview

Query Service Web is a single-page application (SPA) built with Angular that provides a user interface for managing query routes, datasources, and monitoring metrics. The application is uniquely packaged as an OSGi bundle, allowing it to be deployed directly into an Apache Karaf container alongside the backend services.

**Key Features:**
- Routes management with Freemarker template editing
- Datasource configuration and health monitoring
- Real-time performance metrics visualization
- SPARQi AI-powered query assistance
- Redis cache management
- Monaco Editor integration for SPARQL/Freemarker editing

## Architecture

### OSGi HTTP Whiteboard Integration

The Angular application is served through OSGi's HTTP Whiteboard pattern using two key components:

1. **`WebResources.java`**: Registers the compiled Angular application (`/build` directory) as static web resources
2. **`QueryServiceContextHelper.java`**: Custom servlet context that handles SPA routing by redirecting all non-file requests to `index.html`

This architecture allows the Angular SPA to:
- Be deployed as a native OSGi bundle
- Use hash-based routing (`/#/routes`, `/#/metrics`, etc.)
- Coexist with JAX-RS REST endpoints in the same Karaf container
- Be hot-reloaded when the bundle is updated

### Application Flow

```
User Request → Karaf HTTP Service → OSGi HTTP Whiteboard
                                     ↓
                          QueryServiceContextHelper
                                     ↓
                    WebResources (serves /build/*)
                                     ↓
                              Angular SPA
                                     ↓
                    Angular Services (HTTP calls)
                                     ↓
                  Backend REST API (/queryrest/api/*)
```

## Technology Stack

### Frontend
- **Angular**: 18.2.0 - Core framework
- **Angular Material**: 18.2.0 - UI component library
- **Bootstrap**: 5.3.0 - Additional styling
- **Monaco Editor**: 0.50.0 - Code editor for Freemarker templates
- **ngx-charts**: 20.5.0 - D3-based charting library
- **ngx-markdown**: 18.0.0 - Markdown rendering
- **RxJS**: 7.8.0 - Reactive programming

### Build Tools
- **Node.js**: 20.18.1 (managed by frontend-maven-plugin)
- **npm**: 10.8.2
- **Angular CLI**: 18.2.0
- **TypeScript**: 5.5.0

### OSGi Integration
- **OSGi Core**: Declarative Services
- **Pax Web**: 8.0.11 - HTTP Whiteboard implementation
- **Maven Bundle Plugin**: OSGi bundle packaging

## Project Structure

```
query-service-web/
├── src/
│   ├── main/
│   │   ├── java/com/inovexcorp/queryservice/web/
│   │   │   ├── WebResources.java           # OSGi HTTP Whiteboard resource registration
│   │   │   └── QueryServiceContextHelper.java  # SPA routing handler
│   │   └── resources/
│   │       └── public/                      # Angular application root
│   │           ├── app/
│   │           │   ├── core/
│   │           │   │   ├── models/          # TypeScript interfaces/models
│   │           │   │   └── services/        # HTTP services for backend communication
│   │           │   ├── pages/
│   │           │   │   ├── routes/          # Route management page
│   │           │   │   ├── datasources/     # Datasource configuration page
│   │           │   │   ├── metrics/         # Performance metrics dashboard
│   │           │   │   ├── settings/        # Application settings
│   │           │   │   ├── config-route/    # Route editor with SPARQi chat
│   │           │   │   ├── add-route/       # New route creation
│   │           │   │   └── config-datasource/  # Datasource editor
│   │           │   ├── shared/              # Shared components (breadcrumbs, etc.)
│   │           │   ├── app.module.ts        # Main application module
│   │           │   ├── app-routing.module.ts   # Route configuration
│   │           │   └── app.component.ts     # Root component
│   │           ├── environments/            # Environment configurations
│   │           ├── index.html               # Application entry point
│   │           ├── main.ts                  # Angular bootstrap
│   │           └── styles.scss              # Global styles
│   └── test/                                # Unit tests
├── angular.json                             # Angular CLI configuration
├── package.json                             # npm dependencies
├── tsconfig.json                            # TypeScript configuration
├── webpack.config.js                        # Custom webpack config for Monaco Editor
├── proxy.conf.js                            # Dev server proxy configuration
├── karma.conf.js                            # Test runner configuration
└── pom.xml                                  # Maven build configuration
```

## Features

### 1. Routes Management
- **Create/Edit/Delete**: Manage query routes that translate JSON requests to SPARQL
- **Template Editor**: Monaco Editor with syntax highlighting for Freemarker templates
- **Route Configuration**: Set datasources, graphmarts, layers, and cache settings
- **Test Routes**: Execute test queries directly from the UI
- **Clone Routes**: Duplicate existing routes for rapid development

### 2. Datasource Management
- **CRUD Operations**: Create, read, update, delete Anzo datasources
- **Health Monitoring**: Real-time health checks and status history
- **Connection Testing**: Test datasource connections before saving
- **Enable/Disable**: Toggle datasource availability without deletion

### 3. Metrics Dashboard
- **Performance Visualization**: Line charts showing route execution times
- **Time Range Selection**: View metrics over different time periods
- **Route Comparison**: Compare performance across multiple routes
- **Statistics**: Mean time, total calls, exchange failures per route

### 4. SPARQi Chat Interface
- **AI-Powered Assistance**: Natural language interface for query development
- **Context-Aware**: Understands route configuration and datasource schemas
- **Ontology Integration**: Autocomplete and visualization of ontology elements
- **Session Management**: Persistent chat sessions per route

### 5. Cache Management
- **Per-Route Cache Control**: Enable/disable Redis caching per route
- **Cache Statistics**: View hit rates, miss rates, and eviction counts
- **Clear Cache**: Manually clear cache for specific routes
- **TTL Configuration**: Set custom time-to-live for cached results

## Build Process

### Maven Build Integration

The `pom.xml` uses the `frontend-maven-plugin` to automate the Node.js/Angular build process within Maven:

1. **Install Node & npm** (prepare-package phase)
   - Downloads Node.js v20.18.1 and npm 10.8.2 to `target/`
   - Ensures consistent build environment across systems

2. **npm install** (prepare-package phase)
   - Installs dependencies from `package.json`
   - Uses `--legacy-peer-deps` for compatibility

3. **ng build** (prepare-package phase)
   - Compiles Angular application with Angular CLI
   - Outputs to `target/classes/build/`

4. **Bundle Packaging** (package phase)
   - Maven Bundle Plugin packages the build output into an OSGi bundle
   - Includes both Angular assets and Java OSGi classes

### Build Output

The compiled application is placed in `target/classes/build/` which becomes part of the OSGi bundle's classpath. The `WebResources` component then exposes this directory at the root HTTP path (`/`).

## Development Setup

### Prerequisites

- **Java**: 17 or higher
- **Maven**: 3.6+ (for full project build)
- **Node.js**: 14.20+ (for local Angular development)
- **npm**: 6.14+

### Local Development (Angular Dev Server)

For rapid frontend development without full Maven builds:

1. **Navigate to the web module**:
   ```bash
   cd query-service-web
   ```

2. **Install dependencies** (first time only):
   ```bash
   npm install --legacy-peer-deps
   ```

3. **Start the development server**:
   ```bash
   npm start
   # or: ng serve
   ```

4. **Access the application**:
   - Frontend: http://localhost:4200
   - Backend API calls are proxied to http://localhost:8080 (configured in `proxy.conf.js`)

5. **Backend setup**:
   - Ensure the backend Query Service is running on port 8080
   - See the main project README for backend startup instructions

### Proxy Configuration

The `proxy.conf.js` file routes API calls from the dev server to the backend:

```javascript
{
  "/queryrest": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true,
    "logLevel": "debug"
  }
}
```

This allows the Angular dev server to proxy requests to `/queryrest/api/*` to the running backend service.

## Build Commands

### Full Maven Build

Build the entire project including the Angular application:

```bash
# From project root
mvn clean install

# Or build only the web module
cd query-service-web
mvn clean install
```

**Note**: This is slow on first run (downloads Node.js, npm packages) but subsequent builds use cached dependencies.

### Angular CLI Commands

Direct Angular CLI commands (requires Node.js installation):

```bash
# Development build (faster, includes source maps)
ng build

# Production build (optimized, minified)
ng build --configuration production

# Watch mode (rebuild on file changes)
ng build --watch

# Serve with live reload
ng serve
```

### Skip Web Module (Faster Backend Development)

When working on backend code only, skip the slow web module build:

```bash
# From project root
mvn -pl '!query-service-web' clean install
```

## Testing

### Unit Tests

Run Angular unit tests with Karma:

```bash
cd query-service-web
npm test
```

Tests are located alongside components with `.spec.ts` extension.

### Test Configuration

- **Test Framework**: Jasmine 5.1.0
- **Test Runner**: Karma 6.4.0
- **Browser**: Chrome (headless mode available)
- **Coverage**: Istanbul code coverage reports

## Deployment

### As Part of Full Distribution

The web bundle is automatically included in the Karaf distribution:

```bash
# From project root
mvn clean install

# Run the distribution
cd query-service-distribution/target/assembly
bin/start
```

The web UI will be available at:
- Production: http://localhost:8181/ (Karaf HTTP port)
- The exact port depends on your Karaf HTTP configuration

### Manual Bundle Deployment

Deploy the web bundle to a running Karaf instance:

```bash
# Copy the bundle
cp query-service-web/target/query-service-web-*.jar \
   $KARAF_HOME/deploy/

# Or install via Karaf console
karaf@root()> bundle:install -s mvn:com.inovexcorp.queryservice/query-service-web/1.0.39-SNAPSHOT
```

### Verify Deployment

Check bundle status in Karaf console:

```bash
karaf@root()> bundle:list | grep web
```

You should see the bundle in "Active" state.

## Configuration

### Build Configuration

**angular.json**: Angular CLI build configuration
- Output path: `target/classes/build`
- Source root: `src/main/resources/public`
- Custom webpack config for Monaco Editor

**tsconfig.json**: TypeScript compiler options
- Target: ES2022
- Module: ES2020
- Strict mode enabled

**webpack.config.js**: Custom webpack rules
- Monaco Editor CSS/font handling
- Asset bundling configuration

### Runtime Configuration

The Angular application adapts to the backend environment:

- **API Base URL**: Determined by backend configuration (`/queryrest/api/`)
- **Hash Routing**: Enabled for OSGi compatibility
- **Environment Files**: `environment.ts` (dev) and `environment.prod.ts` (prod)

## Development Notes

### Why Hash Routing?

The application uses hash-based routing (`/#/routes`) instead of HTML5 push-state routing because:
1. OSGi HTTP Whiteboard serves static resources
2. Hash routing doesn't require server-side URL rewriting
3. The `QueryServiceContextHelper` handles SPA routing by serving `index.html` for non-file requests

### Monaco Editor Integration

Monaco Editor (VS Code's editor) requires special webpack configuration:
- CSS files must be loaded from node_modules
- Fonts and assets are copied to `assets/monaco/`
- Custom webpack config in `webpack.config.js` handles this

### Legacy Peer Dependencies

The `--legacy-peer-deps` flag is required due to peer dependency conflicts between:
- Angular 18 packages
- Monaco Editor wrapper (ngx-monaco-editor-v2)
- ngx-charts and D3 dependencies

### Build Performance

**Fast Development Cycle**:
- Use `npm start` for frontend development (live reload)
- Backend changes don't require Angular rebuild

**Slow Maven Build**:
- First build: ~5-10 minutes (downloads Node.js + npm packages)
- Subsequent builds: ~2-3 minutes
- Skip with `-pl '!query-service-web'` when not needed

## Troubleshooting

### Build Fails with "npm install" Error

**Problem**: `frontend-maven-plugin` fails to install dependencies

**Solution**:
```bash
cd query-service-web
rm -rf node_modules target
npm install --legacy-peer-deps
mvn clean install
```

### Bundle Not Active in Karaf

**Problem**: Web bundle shows "Installed" but not "Active"

**Solution**:
1. Check Karaf logs: `log:tail`
2. Verify Pax Web is installed: `bundle:list | grep pax-web`
3. Check for missing dependencies: `bundle:diag <bundle-id>`

### Cannot Access Web UI

**Problem**: http://localhost:8181 shows 404

**Solution**:
1. Verify web bundle is active: `bundle:list | grep web`
2. Check HTTP service port: `config:property-get org.osgi.service.http.port`
3. Check Pax Web configuration: `config:list | grep pax.web`

### Dev Server Proxy Errors

**Problem**: API calls fail with CORS or 404 errors

**Solution**:
1. Ensure backend is running on port 8080
2. Check `proxy.conf.js` configuration
3. Start dev server with proxy: `ng serve --proxy-config proxy.conf.js`

### Monaco Editor Not Loading

**Problem**: Template editor shows blank or errors in console

**Solution**:
1. Clear Angular cache: `rm -rf .angular/cache`
2. Reinstall Monaco assets: `npm install --legacy-peer-deps`
3. Check `angular.json` assets configuration includes Monaco

## Related Modules

- **query-service-route-builder**: Backend route management and Camel integration
- **query-service-persistence**: JPA entities for routes/datasources
- **query-service-metrics**: JMX metrics collection
- **query-service-cache**: Redis caching layer
- **query-service-distribution**: Karaf assembly including this web bundle

## Contributing

When making changes to the web module:

1. **Test locally**: Use `npm start` for rapid iteration
2. **Run tests**: `npm test` before committing
3. **Update models**: Keep TypeScript interfaces in sync with backend DTOs
4. **Build verification**: Run full Maven build before creating PR
5. **Bundle test**: Deploy to Karaf and verify bundle activation

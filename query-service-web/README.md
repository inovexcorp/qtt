# Query Service Web

An Angular-based web interface for the Query Service, packaged as a jar of static resources that the Quarkus
application (`query-service-app`) serves.

## Overview

Query Service Web is a single-page application (SPA) built with Angular that provides a user interface for managing query routes, datasources, and monitoring metrics. The build places the compiled application under `META-INF/resources` in the module's jar; `query-service-app` depends on the jar, and Quarkus serves those files at `/` alongside the REST API.

**Key Features:**
- Routes management with Freemarker template editing
- Datasource configuration and health monitoring
- Real-time performance metrics visualization
- SPARQi AI-powered query assistance
- Redis cache management
- Monaco Editor integration for SPARQL/Freemarker editing

## Architecture

### Static Resources in Quarkus

The module contains no Java code. The Angular build output is written to `target/classes/META-INF/resources`, which
Quarkus serves as static content from the classpath. `query-service-app` adds a small `SpaFallback` route that serves
`index.html` for UI paths that are not files (anything outside `/queryrest` whose last path segment has no
extension).

This allows the Angular SPA to:
- Ship inside the single QTT application, with no separate web server
- Use hash-based routing (`/#/routes`, `/#/metrics`, etc.)
- Share the HTTP server (ports 8443 HTTPS and 8080 HTTP) with the REST endpoints under `/queryrest`

### Application Flow

```
User Request → Quarkus HTTP server (8443 / 8080)
                                     ↓
                 Static resources (META-INF/resources/*)
                 or SpaFallback → index.html
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

### Packaging
- **frontend-maven-plugin**: Runs npm install, tests and build during the Maven build
- **Jar packaging**: Build output under `META-INF/resources`, served by `query-service-app`

## Project Structure

```
query-service-web/
├── src/
│   ├── main/
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

3. **npm test** (prepare-package phase)
   - Runs the Karma unit tests in headless Chrome with code coverage

4. **ng build** (prepare-package phase)
   - Compiles Angular application with Angular CLI
   - Outputs to `target/classes/META-INF/resources/`

5. **Jar Packaging** (package phase)
   - The build output is packaged into the module's jar

### Build Output

The compiled application is placed in `target/classes/META-INF/resources/`, so it ends up under
`META-INF/resources` in the jar. Quarkus serves that classpath directory at the root HTTP path (`/`) of
`query-service-app`.

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

### As Part of the QTT Application

The web jar is a dependency of `query-service-app` and is included in the application and container image:

```bash
# From project root
make build

# Run the application
make run
```

The web UI will be available at:
- https://localhost:8443/ (HTTPS)
- http://localhost:8080/ (plain HTTP)

There is no separate deployment of the web module; rebuild `query-service-app` (or the image) to pick up UI changes.

## Configuration

### Build Configuration

**angular.json**: Angular CLI build configuration
- Output path: `target/classes/META-INF/resources`
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
- **Hash Routing**: Enabled (`useHash: true`)
- **Environment Files**: `environment.ts` (dev) and `environment.prod.ts` (prod)

## Development Notes

### Why Hash Routing?

The application uses hash-based routing (`/#/routes`) instead of HTML5 push-state routing because:
1. Quarkus serves the UI as static resources
2. Hash routing doesn't require server-side URL rewriting
3. As a fallback, `SpaFallback` in `query-service-app` serves `index.html` for non-file UI paths

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

### Cannot Access Web UI

**Problem**: https://localhost:8443 shows 404

**Solution**:
1. Check that the web jar contains the build: `unzip -l query-service-web/target/query-service-web-*.jar | grep META-INF/resources/index.html`
2. Rebuild `query-service-app` after rebuilding the web module (or use `make build`)
3. Check the QTT console log for startup errors

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
- **query-service-app**: Quarkus application that serves this UI

## Contributing

When making changes to the web module:

1. **Test locally**: Use `npm start` for rapid iteration
2. **Run tests**: `npm test` before committing
3. **Update models**: Keep TypeScript interfaces in sync with backend DTOs
4. **Build verification**: Run full Maven build before creating PR
5. **Application test**: Run `make run` and check the UI at https://localhost:8443

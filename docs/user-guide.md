# User Guide

This guide walks you through using QTT to create and manage query routes.

## Getting Started Tutorial

This tutorial walks you through creating your first query route from start to finish.

### Prerequisites

- QTT is running and accessible at http://localhost:8080
- You have access to an Altair Graph Studio graph database instance
- Basic familiarity with SPARQL and RDF

### Step 1: Access the Web UI

1. Open your browser and navigate to: **http://localhost:8080/**
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

## Web UI Guide

The Query Templating Tool provides a rich Angular-based web interface for managing all aspects of your query routes and
datasources.

### Navigation Overview

The application uses a left sidebar navigation with four main sections:

- **Metrics**: Performance monitoring and analytics dashboard
- **Routes**: Query route management
- **DataSources**: Graph database connection management
- **Settings**: System information and statistics

### DataSources Page

The DataSources page displays all configured Anzo graph database connections as cards.

#### DataSource Card Information

Each card shows:

- **Title**: DataSource ID
- **Status Badge**: Health indicator with color coding
    - **UP** (Green checkmark) - Healthy and accessible
    - **DOWN** (Red X) - Unhealthy, connection failed
    - **DISABLED** (Red block) - Manually disabled
    - **CHECKING** (Rotating sync) - Health check in progress
    - **UNKNOWN** (Gray help icon) - Status not yet determined
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

### Routes Page

The Routes page displays all configured query endpoints in a searchable, sortable table.

#### Route Table Columns

- **Status Icon**: Visual indicator of route and datasource status
- **Route ID**: Unique identifier (sortable)
- **Endpoint URL**: Full query path (http://localhost:8888/{route-id})
- **Description**: User-friendly description (sortable)
- **DataSource ID**: Associated datasource with health warnings
- **Actions Menu**: Three-dot menu for route operations

#### Route Status Indicators

- **Green checkmark** - Route started and healthy
- **Red stop circle** - Route stopped
- **Orange warning** - Datasource down (route may fail)
- **Red block** - Datasource disabled (route operations restricted)

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

- **Cache Settings** (expandable panel):
    - **Cache Enabled**: Toggle to enable/disable caching for this specific route
    - **Cache TTL**: Time-to-live in seconds (leave blank to use global default)
    - **Cache Statistics** (live display when cache enabled)
    - **Clear Cache**: Button to invalidate all cached results for this route

#### Cloning a Route

The Clone feature creates a copy of an existing route:

1. Click **Clone** from the route's action menu
2. System creates new route with ID: `{original-id}-copy`
3. All configuration and template content is duplicated
4. New route starts in "Stopped" state
5. Edit the cloned route to customize

### Metrics Page

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

### Settings Page

The Settings page displays system information and statistics.

#### System Information Card

- **Version**: Application version number
- **System Uptime**: Formatted uptime duration
- **Database Type**: Active database driver (Derby/PostgreSQL/SQL Server)
- **Remote Database Address**: JDBC connection URL

#### Application Stats Card

- **Total DataSources**: Count of configured datasources
- **Total Routes**: Count of configured routes

#### Cache Statistics Cards

- **Ontology Cache Statistics**: Hit rate, eviction count, load times
- **Query Result Cache Statistics**: Redis connection status, hit rate, memory usage

## UI Tips and Best Practices

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

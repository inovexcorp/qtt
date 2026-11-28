# User Guide

This guide walks you through using QTT to create and manage query routes.

## Getting Started Tutorial

This tutorial walks you through creating your first query route from start to finish.

### Prerequisites

- QTT is running and accessible at https://localhost:8443 (or over plain HTTP at http://localhost:8080)
- You have access to an Altair Graph Studio graph database instance
- Basic familiarity with SPARQL and RDF

### Step 1: Access the Web UI

1. Open your browser and navigate to: **https://localhost:8443/** (accept the self-signed certificate warning if
   you use the default keystore)
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

2. **Click "Add route"** (it opens the new-route page)

3. **Fill in the request:**
    - **Route name**: `people-search` (this becomes your endpoint: `/query/people-search`)
    - **HTTP Methods**: Check **GET** (and optionally **POST**)
    - **Description**: `Search for people by name`

4. **Pick the data:**
    - **Datasource**: Select `my-anzo-instance`
    - **Graphmart**: Start typing and pick your graphmart
    - **Layers** (optional): Pick layers; none means every layer of the graphmart

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

6. **Create the route**
    - Click "Create route"; its page opens

### Step 4: Test Your Route

Your new endpoint is now live at: `https://localhost:8443/query/people-search`. The route's page shows this URL
and a ready-to-copy `curl` command.

Routes are open to anyone who can reach the server unless you change their **Access** (on the route's page) or the
server default (`QUERY_AUTH_DEFAULT`). An authenticated route needs `-H "Authorization: Bearer $QTT_TOKEN"`, where
the token is an OIDC access token with the `qtt-query` role or an API key from **Settings › Query Access**; see
[Securing the Query Endpoints](administration.md#securing-the-query-endpoints).

**Test without parameters:**

```bash
curl -k -X GET "https://localhost:8443/query/people-search"
```

**Test with parameters:**

```bash
curl -k -X GET "https://localhost:8443/query/people-search?name=john&limit=10"
```

### Step 5: Monitor Performance

1. **Navigate to Metrics**
    - Click "Metrics" in the left sidebar

2. **View Your Route's Performance:**
    - Find `people-search` in the metrics table
    - See processing times, exchange counts, success rate

3. **Analyze Trends:**
    - Pick your route in the **Routes** filter at the top
    - **Exchanges per minute** shows the last 30 minutes (switch to failures or average time)
    - The Routes page shows each route's last 30 minutes too, live

### Step 6: Modify and Iterate

1. **Edit the Route:**
    - On the Routes page, click the route's name

2. **Update the template:**
    - Open the **Template** tab and change the SPARQL or FreeMarker
    - Try it on the **Test** tab (it runs your unsaved changes)
    - Click **Save**: changes take effect immediately (no restart required)

3. **Stop or start the route:**
    - Use **Stop** / **Start** in its row or at the top of its page
    - Stopped routes answer with an error but keep their configuration

## Web UI Guide

The Query Templating Tool provides a rich Angular-based web interface for managing all aspects of your query routes and
datasources.

### Navigation Overview

The application uses a left sidebar navigation with four main sections:

- **Metrics**: Performance monitoring and analytics dashboard
- **Routes**: Query route management
- **DataSources**: Graph database connection management
- **Settings**: System information and statistics

### Search and Quick Actions

The search field in the top bar finds pages, routes and datasources, and runs common actions without leaving the
keyboard. Open it by clicking it, pressing **/** (when you aren't typing in a field), or pressing **⌘K** (macOS) or
**Ctrl+K** (elsewhere). ⌘K/Ctrl+K works everywhere, including in the template editor; there it replaces the editor's
own ⌘K/Ctrl+K two-key shortcuts.

- **Before you type**, the list shows the current page's actions, the routes and datasources you opened lately, the
  pages, and actions available everywhere.
- **Type** part of a route or datasource ID (or a word from a route's description, or a datasource's URL), a page name
  or an action. Matches are highlighted; letters in order work too (`crc` finds "Clear this route's cache").
- **↑ ↓** move, **Enter** opens or runs the highlighted row, **Esc** closes and puts you back where you were.
- **Narrow the search** by starting with **>** (actions), **#** (routes) or **@** (datasources). A chip shows the
  narrowing; **Backspace** in the empty field removes it. Type **?** to see these options.
- Press **?** anywhere outside a field to see every shortcut.

Actions follow your role, as the buttons do: viewers see only what they can do, editors also see route actions, and
admins also see datasource and system actions. An action that can't run right now is greyed out with the reason
(for example "Caching is off for this route").

| Where | Actions |
|---|---|
| Everywhere | Add route (editor), Add datasource, Create API key, Clear all caches (admin), Keyboard shortcuts, Sign out |
| A route's page | Save route, Turn route on/off, Clear this route's cache, Run test request, Toggle SPARQi assistant (editor); Toggle full-screen editor, Copy endpoint URL |
| A datasource's page | Save datasource, Test connection, Enable/Disable datasource, Delete datasource (admin); Run health check (editor); Show usage |

Clearing all caches and disabling a datasource ask for confirmation first, as deleting does.

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

The Routes page lists every query endpoint with its state, its datasource's health, who may call it, and its
traffic, all live: states and numbers change in place as the server pushes them (the dot by **Live** shows the
connection; pause and refresh are next to it).

#### Summary and filters

The card at the top sums up every route in one sentence ("42 routes · 37 running · 2 blocked by their datasources ·
3 stopped") with a bar split by state, and holds the filters: **All / Running / Blocked / Stopped**, a datasource, who
may call it (**Access**), and a search over route names, descriptions and datasources. **Sort** orders the list by
name, by what needs attention first, or by traffic (busiest, slowest, most errors). **Group by datasource** shows a
section per datasource, troubled ones first; sections fold away. The sort and grouping are remembered in your browser.

#### Route states

- **Running** (green dot): started, and its datasource can serve it.
- **Blocked** (red, or amber for a disabled datasource): started, but its datasource is down or disabled, so calls
  fail. The row says why.
- **Stopped** (hollow dot): stopped by someone, by the health check, or because it has no template yet; the row says
  which, and when for the health check.

When the health check has stopped routes and their datasource is back up, a banner offers **Start them** (or
**Review** to list just those routes).

#### A route's row

The route name (opens its page), its HTTP methods, a lock (callers need a token or API key) or a globe (open to
anyone), its datasource with its health (opens the datasource), and the last 30 minutes: calls per minute (averaged
over the last 5), the share of failed calls, the mean time per call, and a line of calls per minute with a red tick
for each minute that had failures. Then **Start** or **Stop** (stopping asks first, and says when the route is in
use; starting is disabled while the datasource is down or disabled), copy the endpoint URL, and a menu: **Open**,
**Test**, **Clone**, **Delete**.

Editors can select routes (the checkboxes) to start, stop or delete several at once; one confirmation lists them,
and one message says what happened, including any that failed or were skipped.

#### Adding a Route

**Add route** opens the new-route page:

- **Request**: the route name (it becomes `/query/{name}`: letters, digits, `.`, `_` and `-`), the HTTP methods, and
  a description. The endpoint URL and a `curl` command update as you type.
- **Data**: the datasource (with its health), the graphmart and the layers, listed from Graph Studio. No layers means
  every layer of the graphmart.
- **Template**: the FreeMarker template (see the editor features below). It can be added later: a route without one
  is created stopped.
- **Access** and **Caching**.

**Create route** creates it and opens its page; it says what is missing first, if anything. Leaving the page with
something entered asks before discarding it.

#### A Route's Page

The header stays at the top: the route's state with **Start**/**Stop**, **Test**, the SPARQi assistant (when
enabled) and a menu (**Copy endpoint URL**, **Clone**, **Delete**); its description, endpoint URL (click to copy),
who may call it, its datasource's health, who created and last changed it, and its traffic over the last 30 minutes.

Three tabs, kept in the URL (`?tab=template`, `?tab=test`) so a reload or a shared link opens the same one:

- **Overview**: the request (methods, description, endpoint and `curl` command), the data (datasource, graphmart,
  layers), access, and caching with the number of results cached for this route and **Clear cache**. When Graph
  Studio can't list graphmarts or layers (the datasource is down), the saved ones are kept, shown by their IRIs, and
  the route can still be saved.
- **Template**: the editor, filling the window. Its features:
    - FreeMarker syntax highlighting and completion of ontology terms (classes, properties, individuals)
    - syntax checking as you type: an error is underlined, with its line, column and message above the editor; a
      route whose template does not parse cannot be saved
    - **Upload** to replace the template with a file, and full screen (Esc leaves it)
- **Test**: a request built from the variables the template reads (a JSON body and query parameters), which SPARQi
  can fill in with realistic values; **Run test** runs it against the datasource with the route as you are editing
  it (saved or not) and shows the SPARQL it built and the results, as a table for SELECT results or as JSON.

Every tab edits the same route: **Save** (in the tab bar) saves all of it, and the bar says what is unsaved
("Unsaved: template, caching") with **Discard** next to it. Leaving the page, or closing the tab, with unsaved changes
asks first. Saving a stopped route keeps it stopped.

**SPARQi** opens beside any tab; drag its edge (or focus it and use the arrow keys) to resize it.

#### Cloning a Route

**Clone** (in a route's menu, on the list or its page) copies the route, its template and its settings to
`{route}Clone` (or `{route}Clone2`, … when that is taken). The copy starts in the same state the original's template
allows: running if it has a template. Open it to change it.

### Metrics Page

The Metrics page is a live dashboard of how your routes are performing. It updates itself every 15 seconds while it is open (and stops while the browser tab is hidden). The status at the top right says when it last updated; pause live updates there, or refresh now.

#### Filters

One row of filters at the top scopes everything on the page:

- **Datasource**: only the routes that use one datasource
- **Routes**: pick one or more routes; **Show all routes** clears the selection

#### Summary

- **Endpoints**: how many routes are in scope, and whether any are not started
- **Avg processing time**: across every exchange the routes have handled
- **Busiest route**: the route with the most exchanges, and its share of the traffic
- **Success rate**: successful exchanges as a share of all of them, with the number that failed

Until a route has handled a query there is nothing to average, so these show a dash and say why ("No exchanges yet") instead of a zero.

#### Charts

- **Per-minute trend** (last 30 minutes): exchanges, failures or average processing time per minute, one line per route. The seven busiest routes get their own line; the rest are combined into one grey line. Hover to read every route's value at a minute
- **Exchanges by route**: successful and failed exchanges per route since it started, busiest first (the ten busiest; the table lists all)
- **Processing time by route**: each route's fastest-to-slowest range since it started, with its average marked, slowest first. Routes that have not handled a query yet are left out

#### Routes table

Every route in scope with its state, successful and failed exchanges, success rate, average and min–max processing time, and uptime. Sort by any column, or find a route by name.

### Settings Page

The Settings page displays system information and statistics.

#### System Information Card

- **Version**: Application version number
- **System Uptime**: Formatted uptime duration
- **Database Type**: The database in use (Apache Derby, PostgreSQL or Microsoft SQL Server), as detected from `DB_URL`
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
- Use **Processing time by route** on the Metrics page to find slow routes
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

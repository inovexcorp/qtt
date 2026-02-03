# Template Development

Freemarker templates are the heart of QTT, transforming simple requests into complex SPARQL queries.

## Freemarker Basics for SPARQL

Freemarker is a template engine that processes directives and expressions to generate text output. In QTT, templates
generate SPARQL queries.

### Template Structure

```freemarker
PREFIX declarations

CONSTRUCT or SELECT {
  template content
}
WHERE {
  query patterns

  <#if parameter??>
  conditional SPARQL
  </#if>
}
<#if limit??>
LIMIT ${limit}
</#if>
```

### Key Concepts

**1. Parameter Access**: `${paramName}`

```freemarker
FILTER(CONTAINS(?name, "${searchTerm}"))
```

**2. Conditional Blocks**: `<#if>...</#if>`

```freemarker
<#if startDate??>
FILTER(?date >= "${startDate}"^^xsd:date)
</#if>
```

**3. Parameter Existence Check**: `paramName??`

```freemarker
<#if email??>
  ?person foaf:mbox "${email}" .
</#if>
```

**4. String Transformations**

```freemarker
${name?lower_case}    # Convert to lowercase
${name?upper_case}    # Convert to uppercase
${name?trim}          # Remove whitespace
${name?url}           # URL encode
```

**5. Default Values**

```freemarker
${limit!'10'}         # Use '10' if limit not provided
```

## Common Template Patterns

### Pattern 1: Simple Search with Optional Filter

```freemarker
PREFIX foaf: <http://xmlns.com/foaf/0.1/>

CONSTRUCT {
  ?person foaf:name ?name .
}
WHERE {
  ?person a foaf:Person ;
    foaf:name ?name .

  <#if searchTerm??>
  FILTER(CONTAINS(LCASE(STR(?name)), "${searchTerm?lower_case}"))
  </#if>
}
<#if limit??>
LIMIT ${limit}
</#if>
```

**Usage:**

```bash
# All people
GET /people-search

# Filtered search
GET /people-search?searchTerm=john&limit=5
```

### Pattern 2: Multi-Parameter Filtering

```freemarker
PREFIX ex: <http://example.org/>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

CONSTRUCT {
  ?doc ex:title ?title ;
    ex:author ?author ;
    ex:date ?date .
}
WHERE {
  ?doc a ex:Document ;
    ex:title ?title .

  <#if author??>
  ?doc ex:author ?author .
  FILTER(CONTAINS(LCASE(STR(?author)), "${author?lower_case}"))
  </#if>

  <#if startDate??>
  ?doc ex:date ?date .
  FILTER(?date >= "${startDate}"^^xsd:date)
  </#if>

  <#if endDate??>
  ?doc ex:date ?date .
  FILTER(?date <= "${endDate}"^^xsd:date)
  </#if>
}
<#if limit??>
LIMIT ${limit}
</#if>
```

### Pattern 3: List Parameters

```freemarker
PREFIX ex: <http://example.org/>

CONSTRUCT {
  ?item ex:name ?name ;
    ex:type ?type .
}
WHERE {
  ?item a ex:Item ;
    ex:name ?name ;
    ex:type ?type .

  <#if types??>
  <#assign typeList = types?split(",")>
  FILTER(?type IN (<#list typeList as type>"${type}"<#if type_has_next>, </#if></#list>))
  </#if>
}
```

**Usage:**

```bash
GET /items?types=TypeA,TypeB,TypeC
```

### Pattern 4: Optional Properties

```freemarker
PREFIX foaf: <http://xmlns.com/foaf/0.1/>

CONSTRUCT {
  ?person foaf:name ?name .
  <#if includeEmail?? && includeEmail == "true">
  ?person foaf:mbox ?email .
  </#if>
  <#if includePhone?? && includePhone == "true">
  ?person foaf:phone ?phone .
  </#if>
}
WHERE {
  ?person a foaf:Person ;
    foaf:name ?name .

  <#if includeEmail?? && includeEmail == "true">
  OPTIONAL { ?person foaf:mbox ?email . }
  </#if>

  <#if includePhone?? && includePhone == "true">
  OPTIONAL { ?person foaf:phone ?phone . }
  </#if>
}
```

## Using Ontology Autocomplete

The Monaco editor provides context-aware autocomplete for ontology elements.

**Trigger Autocomplete:**

- Type any character in the editor
- Press `Ctrl+Space`

**Autocomplete Provides:**

- **Classes**: All OWL/RDFS classes from your GraphMart
- **Properties**: Object properties, datatype properties, annotation properties
- **Individuals**: Named instances

**Example:**

```
Type: PREFIX foaf: <http://xmlns.com/foaf/
[Autocomplete suggests URIs]

Type: ?person a foaf:
[Autocomplete suggests: Person, Organization, Agent, etc.]

Type: ?person foaf:
[Autocomplete suggests: name, mbox, knows, member, etc.]
```

## Parameter Handling

### GET Parameters (Query String)

Parameters from URL query string are automatically available:

```bash
GET /my-route?name=John&age=30
```

Template access:

```freemarker
${headers.name}  # "John"
${headers.age}   # "30"
```

### POST Parameters (JSON Body)

Parameters from JSON body are automatically extracted:

```bash
POST /my-route
Content-Type: application/json

{
  "name": "John",
  "age": 30
}
```

Template access (same as GET):

```freemarker
${body.name}  # "John"
${body.age}   # 30
```

### Parameter Type Considerations

**All parameters are strings by default.** For type-safe queries:

```freemarker
# Integer
FILTER(?age = ${age})

# String (needs quotes)
FILTER(?name = "${name}")

# Date (with type cast)
FILTER(?date >= "${startDate}"^^xsd:date)

# Boolean
<#if includeInactive == "true">
  # Include inactive items
</#if>
```

## Best Practices

### 1. Always Validate Parameters

```freemarker
<#if !limit?? || limit?number < 1 || limit?number > 1000>
  <#assign limit = "100">
</#if>
LIMIT ${limit}
```

### 2. Escape User Input

Freemarker automatically escapes strings, but be cautious with direct injection:

```freemarker
# GOOD - Freemarker handles escaping
FILTER(CONTAINS(?name, "${searchTerm}"))

# BAD - Direct injection risk
FILTER(CONTAINS(?name, ${searchTerm}))  # Missing quotes!
```

### 3. Use Conditional OPTIONAL Clauses

```freemarker
# Efficient - only includes OPTIONAL when needed
<#if includeEmail??>
OPTIONAL { ?person foaf:mbox ?email . }
</#if>

# Inefficient - always includes OPTIONAL
OPTIONAL { ?person foaf:mbox ?email . }
```

### 4. Provide Sensible Defaults

```freemarker
<#assign maxResults = limit!'50'>
<#assign offset = offset!'0'>

LIMIT ${maxResults}
OFFSET ${offset}
```

### 5. Document Your Templates

```freemarker
#
#  Route: people-search
#  Description: Searches for people by name, email, or organization
#
#  Parameters:
#    - name (optional): Person name filter
#    - email (optional): Email address filter
#    - org (optional): Organization name filter
#    - limit (optional, default=50): Maximum results
#
#  Example: /people-search?name=john&limit=10
#
PREFIX foaf: <http://xmlns.com/foaf/0.1/>
...
```

### 6. Test Incrementally

1. Start with a basic CONSTRUCT query without parameters
2. Test manually with curl
3. Add one parameter at a time
4. Test each addition
5. Use SPARQi to validate complex logic

### 7. Optimize Query Performance

```freemarker
# Use specific graph patterns
?person a foaf:Person .  # More specific than ?person a ?type

# Limit early in complex queries
{
  SELECT DISTINCT ?person WHERE {
    ?person a foaf:Person .
    <#if name??>
    ?person foaf:name ?name .
    FILTER(CONTAINS(?name, "${name}"))
    </#if>
  }
  LIMIT ${limit!'100'}
}

# Use OPTIONAL sparingly
OPTIONAL { ?person foaf:mbox ?email . }  # Only if truly optional
```

## Debugging Templates

**Check Generated SPARQL:**

Enable Karaf logging to see the generated SPARQL queries:

```bash
# In Karaf console
log:set DEBUG com.inovexcorp.queryservice

# View logs
docker logs -f qtt
```

**Common Issues:**

1. **Missing quotes around string parameters**
   ```freemarker
   # Wrong
   FILTER(?name = ${name})

   # Correct
   FILTER(?name = "${name}")
   ```

2. **Unclosed Freemarker directives**
   ```freemarker
   # Wrong
   <#if param??>
   FILTER clause
   # Missing </#if>

   # Correct
   <#if param??>
   FILTER clause
   </#if>
   ```

3. **Type mismatches**
   ```freemarker
   # Wrong - limit is a string
   LIMIT ${limit}

   # Correct - convert to number if validating
   <#assign limitNum = limit?number>
   LIMIT ${limitNum}
   ```

## Cache Considerations for Templates

When using Redis caching, template design affects cache efficiency:

**Cache Key Generation:**

- Cache keys are generated from the final SPARQL query + GraphMart + Layers
- Different parameters create different cache keys
- Case-sensitive: `?name=John` and `?name=john` are different keys

**Optimizing Cache Hit Rate:**

Normalize parameters in your template:

```freemarker
<#-- Normalize to lowercase for better cache hit rate -->
<#assign normalizedName = (name!"")?lower_case>

SELECT * WHERE {
  ?person foaf:name ?name .
  FILTER(LCASE(?name) = "${normalizedName}")
}
```

Now both `?name=John` and `?name=john` generate the same SPARQL query, resulting in the same cache key.

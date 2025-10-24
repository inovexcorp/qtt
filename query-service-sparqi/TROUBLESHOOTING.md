# SPARQi Troubleshooting Guide

## Common Issue: OSGi Bundle Resolution Failure

### Symptom

When starting Karaf, you see an error like:

```
Unable to resolve com.inovexcorp.queryservice.query-service-sparqi:
missing requirement: osgi.wiring.package; filter:="(osgi.wiring.package=dev.langchain4j.model.chat)"
```

### Root Cause

The LangChain4j dependencies need to be wrapped as OSGi bundles, but the wrapping may fail during feature installation. This happens because:
1. These libraries weren't designed for OSGi
2. The `wrap:` protocol needs proper configuration
3. Dependencies must be installed in the correct order

### Solution Options

## Option 1: Manual Bundle Installation (Recommended)

If the automatic feature installation fails, manually install the wrapped bundles first:

```bash
# Start Karaf
bin/karaf

# Install dependencies in order
karaf@root()> bundle:install -s wrap:mvn:com.google.code.gson/gson/2.10.1
karaf@root()> bundle:install -s wrap:mvn:com.squareup.okio/okio-jvm/3.6.0
karaf@root()> bundle:install -s wrap:mvn:com.squareup.okhttp3/okhttp/4.12.0
karaf@root()> bundle:install -s wrap:mvn:com.squareup.retrofit2/retrofit/2.9.0
karaf@root()> bundle:install -s wrap:mvn:com.squareup.retrofit2/converter-gson/2.9.0
karaf@root()> bundle:install -s wrap:mvn:dev.langchain4j/langchain4j/0.35.0
karaf@root()> bundle:install -s wrap:mvn:dev.langchain4j/langchain4j-open-ai/0.35.0
karaf@root()> bundle:install -s mvn:org.freemarker/freemarker/2.3.32

# Now install the main feature
karaf@root()> feature:install query-service

# Verify SPARQi bundle is active
karaf@root()> bundle:list | grep sparqi
```

## Option 2: Install SPARQi as Separate Feature

Install the base system first, then SPARQi:

```bash
# Edit etc/org.apache.karaf.features.cfg
# Remove qs-sparqi from the main feature dependency

# Start Karaf - it will install without SPARQi
bin/karaf

# After system is up, install SPARQi separately
karaf@root()> feature:install qs-sparqi

# Then install the SPARQi bundle
karaf@root()> bundle:install mvn:com.inovexcorp.queryservice/query-service-sparqi/1.0.33-SNAPSHOT
karaf@root()> bundle:start <bundle-id>
```

## Option 3: Disable SPARQi (Temporary Workaround)

If you need to get the system running quickly:

### Remove from Feature

Edit `query-service-feature/src/main/resources/feature.xml`:

```xml
<feature name="query-service" ...>
    ...
    <!-- Comment out the SPARQi feature -->
    <!-- <feature dependency="true">qs-sparqi</feature> -->
    ...
    <!-- Comment out the SPARQi bundle -->
    <!-- <bundle start-level="86">mvn:com.inovexcorp.queryservice/query-service-sparqi/${project.version}</bundle> -->
</feature>
```

Rebuild:
```bash
mvn clean install -DskipTests
```

## Option 4: Use Pre-Wrapped Bundles

Create properly wrapped bundles ahead of time using the Maven Bundle Plugin.

### Create Wrapper Module

Create a new module `query-service-sparqi-deps` with properly wrapped dependencies:

`pom.xml`:
```xml
<project>
    <artifactId>query-service-sparqi-deps</artifactId>
    <packaging>bundle</packaging>

    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
            <version>0.35.0</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai</artifactId>
            <version>0.35.0</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.felix</groupId>
                <artifactId>maven-bundle-plugin</artifactId>
                <extensions>true</extensions>
                <configuration>
                    <instructions>
                        <Embed-Dependency>*;scope=compile</Embed-Dependency>
                        <Export-Package>
                            dev.langchain4j.*,
                            com.google.gson.*,
                            okhttp3.*,
                            retrofit2.*
                        </Export-Package>
                        <Import-Package>
                            javax.*,
                            org.slf4j.*;resolution:=optional,
                            *;resolution:=optional
                        </Import-Package>
                    </instructions>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## Verification Steps

After any solution, verify SPARQi is working:

### 1. Check Bundle Status

```bash
karaf@root()> bundle:list | grep -i "sparqi\|langchain"

# Should see bundles in ACTIVE state
```

### 2. Check Service Registration

```bash
karaf@root()> service:list | grep SparqiService

# Should see: SparqiService registered
```

### 3. Test API Endpoint

```bash
curl -k https://localhost:8443/queryrest/api/sparqi/health

# Should return: {"status":"available","activeSessions":0}
# Or: {"status":"disabled"} if enableSparqi=false
```

### 4. Check Logs

```bash
tail -f data/log/karaf.log | grep -i sparqi

# Look for:
# "SPARQi AI Assistant service activated"
# No ERROR or WARN messages about missing packages
```

## Understanding the Error

The error message breaks down as:

```
Unable to resolve com.inovexcorp.queryservice.query-service-sparqi
```
- SPARQi bundle cannot start

```
missing requirement: osgi.wiring.package; filter:="(osgi.wiring.package=dev.langchain4j.model.chat)"
```
- It needs the `dev.langchain4j.model.chat` package
- This package should come from the wrapped LangChain4j bundle
- The bundle either isn't installed or isn't exporting the package

## Debug Commands

### List All Bundles
```bash
karaf@root()> bundle:list -t 0
```

### Show Bundle Dependencies
```bash
karaf@root()> bundle:tree-show <bundle-id>
```

### Check Package Exports
```bash
karaf@root()> package:exports dev.langchain4j
```

### Bundle Diagnostics
```bash
karaf@root()> bundle:diag <bundle-id>
```

### Feature Info
```bash
karaf@root()> feature:info qs-sparqi
karaf@root()> feature:info query-service
```

## Prevention

For future updates, ensure:

1. **Test wrapped bundles independently**:
   ```bash
   bundle:install wrap:mvn:dev.langchain4j/langchain4j/0.35.0
   bundle:headers <bundle-id>  # Verify exports
   ```

2. **Use Apache ServiceMix bundles** when available:
   - Many popular libraries have pre-wrapped versions
   - Example: `mvn:org.apache.servicemix.bundles/org.apache.servicemix.bundles.retrofit/2.9.0_1`

3. **Consider Fat Bundle approach**:
   - Embed all dependencies in SPARQi bundle
   - Use `<Embed-Dependency>*</Embed-Dependency>`
   - Larger bundle but simpler deployment

## Getting Help

1. **Check Karaf logs**: `data/log/karaf.log`
2. **Enable debug logging**: `log:set DEBUG com.inovexcorp.queryservice.sparqi`
3. **Export diagnostics**: `dev:dump-create`
4. **Check GitHub issues**: Look for similar OSGi resolution problems

## Workaround: Run Without LLM Integration

If you need SPARQi to start but don't have LLM access yet:

1. Make sure `enableSparqi=false` in config
2. SPARQi will register but return "service disabled" for API calls
3. Health endpoint will show `{"status":"disabled"}`
4. Enable later when LLM is configured

## Long-term Solution

Consider creating an uber-bundle that includes all dependencies:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <artifactSet>
                    <includes>
                        <include>dev.langchain4j:*</include>
                        <include>com.squareup.*:*</include>
                        <include>com.google.code.gson:gson</include>
                    </includes>
                </artifactSet>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Then make this shaded jar an OSGi bundle with proper manifests.

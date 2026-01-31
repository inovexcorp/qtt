package com.inovexcorp.queryservice.camel.anzo.comm;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.util.IOHelper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;


/**
 * Simple implementation of the {@link AnzoClient} interface.
 */
@Slf4j
public class SimpleAnzoClient implements AnzoClient {

    private static final String GM_LOOKUP = "SELECT ?gm ?graphmartTitle ?gmActive WHERE { ?gm a <http://cambridgesemantics.com/ontologies/Graphmarts#Graphmart> ; <http://purl.org/dc/elements/1.1/title> ?graphmartTitle . OPTIONAL {?gm <http://cambridgesemantics.com/ontologies/Graphmarts#graphQueryEngineUri> ?azg} BIND(IF(BOUND(?azg),true,false) as ?gmActive)} ORDER BY DESC(?gmActive) ASC(?graphmartTitle)";
    private static final String GRAPHMARTS_DS_CAT = "http://openanzo.org/catEntry(%5Bhttp%3A%2F%2Fcambridgesemantics.com%2Fregistries%2FGraphmarts%5D%40%5Bhttp%3A%2F%2Fopenanzo.org%2Fdatasource%2FsystemDatasource%5D)";
    private static final String GRAPHMART_DS = "http://cambridgesemantics.com/registries/Graphmarts";
    private static final String LAYER_LOOKUP = "SELECT ?title ?layer ?layerActive WHERE { <%s> <http://cambridgesemantics.com/ontologies/Graphmarts#layer>/<http://openanzo.org/ontologies/2008/07/Anzo#orderedValue> ?layer . ?layer <http://purl.org/dc/elements/1.1/title> ?title ; <http://cambridgesemantics.com/ontologies/Graphmarts#enabled> ?layerActive .} ORDER BY DESC(?layerActive) ASC(?title)";
    private static final String GRAPHMARTS_COMP_DS_CAT = "http://openanzo.org/catEntry(%5Bhttp%3A%2F%2Fcambridgesemantics.com%2Fregistries%2FGraphmartElements%5D%40%5Bhttp%3A%2F%2Fopenanzo.org%2Fdatasource%2FsystemDatasource%5D)";
    private static final String GRAPHMARTS_COMP_DS = "http://cambridgesemantics.com/registries/GraphmartElements";
    private static final String SYSTEM_DS = "http://openanzo.org/datasource/systemDatasource";

    private final String server;
    private final String user;
    private final String password;
    private final String bearerToken;
    private final boolean useBearerAuth;
    private final HttpClient httpClient;
    private final int requestTimeoutSeconds;

    public SimpleAnzoClient(String server, String user, String password,
                            int connectTimeoutSeconds) {
        this(server, user, password, connectTimeoutSeconds, true);
    }

    public SimpleAnzoClient(String server, String user, String password,
                            int connectTimeoutSeconds, boolean validateCertificate) {
        this.server = server;
        this.user = user;
        this.password = password;
        this.bearerToken = null;
        this.useBearerAuth = false;
        // Use same timeout for both connection and request for health checks
        this.requestTimeoutSeconds = connectTimeoutSeconds;
        this.httpClient = createHttpClient(connectTimeoutSeconds, validateCertificate);
    }

    /**
     * Creates a SimpleAnzoClient that uses bearer token authentication.
     *
     * @param server              The Anzo server URL
     * @param bearerToken         The bearer token to use for authentication
     * @param connectTimeoutSeconds The connection timeout in seconds
     * @param validateCertificate Whether to validate SSL certificates
     */
    public SimpleAnzoClient(String server, String bearerToken,
                            int connectTimeoutSeconds, boolean validateCertificate) {
        this.server = server;
        this.user = null;
        this.password = null;
        this.bearerToken = bearerToken;
        this.useBearerAuth = true;
        this.requestTimeoutSeconds = connectTimeoutSeconds;
        this.httpClient = createHttpClient(connectTimeoutSeconds, validateCertificate);
    }

    /**
     * Factory method to create a bearer token authenticated client.
     */
    public static SimpleAnzoClient withBearerToken(String server, String bearerToken,
                                                    int connectTimeoutSeconds, boolean validateCertificate) {
        return new SimpleAnzoClient(server, bearerToken, connectTimeoutSeconds, validateCertificate);
    }

    private HttpClient createHttpClient(int connectTimeoutSeconds, boolean validateCertificate) {
        try {
            HttpClient.Builder builder = HttpClient.newBuilder()
                    .connectTimeout(Duration.of(connectTimeoutSeconds, ChronoUnit.SECONDS))
                    .followRedirects(HttpClient.Redirect.ALWAYS);

            if (!validateCertificate) {
                SSLContext sslContext = createInsecureSSLContext();
                builder.sslContext(sslContext);
                System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true"); // Disable hostname verification
            }

            return builder.build();
        } catch (Exception e) {
            //TODO - better error handling?
            throw new RuntimeException("Failed to create HttpClient", e);
        }
    }

    private SSLContext createInsecureSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{new InsecureTrustManager()}, new java.security.SecureRandom());
        return sslContext;
    }

    private static class InsecureTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            // No-op
        }

        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            // No-op
        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[0];
        }
    }

    /**
     * Query Anzo in the context of a graphmart.
     *
     * @param query          The SPARQL query to run.
     * @param graphmartUri   The URI of the target graphmart.
     * @param format         The desired response format.
     * @param timeoutSeconds The number of seconds to wait for a result.
     * @return The {@link QueryResponse} object with associated metadata.
     * @throws QueryException       If there was an issue executing the query.
     * @throws IOException          If there was a communication issue with the Anzo server.
     * @throws InterruptedException If there was an issue sending the request.
     */
    public QueryResponse queryGraphmart(String query, String graphmartUri, String layerUris, RESPONSE_FORMAT format,
                                        int timeoutSeconds, boolean skipCache)
            throws QueryException, IOException, InterruptedException {
        final long start = System.currentTimeMillis();
        final URI resource = createGraphmartSparqlUri(graphmartUri, layerUris);

        try {
            final HttpResponse<InputStream> resp = httpClient.send(
                    buildRequest(resource, query, format, timeoutSeconds, skipCache),
                    HttpResponse.BodyHandlers.ofInputStream());

            long duration = System.currentTimeMillis() - start;

            if (resp.statusCode() == 200) {
                return QueryResponse.builder()
                        .query(query)
                        .response(resp)
                        .queryDuration(duration)
                        .build();
            } else if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                throw new AnzoAuthenticationException(
                        "Authentication failed: " + IOHelper.loadText(resp.body()),
                        server, resp.statusCode());
            } else {
                throw new QueryException("Query request failed with HTTP " + resp.statusCode()
                        + ": " + IOHelper.loadText(resp.body()));
            }
        } catch (java.net.ConnectException e) {
            long duration = System.currentTimeMillis() - start;
            throw new AnzoConnectionException("Connection refused", e, server, duration);
        } catch (java.net.http.HttpTimeoutException e) {
            long duration = System.currentTimeMillis() - start;
            throw new AnzoConnectionException("Request timeout after " + timeoutSeconds + "s", e, server, duration);
        } catch (AnzoAuthenticationException | AnzoConnectionException e) {
            // Re-throw our custom exceptions
            throw e;
        } catch (IOException | InterruptedException e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Error communicating with Anzo server {}: {}", server, e.getMessage());
            throw e;
        }
    }


    @Override
    public QueryResponse getGraphmarts() throws IOException, InterruptedException {
        final long start = System.currentTimeMillis();

        try {
            // Use configured request timeout instead of hard-coded 30 seconds
            HttpResponse<InputStream> resp = makeLdsRequest(GRAPHMARTS_DS_CAT, GM_LOOKUP, requestTimeoutSeconds);
            long duration = System.currentTimeMillis() - start;

            if (resp.statusCode() == 200) {
                return QueryResponse.builder()
                        .query(GM_LOOKUP)
                        .response(resp)
                        .queryDuration(duration)
                        .build();
            } else if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                throw new AnzoAuthenticationException(
                        "Authentication failed getting graphmarts: " + IOHelper.loadText(resp.body()),
                        server, resp.statusCode());
            } else {
                throw new QueryException("Failed to get graphmarts, HTTP " + resp.statusCode() + ": " +
                        IOHelper.loadText(resp.body()));
            }
        } catch (java.net.ConnectException e) {
            long duration = System.currentTimeMillis() - start;
            throw new AnzoConnectionException("Connection refused getting graphmarts", e, server, duration);
        } catch (java.net.http.HttpTimeoutException e) {
            long duration = System.currentTimeMillis() - start;
            throw new AnzoConnectionException("Timeout getting graphmarts", e, server, duration);
        } catch (AnzoAuthenticationException | AnzoConnectionException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            log.error("Error getting graphmarts from Anzo server {}: {}", server, e.getMessage());
            throw e;
        }
    }

    @Override
    public QueryResponse getLayersForGraphmart(String graphmart) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        final String query = String.format(LAYER_LOOKUP, graphmart);
        log.debug(query);
        HttpResponse<InputStream> resp = makeLegacyRequest(query, SYSTEM_DS, GRAPHMART_DS, GRAPHMARTS_COMP_DS);

        if (resp.statusCode() == 200) {
            return QueryResponse.builder()
                    .query(GM_LOOKUP)
                    .response(resp)
                    .queryDuration(System.currentTimeMillis() - start)
                    .build();
        } else {
            try (InputStream is = resp.body()) {

                throw new QueryException("Issue making query request to Anzo (" + resp.statusCode() + "): "
                        + IOHelper.loadText(is));
            }
        }
    }

    private HttpResponse<InputStream> makeLdsRequest(String dataset, String query)
            throws IOException, InterruptedException {
        // Use default 30-second timeout for backward compatibility
        return makeLdsRequest(dataset, query, 30);
    }

    private HttpResponse<InputStream> makeLdsRequest(String dataset, String query, int timeoutSeconds)
            throws IOException, InterruptedException {
        return httpClient.send(
                buildRequest(createLdsSparqlUri(dataset), query, RESPONSE_FORMAT.JSON, timeoutSeconds, false),
                HttpResponse.BodyHandlers.ofInputStream());
    }

    private HttpResponse<InputStream> makeLegacyRequest(String query, String datasource, String... LDS)
            throws IOException, InterruptedException {
        return httpClient.send(
                buildLegacyRequest(query, RESPONSE_FORMAT.JSON, 30, false, datasource, LDS),
                HttpResponse.BodyHandlers.ofInputStream());
    }

    private HttpRequest buildLegacyRequest(String query, RESPONSE_FORMAT responseFormat,
                                           int timeoutSeconds, boolean skipCache, String datasource, String... LDS) {
        return HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(buildFormMultipartQueryBody(query,
                        responseFormat, skipCache)))
                .uri(createLegacySparqlUri())
                .POST(HttpRequest.BodyPublishers.ofString(buildFormMultipartQueryBody(query,
                        responseFormat, skipCache, datasource, LDS)))
                .header("Authorization", buildAuthorizationHeader())
                .header("Content-Type", "application/x-www-form-urlencoded")
                // Set timeout.
                .timeout(Duration.of(timeoutSeconds, ChronoUnit.SECONDS))
                // Build the request.
                .build();
    }

    /**
     * Build a request to execute a query against a graphmart-based SPARQL endpoint.
     *
     * @param resource       The URI to hit.
     * @param query          The query to execute.
     * @param responseFormat The desired response format.
     * @param timeoutSeconds The number of seconds to wait for a response.
     * @return The {@link HttpRequest} we'll make to Anzo.
     */
    private HttpRequest buildRequest(URI resource, String query, RESPONSE_FORMAT responseFormat,
                                     int timeoutSeconds, boolean skipCache) {
        return HttpRequest.newBuilder()
                // URI location.
                .uri(resource)
                // Do POST.
                .POST(HttpRequest.BodyPublishers.ofString(buildFormMultipartQueryBody(query,
                        responseFormat, skipCache)))
                // Add auth header (bearer or basic).
                .header("Authorization", buildAuthorizationHeader())
                .header("Content-Type", "application/x-www-form-urlencoded")
                // Set timeout.
                .timeout(Duration.of(timeoutSeconds, ChronoUnit.SECONDS))
                // Build the request.
                .build();
    }

    /**
     * Builds the appropriate Authorization header based on auth mode.
     *
     * @return The Authorization header value
     */
    private String buildAuthorizationHeader() {
        if (useBearerAuth && bearerToken != null) {
            return "Bearer " + bearerToken;
        } else {
            return String.format("Basic %s",
                    Base64.getEncoder().encodeToString(String.format("%s:%s", this.user,
                            this.password).getBytes(StandardCharsets.UTF_8)));
        }
    }

    /**
     * Simply template out the endpoint that will be hit by the request when querying a specific graphmart.
     *
     * @param graphmartUri The URI of the graphmart being targeted.
     * @return The {@link URI} of the endpoint our client will hit to execute the query.
     */
    private URI createGraphmartSparqlUri(String graphmartUri, String layerUris) {
        // Three part constructor for concatenating layers... ex: "named-graph-uri" "=" "http://example.com/graph"
        final String namedGraphUriFormat = "%s%s=%s";
        String value = String.format("%s/sparql/graphmart/%s", this.server,
                URLEncoder.encode(graphmartUri, Charset.defaultCharset()));
        // If layerUri's are specified, concatenate the URL with layers
        if (!layerUris.isEmpty()) {
            List<String> layers = Arrays.asList(layerUris.split(","));
            for (int i = 0; i < layers.size(); i++) {
                //First layer needs ? instead of &
                String delimiter = (i == 0) ? "?" : "&";
                value = value.concat(String.format(namedGraphUriFormat, delimiter, "named-graph-uri", URLEncoder.encode(layers.get(i), Charset.defaultCharset())));
            }
        }
        log.trace("Created URI to target: {}", value);
        return URI.create(value);
    }

    private URI createLdsSparqlUri(String ldsCatalogUri) {
        final String value = String.format("%s/sparql/lds/%s", this.server,
                URLEncoder.encode(ldsCatalogUri, Charset.defaultCharset()));
        log.trace("Created URI to target: {}", value);
        return URI.create(value);
    }

    private URI createLegacySparqlUri() {
        String data = String.format("%s/sparql", this.server);
        return URI.create(data);
    }

    /**
     * Build simple form multi-part encoded values from query and response format for request.
     *
     * @param query     The SPARQL query.
     * @param format    The desired response format.
     * @param skipCache Whether to skip the cache on the Anzo query side.
     * @return The String body that will be passed with the request.
     */
    private String buildFormMultipartQueryBody(String query, RESPONSE_FORMAT format, boolean skipCache) {
        return String.format("query=%s&format=%s&skipCache=%b",
                URLEncoder.encode(query, StandardCharsets.UTF_8),
                URLEncoder.encode(format.getKey(), StandardCharsets.UTF_8),
                skipCache);
    }

    private String buildFormMultipartQueryBody(String query, RESPONSE_FORMAT format, boolean skipCache, String datasource, String... LDS) {
        String request = String.format("query=%s&format=%s&skipCache=%b&datasourceURI=%s",
                URLEncoder.encode(query, StandardCharsets.UTF_8),
                URLEncoder.encode(format.getKey(), StandardCharsets.UTF_8),
                skipCache,
                URLEncoder.encode(datasource, StandardCharsets.UTF_8));
        return String.format("%s%s", request, datasetsProcess(LDS));


    }

    private static String datasetsProcess(String[] LDS) {
        final StringBuilder sb = new StringBuilder();
        if (LDS != null && LDS.length > 0) {
            Arrays.stream(LDS).map(SimpleAnzoClient::urlEncodeDsAttribute).forEach(sb::append);
        }
        return sb.toString();
    }

    public static String urlEncodeDsAttribute(String value) {
        return "&named-dataset-uri=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

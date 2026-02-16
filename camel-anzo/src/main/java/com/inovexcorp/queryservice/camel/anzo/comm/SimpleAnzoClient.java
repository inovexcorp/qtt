package com.inovexcorp.queryservice.camel.anzo.comm;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.util.IOHelper;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.net.ssl.SSLSocketFactory;


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

    private static final HostnameVerifier TRUST_ALL_HOSTNAMES = (hostname, session) -> true;

    private final String server;
    private final String user;
    private final String password;
    private final int connectTimeoutMs;
    private final int requestTimeoutSeconds;
    private final boolean validateCertificate;
    private final SSLSocketFactory sslSocketFactory;
    private final String authHeader;

    public SimpleAnzoClient(String server, String user, String password,
                            int connectTimeoutSeconds) {
        this(server, user, password, connectTimeoutSeconds, true);
    }

    public SimpleAnzoClient(String server, String user, String password,
                            int connectTimeoutSeconds, boolean validateCertificate) {
        if (connectTimeoutSeconds <= 0) {
            throw new RuntimeException("Connect timeout must be positive, got: " + connectTimeoutSeconds);
        }
        this.server = server;
        this.user = user;
        this.password = password;
        this.connectTimeoutMs = connectTimeoutSeconds * 1000;
        this.requestTimeoutSeconds = connectTimeoutSeconds;
        this.validateCertificate = validateCertificate;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (user + ":" + password).getBytes(StandardCharsets.UTF_8));

        if (!validateCertificate) {
            try {
                SSLContext sslContext = createInsecureSSLContext();
                this.sslSocketFactory = sslContext.getSocketFactory();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create insecure SSL context", e);
            }
        } else {
            this.sslSocketFactory = null;
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
     * Sends a POST request and returns the response, adapting {@link HttpURLConnection}
     * to the {@link HttpResponse} interface that {@link QueryResponse} expects.
     */
    private HttpResponse<InputStream> sendPost(URI uri, String body, int timeoutSeconds) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();

        if (conn instanceof HttpsURLConnection httpsConn) {
            if (!validateCertificate) {
                httpsConn.setSSLSocketFactory(sslSocketFactory);
                httpsConn.setHostnameVerifier(TRUST_ALL_HOSTNAMES);
            }
        }

        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(timeoutSeconds * 1000);
        conn.setRequestProperty("Authorization", authHeader);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bodyBytes.length);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyBytes);
        }

        int statusCode = conn.getResponseCode();
        InputStream responseStream = (statusCode >= 400)
                ? conn.getErrorStream()
                : conn.getInputStream();

        return new UrlConnectionResponse(statusCode, responseStream, conn.getHeaderFields(), uri);
    }

    /**
     * Adapter that wraps an {@link HttpURLConnection} result as an {@link HttpResponse},
     * allowing {@link QueryResponse} to remain unchanged.
     */
    private static class UrlConnectionResponse implements HttpResponse<InputStream> {
        private final int statusCode;
        private final InputStream body;
        private final HttpHeaders headers;
        private final URI uri;

        UrlConnectionResponse(int statusCode, InputStream body, Map<String, List<String>> headerMap, URI uri) {
            this.statusCode = statusCode;
            this.body = body;
            // HttpURLConnection includes a null key for the status line; filter it out
            Map<String, List<String>> filtered = headerMap.entrySet().stream()
                    .filter(e -> e.getKey() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            this.headers = HttpHeaders.of(filtered, (k, v) -> true);
            this.uri = uri;
        }

        @Override public int statusCode() { return statusCode; }
        @Override public InputStream body() { return body; }
        @Override public HttpHeaders headers() { return headers; }
        @Override public URI uri() { return uri; }
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
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
            final HttpResponse<InputStream> resp = sendPost(resource,
                    buildFormMultipartQueryBody(query, format, skipCache), timeoutSeconds);

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
        } catch (SocketTimeoutException e) {
            long duration = System.currentTimeMillis() - start;
            throw new AnzoConnectionException("Request timeout after " + timeoutSeconds + "s", e, server, duration);
        } catch (AnzoAuthenticationException | AnzoConnectionException e) {
            // Re-throw our custom exceptions
            throw e;
        } catch (IOException e) {
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
        } catch (SocketTimeoutException e) {
            long duration = System.currentTimeMillis() - start;
            throw new AnzoConnectionException("Timeout getting graphmarts", e, server, duration);
        } catch (AnzoAuthenticationException | AnzoConnectionException e) {
            throw e;
        } catch (IOException e) {
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
            throws IOException {
        // Use default 30-second timeout for backward compatibility
        return makeLdsRequest(dataset, query, 30);
    }

    private HttpResponse<InputStream> makeLdsRequest(String dataset, String query, int timeoutSeconds)
            throws IOException {
        return sendPost(createLdsSparqlUri(dataset),
                buildFormMultipartQueryBody(query, RESPONSE_FORMAT.JSON, false), timeoutSeconds);
    }

    private HttpResponse<InputStream> makeLegacyRequest(String query, String datasource, String... LDS)
            throws IOException {
        return sendPost(createLegacySparqlUri(),
                buildFormMultipartQueryBody(query, RESPONSE_FORMAT.JSON, false, datasource, LDS), 30);
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

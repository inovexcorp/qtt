package com.inovexcorp.queryservice.camel.anzo.auth;

import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for verifying bearer tokens against Anzo and caching valid tokens.
 * <p>
 * When bearer token authentication is enabled on a route, this service:
 * 1. Extracts the bearer token from the client's Authorization header
 * 2. Validates the token against Anzo by making a lightweight request
 * 3. Caches valid tokens to reduce authentication overhead
 * <p>
 * The token is then passed through to Anzo for all subsequent requests
 * instead of using the datasource's basic auth credentials.
 */
@Slf4j
public class BearerTokenAuthService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int DEFAULT_CACHE_TTL_SECONDS = 300; // 5 minutes
    private static final int DEFAULT_VERIFICATION_TIMEOUT_SECONDS = 10;

    // Cache of validated tokens: token hash -> TokenCacheEntry
    private final Map<String, TokenCacheEntry> tokenCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;
    private final int cacheTtlSeconds;
    private final int verificationTimeoutSeconds;
    private final HttpClient httpClient;

    /**
     * Creates a BearerTokenAuthService with default settings.
     */
    public BearerTokenAuthService() {
        this(DEFAULT_CACHE_TTL_SECONDS, DEFAULT_VERIFICATION_TIMEOUT_SECONDS, true);
    }

    /**
     * Creates a BearerTokenAuthService with custom settings.
     *
     * @param cacheTtlSeconds             TTL for cached tokens in seconds
     * @param verificationTimeoutSeconds  Timeout for token verification requests
     * @param validateCertificate         Whether to validate SSL certificates
     */
    public BearerTokenAuthService(int cacheTtlSeconds, int verificationTimeoutSeconds, boolean validateCertificate) {
        this.cacheTtlSeconds = cacheTtlSeconds;
        this.verificationTimeoutSeconds = verificationTimeoutSeconds;
        this.httpClient = createHttpClient(verificationTimeoutSeconds, validateCertificate);

        // Schedule periodic cleanup of expired tokens
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bearer-token-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredTokens,
                cacheTtlSeconds, cacheTtlSeconds, TimeUnit.SECONDS);

        log.info("BearerTokenAuthService initialized with cacheTtl={}s, verificationTimeout={}s",
                cacheTtlSeconds, verificationTimeoutSeconds);
    }

    /**
     * Extracts the bearer token from an Authorization header value.
     *
     * @param authorizationHeader The full Authorization header value
     * @return Optional containing the token if present and properly formatted
     */
    public Optional<String> extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return Optional.empty();
        }
        if (authorizationHeader.startsWith(BEARER_PREFIX)) {
            String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }

    /**
     * Verifies a bearer token against an Anzo server.
     * <p>
     * This method first checks the cache for a previously validated token.
     * If not cached, it makes a lightweight request to Anzo to verify the token.
     *
     * @param token            The bearer token to verify
     * @param anzoServerUrl    The Anzo server URL (e.g., https://anzo.example.com)
     * @param validateCert     Whether to validate SSL certificates
     * @return TokenValidationResult indicating success or failure with details
     */
    public TokenValidationResult verifyToken(String token, String anzoServerUrl, boolean validateCert) {
        if (token == null || token.isBlank()) {
            return TokenValidationResult.failure("Token is null or empty");
        }

        // Check cache first
        String cacheKey = generateCacheKey(token, anzoServerUrl);
        TokenCacheEntry cached = tokenCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Token found in cache for server {}", anzoServerUrl);
            return TokenValidationResult.success(token);
        }

        // Token not in cache or expired - verify against Anzo
        log.debug("Verifying token against Anzo server {}", anzoServerUrl);
        try {
            HttpClient client = validateCert ? httpClient : createHttpClient(verificationTimeoutSeconds, false);
            boolean valid = verifyTokenAgainstAnzo(token, anzoServerUrl, client);

            if (valid) {
                // Cache the valid token
                tokenCache.put(cacheKey, new TokenCacheEntry(System.currentTimeMillis(), cacheTtlSeconds));
                log.debug("Token verified and cached for server {}", anzoServerUrl);
                return TokenValidationResult.success(token);
            } else {
                log.warn("Token verification failed for server {}", anzoServerUrl);
                return TokenValidationResult.failure("Token verification failed - invalid or expired token");
            }
        } catch (Exception e) {
            log.error("Error verifying token against Anzo server {}: {}", anzoServerUrl, e.getMessage());
            return TokenValidationResult.failure("Token verification error: " + e.getMessage());
        }
    }

    /**
     * Verifies a token by making a lightweight request to Anzo.
     * Uses the /sparql endpoint with a simple ASK query to verify auth.
     */
    private boolean verifyTokenAgainstAnzo(String token, String anzoServerUrl, HttpClient client)
            throws Exception {
        // Use the user info endpoint or a simple query to verify the token
        // The /info endpoint is lightweight and requires authentication
        URI verifyUri = URI.create(anzoServerUrl + "/info");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(verifyUri)
                .header("Authorization", BEARER_PREFIX + token)
                .timeout(Duration.ofSeconds(verificationTimeoutSeconds))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // 200 OK means the token is valid
        // 401/403 means invalid token
        return response.statusCode() == 200;
    }

    /**
     * Invalidates a specific token from the cache.
     *
     * @param token         The token to invalidate
     * @param anzoServerUrl The Anzo server URL
     */
    public void invalidateToken(String token, String anzoServerUrl) {
        String cacheKey = generateCacheKey(token, anzoServerUrl);
        tokenCache.remove(cacheKey);
        log.debug("Token invalidated from cache for server {}", anzoServerUrl);
    }

    /**
     * Clears all cached tokens.
     */
    public void clearCache() {
        int size = tokenCache.size();
        tokenCache.clear();
        log.info("Cleared {} tokens from cache", size);
    }

    /**
     * Gets the current number of cached tokens.
     */
    public int getCacheSize() {
        return tokenCache.size();
    }

    /**
     * Gets cache statistics.
     */
    public Map<String, Object> getCacheStats() {
        cleanupExpiredTokens();
        return Map.of(
                "size", tokenCache.size(),
                "ttlSeconds", cacheTtlSeconds
        );
    }

    /**
     * Shuts down the cleanup executor.
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        tokenCache.clear();
        log.info("BearerTokenAuthService shut down");
    }

    private void cleanupExpiredTokens() {
        int before = tokenCache.size();
        tokenCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removed = before - tokenCache.size();
        if (removed > 0) {
            log.debug("Cleaned up {} expired tokens from cache", removed);
        }
    }

    private String generateCacheKey(String token, String serverUrl) {
        // Use a hash of the token + server to avoid storing the actual token
        int hash = (token + "|" + serverUrl).hashCode();
        return "bearer:" + hash;
    }

    private HttpClient createHttpClient(int timeoutSeconds, boolean validateCertificate) {
        try {
            HttpClient.Builder builder = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .followRedirects(HttpClient.Redirect.ALWAYS);

            if (!validateCertificate) {
                SSLContext sslContext = createInsecureSSLContext();
                builder.sslContext(sslContext);
                System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
            }

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create HttpClient for token verification", e);
        }
    }

    private SSLContext createInsecureSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{new InsecureTrustManager()}, new java.security.SecureRandom());
        return sslContext;
    }

    private static class InsecureTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[0];
        }
    }

    /**
     * Internal class to track cached token entries with expiration.
     */
    private static class TokenCacheEntry {
        private final long createdAt;
        private final int ttlSeconds;

        TokenCacheEntry(long createdAt, int ttlSeconds) {
            this.createdAt = createdAt;
            this.ttlSeconds = ttlSeconds;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > (ttlSeconds * 1000L);
        }
    }
}

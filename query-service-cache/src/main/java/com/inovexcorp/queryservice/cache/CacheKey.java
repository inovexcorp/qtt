package com.inovexcorp.queryservice.cache;

import lombok.Builder;
import lombok.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Builder for cache keys that creates consistent, unique keys
 * based on route ID, SPARQL query, graphmart URI, and layers.
 */
@Value
@Builder
public class CacheKey {

    String prefix;
    String routeId;
    String query;
    String graphmartUri;
    String layerUris;

    /**
     * Generates the complete cache key.
     * Format: {prefix}{routeId}:{hash}
     * where hash = SHA-256(query + graphmart + layers)
     *
     * @return The generated cache key
     */
    public String generate() {
        String combinedData = query + "|" + graphmartUri + "|" + layerUris;
        String hash = sha256(combinedData);
        return prefix + routeId + ":" + hash;
    }

    /**
     * Generates a pattern for matching all cache keys for a specific route.
     * Format: {prefix}{routeId}:*
     *
     * @return The pattern for matching route cache keys
     */
    public String generateRoutePattern() {
        return prefix + routeId + ":*";
    }

    /**
     * Computes SHA-256 hash of the input string.
     *
     * @param input The string to hash
     * @return Hex-encoded SHA-256 hash
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 should always be available
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Converts byte array to hex string.
     *
     * @param bytes The byte array
     * @return Hex-encoded string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}

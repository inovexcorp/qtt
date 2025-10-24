package com.inovexcorp.queryservice.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CacheKey builder class.
 * Tests cache key generation, hashing consistency, and pattern matching.
 */
class CacheKeyTest {

    private static final String TEST_PREFIX = "qtt:cache:";
    private static final String TEST_ROUTE_ID = "test-route";
    private static final String TEST_QUERY = "SELECT * FROM <graph>";
    private static final String TEST_GRAPHMART = "http://example.com/graphmart";
    private static final String TEST_LAYERS = "layer1,layer2";

    @Test
    void generate_ConsistentHashForSameInputs() {
        // Arrange
        CacheKey key1 = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .query(TEST_QUERY)
                .graphmartUri(TEST_GRAPHMART)
                .layerUris(TEST_LAYERS)
                .build();

        CacheKey key2 = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .query(TEST_QUERY)
                .graphmartUri(TEST_GRAPHMART)
                .layerUris(TEST_LAYERS)
                .build();

        // Act
        String result1 = key1.generate();
        String result2 = key2.generate();

        // Assert
        assertEquals(result1, result2, "Same inputs should generate identical keys");
    }

    @Test
    void generate_DifferentHashForDifferentQueries() {
        // Arrange
        CacheKey key1 = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .query("SELECT * FROM <graph1>")
                .graphmartUri(TEST_GRAPHMART)
                .layerUris(TEST_LAYERS)
                .build();

        CacheKey key2 = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .query("SELECT * FROM <graph2>")
                .graphmartUri(TEST_GRAPHMART)
                .layerUris(TEST_LAYERS)
                .build();

        // Act
        String result1 = key1.generate();
        String result2 = key2.generate();

        // Assert
        assertNotEquals(result1, result2, "Different queries should generate different keys");
    }

    @Test
    void generate_DifferentHashForDifferentRouteIds() {
        // Arrange
        CacheKey key1 = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId("route-1")
                .query(TEST_QUERY)
                .graphmartUri(TEST_GRAPHMART)
                .layerUris(TEST_LAYERS)
                .build();

        CacheKey key2 = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId("route-2")
                .query(TEST_QUERY)
                .graphmartUri(TEST_GRAPHMART)
                .layerUris(TEST_LAYERS)
                .build();

        // Act
        String result1 = key1.generate();
        String result2 = key2.generate();

        // Assert
        assertNotEquals(result1, result2, "Different route IDs should generate different keys");
        // Verify route ID is in the key
        assertTrue(result1.contains("route-1:"));
        assertTrue(result2.contains("route-2:"));
    }

    @Test
    void generate_DifferentHashForDifferentGraphmarts() {
        // Arrange
        CacheKey key1 = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .query(TEST_QUERY)
                .graphmartUri("http://example.com/graphmart1")
                .layerUris(TEST_LAYERS)
                .build();

        CacheKey key2 = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .query(TEST_QUERY)
                .graphmartUri("http://example.com/graphmart2")
                .layerUris(TEST_LAYERS)
                .build();

        // Act
        String result1 = key1.generate();
        String result2 = key2.generate();

        // Assert
        assertNotEquals(result1, result2, "Different graphmarts should generate different keys");
    }

    @Test
    void generate_DifferentHashForDifferentLayers() {
        // Arrange
        CacheKey key1 = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .query(TEST_QUERY)
                .graphmartUri(TEST_GRAPHMART)
                .layerUris("layer1,layer2")
                .build();

        CacheKey key2 = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .query(TEST_QUERY)
                .graphmartUri(TEST_GRAPHMART)
                .layerUris("layer1,layer3")
                .build();

        // Act
        String result1 = key1.generate();
        String result2 = key2.generate();

        // Assert
        assertNotEquals(result1, result2, "Different layers should generate different keys");
    }

    @Test
    void generate_WithNullLayers_ProducesDifferentHash() {
        // Arrange
        CacheKey key1 = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .query(TEST_QUERY)
                .graphmartUri(TEST_GRAPHMART)
                .layerUris(null)
                .build();

        CacheKey key2 = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .query(TEST_QUERY)
                .graphmartUri(TEST_GRAPHMART)
                .layerUris("")
                .build();

        // Act
        String result1 = key1.generate();
        String result2 = key2.generate();

        // Assert - null is handled as string "null", so hashes differ
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotEquals(result1, result2, "Null and empty string produce different hashes");
    }

    @Test
    void generate_WithSpecialCharacters_HashesCorrectly() {
        // Arrange
        String specialQuery = "SELECT ?s ?p ?o WHERE { ?s ?p \"test'value\" . FILTER(?o > 100) }";
        CacheKey key = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .query(specialQuery)
                .graphmartUri(TEST_GRAPHMART)
                .layerUris(TEST_LAYERS)
                .build();

        // Act
        String result = key.generate();

        // Assert
        assertNotNull(result);
        assertTrue(result.startsWith(TEST_PREFIX + TEST_ROUTE_ID + ":"));
        assertEquals(64, result.substring(result.lastIndexOf(':') + 1).length(), "Hash should be 64 characters (SHA-256)");
    }

    @Test
    void generate_WithVeryLongQuery_HandlesCorrectly() {
        // Arrange
        StringBuilder longQuery = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            longQuery.append("SELECT * FROM <graph").append(i).append("> . ");
        }
        CacheKey key = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .query(longQuery.toString())
                .graphmartUri(TEST_GRAPHMART)
                .layerUris(TEST_LAYERS)
                .build();

        // Act
        String result = key.generate();

        // Assert
        assertNotNull(result);
        // Hash should still be 64 characters regardless of input length
        String hash = result.substring(result.lastIndexOf(':') + 1);
        assertEquals(64, hash.length(), "Hash should always be 64 characters");
    }

    @Test
    void generate_FormatIsCorrect() {
        // Arrange
        CacheKey key = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .query(TEST_QUERY)
                .graphmartUri(TEST_GRAPHMART)
                .layerUris(TEST_LAYERS)
                .build();

        // Act
        String result = key.generate();

        // Assert
        assertTrue(result.startsWith(TEST_PREFIX), "Key should start with prefix");
        assertTrue(result.contains(TEST_ROUTE_ID + ":"), "Key should contain routeId followed by colon");
        String[] parts = result.split(":");
        assertTrue(parts.length >= 3, "Key should have at least 3 parts (prefix, routeId, hash)");
        // Last part should be the hash (64 hex characters)
        String hash = parts[parts.length - 1];
        assertEquals(64, hash.length(), "Hash should be 64 characters");
        assertTrue(hash.matches("[0-9a-f]{64}"), "Hash should be lowercase hex");
    }

    @Test
    void generateRoutePattern_ReturnsCorrectFormat() {
        // Arrange
        CacheKey key = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .build();

        // Act
        String result = key.generateRoutePattern();

        // Assert
        assertEquals(TEST_PREFIX + TEST_ROUTE_ID + ":*", result);
    }

    @Test
    void generateRoutePattern_IncludesPrefix() {
        // Arrange
        CacheKey key = CacheKey.builder()
                .prefix("custom:prefix:")
                .routeId(TEST_ROUTE_ID)
                .build();

        // Act
        String result = key.generateRoutePattern();

        // Assert
        assertTrue(result.startsWith("custom:prefix:"));
    }

    @Test
    void generateRoutePattern_IncludesRouteId() {
        // Arrange
        CacheKey key = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId("my-special-route")
                .build();

        // Act
        String result = key.generateRoutePattern();

        // Assert
        assertTrue(result.contains("my-special-route"));
    }

    @Test
    void generateRoutePattern_IncludesWildcard() {
        // Arrange
        CacheKey key = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .build();

        // Act
        String result = key.generateRoutePattern();

        // Assert
        assertTrue(result.endsWith(":*"), "Pattern should end with wildcard");
    }

    @Test
    void generate_HashIsLowercase() {
        // Arrange
        CacheKey key = CacheKey.builder()
                .prefix(TEST_PREFIX)
                .routeId(TEST_ROUTE_ID)
                .query(TEST_QUERY)
                .graphmartUri(TEST_GRAPHMART)
                .layerUris(TEST_LAYERS)
                .build();

        // Act
        String result = key.generate();
        String hash = result.substring(result.lastIndexOf(':') + 1);

        // Assert
        assertEquals(hash, hash.toLowerCase(), "Hash should be lowercase");
        assertFalse(hash.matches(".*[A-F].*"), "Hash should not contain uppercase letters");
    }
}

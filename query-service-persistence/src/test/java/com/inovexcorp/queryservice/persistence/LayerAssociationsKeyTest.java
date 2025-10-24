package com.inovexcorp.queryservice.persistence;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for LayerAssociationsKey composite key class.
 * Tests constructor behavior, equals/hashCode contract, and Serializable implementation.
 */
public class LayerAssociationsKeyTest {

    @Test
    public void testDefaultConstructor() {
        // Act
        LayerAssociationsKey key = new LayerAssociationsKey();

        // Assert
        assertNotNull(key);
        assertNull(key.getLayerUri());
        assertNull(key.getRouteId());
    }

    @Test
    public void testParameterizedConstructor() {
        // Arrange
        String layerUri = "http://layer1";
        String routeId = "route1";

        // Act
        LayerAssociationsKey key = new LayerAssociationsKey(layerUri, routeId);

        // Assert
        assertEquals(layerUri, key.getLayerUri());
        assertEquals(routeId, key.getRouteId());
    }

    @Test
    public void testSettersAndGetters() {
        // Arrange
        LayerAssociationsKey key = new LayerAssociationsKey();
        String layerUri = "http://example.com/layer";
        String routeId = "testRoute";

        // Act
        key.setLayerUri(layerUri);
        key.setRouteId(routeId);

        // Assert
        assertEquals(layerUri, key.getLayerUri());
        assertEquals(routeId, key.getRouteId());
    }

    @Test
    public void testEquals_SameObject() {
        // Arrange
        LayerAssociationsKey key = new LayerAssociationsKey("http://layer1", "route1");

        // Act & Assert
        assertTrue(key.equals(key));
    }

    @Test
    public void testEquals_EqualObjects() {
        // Arrange
        LayerAssociationsKey key1 = new LayerAssociationsKey("http://layer1", "route1");
        LayerAssociationsKey key2 = new LayerAssociationsKey("http://layer1", "route1");

        // Act & Assert
        assertTrue(key1.equals(key2));
        assertTrue(key2.equals(key1));
    }

    @Test
    public void testEquals_DifferentLayerUri() {
        // Arrange
        LayerAssociationsKey key1 = new LayerAssociationsKey("http://layer1", "route1");
        LayerAssociationsKey key2 = new LayerAssociationsKey("http://layer2", "route1");

        // Act & Assert
        assertFalse(key1.equals(key2));
        assertFalse(key2.equals(key1));
    }

    @Test
    public void testEquals_DifferentRouteId() {
        // Arrange
        LayerAssociationsKey key1 = new LayerAssociationsKey("http://layer1", "route1");
        LayerAssociationsKey key2 = new LayerAssociationsKey("http://layer1", "route2");

        // Act & Assert
        assertFalse(key1.equals(key2));
        assertFalse(key2.equals(key1));
    }

    @Test
    public void testEquals_BothFieldsDifferent() {
        // Arrange
        LayerAssociationsKey key1 = new LayerAssociationsKey("http://layer1", "route1");
        LayerAssociationsKey key2 = new LayerAssociationsKey("http://layer2", "route2");

        // Act & Assert
        assertFalse(key1.equals(key2));
    }

    @Test
    public void testEquals_NullObject() {
        // Arrange
        LayerAssociationsKey key = new LayerAssociationsKey("http://layer1", "route1");

        // Act & Assert
        assertFalse(key.equals(null));
    }

    @Test
    public void testEquals_DifferentClass() {
        // Arrange
        LayerAssociationsKey key = new LayerAssociationsKey("http://layer1", "route1");
        String other = "not a key";

        // Act & Assert
        assertFalse(key.equals(other));
    }

    @Test
    public void testEquals_BothFieldsNull() {
        // Arrange
        LayerAssociationsKey key1 = new LayerAssociationsKey(null, null);
        LayerAssociationsKey key2 = new LayerAssociationsKey(null, null);

        // Act & Assert
        assertTrue(key1.equals(key2));
    }

    @Test
    public void testEquals_OneFieldNull() {
        // Arrange
        LayerAssociationsKey key1 = new LayerAssociationsKey("http://layer1", null);
        LayerAssociationsKey key2 = new LayerAssociationsKey("http://layer1", null);

        // Act & Assert
        assertTrue(key1.equals(key2));
    }

    @Test
    public void testHashCode_EqualObjects() {
        // Arrange
        LayerAssociationsKey key1 = new LayerAssociationsKey("http://layer1", "route1");
        LayerAssociationsKey key2 = new LayerAssociationsKey("http://layer1", "route1");

        // Act & Assert
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    public void testHashCode_DifferentObjects() {
        // Arrange
        LayerAssociationsKey key1 = new LayerAssociationsKey("http://layer1", "route1");
        LayerAssociationsKey key2 = new LayerAssociationsKey("http://layer2", "route2");

        // Act & Assert
        // Note: Different objects can have the same hashCode, but it's unlikely
        // We just verify that hashCode is consistent
        assertEquals(key1.hashCode(), key1.hashCode());
        assertEquals(key2.hashCode(), key2.hashCode());
    }

    @Test
    public void testHashCode_ConsistentWithEquals() {
        // Arrange
        LayerAssociationsKey key1 = new LayerAssociationsKey("http://layer1", "route1");
        LayerAssociationsKey key2 = new LayerAssociationsKey("http://layer1", "route1");
        LayerAssociationsKey key3 = new LayerAssociationsKey("http://layer2", "route1");

        // Act & Assert
        // Equal objects must have equal hashCodes
        assertTrue(key1.equals(key2));
        assertEquals(key1.hashCode(), key2.hashCode());

        // Unequal objects may or may not have different hashCodes
        assertFalse(key1.equals(key3));
    }

    @Test
    public void testHashCode_NullFields() {
        // Arrange
        LayerAssociationsKey key1 = new LayerAssociationsKey(null, null);
        LayerAssociationsKey key2 = new LayerAssociationsKey(null, null);

        // Act & Assert
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    public void testHashCode_Consistent() {
        // Arrange
        LayerAssociationsKey key = new LayerAssociationsKey("http://layer1", "route1");

        // Act
        int hashCode1 = key.hashCode();
        int hashCode2 = key.hashCode();
        int hashCode3 = key.hashCode();

        // Assert
        assertEquals(hashCode1, hashCode2);
        assertEquals(hashCode2, hashCode3);
    }

    @Test
    public void testSerializable() {
        // Arrange
        LayerAssociationsKey key = new LayerAssociationsKey("http://layer1", "route1");

        // Assert - Just verify it implements Serializable (compile-time check)
        assertTrue(key instanceof java.io.Serializable);
    }

    @Test
    public void testNullLayerUri() {
        // Act
        LayerAssociationsKey key = new LayerAssociationsKey(null, "route1");

        // Assert
        assertNull(key.getLayerUri());
        assertEquals("route1", key.getRouteId());
    }

    @Test
    public void testNullRouteId() {
        // Act
        LayerAssociationsKey key = new LayerAssociationsKey("http://layer1", null);

        // Assert
        assertEquals("http://layer1", key.getLayerUri());
        assertNull(key.getRouteId());
    }

    @Test
    public void testEmptyStrings() {
        // Act
        LayerAssociationsKey key = new LayerAssociationsKey("", "");

        // Assert
        assertEquals("", key.getLayerUri());
        assertEquals("", key.getRouteId());
    }

    @Test
    public void testLongValues() {
        // Arrange
        StringBuilder longUri = new StringBuilder("http://example.com");
        for (int i = 0; i < 100; i++) {
            longUri.append("/segment").append(i);
        }
        String layerUri = longUri.toString();
        String routeId = "route".repeat(100);

        // Act
        LayerAssociationsKey key = new LayerAssociationsKey(layerUri, routeId);

        // Assert
        assertEquals(layerUri, key.getLayerUri());
        assertEquals(routeId, key.getRouteId());
    }

    @Test
    public void testSpecialCharacters() {
        // Arrange
        String layerUri = "http://example.com/layer?param=value&filter=true#anchor";
        String routeId = "route-with-special_chars.123";

        // Act
        LayerAssociationsKey key = new LayerAssociationsKey(layerUri, routeId);

        // Assert
        assertEquals(layerUri, key.getLayerUri());
        assertEquals(routeId, key.getRouteId());
    }

    @Test
    public void testUnicodeCharacters() {
        // Arrange
        String layerUri = "http://example.com/レイヤー";
        String routeId = "route_日本語";

        // Act
        LayerAssociationsKey key = new LayerAssociationsKey(layerUri, routeId);

        // Assert
        assertEquals(layerUri, key.getLayerUri());
        assertEquals(routeId, key.getRouteId());
    }

    @Test
    public void testEqualsAndHashCodeContract() {
        // Create multiple equal objects
        LayerAssociationsKey key1 = new LayerAssociationsKey("http://layer1", "route1");
        LayerAssociationsKey key2 = new LayerAssociationsKey("http://layer1", "route1");
        LayerAssociationsKey key3 = new LayerAssociationsKey("http://layer1", "route1");

        // Reflexive: x.equals(x)
        assertTrue(key1.equals(key1));

        // Symmetric: x.equals(y) implies y.equals(x)
        assertTrue(key1.equals(key2));
        assertTrue(key2.equals(key1));

        // Transitive: x.equals(y) and y.equals(z) implies x.equals(z)
        assertTrue(key1.equals(key2));
        assertTrue(key2.equals(key3));
        assertTrue(key1.equals(key3));

        // Consistent hashCode
        assertEquals(key1.hashCode(), key2.hashCode());
        assertEquals(key2.hashCode(), key3.hashCode());
    }
}

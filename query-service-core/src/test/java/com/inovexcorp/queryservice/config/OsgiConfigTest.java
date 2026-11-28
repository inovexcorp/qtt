package com.inovexcorp.queryservice.config;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OsgiConfigTest {

    @interface Sample {
        boolean redis_enabled() default false;

        String redis_password() default "";

        int pool__size() default 5;

        String required();

        String[] hosts() default {"a"};

        long ttl() default 10L;
    }

    private static SmallRyeConfig config(Map<String, String> props) {
        return new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(props, "test", 500))
                .build();
    }

    @Test
    void mapsMethodNamesLikeOsgi() {
        assertEquals("redis.enabled", OsgiConfig.propertyName("redis_enabled"));
        assertEquals("pool_size", OsgiConfig.propertyName("pool__size"));
        assertEquals("a-b", OsgiConfig.propertyName("a$_$b"));
        assertEquals("ab", OsgiConfig.propertyName("a$b"));
        assertEquals("a$b", OsgiConfig.propertyName("a$$b"));
    }

    @Test
    void readsConfiguredValues() {
        Sample s = OsgiConfig.load(Sample.class, "my.pid", config(Map.of(
                "my.pid.redis.enabled", "true",
                "my.pid.redis.password", "secret",
                "my.pid.pool_size", "9",
                "my.pid.required", "x",
                "my.pid.hosts", "h1, h2",
                "my.pid.ttl", "42")));
        assertTrue(s.redis_enabled());
        assertEquals("secret", s.redis_password());
        assertEquals(9, s.pool__size());
        assertEquals("x", s.required());
        assertArrayEquals(new String[]{"h1", "h2"}, s.hosts());
        assertEquals(42L, s.ttl());
    }

    @Test
    void fallsBackToAnnotationDefaultsIncludingEmptyStrings() {
        Sample s = OsgiConfig.load(Sample.class, "my.pid", config(Map.of("my.pid.required", "x")));
        assertFalse(s.redis_enabled());
        assertEquals("", s.redis_password());
        assertEquals(5, s.pool__size());
        assertArrayEquals(new String[]{"a"}, s.hosts());
    }

    @Test
    void emptyValueMeansDefault() {
        Sample s = OsgiConfig.load(Sample.class, "my.pid", config(Map.of("my.pid.required", "x", "my.pid.pool_size", "")));
        assertEquals(5, s.pool__size());
    }

    @Test
    void missingRequiredValueFailsClearly() {
        Sample s = OsgiConfig.load(Sample.class, "my.pid", config(Map.of()));
        IllegalStateException e = assertThrows(IllegalStateException.class, s::required);
        assertTrue(e.getMessage().contains("my.pid.required"));
    }

    @Test
    void invalidValueNamesTheProperty() {
        Sample s = OsgiConfig.load(Sample.class, "my.pid", config(Map.of("my.pid.pool_size", "lots", "my.pid.required", "x")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, s::pool__size);
        assertTrue(e.getMessage().contains("my.pid.pool_size"));
    }

    @Test
    void toStringMasksSecrets() {
        Sample s = OsgiConfig.load(Sample.class, "my.pid", config(Map.of("my.pid.redis.password", "secret", "my.pid.required", "x")));
        assertFalse(s.toString().contains("secret"));
        assertEquals(Sample.class, s.annotationType());
    }
}

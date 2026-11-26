package com.inovexcorp.queryservice.config;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Optional;

/**
 * Builds instances of QTT's configuration annotation types (formerly OSGi metatype configurations) from MicroProfile
 * Config. Each method maps to {@code <pid>.<property>} using the OSGi Declarative Services naming rules
 * ({@code redis_enabled()} reads {@code redis.enabled}); a missing or empty value falls back to the annotation's
 * default. This keeps the property names of the old etc/*.cfg files and lets empty-string defaults work, which
 * MicroProfile Config otherwise treats as "missing".
 */
public final class OsgiConfig {

    private OsgiConfig() {
    }

    public static <A extends Annotation> A load(Class<A> type, String pid) {
        return load(type, pid, ConfigProvider.getConfig());
    }

    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A load(Class<A> type, String pid, Config config) {
        return (A) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "annotationType":
                    return type;
                case "toString":
                    return describe(type, pid, config);
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return value(method, pid, config);
            }
        });
    }

    /** OSGi DS 1.3 method-name to property-name mapping. */
    public static String propertyName(String methodName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < methodName.length(); i++) {
            char c = methodName.charAt(i);
            if (c == '_') {
                if (i + 1 < methodName.length() && methodName.charAt(i + 1) == '_') {
                    sb.append('_');
                    i++;
                } else {
                    sb.append('.');
                }
            } else if (c == '$') {
                if (i + 1 < methodName.length() && methodName.charAt(i + 1) == '$') {
                    sb.append('$');
                    i++;
                } else if (i + 2 < methodName.length() && methodName.charAt(i + 1) == '_'
                        && methodName.charAt(i + 2) == '$') {
                    sb.append('-');
                    i += 2;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Object value(Method method, String pid, Config config) {
        String key = pid + "." + propertyName(method.getName());
        Optional<String> raw = config.getOptionalValue(key, String.class).filter(v -> !v.isEmpty());
        Object fallback = method.getDefaultValue();
        if (raw.isEmpty()) {
            if (fallback == null) {
                throw new IllegalStateException("Missing required configuration property " + key);
            }
            return fallback;
        }
        return convert(raw.get(), method.getReturnType(), key);
    }

    private static Object convert(String v, Class<?> t, String key) {
        try {
            if (t == String.class) {
                return v;
            } else if (t == int.class) {
                return Integer.parseInt(v.trim());
            } else if (t == long.class) {
                return Long.parseLong(v.trim());
            } else if (t == boolean.class) {
                return Boolean.parseBoolean(v.trim());
            } else if (t == double.class) {
                return Double.parseDouble(v.trim());
            } else if (t == float.class) {
                return Float.parseFloat(v.trim());
            } else if (t.isEnum()) {
                return Enum.valueOf(t.asSubclass(Enum.class), v.trim());
            } else if (t.isArray()) {
                String[] parts = v.split("\\s*,\\s*");
                Object arr = Array.newInstance(t.getComponentType(), parts.length);
                for (int i = 0; i < parts.length; i++) {
                    Array.set(arr, i, convert(parts[i], t.getComponentType(), key));
                }
                return arr;
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid value '" + v + "' for " + key, e);
        }
        throw new IllegalArgumentException("Unsupported configuration type " + t + " for " + key);
    }

    private static String describe(Class<?> type, String pid, Config config) {
        StringBuilder sb = new StringBuilder(type.getSimpleName()).append('[');
        Method[] methods = type.getDeclaredMethods();
        Arrays.sort(methods, (a, b) -> a.getName().compareTo(b.getName()));
        for (Method m : methods) {
            String name = propertyName(m.getName());
            boolean secret = name.contains("password") || name.contains("key") || name.contains("salt");
            Object v = secret ? "****" : value(m, pid, config);
            sb.append(name).append('=').append(v instanceof Object[] ? Arrays.toString((Object[]) v) : v).append(", ");
        }
        return sb.append(']').toString();
    }
}

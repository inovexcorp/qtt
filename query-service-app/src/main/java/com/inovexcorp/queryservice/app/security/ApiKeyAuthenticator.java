package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.persistence.ApiKey;
import com.inovexcorp.queryservice.persistence.ApiKeyService;
import com.inovexcorp.queryservice.security.QttRoles;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Checks QTT API keys. Key records are cached for {@link #CACHE_TTL}, so a revocation takes effect on every instance
 * within that time without a database read per request; the last-used time is written at most once per
 * {@link #TOUCH_INTERVAL} per key. Blocking (database): call it off the event loop.
 */
@ApplicationScoped
public class ApiKeyAuthenticator {

    private static final Logger LOG = Logger.getLogger(ApiKeyAuthenticator.class);

    static final Duration CACHE_TTL = Duration.ofSeconds(30);
    static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);
    /** Identity attribute with the key's display name. */
    static final String API_KEY_NAME = "qtt.apiKey.name";

    private record Cached(ApiKey key, Instant loadedAt) {
    }

    private final ApiKeyService keys;
    private final Clock clock;
    private final ConcurrentMap<String, Cached> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> lastTouched = new ConcurrentHashMap<>();

    @Inject
    ApiKeyAuthenticator(ApiKeyService keys) {
        this(keys, Clock.systemUTC());
    }

    ApiKeyAuthenticator(ApiKeyService keys, Clock clock) {
        this.keys = keys;
        this.clock = clock;
    }

    /** The identity of a valid key, or null (malformed, unknown, wrong secret, revoked or expired). */
    SecurityIdentity authenticate(String token) {
        ApiKeyFormat.Parsed parsed = ApiKeyFormat.parse(token);
        if (parsed == null) {
            return null;
        }
        Instant now = clock.instant();
        ApiKey key = load(parsed.id(), now);
        if (key == null || !ApiKeyFormat.matches(parsed.secret(), key.getSecretHash())) {
            LOG.debugf("Refused API key %s: unknown id or wrong secret", parsed.id());
            return null;
        }
        if (key.getRevokedAt() != null || (key.getExpiresAt() != null && !now.isBefore(key.getExpiresAt().toInstant()))) {
            LOG.debugf("Refused API key %s (%s): revoked or expired", key.getId(), key.getName());
            return null;
        }
        touch(key.getId(), now);
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("apikey:" + key.getId()))
                .addRole(QttRoles.QUERY)
                .addAttribute(QueryAccessDecision.API_KEY_ROUTES, routes(key.getRoutes()))
                .addAttribute(API_KEY_NAME, key.getName())
                .build();
    }

    /** Forgets a key's cached record (after a revocation on this instance, so it takes effect at once here). */
    void evict(String id) {
        cache.remove(id);
    }

    private ApiKey load(String id, Instant now) {
        Cached cached = cache.get(id);
        if (cached != null && Duration.between(cached.loadedAt(), now).compareTo(CACHE_TTL) < 0) {
            return cached.key();
        }
        ApiKey key = keys.get(id);
        if (key == null) {
            cache.remove(id); // unknown ids are not cached, so a flood of random ids can't grow the cache
        } else {
            cache.put(id, new Cached(key, now));
        }
        return key;
    }

    private void touch(String id, Instant now) {
        Instant previous = lastTouched.get(id);
        if (previous != null && Duration.between(previous, now).compareTo(TOUCH_INTERVAL) < 0) {
            return;
        }
        lastTouched.put(id, now);
        try {
            keys.touch(id, Date.from(now));
        } catch (RuntimeException e) {
            LOG.debugf(e, "Could not record use of API key %s", id);
        }
    }

    /** A key's stored route scope (comma-separated) as a set. */
    static Set<String> routes(String stored) {
        if (stored == null || stored.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(stored.split(",")).map(String::trim).filter(r -> !r.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}

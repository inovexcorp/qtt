package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.persistence.ApiKey;
import com.inovexcorp.queryservice.persistence.ApiKeyService;
import com.inovexcorp.queryservice.security.QttRoles;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyAuthenticatorTest {

    private final ApiKeyService service = mock(ApiKeyService.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-27T12:00:00Z"));
    private final ApiKeyAuthenticator authenticator = new ApiKeyAuthenticator(service, clock);
    private ApiKeyFormat.Generated generated;
    private ApiKey key;

    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @BeforeEach
    void setUp() {
        generated = ApiKeyFormat.generate();
        key = new ApiKey();
        key.setId(generated.id());
        key.setName("reporting app");
        key.setSecretHash(generated.secretHash());
        key.setRoutes("people, places");
        when(service.get(generated.id())).thenReturn(key);
    }

    @Test
    void aValidKeyIsAQueryCallerScopedToItsRoutes() {
        SecurityIdentity id = authenticator.authenticate(generated.key());
        assertNotNull(id);
        assertEquals("apikey:" + generated.id(), id.getPrincipal().getName());
        assertEquals(Set.of(QttRoles.QUERY), id.getRoles());
        assertEquals(Set.of("people", "places"), id.getAttribute(QueryAccessDecision.API_KEY_ROUTES));
        assertEquals("reporting app", id.getAttribute(ApiKeyAuthenticator.API_KEY_NAME));
    }

    @Test
    void wrongSecretsUnknownIdsAndMalformedKeysAreRefused() {
        assertNull(authenticator.authenticate(generated.key() + "0"));
        assertNull(authenticator.authenticate("qtt_" + generated.id() + "_" + "0".repeat(43)));
        assertNull(authenticator.authenticate(ApiKeyFormat.generate().key())); // unknown id
        assertNull(authenticator.authenticate("qtt_nonsense"));
    }

    @Test
    void revokedAndExpiredKeysAreRefused() {
        key.setExpiresAt(Date.from(clock.instant().plusSeconds(60)));
        assertNotNull(authenticator.authenticate(generated.key()));
        clock.advance(Duration.ofSeconds(60));
        assertNull(authenticator.authenticate(generated.key()), "expired at exactly expiresAt");

        ApiKeyFormat.Generated other = ApiKeyFormat.generate();
        ApiKey revoked = new ApiKey();
        revoked.setId(other.id());
        revoked.setSecretHash(other.secretHash());
        revoked.setRoutes("*");
        revoked.setRevokedAt(new Date());
        when(service.get(other.id())).thenReturn(revoked);
        assertNull(authenticator.authenticate(other.key()));
    }

    @Test
    void recordsAreCachedForThirtySecondsAndEvictable() {
        authenticator.authenticate(generated.key());
        authenticator.authenticate(generated.key());
        verify(service, times(1)).get(generated.id());

        // A revocation made on another instance is seen once the cache entry is older than 30 s.
        ApiKey revoked = new ApiKey();
        revoked.setId(key.getId());
        revoked.setSecretHash(key.getSecretHash());
        revoked.setRoutes(key.getRoutes());
        revoked.setRevokedAt(new Date());
        when(service.get(generated.id())).thenReturn(revoked);
        clock.advance(Duration.ofSeconds(29));
        assertNotNull(authenticator.authenticate(generated.key()));
        clock.advance(Duration.ofSeconds(1));
        assertNull(authenticator.authenticate(generated.key()));

        // On the instance that revoked it, eviction makes it immediate.
        when(service.get(generated.id())).thenReturn(key);
        authenticator.authenticate(generated.key());
        when(service.get(generated.id())).thenReturn(revoked);
        authenticator.evict(generated.id());
        assertNull(authenticator.authenticate(generated.key()));
    }

    @Test
    void lastUseIsWrittenAtMostOncePerMinute() {
        authenticator.authenticate(generated.key());
        authenticator.authenticate(generated.key());
        verify(service, times(1)).touch(anyString(), any());
        clock.advance(Duration.ofSeconds(61));
        authenticator.authenticate(generated.key());
        verify(service, times(2)).touch(anyString(), any());
    }

    @Test
    void refusedKeysAreNeverTouched() {
        authenticator.authenticate(generated.key() + "0");
        verify(service, never()).touch(anyString(), any());
    }
}

package com.inovexcorp.queryservice.app.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyFormatTest {

    @Test
    void generatedKeysParseAndMatchTheirHash() {
        ApiKeyFormat.Generated g = ApiKeyFormat.generate();
        assertTrue(g.key().startsWith("qtt_" + g.id() + "_"), g.key());
        assertEquals(12, g.id().length());
        ApiKeyFormat.Parsed p = ApiKeyFormat.parse(g.key());
        assertEquals(g.id(), p.id());
        assertEquals(43, p.secret().length());
        assertTrue(ApiKeyFormat.matches(p.secret(), g.secretHash()));
        assertFalse(ApiKeyFormat.matches(p.secret() + "x", g.secretHash()));
        assertEquals(64, g.secretHash().length());
        assertFalse(g.secretHash().contains(p.secret()));
    }

    @Test
    void keysAreUnique() {
        Set<String> ids = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            ApiKeyFormat.Generated g = ApiKeyFormat.generate();
            ids.add(g.id());
            keys.add(g.key());
        }
        assertEquals(200, ids.size());
        assertEquals(200, keys.size());
        assertNotEquals(ApiKeyFormat.generate().secretHash(), ApiKeyFormat.generate().secretHash());
    }

    @Test
    void malformedKeysDoNotParse() {
        for (String bad : new String[]{null, "", "qtt_", "qtt_short_secretsecretsecretsecret", "eyJhbGciOi.x.y",
                "qtt_ABCDEFGHIJKL_", "qtt_ABCDEFGHIJKL_has-dash-in-the-secret-part-xx", "QTT_ABCDEFGHIJKL_abcdefghijklmnopqrstuvwxyz"}) {
            assertNull(ApiKeyFormat.parse(bad), String.valueOf(bad));
        }
        assertTrue(ApiKeyFormat.looksLikeKey("qtt_anything"));
        assertFalse(ApiKeyFormat.looksLikeKey("eyJhbGciOi.x.y"));
        assertFalse(ApiKeyFormat.looksLikeKey(null));
    }
}

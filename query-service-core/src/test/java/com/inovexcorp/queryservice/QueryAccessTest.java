package com.inovexcorp.queryservice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryAccessTest {

    @Test
    void parsesCaseInsensitivelyWithBlankAsDefault() {
        assertEquals(QueryAccess.DEFAULT, QueryAccess.parse(null));
        assertEquals(QueryAccess.DEFAULT, QueryAccess.parse(" "));
        assertEquals(QueryAccess.OPEN, QueryAccess.parse("Open"));
        assertEquals(QueryAccess.AUTHENTICATED, QueryAccess.parse(" authenticated "));
        assertThrows(IllegalArgumentException.class, () -> QueryAccess.parse("public"));
    }

    @Test
    void defaultResolvesToTheServerDefault() {
        assertEquals(QueryAccess.OPEN, QueryAccess.DEFAULT.resolve(QueryAccess.OPEN));
        assertEquals(QueryAccess.AUTHENTICATED, QueryAccess.DEFAULT.resolve(QueryAccess.AUTHENTICATED));
        assertEquals(QueryAccess.OPEN, QueryAccess.OPEN.resolve(QueryAccess.AUTHENTICATED));
        assertEquals(QueryAccess.AUTHENTICATED, QueryAccess.AUTHENTICATED.resolve(QueryAccess.OPEN));
        assertThrows(IllegalArgumentException.class, () -> QueryAccess.DEFAULT.resolve(QueryAccess.DEFAULT));
    }

    @Test
    void rolesAreTrimmedDeduplicatedAndRoundTrip() {
        assertEquals(List.of("a", "b"), QueryAccess.parseRoles(" a, b ,,a "));
        assertEquals(List.of(), QueryAccess.parseRoles(null));
        assertEquals("a,b", QueryAccess.formatRoles(List.of("a", "b")));
        assertNull(QueryAccess.formatRoles(List.of()));
        assertEquals("open", QueryAccess.OPEN.value());
    }
}

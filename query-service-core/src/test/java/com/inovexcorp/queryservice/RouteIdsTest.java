package com.inovexcorp.queryservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteIdsTest {

    @ParameterizedTest
    @ValueSource(strings = {"people-search", "people_search", "v2.people", "A", "route123", "a.b-c_d"})
    void acceptsPlainPathSegments(String id) {
        assertTrue(RouteIds.isValid(id), id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a/b", "a b", "{id}", "a{b}", "a%2Fb", "a?b", "a#b", ".", "..", "ünïcode",
            "a\\b", "a:b"})
    void rejectsAnythingThatIsNotOnePlainSegment(String id) {
        assertFalse(RouteIds.isValid(id), id);
    }

    @Test
    void rejectsNullAndOverlongIds() {
        assertFalse(RouteIds.isValid(null));
        assertTrue(RouteIds.isValid("a".repeat(128)));
        assertFalse(RouteIds.isValid("a".repeat(129)));
    }
}

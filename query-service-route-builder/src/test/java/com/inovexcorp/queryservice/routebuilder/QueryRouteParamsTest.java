package com.inovexcorp.queryservice.routebuilder;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryRouteParamsTest {

    @Test
    void readsTheMethodRestrictionTheUiWrites() {
        QueryRouteParams p = QueryRouteParams.parse("r", "httpMethodRestrict=GET,POST");
        assertEquals(Set.of("GET", "POST"), p.allowedMethods());
        assertTrue(p.allows("GET"));
        assertTrue(p.allows("post"));
        assertFalse(p.allows("DELETE"));
        assertEquals("GET, POST", p.allowHeader());
    }

    @Test
    void acceptsTheOldLeadingQuestionMarkAndSpaces() {
        QueryRouteParams p = QueryRouteParams.parse("r", "?httpMethodRestrict= get , put ");
        assertEquals(Set.of("GET", "PUT"), p.allowedMethods());
    }

    @Test
    void noRestrictionMeansEveryMethod() {
        for (String params : new String[]{null, "", "?", "&&"}) {
            QueryRouteParams p = QueryRouteParams.parse("r", params);
            assertTrue(p.allowedMethods().isEmpty(), String.valueOf(params));
            assertTrue(p.allows("DELETE"));
        }
    }

    @Test
    void ignoresNettyOnlyOptionsButKeepsTheRestriction() {
        QueryRouteParams p = QueryRouteParams.parse("r", "param1={param1}&httpMethodRestrict=DELETE&chunked=false");
        assertEquals(Set.of("DELETE"), p.allowedMethods());
    }

    @Test
    void aRequestWithoutAnHttpMethodIsNotRejected() {
        // Exchanges that did not come from HTTP (unit tests) carry no CamelHttpMethod header.
        assertTrue(QueryRouteParams.parse("r", "httpMethodRestrict=POST").allows(null));
    }
}

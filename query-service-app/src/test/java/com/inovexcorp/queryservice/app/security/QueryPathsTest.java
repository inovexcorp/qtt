package com.inovexcorp.queryservice.app.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryPathsTest {

    @Test
    void recognisesTheQuerySpaceOnly() {
        assertTrue(QueryPaths.isQueryPath("/query"));
        assertTrue(QueryPaths.isQueryPath("/query/people"));
        assertFalse(QueryPaths.isQueryPath("/queryrest/api/routes"));
        assertFalse(QueryPaths.isQueryPath("/queryish"));
        assertFalse(QueryPaths.isQueryPath("/"));
        assertFalse(QueryPaths.isQueryPath(null));
    }

    @Test
    void extractsASingleSegmentRouteId() {
        assertEquals("people", QueryPaths.routeId("/query/people"));
        assertEquals("v2.people", QueryPaths.routeId("/query/v2.people"));
        assertNull(QueryPaths.routeId("/query"));
        assertNull(QueryPaths.routeId("/query/"));
        assertNull(QueryPaths.routeId("/query/a/b"));
        assertNull(QueryPaths.routeId("/queryrest/api"));
    }
}

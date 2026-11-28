package com.inovexcorp.queryservice.routebuilder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryRouteUpdatesTest {

    private final QueryRouteUpdates updates = new QueryRouteUpdates();

    @Test
    void aRouteIsUpdatingBetweenBeginAndEnd() {
        assertFalse(updates.isUpdating("r1"));
        updates.begin("r1", "authenticated", "finance-apps");
        assertTrue(updates.isUpdating("r1"));
        assertFalse(updates.isUpdating("r2"));
        assertEquals(new QueryRouteUpdates.Pending("authenticated", "finance-apps"), updates.pending("r1").get());
        updates.end("r1");
        assertFalse(updates.isUpdating("r1"));
    }

    @Test
    void overlappingUpdatesOfOneRouteAreCounted() {
        updates.begin("r1", null, null);
        updates.begin("r1", "open", null);
        assertEquals("open", updates.pending("r1").get().accessPolicy(), "the newest setting is kept");
        updates.end("r1");
        assertTrue(updates.isUpdating("r1"), "the other update is still running");
        updates.end("r1");
        assertFalse(updates.isUpdating("r1"));
    }

    @Test
    void endWithoutBeginAndNullIdsAreHarmless() {
        updates.end("r1");
        assertFalse(updates.isUpdating("r1"));
        assertFalse(updates.isUpdating(null));
    }
}

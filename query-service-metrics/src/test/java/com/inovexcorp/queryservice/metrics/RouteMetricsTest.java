package com.inovexcorp.queryservice.metrics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RouteMetricsTest {

    @Test
    void defaultState_isEmpty() {
        RouteMetrics metrics = new RouteMetrics();

        assertNotNull(metrics.getMetrics(), "metrics list should be initialized");
        assertTrue(metrics.getMetrics().isEmpty(), "metrics list should be empty by default");
    }

    @Test
    void getMetrics_returnsLiveMutableList() {
        RouteMetrics metrics = new RouteMetrics();

        MetricObject m = mock(MetricObject.class);
        metrics.getMetrics().add(m);

        assertEquals(1, metrics.getMetrics().size(), "metrics list should reflect added item");
        assertSame(m, metrics.getMetrics().get(0), "added element should be present in the list");
    }

    @Test
    void setter_replacesListReference() {
        RouteMetrics metrics = new RouteMetrics();

        List<MetricObject> newList = new ArrayList<>();
        MetricObject m = mock(MetricObject.class);
        newList.add(m);

        metrics.setMetrics(newList);

        assertSame(newList, metrics.getMetrics(), "setter should replace list reference");
        assertEquals(1, metrics.getMetrics().size(), "new list content should be visible");
    }

    @Test
    void equalsAndHashCode_basedOnMetrics() {
        MetricObject m = mock(MetricObject.class);

        List<MetricObject> list1 = new ArrayList<>();
        list1.add(m);
        List<MetricObject> list2 = new ArrayList<>();
        list2.add(m);

        RouteMetrics a = new RouteMetrics();
        a.setMetrics(list1);
        RouteMetrics b = new RouteMetrics();
        b.setMetrics(list2);

        assertEquals(a, b, "objects with same list content should be equal");
        assertEquals(a.hashCode(), b.hashCode(), "equal objects must have same hashCode");

        // modify b and ensure inequality
        b.getMetrics().add(mock(MetricObject.class));
        assertNotEquals(a, b, "objects should not be equal after list mutation");
    }

    @Test
    void toString_containsMetrics() {
        RouteMetrics metrics = new RouteMetrics();

        String s = metrics.toString();
        assertNotNull(s, "toString should not return null");
        assertTrue(s.contains("metrics"), "toString should include field name 'metrics'");
    }
}

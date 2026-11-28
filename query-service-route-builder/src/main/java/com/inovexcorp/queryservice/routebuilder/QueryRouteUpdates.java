package com.inovexcorp.queryservice.routebuilder;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The query routes being replaced right now. Changing a route replaces its Camel route (a modify, a template change,
 * or a change to its datasource), so for a moment {@code /query/<routeId>} has no route behind it. While a route is
 * listed here, callers who may use it get {@code 503} with {@code Retry-After} instead of a 404, and the access
 * setting it had before the change keeps deciding who may call it.
 */
@ApplicationScoped
public class QueryRouteUpdates {

    /**
     * The access setting of the route being replaced, as stored ({@code accessPolicy}, {@code requiredRoles}).
     *
     * @param accessPolicy  the stored access setting; null means "default"
     * @param requiredRoles the stored comma-separated required roles, or null
     */
    public record Pending(String accessPolicy, String requiredRoles) {
    }

    private record Entry(Pending pending, int updates) {
    }

    private final ConcurrentMap<String, Entry> updating = new ConcurrentHashMap<>();

    /**
     * Marks a route as being replaced. Every call must be paired with {@link #end(String)} (in a finally block);
     * overlapping changes to the same route are counted, and the newest access setting is kept.
     *
     * @param routeId       the route
     * @param accessPolicy  its access setting before the change
     * @param requiredRoles its required roles before the change
     */
    public void begin(String routeId, String accessPolicy, String requiredRoles) {
        Pending pending = new Pending(accessPolicy, requiredRoles);
        updating.merge(routeId, new Entry(pending, 1), (old, added) -> new Entry(pending, old.updates() + 1));
    }

    /** Ends one {@link #begin} for this route. */
    public void end(String routeId) {
        updating.computeIfPresent(routeId, (id, entry) -> entry.updates() <= 1 ? null
                : new Entry(entry.pending(), entry.updates() - 1));
    }

    /** The route's access setting from before the change, while it is being replaced. */
    public Optional<Pending> pending(String routeId) {
        Entry entry = routeId == null ? null : updating.get(routeId);
        return entry == null ? Optional.empty() : Optional.of(entry.pending());
    }

    public boolean isUpdating(String routeId) {
        return pending(routeId).isPresent();
    }
}

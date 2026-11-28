package com.inovexcorp.queryservice.routebuilder;

/**
 * {@link QueryHeaderFilterStrategy} that also returns the Anzo metadata headers ({@code anzo.query},
 * {@code anzo.query_duration}, {@code anzo.graphmart}); used when {@code QUERY_DEBUG_HEADERS=true}.
 */
public class QueryDebugHeaderFilterStrategy extends QueryHeaderFilterStrategy {

    public QueryDebugHeaderFilterStrategy() {
        super(true);
    }
}

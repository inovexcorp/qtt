package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.QueryAccess;

import java.util.List;

/**
 * The access setting of one running query route, as its Camel route carries it.
 *
 * @param routeId       the route
 * @param access        as stored on the route; {@link QueryAccess#DEFAULT} follows QUERY_AUTH_DEFAULT
 * @param requiredRoles roles an authenticated caller must also hold
 */
record QueryRouteAccess(String routeId, QueryAccess access, List<String> requiredRoles) {
}

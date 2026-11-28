package com.inovexcorp.queryservice.persistence;

import java.util.List;

public interface RouteService {

    void add(CamelRouteTemplate camelRouteTemplate);

    void deleteAll();

    List<CamelRouteTemplate> getAll();

    boolean routeExists(String routeId);

    void delete(String routeId);

    CamelRouteTemplate getRoute(String routeId);

    /**
     * Sets a route's stored status. Stopping records why and when ({@code reason} is required); starting clears both.
     *
     * @param status "Started" or "Stopped"
     * @param reason why it was stopped; ignored when starting
     */
    void updateRouteStatus(String routeId, String status, RouteStopReason reason);

    long countRoutes();

    List<CamelRouteTemplate> getRoutesByDatasource(String dataSourceId);
}

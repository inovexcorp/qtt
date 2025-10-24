package com.inovexcorp.queryservice.persistence;

import java.util.List;

public interface RouteService {

    void add(CamelRouteTemplate camelRouteTemplate);

    void deleteAll();

    List<CamelRouteTemplate> getAll();

    boolean routeExists(String routeId);

    void delete(String routeId);

    CamelRouteTemplate getRoute(String routeId);

    void updateRouteStatus(String routeId, String status);

    long countRoutes();

    List<CamelRouteTemplate> getRoutesByDatasource(String dataSourceId);
}

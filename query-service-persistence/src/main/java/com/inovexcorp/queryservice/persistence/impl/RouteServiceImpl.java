package com.inovexcorp.queryservice.persistence.impl;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.RouteService;
import com.inovexcorp.queryservice.persistence.RouteStopReason;
import lombok.extern.slf4j.Slf4j;
import com.inovexcorp.queryservice.persistence.jpa.JpaTemplate;
import com.inovexcorp.queryservice.persistence.jpa.TransactionType;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@ApplicationScoped
public class RouteServiceImpl implements RouteService {

    @Inject
    JpaTemplate jpa;


    /**
     * Method to add a camel route to the DataSource.
     *
     * @param camelRouteTemplate A {@link CamelRouteTemplate} object constructed prior to method call
     */
    @Override
    public void add(CamelRouteTemplate camelRouteTemplate) {
        jpa.tx(TransactionType.Required, em -> {
            em.merge(camelRouteTemplate);
            em.flush();
        });
    }

    public void deleteAll() {
        jpa.tx(TransactionType.Required, em -> {
            em.createQuery("delete from CamelRouteTemplate").executeUpdate();
            em.flush();
        });
    }

    @Override
    public List<CamelRouteTemplate> getAll() {
        return jpa.txExpr(TransactionType.Supports, em -> em.createQuery("select r from CamelRouteTemplate r", CamelRouteTemplate.class).getResultList());
    }

    /**
     * Method to verify if a route exists. Camel overwrites existing routes when re-created, so
     * this method is used to mimic that functionality
     *
     * @param routeId Primary key for a {@link CamelRouteTemplate}
     * @return True or False depending on if the route exists
     */
    @Override
    public boolean routeExists(String routeId) {
        AtomicReference<CamelRouteTemplate> camelRoute = new AtomicReference<>(new CamelRouteTemplate());
        jpa.tx(TransactionType.Supports, em -> camelRoute.set(em.find(CamelRouteTemplate.class, routeId)));
        //If the route is not null, return true. Otherwise, false
        return camelRoute.get() != null;
    }

    /**
     * Method to delete routes given a {@link CamelRouteTemplate} routeId string
     *
     * @param routeId
     */
    @Override
    public void delete(String routeId) {
        jpa.tx(TransactionType.Required, em -> {
            CamelRouteTemplate camelRouteTemplate = em.find(CamelRouteTemplate.class, routeId);
            try {
                // Unlink from the datasource's orphanRemoval collection first; otherwise the flush cascades an
                // orphan delete of an instance that is already being removed (Hibernate 6+ rejects that).
                if (camelRouteTemplate != null && camelRouteTemplate.getDatasources() != null
                        && camelRouteTemplate.getDatasources().getCamelRouteTemplate() != null) {
                    camelRouteTemplate.getDatasources().getCamelRouteTemplate().remove(camelRouteTemplate);
                }
                em.remove(camelRouteTemplate);
                em.flush();
            } catch (Exception e) {
                log.warn("Cannot delete non-existent route: {}", routeId, e);
            }
        });
    }

    @Override
    public CamelRouteTemplate getRoute(String routeId) {
        AtomicReference<CamelRouteTemplate> route = new AtomicReference<>(new CamelRouteTemplate());
        jpa.tx(TransactionType.Supports, em -> route.set(em.find(CamelRouteTemplate.class, routeId)));
        return route.get();
    }

    @Override
    public void updateRouteStatus(String routeId, String status, RouteStopReason reason) {
        boolean stopping = "Stopped".equals(status);
        if (stopping && reason == null) {
            throw new IllegalArgumentException("Stopping route " + routeId + " needs a reason");
        }
        jpa.tx(TransactionType.Required, em -> {
            CamelRouteTemplate camelRoute = em.find(CamelRouteTemplate.class, routeId);
            camelRoute.setStatus(status);
            camelRoute.setStopReason(stopping ? reason : null);
            camelRoute.setStoppedAt(stopping ? new Date() : null);
            em.merge(camelRoute);
            em.flush();
        });
    }


    /**
     * Method to count the number of routes in the database
     *
     * @return The number of routes in the database
     */
    public long countRoutes() {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery("select count(r) from CamelRouteTemplate r", Long.class).getSingleResult());
    }

    /**
     * Method to get all routes associated with a specific datasource
     *
     * @param dataSourceId The datasource ID to filter routes by
     * @return List of routes associated with the datasource
     */
    @Override
    public List<CamelRouteTemplate> getRoutesByDatasource(String dataSourceId) {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery("select r from CamelRouteTemplate r where r.datasources.dataSourceId = :dataSourceId", CamelRouteTemplate.class)
                        .setParameter("dataSourceId", dataSourceId)
                        .getResultList());
    }
}

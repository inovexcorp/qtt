package com.inovexcorp.queryservice.persistence.impl;

import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.RouteService;
import lombok.extern.slf4j.Slf4j;
import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component(immediate = true, service = RouteService.class)
public class RouteServiceImpl implements RouteService {

    @Reference(target = "(osgi.unit.name=qtt-pu)")
    private JpaTemplate jpa;


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
    public void updateRouteStatus(String routeId, String status) {
        jpa.tx(TransactionType.Required, em -> {
            CamelRouteTemplate camelRoute = em.find(CamelRouteTemplate.class, routeId);
            camelRoute.setStatus(status);
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

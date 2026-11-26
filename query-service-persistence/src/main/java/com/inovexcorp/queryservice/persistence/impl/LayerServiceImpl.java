package com.inovexcorp.queryservice.persistence.impl;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.LayerAssociations;
import com.inovexcorp.queryservice.persistence.LayerService;
import com.inovexcorp.queryservice.persistence.jpa.JpaTemplate;
import com.inovexcorp.queryservice.persistence.jpa.TransactionType;

import java.util.List;

@ApplicationScoped
public class LayerServiceImpl implements LayerService {

    @Inject
    JpaTemplate jpa;

    @Override
    public void add(LayerAssociations layerAssociations) {
        jpa.tx(TransactionType.Required, em -> {
            em.merge(layerAssociations);
            em.flush();
        });
    }

    @Override
    public void deleteAll(CamelRouteTemplate routeId) {
        jpa.tx(TransactionType.Required, em -> {
            em.createQuery("delete from LayerAssociations l where l.id.routeId = :route_Id").setParameter("route_Id", routeId.getRouteId()).executeUpdate();
            em.flush();
        });
    }

    @Override
    public List<String> getLayerUris(CamelRouteTemplate routeId) {
        return jpa.txExpr(TransactionType.Supports, em -> em.createQuery("select l.id.layerUri from LayerAssociations l where l.id.routeId = :route_Id")
                .setParameter("route_Id", routeId.getRouteId())
                .getResultList());
    }
}

package com.inovexcorp.queryservice.persistence.impl;

import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.LayerAssociations;
import com.inovexcorp.queryservice.persistence.LayerService;
import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.List;

@Component(immediate = true, service = LayerService.class)
public class LayerServiceImpl implements LayerService {

    @Reference(target = "(osgi.unit.name=qtt-pu)")
    private JpaTemplate jpa;

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

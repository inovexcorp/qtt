package com.inovexcorp.queryservice.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@Entity
@NoArgsConstructor
@Table(name = "layers")
public class LayerAssociations {

    @EmbeddedId
    private LayerAssociationsKey id;

    @JsonIgnore
    @ManyToOne(cascade = {javax.persistence.CascadeType.MERGE})
    @MapsId("routeId")
    @JoinColumn(name = "route_id")
    private CamelRouteTemplate route;

    public LayerAssociations(String layerUri, CamelRouteTemplate route) {
        this.id = new LayerAssociationsKey(layerUri, route.getRouteId());
        this.route = route;
    }
}


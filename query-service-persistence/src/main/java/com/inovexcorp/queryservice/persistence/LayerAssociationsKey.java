package com.inovexcorp.queryservice.persistence;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Data
@Embeddable
@NoArgsConstructor
public class LayerAssociationsKey implements Serializable {

    @Column(name = "layerUri")
    private String layerUri;

    @Column(name = "routeId")
    private String routeId;

    public LayerAssociationsKey(String layerUri, String routeId) {
        this.layerUri = layerUri;
        this.routeId = routeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LayerAssociationsKey)) return false;
        LayerAssociationsKey that = (LayerAssociationsKey) o;
        return Objects.equals(getLayerUri(), that.getLayerUri()) && Objects.equals(getRouteId(), that.getRouteId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLayerUri(), getRouteId());
    }
}






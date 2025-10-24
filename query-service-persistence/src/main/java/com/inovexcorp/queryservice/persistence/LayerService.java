package com.inovexcorp.queryservice.persistence;

import java.util.List;

public interface LayerService {

    void add(LayerAssociations layerAssociations);

    void deleteAll(CamelRouteTemplate route);

    List<String> getLayerUris(CamelRouteTemplate route);
}

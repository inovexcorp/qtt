package com.inovexcorp.queryservice.sparqi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Context information for a SPARQi session.
 * Contains route details, template content, and ontology information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SparqiContext {

    @JsonProperty("routeId")
    private String routeId;

    @JsonProperty("currentTemplate")
    private String currentTemplate;

    @JsonProperty("routeDescription")
    private String routeDescription;

    @JsonProperty("graphMartUri")
    private String graphMartUri;

    @JsonProperty("layerUris")
    private List<String> layerUris;

    @JsonProperty("datasourceUrl")
    private String datasourceUrl;

    @JsonProperty("ontologyElementCount")
    private int ontologyElementCount;
}

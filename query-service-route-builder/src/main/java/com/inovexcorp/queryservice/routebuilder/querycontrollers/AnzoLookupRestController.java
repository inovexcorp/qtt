package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.RouteService;
import com.inovexcorp.queryservice.camel.anzo.comm.AnzoClient;
import com.inovexcorp.queryservice.camel.anzo.comm.QueryResponse;
import com.inovexcorp.queryservice.camel.anzo.comm.SimpleAnzoClient;
import com.inovexcorp.queryservice.persistence.Datasources;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.json.Json;
import jakarta.json.JsonValue;
import jakarta.json.JsonReader;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;

/**
 * REST controller for interacting with Anzograph data and metadata.
 */
@ApplicationScoped
@Path("/api/anzo")
public class AnzoLookupRestController {

    private static final Logger LOG = LoggerFactory.getLogger(AnzoLookupRestController.class);

    private static final String VALUE_KEY = "value";

    @Inject
    RouteService routeService;

    @Inject
    DataSourceService dataSourceService;

    /**
     * Retrieves graphmarts from a specified data source.
     *
     * @param datasourceId The identifier of the data source to retrieve graphmarts from.
     * @return A response containing an array of graphmart objects in JSON format. Each object includes fields such as title, IRI, and active status.
     *         If the data source does not exist, a 404 Not Found response is returned with a message indicating that the data source was not found.
     * @throws IOException          If there is an error communicating with the data source.
     * @throws InterruptedException If the request to retrieve graphmarts is interrupted.
     */
    @GET
    @Path("graphmarts/{datasourceId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGraphmartsFromDatasource(@PathParam("datasourceId") String datasourceId)
            throws IOException, InterruptedException {
        if (!this.dataSourceService.dataSourceExists(datasourceId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("DataSource: " + datasourceId + " not found")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        Datasources source = this.dataSourceService.getDataSource(datasourceId);
        AnzoClient client = createClient(source);
        LOG.info("Requested to get graphmarts as {} at Anzo: {}", source.getUsername(), source.getUrl());
        return Response.status(200).type(MediaType.APPLICATION_JSON)
                .entity(parseQueryResponse(client.getGraphmarts(), "graphmartTitle", "gm", "gmActive").toString())
                .build();
    }

    /**
     * Retrieves the layers associated with a specified data source and graphmart URI.
     *
     * @param datasourceId The identifier of the data source to retrieve layers from.
     * @param graphmartUri The URI of the target graphmart for which layers are being retrieved.
     * @return A response containing an array of layer objects in JSON format. Each object includes fields such as title, IRI, and active status.
     *         If the data source does not exist, a 404 Not Found response is returned with a message indicating that the data source was not found.
     * @throws IOException          If there is an error communicating with the data source.
     * @throws InterruptedException If the request to retrieve layers is interrupted.
     */
    @GET
    @Path("layers/{datasourceId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSpecifiedGraphmartLayers(@PathParam("datasourceId") String datasourceId,
                                                @QueryParam("graphmartUri") String graphmartUri) throws IOException, InterruptedException {
        if (!this.dataSourceService.dataSourceExists(datasourceId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("DataSource: " + datasourceId + " not found")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        Datasources source = this.dataSourceService.getDataSource(datasourceId);
        AnzoClient client = createClient(source);
        LOG.info("Requested to get layers as {} at Anzo: {}\n\tfor Graphmart: {}", source.getUsername(),
                source.getUrl(), graphmartUri);
        return Response.status(200).type(MediaType.APPLICATION_JSON)
                .entity(parseQueryResponse(client.getLayersForGraphmart(graphmartUri), "title", "layer", "layerActive")
                        .toString())
                .build();

    }

    /**
     * Parses a QueryResponse object into an array of JSON objects containing graphmart information.
     *
     * @param qResponse The QueryResponse object to parse.
     * @param titleKey   The key in the JSON response for the title field.
     * @param iriKey     The key in the JSON response for the IRI (Uniform Resource Identifier) field.
     * @param activeKey  The key in the JSON response for the active status field.
     * @return A JSONArray containing JSONObjects representing graphmart information.
     */
    private JSONArray parseQueryResponse(QueryResponse qResponse, String titleKey, String iriKey, String activeKey) {
        return parseBindings(qResponse.getHttpResponse().body(), titleKey, iriKey, activeKey);
    }

    /**
     * Converts SPARQL JSON results into [{title, iri, active}]. Reads the whole document with a JsonReader; the old
     * JsonParser#getObject() call without a preceding next() only worked with the lenient Glassfish JSON-P that Karaf
     * shipped, and fails with Parsson (HTTP 500 on the graphmart and layer lookups).
     */
    static JSONArray parseBindings(InputStream body, String titleKey, String iriKey, String activeKey) {
        final JSONArray arr = new JSONArray();
        try (JsonReader reader = Json.createReader(body)) {
            reader.readObject().getJsonObject("results")
                    .getJsonArray("bindings").stream().map(JsonValue::asJsonObject).forEach(obj -> {
                        JSONObject jsObj = new JSONObject();
                        jsObj.put("title", obj.getJsonObject(titleKey).getString(VALUE_KEY));
                        jsObj.put("iri", obj.getJsonObject(iriKey).getString(VALUE_KEY));
                        jsObj.put("active", obj.getJsonObject(activeKey).getString(VALUE_KEY));
                        arr.put(jsObj);
                    });
        }
        return arr;
    }

    /**
     * Creates a new instance of AnzoClient from a datasource, respecting its certificate validation setting.
     *
     * @param source The datasource to create a client for.
     * @return A new instance of SimpleAnzoClient configured from the datasource.
     */
    private AnzoClient createClient(Datasources source) {
        return new SimpleAnzoClient(source.getUrl(), source.getUsername(), source.getPassword(),
                60, source.isValidateCertificate());
    }
}

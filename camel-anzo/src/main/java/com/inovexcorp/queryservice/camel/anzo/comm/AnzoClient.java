package com.inovexcorp.queryservice.camel.anzo.comm;

import lombok.Getter;

import java.io.IOException;

public interface AnzoClient {

    @Getter
    enum RESPONSE_FORMAT {
        RDFXML("application/rdf+xml"), JSON("application/json"), CSV("text/csv"), XML("application/xml"),
        TRIG("application/x-trig"), TTL("application/x-turtle"), NTRIPLES("nt"),
        NQUADS("text/x-nquads"), TRIX("application/trix");

        RESPONSE_FORMAT(String key) {
            this.key = key;
        }

        private final String key;
    }

    /**
     * Query Anzo in the context of a graphmart.
     *
     * @param query          The SPARQL query to run.
     * @param graphmartUri   The URI of the target graphmart.
     * @param format         The desired response format.
     * @param timeoutSeconds The number of seconds to wait for a result.
     * @return The {@link QueryResponse} object with associated metadata.
     * @throws QueryException       If there was an issue executing the query.
     * @throws IOException          If there was a communication issue with the Anzo server.
     * @throws InterruptedException If there was an issue sending the request.
     */
    QueryResponse queryGraphmart(String query, String graphmartUri, String layerUris, RESPONSE_FORMAT format,
                                 int timeoutSeconds, boolean skipCache)
            throws QueryException, IOException, InterruptedException;

    QueryResponse getGraphmarts() throws QueryException, IOException, InterruptedException;

    QueryResponse getLayersForGraphmart(String graphmart) throws IOException, InterruptedException;
}

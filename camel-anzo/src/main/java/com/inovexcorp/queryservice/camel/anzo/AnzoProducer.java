package com.inovexcorp.queryservice.camel.anzo;

import com.inovexcorp.queryservice.camel.anzo.comm.AnzoClient;
import com.inovexcorp.queryservice.camel.anzo.comm.QueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;

import java.nio.charset.StandardCharsets;

@Slf4j
public class AnzoProducer extends DefaultProducer {

    private final AnzoEndpoint endpoint;
    private final AnzoClient anzoClient;

    public AnzoProducer(AnzoEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        log.info("Anzo producer created for endpoint: {}", endpoint.toString());
        this.anzoClient = this.endpoint.getClient();
    }

    public void process(Exchange exchange) throws Exception {
        log.trace("Processing production request for exchange: {}", exchange.getExchangeId());
        final String query = getQuery(exchange);
        final long start = System.currentTimeMillis();
        // Use the AnzoClient implementation to query the graphmart.
        QueryResponse response;
        // If optional layers specified in header, utilize them over pre-set layers during route creation to utilize anzoclient to query graphmart
        if (exchange.getIn().getHeader("qtt-layers") != null) {
            response = anzoClient.queryGraphmart(query, endpoint.getGraphmartUri(), exchange.getIn().getHeader("qtt-layers").toString(),
                    AnzoClient.RESPONSE_FORMAT.RDFXML, endpoint.getTimeoutSeconds(), endpoint.isSkipCache());
        } else {
            response = anzoClient.queryGraphmart(query, endpoint.getGraphmartUri(), endpoint.getLayerUris(),
                    AnzoClient.RESPONSE_FORMAT.RDFXML, endpoint.getTimeoutSeconds(), endpoint.isSkipCache());
        }
        // Log the response data.
        log.info("Anzo query for exchange '{}' took {}ms", exchange, response.getQueryDuration());
        if (log.isDebugEnabled()) {
            log.debug("Anzo Query Response Headers:\n\t{}", response.getHeaders());
        }
        // Add the query to the response headers if less than a configured length.
        attachQueryToOutHeaders(query, exchange);
        // Add query duration to response headers.
        exchange.getMessage().setHeader(AnzoHeaders.ANZO_QUERY_DURATION, response.getQueryDuration());
        if (endpoint.getGraphmartUri() != null) {
            exchange.getMessage().setHeader(AnzoHeaders.ANZO_GM, endpoint.getGraphmartUri());
        }
        exchange.getMessage().setBody(response.getResult());
    }

    private String getQuery(Exchange exchange) {
        //TODO - is this supposed to be configurable?
        return exchange.getIn().getBody(String.class);
    }

    private void attachQueryToOutHeaders(String query, Exchange exchange) {
        int queryLength = query.getBytes(StandardCharsets.UTF_8).length;
        if (queryLength < endpoint.getMaxQueryHeaderLength()) {
            exchange.getMessage().setHeader(AnzoHeaders.ANZO_QUERY, query);
        } else {
            exchange.getMessage().setHeader(AnzoHeaders.ANZO_QUERY, queryLength + " exceeds maximum size of "
                    + endpoint.getMaxQueryHeaderLength() + " bytes -- See query-service logs.");
        }
    }
}

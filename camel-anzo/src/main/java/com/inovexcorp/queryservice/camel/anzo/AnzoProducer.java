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
    private final AnzoClient defaultClient;

    public AnzoProducer(AnzoEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        log.info("Anzo producer created for endpoint: {}", endpoint.toString());
        // Create default client with basic auth for non-bearer requests
        this.defaultClient = this.endpoint.getClient();
    }

    public void process(Exchange exchange) throws Exception {
        log.trace("Processing production request for exchange: {}", exchange.getExchangeId());
        final String query = getQuery(exchange);
        final long start = System.currentTimeMillis();

        // Determine which client to use - bearer token or default basic auth
        AnzoClient client = resolveClient(exchange);

        // Use the AnzoClient implementation to query the graphmart.
        QueryResponse response;
        // If optional layers specified in header, utilize them over pre-set layers during route creation to utilize anzoclient to query graphmart
        if (exchange.getIn().getHeader("qtt-layers") != null) {
            response = client.queryGraphmart(query, endpoint.getGraphmartUri(), exchange.getIn().getHeader("qtt-layers").toString(),
                    AnzoClient.RESPONSE_FORMAT.RDFXML, endpoint.getTimeoutSeconds(), endpoint.isSkipCache());
        } else {
            response = client.queryGraphmart(query, endpoint.getGraphmartUri(), endpoint.getLayerUris(),
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

    /**
     * Resolves the appropriate AnzoClient based on bearer token presence.
     * If bearer auth is enabled and a token is present in the exchange headers,
     * creates a new client with bearer token auth. Otherwise, uses the default client.
     */
    private AnzoClient resolveClient(Exchange exchange) {
        // Check if bearer token is present in the exchange headers
        String bearerToken = exchange.getIn().getHeader(AnzoEndpoint.BEARER_TOKEN_HEADER, String.class);

        if (bearerToken != null && !bearerToken.isBlank()) {
            log.debug("Using bearer token authentication for exchange: {}", exchange.getExchangeId());
            return endpoint.getClientWithBearerToken(bearerToken);
        }

        // Fall back to default client with basic auth
        log.trace("Using default basic auth client for exchange: {}", exchange.getExchangeId());
        return defaultClient;
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

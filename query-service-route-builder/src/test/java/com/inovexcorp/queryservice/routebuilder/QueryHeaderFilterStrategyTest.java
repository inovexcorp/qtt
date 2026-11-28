package com.inovexcorp.queryservice.routebuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryHeaderFilterStrategyTest {

    private final QueryHeaderFilterStrategy normal = new QueryHeaderFilterStrategy();
    private final QueryHeaderFilterStrategy debug = new QueryDebugHeaderFilterStrategy();

    @ParameterizedTest
    @ValueSource(strings = {"Authorization", "authorization", "AUTHORIZATION", "Proxy-Authorization", "Cookie",
            "Forwarded", "X-Forwarded-For", "x-forwarded-host", "X-Forwarded-Proto", "CamelHttpResponseCode",
            "camelHttpUri"})
    void credentialsProxyAndCamelHeadersNeverReachTemplates(String header) {
        assertTrue(normal.applyFilterToExternalHeaders(header, "x", null), header);
        assertTrue(debug.applyFilterToExternalHeaders(header, "x", null), header);
    }

    @ParameterizedTest
    @ValueSource(strings = {"label", "limit", "X-Request-Id", "Accept", "Content-Type", "body.name", "X-Api-Key"})
    void otherRequestHeadersAndParametersReachTemplates(String header) {
        assertFalse(normal.applyFilterToExternalHeaders(header, "x", null), header);
    }

    @ParameterizedTest
    @ValueSource(strings = {"label", "X-Api-Key", "Authorization", "Cookie", "X-Request-Id", "anzo.query",
            "anzo.query_duration", "anzo.graphmart", "Set-Cookie", "CamelHttpUri"})
    void requestHeadersAndAnzoMetadataAreNotSentBack(String header) {
        assertTrue(normal.applyFilterToCamelHeaders(header, "x", null), header);
    }

    @Test
    void allowIsSentBack() {
        assertFalse(normal.applyFilterToCamelHeaders("Allow", "GET", null));
        assertFalse(debug.applyFilterToCamelHeaders("Allow", "GET", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"anzo.query", "anzo.query_duration", "anzo.graphmart", "ANZO.QUERY"})
    void debugModeSendsBackTheAnzoMetadata(String header) {
        assertFalse(debug.applyFilterToCamelHeaders(header, "x", null), header);
    }

    @ParameterizedTest
    @ValueSource(strings = {"label", "X-Api-Key", "Authorization", "Cookie"})
    void debugModeStillEchoesNoRequestHeaders(String header) {
        assertTrue(debug.applyFilterToCamelHeaders(header, "x", null), header);
    }

    @Test
    void bothStrategiesCanBeCreatedByCamelFromTheirClassName() throws Exception {
        // The endpoint URI uses #class:, which needs a public no-argument constructor.
        QueryHeaderFilterStrategy.class.getConstructor().newInstance();
        QueryDebugHeaderFilterStrategy.class.getConstructor().newInstance();
    }
}

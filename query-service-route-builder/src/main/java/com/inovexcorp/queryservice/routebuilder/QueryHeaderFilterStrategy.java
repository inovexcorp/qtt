package com.inovexcorp.queryservice.routebuilder;

import com.inovexcorp.queryservice.camel.anzo.AnzoHeaders;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultHeaderFilterStrategy;

import java.util.Locale;
import java.util.Set;

/**
 * Decides which HTTP headers cross the boundary of a query route ({@code /query/<routeId>}).
 * <ul>
 *   <li><b>Inbound</b> (request headers and query parameters becoming Camel headers, which templates read as
 *       {@code headers.*}): credentials and proxy headers are dropped, so a template can never read a caller's token,
 *       cookie or API key and put it into the SPARQL sent to Anzo. Headers starting with {@code Camel} are dropped
 *       too, so callers can't set Camel's own control headers.</li>
 *   <li><b>Outbound</b> (Camel headers becoming response headers): only an allow list leaves, so request headers are
 *       never echoed back. {@code Content-Type} is written by platform-http itself. The Anzo metadata headers
 *       ({@code anzo.query}, {@code anzo.query_duration}, {@code anzo.graphmart}) leave only in
 *       debug mode ({@link QueryDebugHeaderFilterStrategy}), because {@code anzo.query} discloses the generated SPARQL.</li>
 * </ul>
 * Referenced from the endpoint URI with {@code #class:}, so it needs a public no-argument constructor.
 */
public class QueryHeaderFilterStrategy extends DefaultHeaderFilterStrategy {

    /** Request headers a template must never see (lower case). */
    static final Set<String> CREDENTIAL_HEADERS = Set.of("authorization", "proxy-authorization", "cookie", "forwarded");

    /** Prefix of the proxy headers that are dropped inbound (X-Forwarded-For, -Host, -Proto, ...). */
    static final String FORWARDED_PREFIX = "x-forwarded-";

    /** Response headers a route may set (lower case); {@code Allow} accompanies a 405. */
    static final Set<String> RESPONSE_HEADERS = Set.of("allow");

    /** Anzo metadata headers, returned only in debug mode (lower case). */
    static final Set<String> DEBUG_HEADERS = Set.of(AnzoHeaders.ANZO_QUERY.toLowerCase(Locale.ROOT),
            AnzoHeaders.ANZO_QUERY_DURATION.toLowerCase(Locale.ROOT), AnzoHeaders.ANZO_GM.toLowerCase(Locale.ROOT));

    private final boolean debugHeaders;

    public QueryHeaderFilterStrategy() {
        this(false);
    }

    protected QueryHeaderFilterStrategy(boolean debugHeaders) {
        this.debugHeaders = debugHeaders;
        setLowerCase(true);
        setInFilterStartsWith(CAMEL_FILTER_STARTS_WITH);
        setOutFilterStartsWith(CAMEL_FILTER_STARTS_WITH);
    }

    @Override
    public boolean applyFilterToExternalHeaders(String headerName, Object headerValue, Exchange exchange) {
        if (headerName == null) {
            return true;
        }
        String name = headerName.toLowerCase(Locale.ROOT);
        if (CREDENTIAL_HEADERS.contains(name) || name.startsWith(FORWARDED_PREFIX)) {
            return true;
        }
        return super.applyFilterToExternalHeaders(headerName, headerValue, exchange);
    }

    @Override
    public boolean applyFilterToCamelHeaders(String headerName, Object headerValue, Exchange exchange) {
        if (headerName == null) {
            return true;
        }
        String name = headerName.toLowerCase(Locale.ROOT);
        boolean allowed = RESPONSE_HEADERS.contains(name) || (debugHeaders && DEBUG_HEADERS.contains(name));
        return !allowed;
    }
}

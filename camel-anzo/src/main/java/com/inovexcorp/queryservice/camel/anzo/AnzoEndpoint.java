package com.inovexcorp.queryservice.camel.anzo;

import com.inovexcorp.queryservice.camel.anzo.comm.AnzoClient;
import com.inovexcorp.queryservice.camel.anzo.comm.SimpleAnzoClient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * camel-anzo component which does bla bla.
 * <p>
 * TODO: Update one line description above what the component does.
 */

@Data
@EqualsAndHashCode(callSuper = true)
@UriEndpoint(firstVersion = "1.0.0-SNAPSHOT", scheme = "anzo", title = "camel-anzo", syntax = "anzo:server",
        category = {Category.JAVA}, producerOnly = true)
public class AnzoEndpoint extends DefaultEndpoint {

    private static final Base64.Decoder decoder = Base64.getDecoder();

    @Getter
    public enum FORMAT {

        RDF("rdf"), JSON("json");

        FORMAT(String key) {
            this.key = key;
        }

        private final String key;
    }

    /**
     * The server to talk to
     */
    @UriPath(name = "server", description = "The Anzo server to talk to (including the protocol and port numbers")
    @Metadata(required = true)
    private String server;

    @UriParam(name = "graphmartUri", description = "The URI of the graphmart you're querying")
    @Metadata(required = true)
    private String graphmartUri;


    @UriParam(name = "layerUris", description = "Optional - The URIs of layers to query against in the Graphmart")
    @Metadata(required = false)
    private String layerUris;

    @UriParam(name = "user", description = "The user to query against the graphmart with")
    @Metadata(required = true)
    private String user;

    @UriParam(name = "password", description = "The password to authenticate with against the graphmart", secret = true)
    @Metadata(required = true, secret = true)
    private String password;

    @UriParam(name = "timeoutSeconds", description = "The number of seconds prior to timing out", defaultValue = "30")
    @Metadata(required = true)
    private int timeoutSeconds = 30;

    @UriParam(name = "maxQueryHeaderLength",
            description = "The max number of bytes a query can be if it is to be serialized back to the client in a header",
            defaultValue = "8192")
    @Metadata(required = true)
    private long maxQueryHeaderLength = 8L * 1024L;

    @UriParam(name = "responseFormat", description = "The response format for querying the data that Anzo will send back",
            defaultValue = "RDF", javaType = "com.inovexcorp.queryservice.camel.anzo.AnzoEndpoint.FORMAT")
    @Metadata(required = true)
    private FORMAT responseFormat = FORMAT.RDF;

    @UriParam(name = "skipCache", description = "Whether to skip Anzo's cache", defaultValue = "false")
    private boolean skipCache = false;

    @UriParam(name = "validateCert", description = "Whether you want to validate the SSL cert Anzo presents",
            defaultValue = "true")
    private boolean validateCert;

    @UriParam(name = "queryLocation",
            description = "Where the query will be present on the exchange prior to sending to Anzo",
            defaultValue = "${body}")
    @Metadata(description = "The query the component will execute.")
    private String queryLocation = "${body}";

    public AnzoEndpoint(String uri, AnzoComponent component, String server) {
        super(uri, component);
        this.server = server;
    }

    public Producer createProducer() throws Exception {
        return new AnzoProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new IllegalArgumentException("Anzo component only supports the Producers");
    }

    /**
     * @return An {@link AnzoClient} implementation built using the configuration from this endpoint.
     */
    public AnzoClient getClient() {
        return new SimpleAnzoClient(getServer(), decode(getUser()), decode(getPassword()), getTimeoutSeconds(), isValidateCert());
    }

    private static String decode(String value) {
        return new String(decoder.decode(value), StandardCharsets.UTF_8);
    }
}

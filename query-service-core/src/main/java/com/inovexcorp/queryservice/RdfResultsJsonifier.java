package com.inovexcorp.queryservice;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.JSONLDMode;
import org.eclipse.rdf4j.rio.helpers.JSONLDSettings;
import io.quarkus.arc.Unremovable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.config.OsgiConfig;
import jakarta.inject.Named;

import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Camel {@link Processor} that will take the RDF on a camel exchange and convert it into JSON-LD.
 */
@Slf4j
@ApplicationScoped
@Unremovable
@Named(RdfResultsJsonifier.BEAN_REFERENCE)
public class RdfResultsJsonifier implements Processor {

    public static final String BEAN_REFERENCE = "RdfResultsJsonifier";

    private String baseUri;

    private JSONLDMode jsonldMode;

    private boolean jsonldOptimize;

    private boolean jsonldNativeTypes;

    private boolean jsonldCompactArrays;

    @Override
    public void process(Exchange exchange) throws Exception {
        long size = -1;
        final long start = System.currentTimeMillis();
        try (final Writer writer = new StringWriter(); final InputStream data = exchange.getMessage().getBody(InputStream.class)) {
            final Model model = Rio.parse(data, this.baseUri, RDFFormat.RDFXML);
            size = model.size();
            Rio.write(model, jsonLdWriter(writer));
            exchange.getMessage().setBody(writer.toString());
            if (log.isTraceEnabled()) {
                log.trace("Resulted in JSONLD serialization to:---\n{}\n----", writer);
            }
        } finally {
            final long duration = System.currentTimeMillis() - start;
            log.debug("Exchange '{}' contains {} statements in result, and took {} ms to serialize", exchange.getExchangeId(), size, duration);
        }
    }

    private RDFWriter jsonLdWriter(Writer writer) {
        RDFWriter rdfWriter = Rio.createWriter(RDFFormat.JSONLD, writer);
        rdfWriter.getWriterConfig()
                // Use configured mode.
                .set(JSONLDSettings.JSONLD_MODE, jsonldMode)
                // Use configured optimize setting.
                .set(JSONLDSettings.OPTIMIZE, jsonldOptimize)
                // Use configured native types setting.
                .set(JSONLDSettings.USE_NATIVE_TYPES, jsonldNativeTypes)
                // Use configured array compaction setting.
                .set(JSONLDSettings.COMPACT_ARRAYS, jsonldCompactArrays);
        return rdfWriter;
    }

    public static final String CONFIG_PID = "com.inovexcorp.queryservice.jsonldSerializer";

    @PostConstruct
    void init() {
        initialize(OsgiConfig.load(RDFSerializerConfig.class, CONFIG_PID));
    }

    public void initialize(final RDFSerializerConfig config) {
        this.baseUri = config.baseUri();
        this.jsonldMode = JSONLDMode.valueOf(config.jsonLdMode());
        this.jsonldOptimize = config.optimize();
        this.jsonldNativeTypes = config.useNativeTypes();
        this.jsonldCompactArrays = config.compactArrays();
        log.info("Starting RDF Results Serializer bean: {}", config);
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping RDF Results Serializer bean...");
    }
}

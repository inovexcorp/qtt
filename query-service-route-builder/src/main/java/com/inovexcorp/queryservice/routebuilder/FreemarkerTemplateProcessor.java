package com.inovexcorp.queryservice.routebuilder;

import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ExchangeHelper;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

/**
 * Renders a route's FreeMarker template into the SPARQL query (the message body).
 *
 * <p>Replaces the {@code freemarker:file:} endpoint. Camel 4's FreeMarker component re-reads and re-parses a
 * template with a URI scheme on every exchange (Camel 3 cached the parsed template); parsing was a third of the CPU
 * time per query. This parses once per route instance (routes are rebuilt whenever a template changes) and renders
 * with the same data model ({@link ExchangeHelper#createVariableMap}: headers, body, ...) and the same FreeMarker
 * settings the Karaf build used (default incompatible-improvements level, no localized lookup), so templates
 * render exactly as before. A template that fails to parse fails each request, as before, rather than route
 * creation. Template failures are thrown as {@link RouteTemplateException} so the route can report them as such.</p>
 */
public class FreemarkerTemplateProcessor implements Processor {

    private static final Configuration CONFIGURATION = createConfiguration();

    private final String name;
    private final String templateContent;
    private volatile Template template;
    private volatile RouteTemplateException parseError;

    public FreemarkerTemplateProcessor(String name, String templateContent) {
        this.name = name;
        this.templateContent = templateContent != null ? templateContent : "";
    }

    static Configuration createConfiguration() {
        Configuration configuration = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        configuration.setLocalizedLookup(false);
        return configuration;
    }

    Template template() throws Exception {
        Template t = template;
        if (t == null) {
            if (parseError != null) {
                throw parseError;
            }
            try {
                t = new Template(name, new StringReader(templateContent), CONFIGURATION);
            } catch (ParseException e) {
                parseError = RouteTemplateException.syntax(name, e);
                throw parseError;
            }
            template = t;
        }
        return t;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Map<String, Object> dataModel = ExchangeHelper.createVariableMap(exchange, false);
        StringWriter buffer = new StringWriter();
        try {
            template().process(dataModel, buffer);
        } catch (TemplateException e) {
            throw RouteTemplateException.render(e);
        }
        exchange.getMessage().setBody(buffer.toString());
    }
}

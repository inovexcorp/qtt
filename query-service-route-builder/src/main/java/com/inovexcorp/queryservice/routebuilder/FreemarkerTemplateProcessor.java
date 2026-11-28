package com.inovexcorp.queryservice.routebuilder;

import freemarker.core.ParseException;
import freemarker.core.TemplateClassResolver;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
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
 *
 * <p>Both query routes and the template test routes render through this class, so the restrictions in
 * {@link #createConfiguration()} apply everywhere a user's template runs.</p>
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

    /**
     * Templates are written by API users, so they are treated as untrusted (see "Is FreeMarker safe for untrusted
     * templates?" in the FreeMarker FAQ). Everything here is a FreeMarker setting; none of it changes how
     * headers, the JSON body or built-ins such as {@code ?eval_json} render.
     * <ul>
     *   <li>{@code ?new} may not create objects: with the default resolver,
     *       {@code "freemarker.template.utility.Execute"?new()} runs operating-system commands.</li>
     *   <li>{@code ?api} stays off (the default, set explicitly so it cannot be switched on by accident).</li>
     *   <li>Java objects in the data model show their text but expose no methods or properties. HTTP components
     *       can put server objects in the exchange (Netty put its channel context in the request headers), and with
     *       the default exposure a template could call any public method on them, for example closing a socket.</li>
     *   <li>No template loader: {@code #include} and {@code #import} would otherwise read any file under the
     *       working directory (FreeMarker's legacy default loader), which holds the Derby database and config.</li>
     * </ul>
     */
    static Configuration createConfiguration() {
        Configuration configuration = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        configuration.setLocalizedLookup(false);
        configuration.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        configuration.setAPIBuiltinEnabled(false);
        configuration.setTemplateLoader(null);
        DefaultObjectWrapperBuilder wrapper = new DefaultObjectWrapperBuilder(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        wrapper.setExposureLevel(BeansWrapper.EXPOSE_NOTHING);
        configuration.setObjectWrapper(wrapper.build());
        return configuration;
    }

    /**
     * Checks that a template parses, exactly as a route would parse it. A template that passes always parses at
     * query time; what it renders still depends on each request.
     *
     * @param name    the route id (used in the message)
     * @param content the template; null or blank is valid (a route without a template is created stopped)
     * @throws RouteTemplateException with the position of the first syntax error
     */
    public static void checkSyntax(String name, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        try {
            new Template(name, new StringReader(content), CONFIGURATION);
        } catch (ParseException e) {
            throw RouteTemplateException.syntax(name, e);
        } catch (java.io.IOException e) {
            // Reading from a String cannot fail; Template's constructor declares it for other readers.
            throw new java.io.UncheckedIOException(e);
        }
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

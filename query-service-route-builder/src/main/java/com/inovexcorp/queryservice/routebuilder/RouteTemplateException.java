package com.inovexcorp.queryservice.routebuilder;

import freemarker.core.InvalidReferenceException;
import freemarker.core.ParseException;
import freemarker.template.TemplateException;

/**
 * A route's FreeMarker template could not produce a query. Such errors used to fall through to the generic error
 * handler and were reported as an unreachable datasource (503 "Backend datasource unavailable", status DOWN).
 *
 * <p>The message is meant for the caller: one line, without the template source that FreeMarker quotes in its own
 * messages.</p>
 */
public class RouteTemplateException extends RuntimeException {

    private final boolean syntaxError;

    private RouteTemplateException(String message, Throwable cause, boolean syntaxError) {
        super(message, cause);
        this.syntaxError = syntaxError;
    }

    /** The template does not parse: the route is misconfigured, whatever the request. */
    static RouteTemplateException syntax(String routeId, ParseException e) {
        return new RouteTemplateException(String.format("The template of route '%s' has a syntax error at line %d, "
                + "column %d: %s", routeId, e.getLineNumber(), e.getColumnNumber(), firstLine(e.getEditorMessage())),
                e, true);
    }

    /** The template parsed but failed for this request, usually because a value it needs was not supplied. */
    static RouteTemplateException render(TemplateException e) {
        String message = e instanceof InvalidReferenceException && e.getBlamedExpressionString() != null
                ? "The query needs a value that the request did not provide: " + e.getBlamedExpressionString()
                : "The template could not be rendered for this request: " + firstLine(e.getMessageWithoutStackTop());
        return new RouteTemplateException(message, e, false);
    }

    /** True for a template that does not parse (a route error), false for a failure of one request. */
    public boolean isSyntaxError() {
        return syntaxError;
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        String line = text.strip();
        int end = line.indexOf('\n');
        return end < 0 ? line : line.substring(0, end).strip();
    }
}

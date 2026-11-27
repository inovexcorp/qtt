package com.inovexcorp.queryservice.routebuilder;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FreemarkerTemplateProcessorTest {

    private final CamelContext context = new DefaultCamelContext();

    @AfterEach
    void close() throws Exception {
        context.close();
    }

    private Exchange exchange(String body) {
        Exchange e = new DefaultExchange(context);
        e.getIn().setBody(body);
        return e;
    }

    @Test
    void rendersHeadersAndJsonBody() throws Exception {
        FreemarkerTemplateProcessor p = new FreemarkerTemplateProcessor("r",
                "<#assign b = body?eval_json>SELECT ${headers.label} ${b.limit} <#if headers.missing??>x<#else>none</#if>");
        Exchange e = exchange("{\"limit\": 5}");
        e.getIn().setHeader("label", "café 日本");
        p.process(e);
        assertEquals("SELECT café 日本 5 none", e.getMessage().getBody(String.class));
    }

    @Test
    void parsesTheTemplateOnlyOnce() throws Exception {
        FreemarkerTemplateProcessor p = new FreemarkerTemplateProcessor("r", "${headers.a}");
        Exchange first = exchange(null);
        first.getIn().setHeader("a", "1");
        p.process(first);
        Object parsed = p.template();
        Exchange second = exchange(null);
        second.getIn().setHeader("a", "2");
        p.process(second);
        assertSame(parsed, p.template());
        assertEquals("2", second.getMessage().getBody(String.class));
    }

    @Test
    void brokenTemplateFailsEachRequest() {
        FreemarkerTemplateProcessor p = new FreemarkerTemplateProcessor("r", "SELECT ${headers.unclosed WHERE {}");
        RouteTemplateException first = assertThrows(RouteTemplateException.class, () -> p.process(exchange(null)));
        RouteTemplateException second = assertThrows(RouteTemplateException.class, () -> p.process(exchange(null)));
        assertTrue(first.isSyntaxError());
        assertSame(first, second, "the parse failure is kept, not re-parsed per request");
        assertTrue(first.getMessage().startsWith("The template of route 'r' has a syntax error at line 1, column"),
                first.getMessage());
        assertFalse(first.getMessage().contains("\n"));
    }

    @Test
    void missingVariableFailsTheRequest() {
        FreemarkerTemplateProcessor p = new FreemarkerTemplateProcessor("r", "SELECT ${headers.nosuch}");
        RouteTemplateException e = assertThrows(RouteTemplateException.class, () -> p.process(exchange(null)));
        assertFalse(e.isSyntaxError());
        assertEquals("The query needs a value that the request did not provide: headers.nosuch", e.getMessage());
    }

    @Test
    void otherRenderErrorsAreReportedOnOneLine() {
        FreemarkerTemplateProcessor p = new FreemarkerTemplateProcessor("r", "<#assign b = body?eval_json>${b.x}");
        RouteTemplateException e = assertThrows(RouteTemplateException.class, () -> p.process(exchange("{not json")));
        assertFalse(e.isSyntaxError());
        assertTrue(e.getMessage().startsWith("The template could not be rendered for this request: "), e.getMessage());
        assertFalse(e.getMessage().contains("\n"), e.getMessage());
    }

    @Test
    void emptyOrNullTemplateRendersEmpty() throws Exception {
        Exchange e = exchange("ignored");
        new FreemarkerTemplateProcessor("r", null).process(e);
        assertEquals("", e.getMessage().getBody(String.class));
    }

    // ------------------------------------------------------------ templates are untrusted

    private static RouteTemplateException renderFails(String template, Exchange e) {
        RouteTemplateException failure = assertThrows(RouteTemplateException.class,
                () -> new FreemarkerTemplateProcessor("r", template).process(e));
        assertFalse(failure.isSyntaxError(), "these templates parse; they fail when rendered");
        return failure;
    }

    /** With FreeMarker's default resolver, {@code ?new} can create {@code Execute}, which runs OS commands. */
    @Test
    void newBuiltinCannotCreateObjects(@TempDir Path dir) {
        Path marker = dir.resolve("command-ran");
        String touch = "touch " + marker;
        for (String template : List.of(
                "<#assign ex = \"freemarker.template.utility.Execute\"?new()>${ex(\"" + touch + "\")}",
                "${\"freemarker.template.utility.ObjectConstructor\"?new()(\"java.io.File\", \"x\")}",
                // ?eval and ?interpret use the same configuration
                "${'\"freemarker.template.utility.Execute\"?new()(\"" + touch + "\")'?eval}",
                "<@r'<#assign ex = \"freemarker.template.utility.Execute\"?new()>${ex(\"" + touch + "\")}'?interpret />")) {
            RouteTemplateException e = renderFails(template, exchange(null));
            assertTrue(e.getMessage().contains("not allowed in the template for security reasons"), e.getMessage());
        }
        assertFalse(Files.exists(marker), "the command must not have run");
    }

    @Test
    void apiBuiltinIsOff() {
        Exchange e = exchange(null);
        e.getIn().setHeader("list", new ArrayList<>(List.of("a", "b")));
        assertTrue(renderFails("${headers.list?api.size()}", e).getMessage().contains("api_builtin_enabled"));
    }

    /**
     * Netty puts its channel context in the request headers. Objects other than strings, numbers, booleans, dates,
     * lists and maps print as text, as before, but expose no methods or properties.
     */
    @Test
    void javaObjectsPrintAsTextButExposeNoMembers() throws Exception {
        Exchange e = exchange(null);
        e.getIn().setHeader("file", new File("data.txt"));
        e.getIn().setHeader("values", List.of("x", "y"));
        e.getIn().setHeader("port", 8888);

        new FreemarkerTemplateProcessor("r", "${headers.file} ${headers.values?join(',')} ${headers.port}").process(e);
        assertEquals("data.txt x,y 8,888", e.getMessage().getBody(String.class));

        for (String template : List.of("${headers.file.getName()}", "${headers.file.name}",
                "${headers.file.getClass()}", "${headers.file.delete()?c}")) {
            Exchange withFile = exchange(null);
            withFile.getIn().setHeader("file", new File("data.txt"));
            renderFails(template, withFile);
        }
    }

    /** FreeMarker's legacy default loader would read any file under the working directory. */
    @Test
    void templatesCannotIncludeFiles() {
        assertTrue(Files.exists(Path.of("pom.xml")), "tests run in the module directory");
        for (String template : List.of("<#include \"/pom.xml\" parse=false>", "<#import \"/pom.xml\" as p>",
                "<#include \"pom.xml\" parse=false>")) {
            renderFails(template, exchange(null));
        }
    }

    /** Same FreeMarker settings as the Karaf build (Camel 3.20): default incompatible-improvements level. */
    @Test
    void usesKarafCompatibleFreemarkerSettings() throws Exception {
        assertEquals(freemarker.template.Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS,
                FreemarkerTemplateProcessor.createConfiguration().getIncompatibleImprovements());
        Exchange e = exchange(null);
        new FreemarkerTemplateProcessor("r", "${1234567}").process(e);
        assertEquals("1,234,567", e.getMessage().getBody(String.class));
    }
}

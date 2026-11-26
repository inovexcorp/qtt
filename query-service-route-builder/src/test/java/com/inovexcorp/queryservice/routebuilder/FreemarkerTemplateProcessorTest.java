package com.inovexcorp.queryservice.routebuilder;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
        assertThrows(Exception.class, () -> p.process(exchange(null)));
        assertThrows(Exception.class, () -> p.process(exchange(null)));
    }

    @Test
    void missingVariableFailsTheRequest() {
        FreemarkerTemplateProcessor p = new FreemarkerTemplateProcessor("r", "SELECT ${headers.nosuch}");
        assertThrows(Exception.class, () -> p.process(exchange(null)));
    }

    @Test
    void emptyOrNullTemplateRendersEmpty() throws Exception {
        Exchange e = exchange("ignored");
        new FreemarkerTemplateProcessor("r", null).process(e);
        assertEquals("", e.getMessage().getBody(String.class));
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

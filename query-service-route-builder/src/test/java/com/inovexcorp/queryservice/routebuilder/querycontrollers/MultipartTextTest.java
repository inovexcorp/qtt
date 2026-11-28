package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.jboss.resteasy.reactive.server.multipart.FileItem;
import org.jboss.resteasy.reactive.server.multipart.FormValue;
import org.jboss.resteasy.reactive.server.multipart.MultipartFormDataInput;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Template text from the UI's Blob parts must survive regardless of the JVM's default charset. */
class MultipartTextTest {

    private static final String TEXT = "# 100% + & é ü 日本 \"q\" <t>";

    private static FormValue filePart(byte[] bytes, String contentType) throws Exception {
        FormValue v = mock(FormValue.class);
        FileItem item = mock(FileItem.class);
        when(v.isFileItem()).thenReturn(true);
        when(v.getFileItem()).thenReturn(item);
        when(item.getInputStream()).thenReturn(new ByteArrayInputStream(bytes));
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        if (contentType != null) {
            headers.add("Content-Type", contentType);
        }
        when(v.getHeaders()).thenReturn(headers);
        // A charset reported from the platform default (ASCII on hosts without a UTF-8 locale) must be ignored.
        when(v.getCharset()).thenReturn("US-ASCII");
        return v;
    }

    private static MultipartFormDataInput form(Map<String, Collection<FormValue>> values) {
        return () -> values;
    }

    @Test
    void blobPartWithoutCharsetIsUtf8() throws Exception {
        assertEquals(TEXT, MultipartText.read(filePart(TEXT.getBytes(StandardCharsets.UTF_8), "multipart/form-data")));
        assertEquals(TEXT, MultipartText.read(filePart(TEXT.getBytes(StandardCharsets.UTF_8), null)));
    }

    @Test
    void declaredCharsetIsHonoured() throws Exception {
        String latin = "café";
        assertEquals(latin, MultipartText.read(filePart(latin.getBytes(StandardCharsets.ISO_8859_1),
                "text/plain; charset=ISO-8859-1")));
    }

    @Test
    void plainFieldUsesItsDecodedValue() throws Exception {
        FormValue v = mock(FormValue.class);
        when(v.isFileItem()).thenReturn(false);
        when(v.getValue()).thenReturn(TEXT);
        assertEquals(TEXT, MultipartText.read(v));
    }

    @Test
    void readsNamedFieldsFromTheForm() throws Exception {
        FormValue layers = mock(FormValue.class);
        when(layers.isFileItem()).thenReturn(false);
        when(layers.getValue()).thenReturn("urn:a,urn:b");
        MultipartFormDataInput input = form(Map.of(
                "freemarker", List.of(filePart(TEXT.getBytes(StandardCharsets.UTF_8), "multipart/form-data")),
                "layers", List.of(layers)));
        assertEquals(TEXT, MultipartText.field(input, "freemarker"));
        assertEquals("urn:a,urn:b", MultipartText.field(input, "layers"));
        assertNull(MultipartText.field(input, "missing"));
        assertNull(MultipartText.field(null, "freemarker"));
    }
}

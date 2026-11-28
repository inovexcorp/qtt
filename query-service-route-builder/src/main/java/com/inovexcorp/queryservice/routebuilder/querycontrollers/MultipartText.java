package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import org.jboss.resteasy.reactive.server.multipart.FormValue;
import org.jboss.resteasy.reactive.server.multipart.MultipartFormDataInput;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Reads multipart form fields as text. The UI sends the template and layers as Blob parts (with a file name); those
 * are decoded as UTF-8 unless the part's own Content-Type declares a charset. Decoding must not depend on the
 * platform default charset: on a host without a UTF-8 locale it is ASCII, and non-ASCII template text was
 * corrupted. Plain (non-file) fields are already decoded by the server.
 */
final class MultipartText {

    private MultipartText() {
    }

    static String field(MultipartFormDataInput form, String name) throws IOException {
        if (form == null || form.getValues() == null) {
            return null;
        }
        Collection<FormValue> values = form.getValues().get(name);
        return values == null || values.isEmpty() ? null : read(values.iterator().next());
    }

    static String read(FormValue value) throws IOException {
        if (value == null) {
            return null;
        }
        if (!value.isFileItem()) {
            return value.getValue();
        }
        try (InputStream in = value.getFileItem().getInputStream()) {
            return new String(in.readAllBytes(), declaredCharset(value));
        }
    }

    /** The charset parameter of the part's own Content-Type header, else UTF-8. */
    static Charset declaredCharset(FormValue value) {
        String contentType = value.getHeaders() != null ? value.getHeaders().getFirst("Content-Type") : null;
        if (contentType != null) {
            for (String param : contentType.split(";")) {
                String p = param.trim();
                if (p.regionMatches(true, 0, "charset=", 0, 8)) {
                    String name = p.substring(8).replace("\"", "").trim();
                    if (Charset.isSupported(name)) {
                        return Charset.forName(name);
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}

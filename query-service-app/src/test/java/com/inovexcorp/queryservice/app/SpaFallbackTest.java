package com.inovexcorp.queryservice.app;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaFallbackTest {

    private static final Pattern P = Pattern.compile(SpaFallback.UI_PATH_WITHOUT_EXTENSION);

    @Test
    void reroutesUiPaths() {
        assertTrue(P.matcher("/datasources").matches());
        assertTrue(P.matcher("/routes/config-route/abc").matches());
        assertTrue(P.matcher("/metrics/").matches());
    }

    @Test
    void leavesFilesApiAndRootAlone() {
        assertFalse(P.matcher("/").matches());
        assertFalse(P.matcher("/main.js").matches());
        assertFalse(P.matcher("/assets/monaco/min/vs/loader.js").matches());
        assertFalse(P.matcher("/queryrest").matches());
        assertFalse(P.matcher("/queryrest/api/routes").matches());
        assertFalse(P.matcher("/q/health").matches());
    }

    @Test
    void leavesQueryRoutesAlone() {
        // A query route the fallback caught would answer 200 with the UI's index.html instead of data.
        assertFalse(P.matcher("/query").matches());
        assertFalse(P.matcher("/query/").matches());
        assertFalse(P.matcher("/query/people-search").matches());
        assertFalse(P.matcher("/query/people-search/").matches());
        assertTrue(P.matcher("/queryish").matches());
    }
}

package com.inovexcorp.queryservice.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GET /api/datasources/{id}/health serializes health records with a plain Jackson ObjectMapper (no java.time
 * module). It used to fail on checkTime, producing truncated JSON, and would have embedded the parent
 * datasource (including its password) in every record.
 */
class DatasourceHealthRecordJsonTest {

    private final ObjectMapper plainMapper = new ObjectMapper();

    private DatasourceHealthRecord record() {
        Datasources ds = new Datasources();
        ds.setDataSourceId("ds1");
        ds.setPassword("secret-password");
        DatasourceHealthRecord r = new DatasourceHealthRecord(DatasourceStatus.UP, 42L, null, ds);
        r.setId(7L);
        r.setCheckTime(LocalDateTime.of(2026, 11, 26, 15, 4, 5, 123_000_000));
        return r;
    }

    @Test
    void serializesWithPlainObjectMapper() throws Exception {
        JsonNode json = plainMapper.readTree(plainMapper.writeValueAsString(record()));
        assertEquals("UP", json.get("status").asText());
        assertEquals(42, json.get("responseTimeMs").asLong());
        assertEquals("2026-11-26T15:04:05.123", json.get("checkTime").asText());
    }

    @Test
    void nullCheckTimeSerializesAsNull() throws Exception {
        DatasourceHealthRecord r = record();
        r.setCheckTime(null);
        JsonNode json = plainMapper.readTree(plainMapper.writeValueAsString(r));
        assertTrue(json.get("checkTime").isNull());
    }

    @Test
    void doesNotEmbedDatasourceOrPassword() throws Exception {
        String json = plainMapper.writeValueAsString(record());
        assertFalse(json.contains("secret-password"), json);
        assertFalse(json.contains("\"datasource\""), json);
    }
}

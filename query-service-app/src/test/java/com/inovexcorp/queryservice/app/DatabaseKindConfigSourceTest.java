package com.inovexcorp.queryservice.app;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseKindConfigSourceTest {

    @Test
    void defaultsToDerby() {
        assertEquals("derby", DatabaseKindConfigSource.kindOf(null, null, null));
        assertEquals("derby", DatabaseKindConfigSource.kindOf(null, "jdbc:derby:data/database;create=true", "derby"));
    }

    @Test
    void detectsFromJdbcUrl() {
        assertEquals("postgresql", DatabaseKindConfigSource.kindOf(null, "jdbc:postgresql://db:5432/qtt", null));
        assertEquals("mssql", DatabaseKindConfigSource.kindOf(null,
                "jdbc:sqlserver://db:1433;databaseName=qtt;encrypt=true", null));
    }

    @Test
    void fallsBackToLegacyDriverName() {
        assertEquals("postgresql", DatabaseKindConfigSource.kindOf(null, null, "PostgreSQL JDBC Driver"));
        assertEquals("mssql", DatabaseKindConfigSource.kindOf(null, null, "Microsoft JDBC Driver for SQL Server"));
    }

    @Test
    void explicitKindWins() {
        assertEquals("mssql", DatabaseKindConfigSource.kindOf("MSSQL", "jdbc:postgresql://x/y", null));
        assertThrows(IllegalArgumentException.class, () -> DatabaseKindConfigSource.kindOf("oracle", null, null));
    }

    @Test
    void rejectsUnsupportedUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseKindConfigSource.kindOf(null, "jdbc:mysql://db/qtt", null));
    }

    @Test
    void activatesExactlyOnePersistenceUnit() {
        DatabaseKindConfigSource source = new DatabaseKindConfigSource(Map.of("DB_URL", "jdbc:postgresql://db/qtt"));
        assertEquals("postgresql", source.getValue("qtt.db.kind"));
        assertEquals("true", source.getValue("quarkus.datasource.postgresql.active"));
        assertEquals("true", source.getValue("quarkus.hibernate-orm.postgresql.active"));
        assertEquals("false", source.getValue("quarkus.datasource.derby.active"));
        assertEquals("false", source.getValue("quarkus.hibernate-orm.mssql.active"));
    }
}

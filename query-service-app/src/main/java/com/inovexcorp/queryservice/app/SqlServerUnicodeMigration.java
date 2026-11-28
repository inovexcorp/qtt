package com.inovexcorp.queryservice.app;

import com.inovexcorp.queryservice.persistence.jpa.JpaTemplate;
import com.inovexcorp.queryservice.persistence.jpa.TransactionType;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * SQL Server only: databases created by QTT 1.x store route templates in a varchar(max) column, which replaces
 * non-Latin characters with '?'. The column is now @Nationalized (new databases get nvarchar(max)); this converts
 * the column of an existing database once, before routes are loaded. Schema "update" never
 * changes the type of an existing column, so this has to be explicit.
 */
@Slf4j
@ApplicationScoped
public class SqlServerUnicodeMigration {

    static final String FIND_VARCHAR_COLUMN = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'routes' AND COLUMN_NAME = 'templateContent' AND DATA_TYPE = 'varchar'";
    static final String ALTER_COLUMN = "ALTER TABLE routes ALTER COLUMN templateContent NVARCHAR(MAX)";

    @Inject
    JpaTemplate jpa;

    @ConfigProperty(name = "qtt.db.kind", defaultValue = "derby")
    String dbKind;

    // Runs before CamelKarafComponent loads routes (default priority 2500).
    void onStart(@Observes @Priority(1000) StartupEvent event) {
        if (!"mssql".equals(dbKind)) {
            return;
        }
        migrate();
    }

    void migrate() {
        Number varcharColumns = jpa.txExpr(TransactionType.Required,
                em -> (Number) em.createNativeQuery(FIND_VARCHAR_COLUMN).getSingleResult());
        if (varcharColumns.intValue() > 0) {
            log.warn("Converting routes.templateContent from varchar(max) to nvarchar(max) so templates keep "
                    + "non-Latin characters. Text already stored with '?' replacements cannot be recovered.");
            jpa.tx(TransactionType.Required, em -> em.createNativeQuery(ALTER_COLUMN).executeUpdate());
        }
    }
}

package com.inovexcorp.queryservice.persistence.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.Datasources;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditListenerTest {

    private final AuditListener listener = new AuditListener();

    @Test
    void outsideAnAuditedCallNothingIsRecorded() {
        Datasources ds = new Datasources();
        listener.onCreate(ds);
        listener.onUpdate(ds);
        assertThat(ds.getAudit()).isNull();
    }

    @Test
    void creationRecordsCreatorAndModifier() throws Exception {
        CamelRouteTemplate route = new CamelRouteTemplate();
        AuditContext.runAs("alice", () -> {
            listener.onCreate(route);
            return null;
        });
        assertThat(route.getAudit().getCreatedBy()).isEqualTo("alice");
        assertThat(route.getAudit().getModifiedBy()).isEqualTo("alice");
        assertThat(route.getAudit().getCreatedAt()).isNotNull();
        assertThat(route.getAudit().getModifiedAt()).isEqualTo(route.getAudit().getCreatedAt());
    }

    @Test
    void updatesKeepTheCreator() throws Exception {
        CamelRouteTemplate route = new CamelRouteTemplate();
        AuditContext.runAs("alice", () -> {
            listener.onCreate(route);
            return null;
        });
        AuditContext.runAs("bob", () -> {
            listener.onUpdate(route);
            return null;
        });
        assertThat(route.getAudit().getCreatedBy()).isEqualTo("alice");
        assertThat(route.getAudit().getModifiedBy()).isEqualTo("bob");
    }

    @Test
    void reAddingAnExistingEntityKeepsItsCreator() throws Exception {
        CamelRouteTemplate route = new CamelRouteTemplate();
        AuditContext.runAs("alice", () -> {
            listener.onCreate(route);
            return null;
        });
        AuditContext.runAs("bob", () -> {
            listener.onCreate(route); // e.g. routes re-added when their datasource changes
            return null;
        });
        assertThat(route.getAudit().getCreatedBy()).isEqualTo("alice");
        assertThat(route.getAudit().getModifiedBy()).isEqualTo("bob");
    }

    @Test
    void nestedCallsRestoreTheOuterActor() throws Exception {
        AuditContext.runAs("outer", () -> {
            AuditContext.runAs("inner", () -> {
                assertThat(AuditContext.actor()).contains("inner");
                return null;
            });
            assertThat(AuditContext.actor()).contains("outer");
            return null;
        });
        assertThat(AuditContext.actor()).isEmpty();
    }

    @Test
    void clientsCannotSetAuditFields() throws Exception {
        Datasources ds = new ObjectMapper().readValue(
                "{\"dataSourceId\":\"x\",\"audit\":{\"createdBy\":\"mallory\"}}", Datasources.class);
        assertThat(ds.getAudit()).isNull();
    }
}

package com.inovexcorp.queryservice.persistence.audit;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.util.Date;

/**
 * Stamps {@link AuditInfo} on entities saved inside an {@link Audited} call. Outside one (no actor) it does
 * nothing, so health checks and background jobs don't count as someone's change.
 */
public class AuditListener {

    /** Implemented by audited entities. */
    public interface Auditable {
        AuditInfo getAudit();

        void setAudit(AuditInfo audit);
    }

    @PrePersist
    void onCreate(Object entity) {
        AuditContext.actor().ifPresent(actor -> stamp((Auditable) entity, actor, true));
    }

    @PreUpdate
    void onUpdate(Object entity) {
        AuditContext.actor().ifPresent(actor -> stamp((Auditable) entity, actor, false));
    }

    static void stamp(Auditable entity, String actor, boolean created) {
        AuditInfo audit = entity.getAudit() == null ? new AuditInfo() : entity.getAudit();
        Date now = new Date();
        if (created && audit.getCreatedBy() == null) {
            audit.setCreatedBy(actor);
            audit.setCreatedAt(now);
        }
        audit.setModifiedBy(actor);
        audit.setModifiedAt(now);
        entity.setAudit(audit);
    }
}

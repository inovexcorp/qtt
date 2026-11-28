package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.persistence.audit.AuditContext;
import com.inovexcorp.queryservice.persistence.audit.Audited;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/** Runs {@link Audited} methods as the signed-in user ("anonymous" with sign-in off), for AuditListener. */
@Audited
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class AuditInterceptor {

    private final SecurityIdentity identity;

    AuditInterceptor(SecurityIdentity identity) {
        this.identity = identity;
    }

    @AroundInvoke
    Object audit(InvocationContext invocation) throws Exception {
        String actor = identity.isAnonymous() || identity.getPrincipal() == null
                ? AuthDisabledMechanism.ANONYMOUS : identity.getPrincipal().getName();
        return AuditContext.runAs(actor, invocation::proceed);
    }
}

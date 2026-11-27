package com.inovexcorp.queryservice.persistence.audit;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * The user on whose behalf the current thread is changing configuration, set for the duration of an
 * {@link Audited} method. Empty everywhere else, which {@link AuditListener} reads as "not a user's change".
 */
public final class AuditContext {

    private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();

    private AuditContext() {
    }

    /** The current actor, if inside an audited call. */
    public static Optional<String> actor() {
        return Optional.ofNullable(ACTOR.get());
    }

    /** Runs work as the given actor, restoring whatever was set before (calls may nest). */
    public static <T> T runAs(String actor, Callable<T> work) throws Exception {
        String previous = ACTOR.get();
        ACTOR.set(actor);
        try {
            return work.call();
        } finally {
            if (previous == null) {
                ACTOR.remove();
            } else {
                ACTOR.set(previous);
            }
        }
    }
}

package com.inovexcorp.queryservice.persistence.jpa;

import io.quarkus.arc.Arc;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * {@link JpaTemplate} backed by the Quarkus persistence unit for the configured database. One persistence unit is
 * built per supported database (derby, postgresql, mssql) because Hibernate's dialect is fixed at build time; only the
 * one named by {@code qtt.db.kind} is active at runtime (see DatabaseKindConfigSource).
 */
@ApplicationScoped
public class QuarkusJpaTemplate implements JpaTemplate {

    private final String persistenceUnit;
    private volatile EntityManager entityManager;

    public QuarkusJpaTemplate(@ConfigProperty(name = "qtt.db.kind", defaultValue = "derby") String persistenceUnit) {
        this.persistenceUnit = persistenceUnit;
    }

    private EntityManager em() {
        EntityManager em = entityManager;
        if (em == null) {
            // A client proxy bound to the current transaction; safe to cache.
            em = Arc.container().select(EntityManager.class,
                    new PersistenceUnit.PersistenceUnitLiteral(persistenceUnit)).get();
            entityManager = em;
        }
        return em;
    }

    @Override
    public void tx(TransactionType type, EmConsumer code) {
        QuarkusTransaction.joiningExisting().run(() -> code.accept(em()));
    }

    @Override
    public <R> R txExpr(TransactionType type, EmFunction<R> code) {
        return QuarkusTransaction.joiningExisting().call(() -> code.apply(em()));
    }
}

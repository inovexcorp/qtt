package com.inovexcorp.queryservice.persistence.impl;

import com.inovexcorp.queryservice.persistence.ApiKey;
import com.inovexcorp.queryservice.persistence.ApiKeyService;
import com.inovexcorp.queryservice.persistence.jpa.JpaTemplate;
import com.inovexcorp.queryservice.persistence.jpa.TransactionType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Date;
import java.util.List;

@ApplicationScoped
public class ApiKeyServiceImpl implements ApiKeyService {

    @Inject
    JpaTemplate jpa;

    @Override
    public void add(ApiKey key) {
        jpa.tx(TransactionType.Required, em -> {
            em.persist(key);
            em.flush();
        });
    }

    @Override
    public ApiKey get(String id) {
        return jpa.txExpr(TransactionType.Supports, em -> em.find(ApiKey.class, id));
    }

    @Override
    public List<ApiKey> getAll() {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery("select k from ApiKey k order by k.createdAt desc", ApiKey.class).getResultList());
    }

    @Override
    public boolean revoke(String id, String revokedBy, Date when) {
        return jpa.txExpr(TransactionType.Required, em -> {
            ApiKey key = em.find(ApiKey.class, id);
            if (key == null) {
                return false;
            }
            if (key.getRevokedAt() == null) {
                key.setRevokedAt(when);
                key.setRevokedBy(revokedBy);
                em.flush();
            }
            return true;
        });
    }

    @Override
    public void touch(String id, Date when) {
        jpa.tx(TransactionType.Required, em ->
                em.createQuery("update ApiKey k set k.lastUsedAt = :when where k.id = :id")
                        .setParameter("when", when).setParameter("id", id).executeUpdate());
    }
}

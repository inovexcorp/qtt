package com.inovexcorp.queryservice.persistence.jpa;

import jakarta.persistence.EntityManager;

@FunctionalInterface
public interface EmConsumer {
    void accept(EntityManager em);
}

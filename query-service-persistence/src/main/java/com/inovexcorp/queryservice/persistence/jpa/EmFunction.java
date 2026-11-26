package com.inovexcorp.queryservice.persistence.jpa;

import jakarta.persistence.EntityManager;

@FunctionalInterface
public interface EmFunction<R> {
    R apply(EntityManager em);
}

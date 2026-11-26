package com.inovexcorp.queryservice.persistence.jpa;

/**
 * Runs work against the QTT persistence unit inside a transaction. Mirrors the Aries JPA template API the services
 * were written against, so service code and its unit tests are unchanged apart from imports.
 */
public interface JpaTemplate {

    void tx(TransactionType type, EmConsumer code);

    <R> R txExpr(TransactionType type, EmFunction<R> code);
}

package com.inovexcorp.queryservice.persistence.jpa;

/** Transaction propagation for {@link JpaTemplate} (same names as the Aries JPA template it replaces). */
public enum TransactionType {
    /** Join the current transaction or start a new one. */
    Required,
    /** Join the current transaction if there is one; otherwise run in a new one (reads need an open session). */
    Supports
}

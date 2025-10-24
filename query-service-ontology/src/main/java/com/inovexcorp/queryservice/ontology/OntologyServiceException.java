package com.inovexcorp.queryservice.ontology;

/**
 * Exception thrown when ontology service operations fail.
 */
public class OntologyServiceException extends Exception {

    public OntologyServiceException(String message) {
        super(message);
    }

    public OntologyServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

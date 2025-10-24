package com.inovexcorp.queryservice.sparqi;

/**
 * Exception thrown by SPARQi service operations.
 */
public class SparqiException extends Exception {

    /**
     * Constructs a new SparqiException with a specified message.
     *
     * @param message The detail message explaining the reason for the exception
     */
    public SparqiException(String message) {
        super(message);
    }

    /**
     * Constructs a new SparqiException with a specified message and cause.
     *
     * @param message The detail message explaining the reason for the exception
     * @param cause   The underlying cause of the exception
     */
    public SparqiException(String message, Throwable cause) {
        super(message, cause);
    }
}

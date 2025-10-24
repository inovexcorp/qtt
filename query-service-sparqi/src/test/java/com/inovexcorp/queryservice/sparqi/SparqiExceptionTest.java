package com.inovexcorp.queryservice.sparqi;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for SparqiException.
 */
public class SparqiExceptionTest {

    @Test
    public void testExceptionWithMessage() {
        String message = "Test error message";
        SparqiException exception = new SparqiException(message);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    public void testExceptionWithMessageAndCause() {
        String message = "Test error message";
        Throwable cause = new RuntimeException("Root cause");
        SparqiException exception = new SparqiException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    public void testExceptionPropagation() {
        try {
            throw new SparqiException("Propagation test");
        } catch (SparqiException e) {
            assertEquals("Propagation test", e.getMessage());
        }
    }
}

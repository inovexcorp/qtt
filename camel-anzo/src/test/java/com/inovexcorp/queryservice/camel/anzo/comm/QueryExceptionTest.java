package com.inovexcorp.queryservice.camel.anzo.comm;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryExceptionTest {

    @Test
    void shouldCreateExceptionWithMessage() {
        // Arrange
        String errorMessage = "Query execution failed";

        // Act
        QueryException exception = new QueryException(errorMessage);

        // Assert
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo(errorMessage);
    }

    @Test
    void shouldCreateExceptionWithMessageAndCause() {
        // Arrange
        String errorMessage = "Query execution failed";
        Throwable cause = new RuntimeException("Network error");

        // Act
        QueryException exception = new QueryException(errorMessage, cause);

        // Assert
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo(errorMessage);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getCause().getMessage()).isEqualTo("Network error");
    }

    @Test
    void shouldBeInstanceOfIOException() {
        // Arrange
        QueryException exception = new QueryException("Test error");

        // Assert
        assertThat(exception).isInstanceOf(IOException.class);
    }

    @Test
    void shouldBeThrowable() {
        // Act & Assert
        assertThatThrownBy(() -> {
            throw new QueryException("Test error");
        }).isInstanceOf(QueryException.class)
          .hasMessage("Test error");
    }

    @Test
    void shouldHandleEmptyMessage() {
        // Act
        QueryException exception = new QueryException("");

        // Assert
        assertThat(exception.getMessage()).isEmpty();
    }

    @Test
    void shouldHandleNullMessage() {
        // Act
        QueryException exception = new QueryException(null);

        // Assert
        assertThat(exception.getMessage()).isNull();
    }

    @Test
    void shouldHandleNullCause() {
        // Act
        QueryException exception = new QueryException("Error message", null);

        // Assert
        assertThat(exception.getMessage()).isEqualTo("Error message");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void shouldHandleMultilineMessage() {
        // Arrange
        String multilineMessage = "Query execution failed\nReason: Network timeout\nServer: localhost:8080";

        // Act
        QueryException exception = new QueryException(multilineMessage);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(multilineMessage);
    }

    @Test
    void shouldHandleMessageWithSpecialCharacters() {
        // Arrange
        String specialMessage = "Query failed: <SELECT * WHERE { ?s ?p ?o }> @ server:8080";

        // Act
        QueryException exception = new QueryException(specialMessage);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(specialMessage);
    }

    @Test
    void shouldHandleMessageWithUnicodeCharacters() {
        // Arrange
        String unicodeMessage = "Quéry fäiled with ërror";

        // Act
        QueryException exception = new QueryException(unicodeMessage);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(unicodeMessage);
    }

    @Test
    void shouldHandleVeryLongMessage() {
        // Arrange
        String longMessage = "Error: ".repeat(1000);

        // Act
        QueryException exception = new QueryException(longMessage);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(longMessage);
    }

    @Test
    void shouldHandleNestedExceptions() {
        // Arrange
        Throwable rootCause = new IOException("Connection refused");
        Throwable intermediateCause = new RuntimeException("Failed to connect", rootCause);
        String errorMessage = "Query execution failed";

        // Act
        QueryException exception = new QueryException(errorMessage, intermediateCause);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(errorMessage);
        assertThat(exception.getCause()).isEqualTo(intermediateCause);
        assertThat(exception.getCause().getCause()).isEqualTo(rootCause);
    }

    @Test
    void shouldHandleDifferentCauseTypes() {
        // Arrange
        String errorMessage = "Query failed";
        IOException ioCause = new IOException("Network error");

        // Act
        QueryException exception = new QueryException(errorMessage, ioCause);

        // Assert
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    void shouldHandleRuntimeExceptionAsCause() {
        // Arrange
        String errorMessage = "Query failed";
        RuntimeException runtimeCause = new RuntimeException("Unexpected error");

        // Act
        QueryException exception = new QueryException(errorMessage, runtimeCause);

        // Assert
        assertThat(exception.getCause()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldHandleErrorAsCause() {
        // Arrange
        String errorMessage = "Query failed";
        Error errorCause = new OutOfMemoryError("Heap space");

        // Act
        QueryException exception = new QueryException(errorMessage, errorCause);

        // Assert
        assertThat(exception.getCause()).isInstanceOf(Error.class);
    }

    @Test
    void shouldMaintainStackTrace() {
        // Act
        QueryException exception = new QueryException("Test error");

        // Assert
        assertThat(exception.getStackTrace()).isNotEmpty();
        assertThat(exception.getStackTrace()[0].getClassName()).contains("QueryExceptionTest");
    }

    @Test
    void shouldBeSerializable() {
        // QueryException extends IOException which is Serializable
        QueryException exception = new QueryException("Test error");
        assertThat(exception).isInstanceOf(java.io.Serializable.class);
    }

    @Test
    void shouldHandleHttpErrorStatusMessage() {
        // Arrange
        String errorMessage = "Issue making query request to Anzo (500): Internal Server Error";

        // Act
        QueryException exception = new QueryException(errorMessage);

        // Assert
        assertThat(exception.getMessage()).contains("500");
        assertThat(exception.getMessage()).contains("Internal Server Error");
    }

    @Test
    void shouldHandleTimeoutErrorMessage() {
        // Arrange
        String errorMessage = "Query timed out after 30 seconds";

        // Act
        QueryException exception = new QueryException(errorMessage);

        // Assert
        assertThat(exception.getMessage()).contains("timed out");
        assertThat(exception.getMessage()).contains("30 seconds");
    }

    @Test
    void shouldHandleAuthenticationErrorMessage() {
        // Arrange
        String errorMessage = "Authentication failed: Invalid credentials";

        // Act
        QueryException exception = new QueryException(errorMessage);

        // Assert
        assertThat(exception.getMessage()).contains("Authentication failed");
    }

    @Test
    void shouldHandleQuerySyntaxErrorMessage() {
        // Arrange
        String errorMessage = "SPARQL syntax error at line 5: Unexpected token 'WHERE'";

        // Act
        QueryException exception = new QueryException(errorMessage);

        // Assert
        assertThat(exception.getMessage()).contains("SPARQL syntax error");
        assertThat(exception.getMessage()).contains("line 5");
    }

    @Test
    void shouldHandleJsonErrorPayload() {
        // Arrange
        String jsonError = "{\"error\": \"Query execution failed\", \"code\": 500}";

        // Act
        QueryException exception = new QueryException(jsonError);

        // Assert
        assertThat(exception.getMessage()).contains("error");
        assertThat(exception.getMessage()).contains("500");
    }

    @Test
    void shouldAllowCatchingAsIOException() {
        // Act & Assert
        assertThatThrownBy(() -> {
            throw new QueryException("Test error");
        }).isInstanceOf(IOException.class);
    }

    @Test
    void shouldAllowCatchingAsQueryException() {
        // Act & Assert
        assertThatThrownBy(() -> {
            throw new QueryException("Test error");
        }).isInstanceOf(QueryException.class);
    }
}

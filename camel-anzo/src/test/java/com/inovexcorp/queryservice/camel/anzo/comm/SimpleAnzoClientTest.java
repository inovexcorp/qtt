package com.inovexcorp.queryservice.camel.anzo.comm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class SimpleAnzoClientTest {

    private static final String TEST_SERVER = "http://localhost:8080";
    private static final String TEST_USER = "testuser";
    private static final String TEST_PASSWORD = "testpassword";
    private static final int TEST_TIMEOUT = 30;

    @Test
    void shouldCreateClientWithValidCertificateValidation() {
        // Act
        SimpleAnzoClient client = new SimpleAnzoClient(TEST_SERVER, TEST_USER, TEST_PASSWORD, TEST_TIMEOUT, true);

        // Assert
        assertThat(client).isNotNull();
    }

    @Test
    void shouldCreateClientWithoutCertificateValidation() {
        // Act
        SimpleAnzoClient client = new SimpleAnzoClient(TEST_SERVER, TEST_USER, TEST_PASSWORD, TEST_TIMEOUT, false);

        // Assert
        assertThat(client).isNotNull();
    }

    @Test
    void shouldCreateClientWithDefaultValidateCertificate() {
        // Act
        SimpleAnzoClient client = new SimpleAnzoClient(TEST_SERVER, TEST_USER, TEST_PASSWORD, TEST_TIMEOUT);

        // Assert
        assertThat(client).isNotNull();
    }

    @Test
    void shouldEncodeUrlForDatasetAttribute() {
        // Arrange
        String datasetUri = "http://example.com/dataset";

        // Act
        String encoded = SimpleAnzoClient.urlEncodeDsAttribute(datasetUri);

        // Assert
        assertThat(encoded).startsWith("&named-dataset-uri=");
        assertThat(encoded).contains("http");
        assertThat(encoded).contains("example.com");
    }

    @Test
    void shouldEncodeUrlForDatasetAttributeWithSpecialCharacters() {
        // Arrange
        String datasetUri = "http://example.com/dataset?param=value&other=test";

        // Act
        String encoded = SimpleAnzoClient.urlEncodeDsAttribute(datasetUri);

        // Assert
        assertThat(encoded).startsWith("&named-dataset-uri=");
        assertThat(encoded).contains("%");
    }

    @Test
    void shouldHandleEmptyDatasetUri() {
        // Arrange
        String datasetUri = "";

        // Act
        String encoded = SimpleAnzoClient.urlEncodeDsAttribute(datasetUri);

        // Assert
        assertThat(encoded).isEqualTo("&named-dataset-uri=");
    }

    @Test
    void shouldHandleNullServerGracefully() {
        // Act & Assert - null server will cause issues when trying to use the client
        // The client may be created but will fail on actual use
        SimpleAnzoClient client = new SimpleAnzoClient(null, TEST_USER, TEST_PASSWORD, TEST_TIMEOUT);
        assertThat(client).isNotNull();
    }

    @Test
    void shouldHandleNullUserGracefully() {
        // This should create the client but may fail during authentication
        SimpleAnzoClient client = new SimpleAnzoClient(TEST_SERVER, null, TEST_PASSWORD, TEST_TIMEOUT);
        assertThat(client).isNotNull();
    }

    @Test
    void shouldHandleNullPasswordGracefully() {
        // This should create the client but may fail during authentication
        SimpleAnzoClient client = new SimpleAnzoClient(TEST_SERVER, TEST_USER, null, TEST_TIMEOUT);
        assertThat(client).isNotNull();
    }

    @Test
    void shouldHandleZeroTimeout() {
        // Act & Assert - Zero timeout may not be valid for HttpClient
        // HttpClient.Builder requires positive timeout values
        assertThatThrownBy(() ->
            new SimpleAnzoClient(TEST_SERVER, TEST_USER, TEST_PASSWORD, 0, true)
        ).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldHandleNegativeTimeout() {
        // Act & Assert - HttpClient throws RuntimeException for negative timeout
        assertThatThrownBy(() ->
            new SimpleAnzoClient(TEST_SERVER, TEST_USER, TEST_PASSWORD, -1, true)
        ).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldCreateClientWithLargeTimeout() {
        // Act
        SimpleAnzoClient client = new SimpleAnzoClient(TEST_SERVER, TEST_USER, TEST_PASSWORD, 600, true);

        // Assert
        assertThat(client).isNotNull();
    }

    @Test
    void shouldHandleServerUrlWithProtocol() {
        // Act
        SimpleAnzoClient client = new SimpleAnzoClient("https://secure.example.com:8443", TEST_USER, TEST_PASSWORD, TEST_TIMEOUT);

        // Assert
        assertThat(client).isNotNull();
    }

    @Test
    void shouldHandleServerUrlWithoutPort() {
        // Act
        SimpleAnzoClient client = new SimpleAnzoClient("http://example.com", TEST_USER, TEST_PASSWORD, TEST_TIMEOUT);

        // Assert
        assertThat(client).isNotNull();
    }

    @Test
    void shouldHandleUserWithSpecialCharacters() {
        // Act
        SimpleAnzoClient client = new SimpleAnzoClient(TEST_SERVER, "user@example.com", TEST_PASSWORD, TEST_TIMEOUT);

        // Assert
        assertThat(client).isNotNull();
    }

    @Test
    void shouldHandlePasswordWithSpecialCharacters() {
        // Act
        SimpleAnzoClient client = new SimpleAnzoClient(TEST_SERVER, TEST_USER, "p@ssw0rd!#$%^&*()", TEST_TIMEOUT);

        // Assert
        assertThat(client).isNotNull();
    }

    @Test
    void shouldHandleUnicodeInCredentials() {
        // Act
        SimpleAnzoClient client = new SimpleAnzoClient(TEST_SERVER, "tëst_üsér", "pässwörd", TEST_TIMEOUT);

        // Assert
        assertThat(client).isNotNull();
    }

    @Test
    void shouldHandleEmptyUser() {
        // Act
        SimpleAnzoClient client = new SimpleAnzoClient(TEST_SERVER, "", TEST_PASSWORD, TEST_TIMEOUT);

        // Assert
        assertThat(client).isNotNull();
    }

    @Test
    void shouldHandleEmptyPassword() {
        // Act
        SimpleAnzoClient client = new SimpleAnzoClient(TEST_SERVER, TEST_USER, "", TEST_TIMEOUT);

        // Assert
        assertThat(client).isNotNull();
    }

    @Test
    void shouldHandleVeryLongServerUrl() {
        // Act
        String longUrl = "http://very-long-server-name-that-might-be-used-in-some-environments.example.com:8080";
        SimpleAnzoClient client = new SimpleAnzoClient(longUrl, TEST_USER, TEST_PASSWORD, TEST_TIMEOUT);

        // Assert
        assertThat(client).isNotNull();
    }

    @Test
    void shouldHandleMultipleDatasetUris() {
        // Arrange
        String dataset1 = "http://example.com/dataset1";
        String dataset2 = "http://example.com/dataset2";

        // Act
        String encoded1 = SimpleAnzoClient.urlEncodeDsAttribute(dataset1);
        String encoded2 = SimpleAnzoClient.urlEncodeDsAttribute(dataset2);

        // Assert
        assertThat(encoded1).contains("dataset1");
        assertThat(encoded2).contains("dataset2");
    }

    @Test
    void shouldHandleDatasetUriWithFragment() {
        // Arrange
        String datasetUri = "http://example.com/dataset#fragment";

        // Act
        String encoded = SimpleAnzoClient.urlEncodeDsAttribute(datasetUri);

        // Assert
        assertThat(encoded).startsWith("&named-dataset-uri=");
        assertThat(encoded).contains("fragment");
    }

    @Test
    void shouldHandleDatasetUriWithQueryParameters() {
        // Arrange
        String datasetUri = "http://example.com/dataset?param1=value1&param2=value2";

        // Act
        String encoded = SimpleAnzoClient.urlEncodeDsAttribute(datasetUri);

        // Assert
        assertThat(encoded).startsWith("&named-dataset-uri=");
        assertThat(encoded).contains("%3F"); // URL encoded ?
        assertThat(encoded).contains("%26"); // URL encoded &
    }

    @Test
    void shouldHandleServerUrlWithPath() {
        // Act
        SimpleAnzoClient client = new SimpleAnzoClient("http://example.com/anzo", TEST_USER, TEST_PASSWORD, TEST_TIMEOUT);

        // Assert
        assertThat(client).isNotNull();
    }

    @Test
    void shouldCreateMultipleClientsWithDifferentSettings() {
        // Act
        SimpleAnzoClient client1 = new SimpleAnzoClient("http://server1.com", "user1", "pass1", 30, true);
        SimpleAnzoClient client2 = new SimpleAnzoClient("http://server2.com", "user2", "pass2", 60, false);

        // Assert
        assertThat(client1).isNotNull();
        assertThat(client2).isNotNull();
        assertThat(client1).isNotEqualTo(client2);
    }
}

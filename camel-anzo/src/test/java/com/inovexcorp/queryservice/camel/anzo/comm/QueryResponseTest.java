package com.inovexcorp.queryservice.camel.anzo.comm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueryResponseTest {

    @Mock
    private HttpResponse<InputStream> httpResponse;

    @Mock
    private HttpHeaders httpHeaders;

    private static final String TEST_QUERY = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";
    private static final long TEST_DURATION = 150L;

    @Test
    void shouldBuildQueryResponseWithAllFields() {
        // Arrange
        InputStream resultStream = new ByteArrayInputStream("<rdf>test</rdf>".getBytes(StandardCharsets.UTF_8));
        when(httpResponse.body()).thenReturn(resultStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getQuery()).isEqualTo(TEST_QUERY);
        assertThat(response.getQueryDuration()).isEqualTo(TEST_DURATION);
        assertThat(response.getResponse()).isEqualTo(httpResponse);
        assertThat(response.getHttpResponse()).isEqualTo(httpResponse);
        assertThat(response.getHeaders()).isEqualTo(httpHeaders);
        assertThat(response.getResult()).isEqualTo(resultStream);
    }

    @Test
    void shouldReturnCorrectQuery() {
        // Arrange
        InputStream resultStream = new ByteArrayInputStream(new byte[0]);
        when(httpResponse.body()).thenReturn(resultStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response.getQuery()).isEqualTo(TEST_QUERY);
    }

    @Test
    void shouldReturnCorrectQueryDuration() {
        // Arrange
        InputStream resultStream = new ByteArrayInputStream(new byte[0]);
        when(httpResponse.body()).thenReturn(resultStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response.getQueryDuration()).isEqualTo(TEST_DURATION);
    }

    @Test
    void shouldReturnHttpResponse() {
        // Arrange
        InputStream resultStream = new ByteArrayInputStream(new byte[0]);
        when(httpResponse.body()).thenReturn(resultStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response.getHttpResponse()).isEqualTo(httpResponse);
    }

    @Test
    void shouldReturnResultInputStream() {
        // Arrange
        InputStream resultStream = new ByteArrayInputStream("<rdf>test</rdf>".getBytes(StandardCharsets.UTF_8));
        when(httpResponse.body()).thenReturn(resultStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response.getResult()).isEqualTo(resultStream);
    }

    @Test
    void shouldReturnHttpHeaders() {
        // Arrange
        InputStream resultStream = new ByteArrayInputStream(new byte[0]);
        when(httpResponse.body()).thenReturn(resultStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response.getHeaders()).isEqualTo(httpHeaders);
    }

    @Test
    void shouldHandleEmptyQuery() {
        // Arrange
        InputStream resultStream = new ByteArrayInputStream(new byte[0]);
        when(httpResponse.body()).thenReturn(resultStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query("")
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response.getQuery()).isEmpty();
    }

    @Test
    void shouldHandleZeroDuration() {
        // Arrange
        InputStream resultStream = new ByteArrayInputStream(new byte[0]);
        when(httpResponse.body()).thenReturn(resultStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(0L)
                .build();

        // Assert
        assertThat(response.getQueryDuration()).isEqualTo(0L);
    }

    @Test
    void shouldHandleLargeDuration() {
        // Arrange
        long largeDuration = 999999L;
        InputStream resultStream = new ByteArrayInputStream(new byte[0]);
        when(httpResponse.body()).thenReturn(resultStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(largeDuration)
                .build();

        // Assert
        assertThat(response.getQueryDuration()).isEqualTo(largeDuration);
    }

    @Test
    void shouldHandleEmptyResultStream() {
        // Arrange
        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);
        when(httpResponse.body()).thenReturn(emptyStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response.getResult()).isNotNull();
        assertThat(response.getResult()).isEqualTo(emptyStream);
    }

    @Test
    void shouldHandleLargeResultStream() {
        // Arrange
        byte[] largeData = new byte[1024 * 1024]; // 1MB
        InputStream largeStream = new ByteArrayInputStream(largeData);
        when(httpResponse.body()).thenReturn(largeStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response.getResult()).isNotNull();
        assertThat(response.getResult()).isEqualTo(largeStream);
    }

    @Test
    void shouldHandleQueryWithSpecialCharacters() {
        // Arrange
        String specialQuery = "SELECT * WHERE { ?s <http://example.com/prop#1> \"value with 'quotes' & symbols\" }";
        InputStream resultStream = new ByteArrayInputStream(new byte[0]);
        when(httpResponse.body()).thenReturn(resultStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(specialQuery)
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response.getQuery()).isEqualTo(specialQuery);
    }

    @Test
    void shouldHandleQueryWithUnicodeCharacters() {
        // Arrange
        String unicodeQuery = "SELECT * WHERE { ?s ?p \"tëst with ünicöde\" }";
        InputStream resultStream = new ByteArrayInputStream(new byte[0]);
        when(httpResponse.body()).thenReturn(resultStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(unicodeQuery)
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response.getQuery()).isEqualTo(unicodeQuery);
    }

    @Test
    void shouldHandleVeryLongQuery() {
        // Arrange
        String longQuery = "SELECT * WHERE { ?s ?p ?o } ".repeat(1000);
        InputStream resultStream = new ByteArrayInputStream(new byte[0]);
        when(httpResponse.body()).thenReturn(resultStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(longQuery)
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response.getQuery()).isEqualTo(longQuery);
    }

    @Test
    void shouldHandleMultilineQuery() {
        // Arrange
        String multilineQuery = "SELECT * WHERE {\n  ?s ?p ?o .\n  ?s <http://example.com/prop> ?value\n}";
        InputStream resultStream = new ByteArrayInputStream(new byte[0]);
        when(httpResponse.body()).thenReturn(resultStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(multilineQuery)
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response.getQuery()).isEqualTo(multilineQuery);
    }

    @Test
    void shouldMaintainReferenceToOriginalHttpResponse() {
        // Arrange
        InputStream resultStream = new ByteArrayInputStream(new byte[0]);
        when(httpResponse.body()).thenReturn(resultStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response.getResponse()).isSameAs(httpResponse);
        assertThat(response.getHttpResponse()).isSameAs(httpResponse);
    }

    @Test
    void shouldHandleResponseWithJsonContent() {
        // Arrange
        String jsonContent = "{\"results\": {\"bindings\": []}}";
        InputStream jsonStream = new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8));
        when(httpResponse.body()).thenReturn(jsonStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response.getResult()).isNotNull();
        assertThat(response.getResult()).isEqualTo(jsonStream);
    }

    @Test
    void shouldHandleResponseWithXmlContent() {
        // Arrange
        String xmlContent = "<?xml version=\"1.0\"?><sparql><results></results></sparql>";
        InputStream xmlStream = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
        when(httpResponse.body()).thenReturn(xmlStream);
        when(httpResponse.headers()).thenReturn(httpHeaders);

        // Act
        QueryResponse response = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(TEST_DURATION)
                .build();

        // Assert
        assertThat(response.getResult()).isNotNull();
        assertThat(response.getResult()).isEqualTo(xmlStream);
    }
}

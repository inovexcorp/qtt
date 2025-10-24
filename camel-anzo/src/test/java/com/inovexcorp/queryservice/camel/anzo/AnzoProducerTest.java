package com.inovexcorp.queryservice.camel.anzo;

import com.inovexcorp.queryservice.camel.anzo.comm.AnzoClient;
import com.inovexcorp.queryservice.camel.anzo.comm.QueryException;
import com.inovexcorp.queryservice.camel.anzo.comm.QueryResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnzoProducerTest {

    @Mock
    private AnzoEndpoint endpoint;

    @Mock
    private AnzoClient anzoClient;

    @Mock
    private Exchange exchange;

    @Mock
    private Message inMessage;

    @Mock
    private Message outMessage;

    @Mock
    private HttpResponse<InputStream> httpResponse;

    private AnzoProducer producer;

    private static final String TEST_QUERY = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";
    private static final String TEST_GRAPHMART_URI = "http://example.com/graphmart";
    private static final String TEST_LAYER_URIS = "http://layer1.com,http://layer2.com";

    @BeforeEach
    void setUp() {
        when(endpoint.getClient()).thenReturn(anzoClient);
        when(endpoint.getGraphmartUri()).thenReturn(TEST_GRAPHMART_URI);
        when(endpoint.getLayerUris()).thenReturn(TEST_LAYER_URIS);
        when(endpoint.getTimeoutSeconds()).thenReturn(30);
        when(endpoint.isSkipCache()).thenReturn(false);
        when(endpoint.getMaxQueryHeaderLength()).thenReturn(8192L);
        when(endpoint.toString()).thenReturn("anzo://localhost:8080");

        when(exchange.getIn()).thenReturn(inMessage);
        when(exchange.getMessage()).thenReturn(outMessage);
        when(exchange.getExchangeId()).thenReturn("test-exchange-123");
        when(inMessage.getBody(String.class)).thenReturn(TEST_QUERY);

        producer = new AnzoProducer(endpoint);
    }

    @Test
    void shouldProcessExchangeSuccessfully() throws Exception {
        // Arrange
        InputStream resultStream = new ByteArrayInputStream("<rdf>test</rdf>".getBytes(StandardCharsets.UTF_8));
        QueryResponse queryResponse = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(150L)
                .build();

        when(httpResponse.body()).thenReturn(resultStream);
        when(anzoClient.queryGraphmart(
                eq(TEST_QUERY),
                eq(TEST_GRAPHMART_URI),
                eq(TEST_LAYER_URIS),
                eq(AnzoClient.RESPONSE_FORMAT.RDFXML),
                eq(30),
                eq(false)
        )).thenReturn(queryResponse);

        // Act
        producer.process(exchange);

        // Assert
        verify(anzoClient).queryGraphmart(
                TEST_QUERY,
                TEST_GRAPHMART_URI,
                TEST_LAYER_URIS,
                AnzoClient.RESPONSE_FORMAT.RDFXML,
                30,
                false
        );
        verify(outMessage).setBody(resultStream);
        verify(outMessage).setHeader(AnzoHeaders.ANZO_QUERY_DURATION, 150L);
        verify(outMessage).setHeader(AnzoHeaders.ANZO_GM, TEST_GRAPHMART_URI);
        verify(outMessage).setHeader(eq(AnzoHeaders.ANZO_QUERY), eq(TEST_QUERY));
    }

    @Test
    void shouldUseLayersFromHeaderWhenPresent() throws Exception {
        // Arrange
        String headerLayers = "http://header-layer1.com,http://header-layer2.com";
        InputStream resultStream = new ByteArrayInputStream("<rdf>test</rdf>".getBytes(StandardCharsets.UTF_8));
        QueryResponse queryResponse = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(150L)
                .build();

        when(httpResponse.body()).thenReturn(resultStream);
        when(inMessage.getHeader("qtt-layers")).thenReturn(headerLayers);
        when(anzoClient.queryGraphmart(
                anyString(),
                anyString(),
                eq(headerLayers),
                any(),
                anyInt(),
                anyBoolean()
        )).thenReturn(queryResponse);

        // Act
        producer.process(exchange);

        // Assert
        verify(anzoClient).queryGraphmart(
                TEST_QUERY,
                TEST_GRAPHMART_URI,
                headerLayers,
                AnzoClient.RESPONSE_FORMAT.RDFXML,
                30,
                false
        );
    }

    @Test
    void shouldUseDefaultLayersWhenHeaderNotPresent() throws Exception {
        // Arrange
        InputStream resultStream = new ByteArrayInputStream("<rdf>test</rdf>".getBytes(StandardCharsets.UTF_8));
        QueryResponse queryResponse = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(150L)
                .build();

        when(httpResponse.body()).thenReturn(resultStream);
        when(inMessage.getHeader("qtt-layers")).thenReturn(null);
        when(anzoClient.queryGraphmart(
                anyString(),
                anyString(),
                eq(TEST_LAYER_URIS),
                any(),
                anyInt(),
                anyBoolean()
        )).thenReturn(queryResponse);

        // Act
        producer.process(exchange);

        // Assert
        verify(anzoClient).queryGraphmart(
                TEST_QUERY,
                TEST_GRAPHMART_URI,
                TEST_LAYER_URIS,
                AnzoClient.RESPONSE_FORMAT.RDFXML,
                30,
                false
        );
    }

    @Test
    void shouldAttachQueryToHeaderWhenSmallerThanMaxLength() throws Exception {
        // Arrange
        String shortQuery = "SELECT * WHERE { ?s ?p ?o }";
        InputStream resultStream = new ByteArrayInputStream("<rdf>test</rdf>".getBytes(StandardCharsets.UTF_8));
        QueryResponse queryResponse = QueryResponse.builder()
                .query(shortQuery)
                .response(httpResponse)
                .queryDuration(100L)
                .build();

        when(httpResponse.body()).thenReturn(resultStream);
        when(inMessage.getBody(String.class)).thenReturn(shortQuery);
        when(endpoint.getMaxQueryHeaderLength()).thenReturn(8192L);
        when(anzoClient.queryGraphmart(anyString(), anyString(), anyString(), any(), anyInt(), anyBoolean()))
                .thenReturn(queryResponse);

        // Act
        producer.process(exchange);

        // Assert
        verify(outMessage).setHeader(AnzoHeaders.ANZO_QUERY, shortQuery);
    }

    @Test
    void shouldNotAttachQueryToHeaderWhenLargerThanMaxLength() throws Exception {
        // Arrange
        String longQuery = "SELECT * WHERE { ?s ?p ?o }".repeat(500);
        InputStream resultStream = new ByteArrayInputStream("<rdf>test</rdf>".getBytes(StandardCharsets.UTF_8));
        QueryResponse queryResponse = QueryResponse.builder()
                .query(longQuery)
                .response(httpResponse)
                .queryDuration(100L)
                .build();

        when(httpResponse.body()).thenReturn(resultStream);
        when(inMessage.getBody(String.class)).thenReturn(longQuery);
        when(endpoint.getMaxQueryHeaderLength()).thenReturn(100L);
        when(anzoClient.queryGraphmart(anyString(), anyString(), anyString(), any(), anyInt(), anyBoolean()))
                .thenReturn(queryResponse);

        // Act
        producer.process(exchange);

        // Assert
        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(outMessage).setHeader(eq(AnzoHeaders.ANZO_QUERY), headerCaptor.capture());

        String headerValue = headerCaptor.getValue();
        assertThat(headerValue).contains("exceeds maximum size");
        assertThat(headerValue).contains("See query-service logs");
    }

    @Test
    void shouldSetGraphmartHeaderWhenGraphmartUriIsNotNull() throws Exception {
        // Arrange
        InputStream resultStream = new ByteArrayInputStream("<rdf>test</rdf>".getBytes(StandardCharsets.UTF_8));
        QueryResponse queryResponse = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(100L)
                .build();

        when(httpResponse.body()).thenReturn(resultStream);
        when(endpoint.getGraphmartUri()).thenReturn(TEST_GRAPHMART_URI);
        when(anzoClient.queryGraphmart(anyString(), anyString(), anyString(), any(), anyInt(), anyBoolean()))
                .thenReturn(queryResponse);

        // Act
        producer.process(exchange);

        // Assert
        verify(outMessage).setHeader(AnzoHeaders.ANZO_GM, TEST_GRAPHMART_URI);
    }

    @Test
    void shouldNotSetGraphmartHeaderWhenGraphmartUriIsNull() throws Exception {
        // Arrange
        InputStream resultStream = new ByteArrayInputStream("<rdf>test</rdf>".getBytes(StandardCharsets.UTF_8));
        QueryResponse queryResponse = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(100L)
                .build();

        when(httpResponse.body()).thenReturn(resultStream);
        when(endpoint.getGraphmartUri()).thenReturn(null);
        when(anzoClient.queryGraphmart(
                eq(TEST_QUERY),
                isNull(),
                eq(TEST_LAYER_URIS),
                eq(AnzoClient.RESPONSE_FORMAT.RDFXML),
                eq(30),
                eq(false)
        )).thenReturn(queryResponse);

        // Act
        producer.process(exchange);

        // Assert
        verify(outMessage, never()).setHeader(eq(AnzoHeaders.ANZO_GM), anyString());
    }

    @Test
    void shouldPropagateQueryExceptionWhenAnzoClientFails() throws Exception {
        // Arrange
        QueryException queryException = new QueryException("Anzo query failed");
        when(anzoClient.queryGraphmart(anyString(), anyString(), anyString(), any(), anyInt(), anyBoolean()))
                .thenThrow(queryException);

        // Act & Assert
        assertThatThrownBy(() -> producer.process(exchange))
                .isInstanceOf(QueryException.class)
                .hasMessage("Anzo query failed");
    }

    @Test
    void shouldPropagateIOExceptionWhenAnzoClientFails() throws Exception {
        // Arrange
        IOException ioException = new IOException("Network error");
        when(anzoClient.queryGraphmart(anyString(), anyString(), anyString(), any(), anyInt(), anyBoolean()))
                .thenThrow(ioException);

        // Act & Assert
        assertThatThrownBy(() -> producer.process(exchange))
                .isInstanceOf(IOException.class)
                .hasMessage("Network error");
    }

    @Test
    void shouldHandleEmptyQueryResult() throws Exception {
        // Arrange
        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);
        QueryResponse queryResponse = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(50L)
                .build();

        when(httpResponse.body()).thenReturn(emptyStream);
        when(anzoClient.queryGraphmart(anyString(), anyString(), anyString(), any(), anyInt(), anyBoolean()))
                .thenReturn(queryResponse);

        // Act
        producer.process(exchange);

        // Assert
        verify(outMessage).setBody(emptyStream);
        verify(outMessage).setHeader(AnzoHeaders.ANZO_QUERY_DURATION, 50L);
    }

    @Test
    void shouldHandleQueryWithSpecialCharacters() throws Exception {
        // Arrange
        String queryWithSpecialChars = "SELECT * WHERE { ?s <http://example.com/prop#1> \"value with 'quotes'\" }";
        InputStream resultStream = new ByteArrayInputStream("<rdf>test</rdf>".getBytes(StandardCharsets.UTF_8));
        QueryResponse queryResponse = QueryResponse.builder()
                .query(queryWithSpecialChars)
                .response(httpResponse)
                .queryDuration(100L)
                .build();

        when(httpResponse.body()).thenReturn(resultStream);
        when(inMessage.getBody(String.class)).thenReturn(queryWithSpecialChars);
        when(anzoClient.queryGraphmart(anyString(), anyString(), anyString(), any(), anyInt(), anyBoolean()))
                .thenReturn(queryResponse);

        // Act
        producer.process(exchange);

        // Assert
        verify(anzoClient).queryGraphmart(
                queryWithSpecialChars,
                TEST_GRAPHMART_URI,
                TEST_LAYER_URIS,
                AnzoClient.RESPONSE_FORMAT.RDFXML,
                30,
                false
        );
    }

    @Test
    void shouldRespectSkipCacheFlag() throws Exception {
        // Arrange
        InputStream resultStream = new ByteArrayInputStream("<rdf>test</rdf>".getBytes(StandardCharsets.UTF_8));
        QueryResponse queryResponse = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(100L)
                .build();

        when(httpResponse.body()).thenReturn(resultStream);
        when(endpoint.isSkipCache()).thenReturn(true);
        when(anzoClient.queryGraphmart(anyString(), anyString(), anyString(), any(), anyInt(), eq(true)))
                .thenReturn(queryResponse);

        // Act
        producer.process(exchange);

        // Assert
        verify(anzoClient).queryGraphmart(
                TEST_QUERY,
                TEST_GRAPHMART_URI,
                TEST_LAYER_URIS,
                AnzoClient.RESPONSE_FORMAT.RDFXML,
                30,
                true
        );
    }

    @Test
    void shouldUseConfiguredTimeout() throws Exception {
        // Arrange
        InputStream resultStream = new ByteArrayInputStream("<rdf>test</rdf>".getBytes(StandardCharsets.UTF_8));
        QueryResponse queryResponse = QueryResponse.builder()
                .query(TEST_QUERY)
                .response(httpResponse)
                .queryDuration(100L)
                .build();

        when(httpResponse.body()).thenReturn(resultStream);
        when(endpoint.getTimeoutSeconds()).thenReturn(60);
        when(anzoClient.queryGraphmart(anyString(), anyString(), anyString(), any(), eq(60), anyBoolean()))
                .thenReturn(queryResponse);

        // Act
        producer.process(exchange);

        // Assert
        verify(anzoClient).queryGraphmart(
                TEST_QUERY,
                TEST_GRAPHMART_URI,
                TEST_LAYER_URIS,
                AnzoClient.RESPONSE_FORMAT.RDFXML,
                60,
                false
        );
    }
}

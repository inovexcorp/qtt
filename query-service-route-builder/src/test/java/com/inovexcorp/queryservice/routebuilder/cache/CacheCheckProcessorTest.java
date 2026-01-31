package com.inovexcorp.queryservice.routebuilder.cache;

import com.inovexcorp.queryservice.cache.CacheService;
import com.inovexcorp.queryservice.cache.RequestCoalescingService;
import com.inovexcorp.queryservice.cache.RequestCoalescingService.CoalescedResult;
import com.inovexcorp.queryservice.cache.RequestCoalescingService.RegistrationResult;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CacheCheckProcessor with focus on request coalescing behavior.
 */
@ExtendWith(MockitoExtension.class)
class CacheCheckProcessorTest {

    @Mock
    private CacheService cacheService;

    @Mock
    private CamelRouteTemplate routeTemplate;

    @Mock
    private Exchange exchange;

    @Mock
    private Message message;

    @Mock
    private RequestCoalescingService coalescingService;

    private CacheCheckProcessor processor;

    private static final String CACHE_KEY_PREFIX = "qtt:cache:";
    private static final String LAYER_URIS = "http://example.org/layer1";
    private static final String ROUTE_ID = "test-route";
    private static final String GRAPHMART_URI = "http://example.org/graphmart";
    private static final String SPARQL_QUERY = "SELECT * WHERE { ?s ?p ?o }";

    @BeforeEach
    void setUp() {
        processor = new CacheCheckProcessor(cacheService, routeTemplate, CACHE_KEY_PREFIX, LAYER_URIS);
    }

    // ========== Cache Disabled Tests ==========

    @Test
    void process_WhenCacheDisabled_SetsCacheHitFalse() throws Exception {
        // Arrange
        when(routeTemplate.getCacheEnabled()).thenReturn(false);

        // Act
        processor.process(exchange);

        // Assert
        verify(exchange).setProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, false);
        verify(exchange).setProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, false);
        verifyNoInteractions(cacheService);
    }

    @Test
    void process_WhenCacheEnabledNull_SetsCacheHitFalse() throws Exception {
        // Arrange
        when(routeTemplate.getCacheEnabled()).thenReturn(null);

        // Act
        processor.process(exchange);

        // Assert
        verify(exchange).setProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, false);
        verify(exchange).setProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, false);
    }

    // ========== Cache Unavailable Tests ==========

    @Test
    void process_WhenCacheUnavailable_SetsCacheHitFalse() throws Exception {
        // Arrange
        when(routeTemplate.getCacheEnabled()).thenReturn(true);
        when(cacheService.isAvailable()).thenReturn(false);

        // Act
        processor.process(exchange);

        // Assert
        verify(exchange).setProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, false);
        verify(exchange).setProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, false);
    }

    // ========== Cache Hit Tests ==========

    @Test
    void process_WhenCacheHit_SetsBodyAndStopsRoute() throws Exception {
        // Arrange
        String cachedResult = "{\"data\": \"cached\"}";
        setupCacheEnabled();
        when(cacheService.get(anyString())).thenReturn(Optional.of(cachedResult));

        // Act
        processor.process(exchange);

        // Assert
        verify(message).setBody(cachedResult);
        verify(exchange).setProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, true);
        verify(exchange).setProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, false);
        verify(exchange).setProperty(Exchange.ROUTE_STOP, true);
    }

    // ========== Cache Miss with Coalescing Tests ==========

    @Test
    void process_WhenCacheMissAndLeader_ProceedsToBackend() throws Exception {
        // Arrange
        setupCacheEnabled();
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(true);

        CompletableFuture<CoalescedResult> future = new CompletableFuture<>();
        RegistrationResult leaderResult = new RegistrationResult(true, future);
        when(coalescingService.registerRequest(anyString())).thenReturn(leaderResult);

        // Act
        processor.process(exchange);

        // Assert
        verify(exchange).setProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, false);
        verify(exchange).setProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, true);
        verify(exchange, never()).setProperty(eq(Exchange.ROUTE_STOP), anyBoolean());
    }

    @Test
    void process_WhenCacheMissAndFollower_WaitsForLeader() throws Exception {
        // Arrange
        String expectedResult = "{\"data\": \"coalesced\"}";
        setupCacheEnabled();
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(true);

        CompletableFuture<CoalescedResult> future = CompletableFuture.completedFuture(
                CoalescedResult.success(expectedResult));
        RegistrationResult followerResult = new RegistrationResult(false, future);
        when(coalescingService.registerRequest(anyString())).thenReturn(followerResult);
        when(coalescingService.awaitResult(any(RegistrationResult.class)))
                .thenReturn(Optional.of(CoalescedResult.success(expectedResult)));

        // Act
        processor.process(exchange);

        // Assert
        verify(message).setBody(expectedResult);
        verify(exchange).setProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, true);
        verify(exchange).setProperty(CacheCheckProcessor.COALESCED_HIT_PROPERTY, true);
        verify(exchange).setProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, false);
        verify(exchange).setProperty(Exchange.ROUTE_STOP, true);
    }

    @Test
    void process_WhenCoalescingTimesOut_ProceedsToBackend() throws Exception {
        // Arrange
        setupCacheEnabled();
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(true);

        // First call - follower
        CompletableFuture<CoalescedResult> followerFuture = new CompletableFuture<>();
        RegistrationResult followerResult = new RegistrationResult(false, followerFuture);
        // Second call - becomes leader after timeout
        CompletableFuture<CoalescedResult> leaderFuture = new CompletableFuture<>();
        RegistrationResult leaderResult = new RegistrationResult(true, leaderFuture);

        when(coalescingService.registerRequest(anyString()))
                .thenReturn(followerResult)
                .thenReturn(leaderResult);
        when(coalescingService.awaitResult(any(RegistrationResult.class)))
                .thenReturn(Optional.empty()); // Timeout

        // Act
        processor.process(exchange);

        // Assert
        verify(exchange).setProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, false);
        // Should have tried to register twice
        verify(coalescingService, times(2)).registerRequest(anyString());
    }

    @Test
    void process_WhenCoalescingFails_ProceedsToBackend() throws Exception {
        // Arrange
        setupCacheEnabled();
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(true);

        // First call - follower
        CompletableFuture<CoalescedResult> followerFuture = new CompletableFuture<>();
        RegistrationResult followerResult = new RegistrationResult(false, followerFuture);
        // Second call - becomes leader after failure
        CompletableFuture<CoalescedResult> leaderFuture = new CompletableFuture<>();
        RegistrationResult leaderResult = new RegistrationResult(true, leaderFuture);

        when(coalescingService.registerRequest(anyString()))
                .thenReturn(followerResult)
                .thenReturn(leaderResult);
        when(coalescingService.awaitResult(any(RegistrationResult.class)))
                .thenReturn(Optional.of(CoalescedResult.failure("Leader failed")));

        // Act
        processor.process(exchange);

        // Assert
        verify(exchange).setProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, false);
        verify(coalescingService, times(2)).registerRequest(anyString());
    }

    // ========== Coalescing Disabled Tests ==========

    @Test
    void process_WhenCoalescingDisabled_ProceedsNormally() throws Exception {
        // Arrange
        setupCacheEnabled();
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(false);

        // Act
        processor.process(exchange);

        // Assert
        verify(exchange).setProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, false);
        verify(exchange).setProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, false);
        verify(coalescingService, never()).registerRequest(anyString());
    }

    @Test
    void process_WhenCoalescingServiceNull_ProceedsNormally() throws Exception {
        // Arrange
        setupCacheEnabled();
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(cacheService.getCoalescingService()).thenReturn(null);

        // Act
        processor.process(exchange);

        // Assert
        verify(exchange).setProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, false);
        verify(exchange).setProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, false);
    }

    // ========== Error Handling Tests ==========

    @Test
    void process_WhenExceptionOccurs_ContinuesWithFailOpen() throws Exception {
        // Arrange
        setupCacheEnabled();
        when(cacheService.get(anyString())).thenThrow(new RuntimeException("Redis error"));

        // Act
        processor.process(exchange);

        // Assert
        verify(exchange).setProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, false);
        verify(exchange).setProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, false);
        // Route should continue (fail-open behavior)
        verify(exchange, never()).setProperty(eq(Exchange.ROUTE_STOP), eq(true));
    }

    // ========== Cache Key Tests ==========

    @Test
    void process_SetsCacheKeyProperty() throws Exception {
        // Arrange
        setupCacheEnabled();
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(false);

        // Act
        processor.process(exchange);

        // Assert
        verify(exchange).setProperty(eq(CacheCheckProcessor.CACHE_KEY_PROPERTY), anyString());
    }

    // ========== Helper Methods ==========

    private void setupCacheEnabled() {
        when(routeTemplate.getCacheEnabled()).thenReturn(true);
        when(routeTemplate.getRouteId()).thenReturn(ROUTE_ID);
        when(routeTemplate.getGraphMartUri()).thenReturn(GRAPHMART_URI);
        when(cacheService.isAvailable()).thenReturn(true);
        when(exchange.getIn()).thenReturn(message);
        when(message.getBody(String.class)).thenReturn(SPARQL_QUERY);
    }
}

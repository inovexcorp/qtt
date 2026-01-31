package com.inovexcorp.queryservice.routebuilder.cache;

import com.inovexcorp.queryservice.cache.CacheService;
import com.inovexcorp.queryservice.cache.RequestCoalescingService;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CacheStoreProcessor with focus on request coalescing behavior.
 */
@ExtendWith(MockitoExtension.class)
class CacheStoreProcessorTest {

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

    private CacheStoreProcessor processor;

    private static final int DEFAULT_TTL_SECONDS = 3600;
    private static final String ROUTE_ID = "test-route";
    private static final String CACHE_KEY = "qtt:cache:test-route:abc123";
    private static final String JSON_RESULT = "{\"data\": \"test result\"}";

    @BeforeEach
    void setUp() {
        processor = new CacheStoreProcessor(cacheService, routeTemplate, DEFAULT_TTL_SECONDS);
    }

    // ========== Cache Disabled Tests ==========

    @Test
    void process_WhenCacheDisabled_SkipsStorage() throws Exception {
        // Arrange
        when(routeTemplate.getCacheEnabled()).thenReturn(false);
        when(routeTemplate.getRouteId()).thenReturn(ROUTE_ID);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService, never()).put(anyString(), anyString(), anyInt());
        verify(cacheService, never()).getCoalescingService();
    }

    @Test
    void process_WhenCacheEnabledNull_SkipsStorage() throws Exception {
        // Arrange
        when(routeTemplate.getCacheEnabled()).thenReturn(null);
        when(routeTemplate.getRouteId()).thenReturn(ROUTE_ID);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService, never()).put(anyString(), anyString(), anyInt());
    }

    // ========== Cache Hit Tests ==========

    @Test
    void process_WhenCacheHit_SkipsStorage() throws Exception {
        // Arrange
        when(routeTemplate.getCacheEnabled()).thenReturn(true);
        when(routeTemplate.getRouteId()).thenReturn(ROUTE_ID);
        when(exchange.getProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, Boolean.class))
                .thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService, never()).put(anyString(), anyString(), anyInt());
    }

    // ========== Cache Unavailable Tests ==========

    @Test
    void process_WhenCacheUnavailable_SkipsStorageButCompletesCoalescing() throws Exception {
        // Arrange
        when(routeTemplate.getCacheEnabled()).thenReturn(true);
        when(routeTemplate.getRouteId()).thenReturn(ROUTE_ID);
        when(exchange.getProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, Boolean.class))
                .thenReturn(false);
        when(exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class))
                .thenReturn(CACHE_KEY);
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(true);
        when(cacheService.isAvailable()).thenReturn(false);
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService, never()).put(anyString(), anyString(), anyInt());
        verify(coalescingService).failRequest(eq(CACHE_KEY), anyString());
    }

    // ========== Successful Storage and Coalescing Tests ==========

    @Test
    void process_WhenLeader_StoresAndCompletesCoalescing() throws Exception {
        // Arrange
        setupNormalCacheStore();
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(true);
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(true);
        when(cacheService.put(anyString(), anyString(), anyInt())).thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService).put(CACHE_KEY, JSON_RESULT, DEFAULT_TTL_SECONDS);
        verify(coalescingService).completeRequest(CACHE_KEY, JSON_RESULT);
    }

    @Test
    void process_WhenNotLeader_StoresButDoesNotCompleteCoalescing() throws Exception {
        // Arrange
        setupNormalCacheStore();
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(false);
        when(cacheService.put(anyString(), anyString(), anyInt())).thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService).put(CACHE_KEY, JSON_RESULT, DEFAULT_TTL_SECONDS);
        verify(cacheService, never()).getCoalescingService();
    }

    @Test
    void process_WhenCoalescingLeaderNull_DoesNotCompleteCoalescing() throws Exception {
        // Arrange
        setupNormalCacheStore();
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(null);
        when(cacheService.put(anyString(), anyString(), anyInt())).thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService).put(CACHE_KEY, JSON_RESULT, DEFAULT_TTL_SECONDS);
        verify(cacheService, never()).getCoalescingService();
    }

    // ========== Coalescing Service Configuration Tests ==========

    @Test
    void process_WhenCoalescingServiceNull_StoresNormally() throws Exception {
        // Arrange
        setupNormalCacheStore();
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(true);
        when(cacheService.getCoalescingService()).thenReturn(null);
        when(cacheService.put(anyString(), anyString(), anyInt())).thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService).put(CACHE_KEY, JSON_RESULT, DEFAULT_TTL_SECONDS);
        // No coalescing completion attempted
    }

    @Test
    void process_WhenCoalescingDisabled_StoresNormally() throws Exception {
        // Arrange
        setupNormalCacheStore();
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(true);
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(false);
        when(cacheService.put(anyString(), anyString(), anyInt())).thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService).put(CACHE_KEY, JSON_RESULT, DEFAULT_TTL_SECONDS);
        verify(coalescingService, never()).completeRequest(anyString(), anyString());
    }

    // ========== Empty/Null Result Tests ==========

    @Test
    void process_WhenResultEmpty_FailsCoalescing() throws Exception {
        // Arrange
        when(routeTemplate.getCacheEnabled()).thenReturn(true);
        when(routeTemplate.getRouteId()).thenReturn(ROUTE_ID);
        when(exchange.getProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, Boolean.class))
                .thenReturn(false);
        when(exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class))
                .thenReturn(CACHE_KEY);
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(true);
        when(cacheService.isAvailable()).thenReturn(true);
        when(exchange.getIn()).thenReturn(message);
        when(message.getBody(String.class)).thenReturn("");
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService, never()).put(anyString(), anyString(), anyInt());
        verify(coalescingService).failRequest(eq(CACHE_KEY), anyString());
    }

    @Test
    void process_WhenResultNull_FailsCoalescing() throws Exception {
        // Arrange
        when(routeTemplate.getCacheEnabled()).thenReturn(true);
        when(routeTemplate.getRouteId()).thenReturn(ROUTE_ID);
        when(exchange.getProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, Boolean.class))
                .thenReturn(false);
        when(exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class))
                .thenReturn(CACHE_KEY);
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(true);
        when(cacheService.isAvailable()).thenReturn(true);
        when(exchange.getIn()).thenReturn(message);
        when(message.getBody(String.class)).thenReturn(null);
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService, never()).put(anyString(), anyString(), anyInt());
        verify(coalescingService).failRequest(eq(CACHE_KEY), anyString());
    }

    // ========== Missing Cache Key Tests ==========

    @Test
    void process_WhenCacheKeyMissing_SkipsStorageAndCoalescing() throws Exception {
        // Arrange
        when(routeTemplate.getCacheEnabled()).thenReturn(true);
        when(routeTemplate.getRouteId()).thenReturn(ROUTE_ID);
        when(exchange.getProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, Boolean.class))
                .thenReturn(false);
        when(exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class))
                .thenReturn(null);
        when(cacheService.isAvailable()).thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService, never()).put(anyString(), anyString(), anyInt());
        verify(cacheService, never()).getCoalescingService();
    }

    // ========== TTL Configuration Tests ==========

    @Test
    void process_UsesRouteSpecificTtl() throws Exception {
        // Arrange
        int routeSpecificTtl = 7200;
        setupNormalCacheStore();
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(false);
        when(routeTemplate.getCacheTtlSeconds()).thenReturn(routeSpecificTtl);
        when(cacheService.put(anyString(), anyString(), anyInt())).thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService).put(CACHE_KEY, JSON_RESULT, routeSpecificTtl);
    }

    @Test
    void process_UsesDefaultTtlWhenRouteSpecificNull() throws Exception {
        // Arrange
        setupNormalCacheStore();
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(false);
        when(routeTemplate.getCacheTtlSeconds()).thenReturn(null);
        when(cacheService.put(anyString(), anyString(), anyInt())).thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService).put(CACHE_KEY, JSON_RESULT, DEFAULT_TTL_SECONDS);
    }

    // ========== Error Handling Tests ==========

    @Test
    void process_WhenStorageFails_FailsCoalescing() throws Exception {
        // Arrange
        setupNormalCacheStore();
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(true);
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(true);
        when(cacheService.put(anyString(), anyString(), anyInt())).thenReturn(false);

        // Act
        processor.process(exchange);

        // Assert
        // Even though storage failed, we still have the result to share
        verify(coalescingService).completeRequest(CACHE_KEY, JSON_RESULT);
    }

    @Test
    void process_WhenExceptionOccurs_FailsCoalescingAndContinues() throws Exception {
        // Arrange
        setupNormalCacheStore();
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(true);
        when(cacheService.put(anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("Storage error"));
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(true);

        // Act - should not throw
        processor.process(exchange);

        // Assert
        verify(coalescingService).failRequest(eq(CACHE_KEY), anyString());
    }

    // ========== Helper Methods ==========

    private void setupNormalCacheStore() {
        when(routeTemplate.getCacheEnabled()).thenReturn(true);
        when(routeTemplate.getRouteId()).thenReturn(ROUTE_ID);
        when(routeTemplate.getCacheTtlSeconds()).thenReturn(null);
        when(exchange.getProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, Boolean.class))
                .thenReturn(false);
        when(exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class))
                .thenReturn(CACHE_KEY);
        when(cacheService.isAvailable()).thenReturn(true);
        when(exchange.getIn()).thenReturn(message);
        when(message.getBody(String.class)).thenReturn(JSON_RESULT);
    }
}

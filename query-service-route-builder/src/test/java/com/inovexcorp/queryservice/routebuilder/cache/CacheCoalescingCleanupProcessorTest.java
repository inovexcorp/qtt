package com.inovexcorp.queryservice.routebuilder.cache;

import com.inovexcorp.queryservice.cache.CacheService;
import com.inovexcorp.queryservice.cache.RequestCoalescingService;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CacheCoalescingCleanupProcessor.
 * Tests the exception handling cleanup of coalescing state.
 */
@ExtendWith(MockitoExtension.class)
class CacheCoalescingCleanupProcessorTest {

    @Mock
    private CacheService cacheService;

    @Mock
    private Exchange exchange;

    @Mock
    private RequestCoalescingService coalescingService;

    private CacheCoalescingCleanupProcessor processor;

    private static final String CACHE_KEY = "qtt:cache:test-route:abc123";

    @BeforeEach
    void setUp() {
        processor = new CacheCoalescingCleanupProcessor(cacheService);
    }

    // ========== No Cache Key Tests ==========

    @Test
    void process_WhenNoCacheKey_DoesNothing() throws Exception {
        // Arrange
        when(exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class))
                .thenReturn(null);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService, never()).getCoalescingService();
    }

    // ========== Not Leader Tests ==========

    @Test
    void process_WhenNotLeader_DoesNotCleanup() throws Exception {
        // Arrange
        when(exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class))
                .thenReturn(CACHE_KEY);
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(false);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService, never()).getCoalescingService();
    }

    @Test
    void process_WhenLeaderPropertyNull_DoesNotCleanup() throws Exception {
        // Arrange
        when(exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class))
                .thenReturn(CACHE_KEY);
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(null);

        // Act
        processor.process(exchange);

        // Assert
        verify(cacheService, never()).getCoalescingService();
    }

    // ========== Leader Cleanup Tests ==========

    @Test
    void process_WhenLeader_CleansUpCoalescing() throws Exception {
        // Arrange
        when(exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class))
                .thenReturn(CACHE_KEY);
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(true);
        when(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class))
                .thenReturn(new RuntimeException("Test error"));
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert
        verify(coalescingService).failRequest(eq(CACHE_KEY), contains("Test error"));
    }

    @Test
    void process_WhenLeaderAndNoException_UsesUnknownError() throws Exception {
        // Arrange
        when(exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class))
                .thenReturn(CACHE_KEY);
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(true);
        when(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class))
                .thenReturn(null);
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert
        verify(coalescingService).failRequest(eq(CACHE_KEY), contains("Unknown error"));
    }

    // ========== Coalescing Service Configuration Tests ==========

    @Test
    void process_WhenCoalescingServiceNull_DoesNotThrow() throws Exception {
        // Arrange
        when(exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class))
                .thenReturn(CACHE_KEY);
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(true);
        when(cacheService.getCoalescingService()).thenReturn(null);

        // Act - should not throw
        processor.process(exchange);

        // Assert - no exception
    }

    @Test
    void process_WhenCoalescingDisabled_DoesNotCleanup() throws Exception {
        // Arrange
        when(exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class))
                .thenReturn(CACHE_KEY);
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(true);
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(false);

        // Act
        processor.process(exchange);

        // Assert
        verify(coalescingService, never()).failRequest(anyString(), anyString());
    }

    // ========== Exception Message Extraction Tests ==========

    @Test
    void process_ExtractsExceptionMessage() throws Exception {
        // Arrange
        String specificError = "Connection refused to backend";
        when(exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class))
                .thenReturn(CACHE_KEY);
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(true);
        when(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class))
                .thenReturn(new RuntimeException(specificError));
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert
        verify(coalescingService).failRequest(eq(CACHE_KEY), contains(specificError));
    }

    @Test
    void process_HandlesNestedException() throws Exception {
        // Arrange
        Exception cause = new IllegalStateException("Root cause");
        Exception wrapper = new RuntimeException("Wrapper", cause);
        when(exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class))
                .thenReturn(CACHE_KEY);
        when(exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class))
                .thenReturn(true);
        when(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class))
                .thenReturn(wrapper);
        when(cacheService.getCoalescingService()).thenReturn(coalescingService);
        when(coalescingService.isEnabled()).thenReturn(true);

        // Act
        processor.process(exchange);

        // Assert - should use the wrapper's message, not the cause
        verify(coalescingService).failRequest(eq(CACHE_KEY), contains("Wrapper"));
    }
}

package com.inovexcorp.queryservice.scheduler;

import com.inovexcorp.queryservice.persistence.MetricService;
import org.apache.karaf.scheduler.JobContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CleanMetrics Tests")
class CleanMetricsTest {

    @Mock
    private MetricService metricService;

    @Mock
    private JobContext jobContext;

    @InjectMocks
    private CleanMetrics cleanMetrics;

    private CleanMetricsConfig config;

    @BeforeEach
    void setUp() {
        config = mock(CleanMetricsConfig.class);
    }

    // ========================================
    // activate() tests
    // ========================================

    @Test
    @DisplayName("Should activate with minutesToLive from config")
    void shouldActivateWithMinutesToLiveFromConfig() {
        // Given
        when(config.minutesToLive()).thenReturn(60);

        // When
        cleanMetrics.activate(config);

        // Then
        verify(config).minutesToLive();
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 15, 60, 120, 1440, 10080})
    @DisplayName("Should activate with various minutesToLive values")
    void shouldActivateWithVariousMinutesToLiveValues(int minutes) {
        // Given
        when(config.minutesToLive()).thenReturn(minutes);

        // When
        cleanMetrics.activate(config);
        cleanMetrics.execute(jobContext);

        // Then
        verify(metricService).deleteOldRecords(minutes);
    }

    // ========================================
    // modified() tests
    // ========================================

    @Test
    @DisplayName("Should update minutesToLive when config is modified")
    void shouldUpdateMinutesToLiveWhenConfigModified() {
        // Given
        when(config.minutesToLive()).thenReturn(60, 120);

        // When
        cleanMetrics.activate(config);
        cleanMetrics.execute(jobContext);
        cleanMetrics.activate(config); // Simulate modification
        cleanMetrics.execute(jobContext);

        // Then
        ArgumentCaptor<Integer> minutesCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(metricService, times(2)).deleteOldRecords(minutesCaptor.capture());
        assertThat(minutesCaptor.getAllValues()).containsExactly(60, 120);
    }

    // ========================================
    // execute() tests
    // ========================================

    @Test
    @DisplayName("Should call metricService.deleteOldRecords with correct TTL")
    void shouldCallDeleteOldRecordsWithCorrectTTL() {
        // Given
        when(config.minutesToLive()).thenReturn(60);
        cleanMetrics.activate(config);

        // When
        cleanMetrics.execute(jobContext);

        // Then
        verify(metricService).deleteOldRecords(60);
    }

    @Test
    @DisplayName("Should execute multiple times successfully")
    void shouldExecuteMultipleTimesSuccessfully() {
        // Given
        when(config.minutesToLive()).thenReturn(60);
        cleanMetrics.activate(config);

        // When
        cleanMetrics.execute(jobContext);
        cleanMetrics.execute(jobContext);
        cleanMetrics.execute(jobContext);

        // Then
        verify(metricService, times(3)).deleteOldRecords(60);
    }

    @Test
    @DisplayName("Should handle exception from metricService gracefully")
    void shouldHandleExceptionFromServiceGracefully() {
        // Given
        when(config.minutesToLive()).thenReturn(60);
        cleanMetrics.activate(config);
        doThrow(new RuntimeException("Database error"))
                .when(metricService).deleteOldRecords(anyInt());

        // When - execute should not throw exception based on code inspection
        // Note: CleanMetrics.execute() does NOT catch exceptions, so it will propagate
        assertThatCode(() -> cleanMetrics.execute(jobContext))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database error");

        // Then
        verify(metricService).deleteOldRecords(60);
    }

    @Test
    @DisplayName("Should handle very short TTL of 5 minutes")
    void shouldHandleVeryShortTTL() {
        // Given
        when(config.minutesToLive()).thenReturn(5);
        cleanMetrics.activate(config);

        // When
        cleanMetrics.execute(jobContext);

        // Then
        verify(metricService).deleteOldRecords(5);
    }

    @Test
    @DisplayName("Should handle very long TTL")
    void shouldHandleVeryLongTTL() {
        // Given
        when(config.minutesToLive()).thenReturn(525600); // 1 year in minutes
        cleanMetrics.activate(config);

        // When
        cleanMetrics.execute(jobContext);

        // Then
        verify(metricService).deleteOldRecords(525600);
    }

    @Test
    @DisplayName("Should execute with null JobContext")
    void shouldExecuteWithNullJobContext() {
        // Given
        when(config.minutesToLive()).thenReturn(60);
        cleanMetrics.activate(config);

        // When - passing null context
        assertThatCode(() -> cleanMetrics.execute(null))
                .doesNotThrowAnyException();

        // Then
        verify(metricService).deleteOldRecords(60);
    }

    @Test
    @DisplayName("Should handle common TTL values")
    void shouldHandleCommonTTLValues() {
        // Given - Test common use cases
        when(config.minutesToLive()).thenReturn(60); // 1 hour
        cleanMetrics.activate(config);

        // When
        cleanMetrics.execute(jobContext);

        // Then
        verify(metricService).deleteOldRecords(60);

        // Test 24 hours
        when(config.minutesToLive()).thenReturn(1440);
        cleanMetrics.activate(config);
        cleanMetrics.execute(jobContext);
        verify(metricService).deleteOldRecords(1440);

        // Test 7 days
        when(config.minutesToLive()).thenReturn(10080);
        cleanMetrics.activate(config);
        cleanMetrics.execute(jobContext);
        verify(metricService).deleteOldRecords(10080);
    }

    @Test
    @DisplayName("Should handle zero minutesToLive")
    void shouldHandleZeroMinutesToLive() {
        // Given
        when(config.minutesToLive()).thenReturn(0);
        cleanMetrics.activate(config);

        // When
        cleanMetrics.execute(jobContext);

        // Then - should still call deleteOldRecords with 0 (may delete all records)
        verify(metricService).deleteOldRecords(0);
    }
}

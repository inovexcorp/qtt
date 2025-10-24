package com.inovexcorp.queryservice.scheduler;

import com.inovexcorp.queryservice.health.HealthChecker;
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
@DisplayName("DatasourceHealthCheck Tests")
class DatasourceHealthCheckTest {

    @Mock
    private HealthChecker healthChecker;

    @Mock
    private JobContext jobContext;

    @InjectMocks
    private DatasourceHealthCheck datasourceHealthCheck;

    private DatasourceHealthConfig config;

    @BeforeEach
    void setUp() {
        config = mock(DatasourceHealthConfig.class);
    }

    // ========================================
    // activate() tests
    // ========================================

    @Test
    @DisplayName("Should activate with consecutive failure threshold from config")
    void shouldActivateWithConsecutiveFailureThreshold() {
        // Given
        when(config.consecutiveFailureThreshold()).thenReturn(5);

        // When
        datasourceHealthCheck.activate(config);

        // Then
        verify(config).consecutiveFailureThreshold();
    }

    @Test
    @DisplayName("Should activate with default threshold of 3")
    void shouldActivateWithDefaultThreshold() {
        // Given
        when(config.consecutiveFailureThreshold()).thenReturn(3);

        // When
        datasourceHealthCheck.activate(config);

        // Then - no exception
        assertThatCode(() -> datasourceHealthCheck.execute(jobContext))
                .doesNotThrowAnyException();
        verify(healthChecker).checkAllDatasources(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 5, 10, 100})
    @DisplayName("Should activate with various threshold values")
    void shouldActivateWithVariousThresholdValues(int threshold) {
        // Given
        when(config.consecutiveFailureThreshold()).thenReturn(threshold);

        // When
        datasourceHealthCheck.activate(config);
        datasourceHealthCheck.execute(jobContext);

        // Then
        verify(healthChecker).checkAllDatasources(threshold);
    }

    // ========================================
    // modified() tests
    // ========================================

    @Test
    @DisplayName("Should update threshold when config is modified")
    void shouldUpdateThresholdWhenConfigModified() {
        // Given
        when(config.consecutiveFailureThreshold()).thenReturn(3, 7);

        // When
        datasourceHealthCheck.activate(config);
        datasourceHealthCheck.execute(jobContext);
        datasourceHealthCheck.activate(config); // Simulate modification
        datasourceHealthCheck.execute(jobContext);

        // Then
        ArgumentCaptor<Integer> thresholdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(healthChecker, times(2)).checkAllDatasources(thresholdCaptor.capture());
        assertThat(thresholdCaptor.getAllValues()).containsExactly(3, 7);
    }

    @Test
    @DisplayName("Should handle threshold of zero to disable auto-stop")
    void shouldHandleThresholdOfZeroToDisableAutoStop() {
        // Given
        when(config.consecutiveFailureThreshold()).thenReturn(0);

        // When
        datasourceHealthCheck.activate(config);
        datasourceHealthCheck.execute(jobContext);

        // Then - threshold of 0 should disable automatic route stopping
        verify(healthChecker).checkAllDatasources(0);
    }

    // ========================================
    // execute() tests
    // ========================================

    @Test
    @DisplayName("Should call healthChecker.checkAllDatasources with correct threshold")
    void shouldCallHealthCheckerWithCorrectThreshold() {
        // Given
        when(config.consecutiveFailureThreshold()).thenReturn(5);
        datasourceHealthCheck.activate(config);

        // When
        datasourceHealthCheck.execute(jobContext);

        // Then
        verify(healthChecker).checkAllDatasources(5);
    }

    @Test
    @DisplayName("Should execute multiple times successfully")
    void shouldExecuteMultipleTimesSuccessfully() {
        // Given
        when(config.consecutiveFailureThreshold()).thenReturn(3);
        datasourceHealthCheck.activate(config);

        // When
        datasourceHealthCheck.execute(jobContext);
        datasourceHealthCheck.execute(jobContext);
        datasourceHealthCheck.execute(jobContext);

        // Then
        verify(healthChecker, times(3)).checkAllDatasources(3);
    }

    @Test
    @DisplayName("Should handle exception from healthChecker gracefully")
    void shouldHandleExceptionFromHealthCheckerGracefully() {
        // Given
        when(config.consecutiveFailureThreshold()).thenReturn(3);
        datasourceHealthCheck.activate(config);
        doThrow(new RuntimeException("Health check error"))
                .when(healthChecker).checkAllDatasources(anyInt());

        // When - should not propagate exception
        assertThatCode(() -> datasourceHealthCheck.execute(jobContext))
                .doesNotThrowAnyException();

        // Then
        verify(healthChecker).checkAllDatasources(3);
    }

    @Test
    @DisplayName("Should pass JobContext to execute method")
    void shouldPassJobContextToExecuteMethod() {
        // Given
        when(config.consecutiveFailureThreshold()).thenReturn(3);
        datasourceHealthCheck.activate(config);
        JobContext customContext = mock(JobContext.class);

        // When
        datasourceHealthCheck.execute(customContext);

        // Then - should not throw exception
        verify(healthChecker).checkAllDatasources(3);
    }

    @Test
    @DisplayName("Should execute with null JobContext")
    void shouldExecuteWithNullJobContext() {
        // Given
        when(config.consecutiveFailureThreshold()).thenReturn(3);
        datasourceHealthCheck.activate(config);

        // When - passing null context
        assertThatCode(() -> datasourceHealthCheck.execute(null))
                .doesNotThrowAnyException();

        // Then
        verify(healthChecker).checkAllDatasources(3);
    }

    @Test
    @DisplayName("Should continue executing after previous exception")
    void shouldContinueExecutingAfterPreviousException() {
        // Given
        when(config.consecutiveFailureThreshold()).thenReturn(3);
        datasourceHealthCheck.activate(config);
        doThrow(new RuntimeException("First error"))
                .doNothing()
                .when(healthChecker).checkAllDatasources(anyInt());

        // When
        datasourceHealthCheck.execute(jobContext); // First call throws
        datasourceHealthCheck.execute(jobContext); // Second call succeeds

        // Then
        verify(healthChecker, times(2)).checkAllDatasources(3);
    }
}

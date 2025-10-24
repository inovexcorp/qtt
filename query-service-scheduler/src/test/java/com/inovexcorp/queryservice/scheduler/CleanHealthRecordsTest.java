package com.inovexcorp.queryservice.scheduler;

import com.inovexcorp.queryservice.persistence.DatasourceHealthService;
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
@DisplayName("CleanHealthRecords Tests")
class CleanHealthRecordsTest {

    @Mock
    private DatasourceHealthService datasourceHealthService;

    @Mock
    private JobContext jobContext;

    @InjectMocks
    private CleanHealthRecords cleanHealthRecords;

    private CleanHealthRecordsConfig config;

    @BeforeEach
    void setUp() {
        config = mock(CleanHealthRecordsConfig.class);
    }

    // ========================================
    // activate() tests
    // ========================================

    @Test
    @DisplayName("Should activate with daysToLive from config")
    void shouldActivateWithDaysToLiveFromConfig() {
        // Given
        when(config.daysToLive()).thenReturn(30);

        // When
        cleanHealthRecords.activate(config);

        // Then
        verify(config).daysToLive();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 7, 30, 90, 365})
    @DisplayName("Should activate with various daysToLive values")
    void shouldActivateWithVariousDaysToLiveValues(int days) {
        // Given
        when(config.daysToLive()).thenReturn(days);

        // When
        cleanHealthRecords.activate(config);
        cleanHealthRecords.execute(jobContext);

        // Then
        verify(datasourceHealthService).deleteOldRecords(days);
    }

    // ========================================
    // modified() tests
    // ========================================

    @Test
    @DisplayName("Should update daysToLive when config is modified")
    void shouldUpdateDaysToLiveWhenConfigModified() {
        // Given
        when(config.daysToLive()).thenReturn(30, 60);

        // When
        cleanHealthRecords.activate(config);
        cleanHealthRecords.execute(jobContext);
        cleanHealthRecords.activate(config); // Simulate modification
        cleanHealthRecords.execute(jobContext);

        // Then
        ArgumentCaptor<Integer> daysCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(datasourceHealthService, times(2)).deleteOldRecords(daysCaptor.capture());
        assertThat(daysCaptor.getAllValues()).containsExactly(30, 60);
    }

    // ========================================
    // execute() tests
    // ========================================

    @Test
    @DisplayName("Should call datasourceHealthService.deleteOldRecords with correct TTL")
    void shouldCallDeleteOldRecordsWithCorrectTTL() {
        // Given
        when(config.daysToLive()).thenReturn(30);
        cleanHealthRecords.activate(config);

        // When
        cleanHealthRecords.execute(jobContext);

        // Then
        verify(datasourceHealthService).deleteOldRecords(30);
    }

    @Test
    @DisplayName("Should execute multiple times successfully")
    void shouldExecuteMultipleTimesSuccessfully() {
        // Given
        when(config.daysToLive()).thenReturn(30);
        cleanHealthRecords.activate(config);

        // When
        cleanHealthRecords.execute(jobContext);
        cleanHealthRecords.execute(jobContext);
        cleanHealthRecords.execute(jobContext);

        // Then
        verify(datasourceHealthService, times(3)).deleteOldRecords(30);
    }

    @Test
    @DisplayName("Should handle exception from datasourceHealthService gracefully")
    void shouldHandleExceptionFromServiceGracefully() {
        // Given
        when(config.daysToLive()).thenReturn(30);
        cleanHealthRecords.activate(config);
        doThrow(new RuntimeException("Database error"))
                .when(datasourceHealthService).deleteOldRecords(anyInt());

        // When - should not propagate exception
        assertThatCode(() -> cleanHealthRecords.execute(jobContext))
                .doesNotThrowAnyException();

        // Then
        verify(datasourceHealthService).deleteOldRecords(30);
    }

    @Test
    @DisplayName("Should handle very short TTL of 1 day")
    void shouldHandleVeryShortTTL() {
        // Given
        when(config.daysToLive()).thenReturn(1);
        cleanHealthRecords.activate(config);

        // When
        cleanHealthRecords.execute(jobContext);

        // Then
        verify(datasourceHealthService).deleteOldRecords(1);
    }

    @Test
    @DisplayName("Should handle very long TTL")
    void shouldHandleVeryLongTTL() {
        // Given
        when(config.daysToLive()).thenReturn(3650); // 10 years
        cleanHealthRecords.activate(config);

        // When
        cleanHealthRecords.execute(jobContext);

        // Then
        verify(datasourceHealthService).deleteOldRecords(3650);
    }

    @Test
    @DisplayName("Should execute with null JobContext")
    void shouldExecuteWithNullJobContext() {
        // Given
        when(config.daysToLive()).thenReturn(30);
        cleanHealthRecords.activate(config);

        // When - passing null context
        assertThatCode(() -> cleanHealthRecords.execute(null))
                .doesNotThrowAnyException();

        // Then
        verify(datasourceHealthService).deleteOldRecords(30);
    }

    @Test
    @DisplayName("Should continue executing after previous exception")
    void shouldContinueExecutingAfterPreviousException() {
        // Given
        when(config.daysToLive()).thenReturn(30);
        cleanHealthRecords.activate(config);
        doThrow(new RuntimeException("First error"))
                .doNothing()
                .when(datasourceHealthService).deleteOldRecords(anyInt());

        // When
        cleanHealthRecords.execute(jobContext); // First call throws
        cleanHealthRecords.execute(jobContext); // Second call succeeds

        // Then
        verify(datasourceHealthService, times(2)).deleteOldRecords(30);
    }
}

package com.inovexcorp.queryservice.cache;

import com.inovexcorp.queryservice.cache.RequestCoalescingService.CoalescedResult;
import com.inovexcorp.queryservice.cache.RequestCoalescingService.RegistrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RequestCoalescingService.
 * Tests the request coalescing logic that prevents duplicate backend calls
 * when multiple requests arrive for the same cache key.
 */
class RequestCoalescingServiceTest {

    private RequestCoalescingService coalescingService;

    @BeforeEach
    void setUp() {
        coalescingService = new RequestCoalescingService(true, 5000);
    }

    // ========== Basic Configuration Tests ==========

    @Test
    void constructor_WithEnabledTrue_IsEnabled() {
        // Arrange & Act
        RequestCoalescingService service = new RequestCoalescingService(true, 5000);

        // Assert
        assertTrue(service.isEnabled(), "Service should be enabled");
    }

    @Test
    void constructor_WithEnabledFalse_IsDisabled() {
        // Arrange & Act
        RequestCoalescingService service = new RequestCoalescingService(false, 5000);

        // Assert
        assertFalse(service.isEnabled(), "Service should be disabled");
    }

    @Test
    void getDefaultTimeoutMs_ReturnsConfiguredValue() {
        // Arrange
        long expectedTimeout = 10000;
        RequestCoalescingService service = new RequestCoalescingService(true, expectedTimeout);

        // Act
        long actualTimeout = service.getDefaultTimeoutMs();

        // Assert
        assertEquals(expectedTimeout, actualTimeout, "Timeout should match configured value");
    }

    // ========== Leader/Follower Registration Tests ==========

    @Test
    void registerRequest_FirstRequest_BecomesLeader() {
        // Arrange
        String cacheKey = "test:key:1";

        // Act
        RegistrationResult result = coalescingService.registerRequest(cacheKey);

        // Assert
        assertTrue(result.isLeader(), "First request should be the leader");
        assertTrue(result.shouldProceed(), "Leader should proceed to backend");
        assertNotNull(result.future(), "Future should not be null");
    }

    @Test
    void registerRequest_SecondRequest_BecomesFollower() {
        // Arrange
        String cacheKey = "test:key:1";
        coalescingService.registerRequest(cacheKey); // First request becomes leader

        // Act
        RegistrationResult result = coalescingService.registerRequest(cacheKey);

        // Assert
        assertFalse(result.isLeader(), "Second request should be a follower");
        assertFalse(result.shouldProceed(), "Follower should not proceed to backend");
    }

    @Test
    void registerRequest_DifferentKeys_BothBecomeLeaders() {
        // Arrange
        String cacheKey1 = "test:key:1";
        String cacheKey2 = "test:key:2";

        // Act
        RegistrationResult result1 = coalescingService.registerRequest(cacheKey1);
        RegistrationResult result2 = coalescingService.registerRequest(cacheKey2);

        // Assert
        assertTrue(result1.isLeader(), "First key's first request should be leader");
        assertTrue(result2.isLeader(), "Second key's first request should be leader");
    }

    @Test
    void registerRequest_WhenDisabled_AlwaysReturnsLeader() {
        // Arrange
        RequestCoalescingService disabledService = new RequestCoalescingService(false, 5000);
        String cacheKey = "test:key:1";

        // Act
        RegistrationResult result1 = disabledService.registerRequest(cacheKey);
        RegistrationResult result2 = disabledService.registerRequest(cacheKey);
        RegistrationResult result3 = disabledService.registerRequest(cacheKey);

        // Assert
        assertTrue(result1.isLeader(), "All requests should be leaders when disabled");
        assertTrue(result2.isLeader(), "All requests should be leaders when disabled");
        assertTrue(result3.isLeader(), "All requests should be leaders when disabled");
    }

    @Test
    void registerRequest_MultipleFollowers_AllGetSameFuture() {
        // Arrange
        String cacheKey = "test:key:1";
        RegistrationResult leader = coalescingService.registerRequest(cacheKey);

        // Act
        RegistrationResult follower1 = coalescingService.registerRequest(cacheKey);
        RegistrationResult follower2 = coalescingService.registerRequest(cacheKey);
        RegistrationResult follower3 = coalescingService.registerRequest(cacheKey);

        // Assert - all followers share the leader's future
        assertSame(leader.future(), follower1.future(), "Followers should share the leader's future");
        assertSame(follower1.future(), follower2.future(), "All followers should share the same future");
        assertSame(follower2.future(), follower3.future(), "All followers should share the same future");
    }

    // ========== Complete Request Tests ==========

    @Test
    @Timeout(5)
    void completeRequest_NotifiesWaitingFollowers() throws Exception {
        // Arrange
        String cacheKey = "test:key:1";
        String expectedResult = "{\"data\": \"test result\"}";

        coalescingService.registerRequest(cacheKey); // Leader
        RegistrationResult follower = coalescingService.registerRequest(cacheKey);

        // Act
        coalescingService.completeRequest(cacheKey, expectedResult);

        // Assert
        Optional<CoalescedResult> result = coalescingService.awaitResult(follower, 1000);
        assertTrue(result.isPresent(), "Result should be present");
        assertTrue(result.get().success(), "Result should be successful");
        assertEquals(expectedResult, result.get().value(), "Result value should match");
    }

    @Test
    @Timeout(5)
    void completeRequest_MultipleFollowersAllReceiveResult() throws Exception {
        // Arrange
        String cacheKey = "test:key:1";
        String expectedResult = "{\"data\": \"shared result\"}";
        int followerCount = 5;

        coalescingService.registerRequest(cacheKey); // Leader
        List<RegistrationResult> followers = new ArrayList<>();
        for (int i = 0; i < followerCount; i++) {
            followers.add(coalescingService.registerRequest(cacheKey));
        }

        // Act
        coalescingService.completeRequest(cacheKey, expectedResult);

        // Assert
        for (RegistrationResult follower : followers) {
            Optional<CoalescedResult> result = coalescingService.awaitResult(follower, 1000);
            assertTrue(result.isPresent(), "Each follower should receive result");
            assertTrue(result.get().success(), "Result should be successful");
            assertEquals(expectedResult, result.get().value(), "All followers should get same result");
        }
    }

    @Test
    void completeRequest_RemovesInFlightEntry() {
        // Arrange
        String cacheKey = "test:key:1";
        coalescingService.registerRequest(cacheKey);
        assertEquals(1, coalescingService.getInFlightCount(), "Should have 1 in-flight request");

        // Act
        coalescingService.completeRequest(cacheKey, "result");

        // Assert
        assertEquals(0, coalescingService.getInFlightCount(), "Should have 0 in-flight requests after completion");
    }

    @Test
    void completeRequest_AllowsNewLeaderForSameKey() {
        // Arrange
        String cacheKey = "test:key:1";
        coalescingService.registerRequest(cacheKey);
        coalescingService.completeRequest(cacheKey, "result");

        // Act
        RegistrationResult newRequest = coalescingService.registerRequest(cacheKey);

        // Assert
        assertTrue(newRequest.isLeader(), "New request should become leader after previous completed");
    }

    @Test
    void completeRequest_WithNonExistentKey_DoesNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() -> coalescingService.completeRequest("non-existent-key", "result"));
    }

    // ========== Fail Request Tests ==========

    @Test
    @Timeout(5)
    void failRequest_NotifiesFollowersOfFailure() throws Exception {
        // Arrange
        String cacheKey = "test:key:1";
        String errorMessage = "Backend error";

        coalescingService.registerRequest(cacheKey); // Leader
        RegistrationResult follower = coalescingService.registerRequest(cacheKey);

        // Act
        coalescingService.failRequest(cacheKey, errorMessage);

        // Assert
        Optional<CoalescedResult> result = coalescingService.awaitResult(follower, 1000);
        assertTrue(result.isPresent(), "Result should be present");
        assertFalse(result.get().success(), "Result should indicate failure");
        assertNull(result.get().value(), "Value should be null on failure");
        assertEquals(errorMessage, result.get().errorMessage(), "Error message should match");
    }

    @Test
    void failRequest_IncrementsFailureCount() {
        // Arrange
        String cacheKey = "test:key:1";
        coalescingService.registerRequest(cacheKey);
        long initialFailures = coalescingService.getFailureCount();

        // Act
        coalescingService.failRequest(cacheKey, "error");

        // Assert
        assertEquals(initialFailures + 1, coalescingService.getFailureCount(), "Failure count should increment");
    }

    @Test
    void failRequest_RemovesInFlightEntry() {
        // Arrange
        String cacheKey = "test:key:1";
        coalescingService.registerRequest(cacheKey);

        // Act
        coalescingService.failRequest(cacheKey, "error");

        // Assert
        assertEquals(0, coalescingService.getInFlightCount(), "Should have 0 in-flight requests after failure");
    }

    // ========== Cancel Request Tests ==========

    @Test
    void cancelRequest_RemovesInFlightEntry() {
        // Arrange
        String cacheKey = "test:key:1";
        coalescingService.registerRequest(cacheKey);

        // Act
        coalescingService.cancelRequest(cacheKey);

        // Assert
        assertEquals(0, coalescingService.getInFlightCount(), "Should have 0 in-flight requests after cancel");
    }

    @Test
    void cancelRequest_WithNonExistentKey_DoesNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() -> coalescingService.cancelRequest("non-existent-key"));
    }

    // ========== Await Result Tests ==========

    @Test
    @Timeout(5)
    void awaitResult_ReturnsResultWhenCompleted() throws Exception {
        // Arrange
        String cacheKey = "test:key:1";
        String expectedResult = "test result";

        coalescingService.registerRequest(cacheKey); // Leader
        RegistrationResult follower = coalescingService.registerRequest(cacheKey);

        // Complete in separate thread
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100);
                coalescingService.completeRequest(cacheKey, expectedResult);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Act
        Optional<CoalescedResult> result = coalescingService.awaitResult(follower);

        // Assert
        assertTrue(result.isPresent(), "Result should be present");
        assertEquals(expectedResult, result.get().value(), "Result should match");
    }

    @Test
    @Timeout(5)
    void awaitResult_ReturnsEmptyOnTimeout() {
        // Arrange
        String cacheKey = "test:key:1";
        coalescingService.registerRequest(cacheKey); // Leader never completes
        RegistrationResult follower = coalescingService.registerRequest(cacheKey);

        // Act
        Optional<CoalescedResult> result = coalescingService.awaitResult(follower, 100);

        // Assert
        assertTrue(result.isEmpty(), "Result should be empty on timeout");
    }

    @Test
    void awaitResult_IncrementsTimeoutCount() {
        // Arrange
        String cacheKey = "test:key:1";
        coalescingService.registerRequest(cacheKey);
        RegistrationResult follower = coalescingService.registerRequest(cacheKey);
        long initialTimeouts = coalescingService.getTimeoutCount();

        // Act
        coalescingService.awaitResult(follower, 10); // Very short timeout

        // Assert
        assertEquals(initialTimeouts + 1, coalescingService.getTimeoutCount(), "Timeout count should increment");
    }

    @Test
    void awaitResult_LeaderThrowsException() {
        // Arrange
        String cacheKey = "test:key:1";
        RegistrationResult leader = coalescingService.registerRequest(cacheKey);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> coalescingService.awaitResult(leader),
                "Leader should not be able to await its own result");
    }

    // ========== Statistics Tests ==========

    @Test
    void getLeaderCount_IncrementsOnNewLeader() {
        // Arrange
        long initialCount = coalescingService.getLeaderCount();

        // Act
        coalescingService.registerRequest("key1");
        coalescingService.registerRequest("key2");
        coalescingService.registerRequest("key3");

        // Assert
        assertEquals(initialCount + 3, coalescingService.getLeaderCount(), "Leader count should increment");
    }

    @Test
    void getCoalescedCount_IncrementsOnFollower() {
        // Arrange
        String cacheKey = "test:key:1";
        coalescingService.registerRequest(cacheKey); // Leader
        long initialCount = coalescingService.getCoalescedCount();

        // Act
        coalescingService.registerRequest(cacheKey); // Follower 1
        coalescingService.registerRequest(cacheKey); // Follower 2
        coalescingService.registerRequest(cacheKey); // Follower 3

        // Assert
        assertEquals(initialCount + 3, coalescingService.getCoalescedCount(), "Coalesced count should increment");
    }

    @Test
    void getInFlightCount_ReflectsCurrentState() {
        // Arrange & Assert initial state
        assertEquals(0, coalescingService.getInFlightCount(), "Initial in-flight count should be 0");

        // Act - register requests for different keys
        coalescingService.registerRequest("key1");
        coalescingService.registerRequest("key2");
        assertEquals(2, coalescingService.getInFlightCount(), "Should have 2 in-flight requests");

        // Complete one
        coalescingService.completeRequest("key1", "result");
        assertEquals(1, coalescingService.getInFlightCount(), "Should have 1 in-flight request");

        // Complete other
        coalescingService.completeRequest("key2", "result");
        assertEquals(0, coalescingService.getInFlightCount(), "Should have 0 in-flight requests");
    }

    @Test
    void resetStats_ClearsAllCounters() {
        // Arrange
        coalescingService.registerRequest("key1");
        coalescingService.registerRequest("key1"); // Coalesced
        coalescingService.completeRequest("key1", "result");

        // Act
        coalescingService.resetStats();

        // Assert
        assertEquals(0, coalescingService.getLeaderCount(), "Leader count should be reset");
        assertEquals(0, coalescingService.getCoalescedCount(), "Coalesced count should be reset");
        assertEquals(0, coalescingService.getTimeoutCount(), "Timeout count should be reset");
        assertEquals(0, coalescingService.getFailureCount(), "Failure count should be reset");
    }

    // ========== CoalescedResult Tests ==========

    @Test
    void coalescedResult_Success_HasCorrectState() {
        // Act
        CoalescedResult result = CoalescedResult.success("test value");

        // Assert
        assertTrue(result.success(), "Should indicate success");
        assertEquals("test value", result.value(), "Value should match");
        assertNull(result.errorMessage(), "Error message should be null");
    }

    @Test
    void coalescedResult_Failure_HasCorrectState() {
        // Act
        CoalescedResult result = CoalescedResult.failure("error message");

        // Assert
        assertFalse(result.success(), "Should indicate failure");
        assertNull(result.value(), "Value should be null");
        assertEquals("error message", result.errorMessage(), "Error message should match");
    }

    // ========== Concurrent Access Tests ==========

    @Test
    @Timeout(30)
    void concurrentRequests_OnlyOneLeaderPerKey() throws Exception {
        // Arrange
        String cacheKey = "test:concurrent:key";
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger leaderCount = new AtomicInteger(0);
        AtomicInteger followerCount = new AtomicInteger(0);

        // Act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RegistrationResult result = coalescingService.registerRequest(cacheKey);
                    if (result.isLeader()) {
                        leaderCount.incrementAndGet();
                    } else {
                        followerCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads simultaneously
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertEquals(1, leaderCount.get(), "Exactly one thread should become leader");
        assertEquals(threadCount - 1, followerCount.get(), "All other threads should be followers");
    }

    @Test
    @Timeout(30)
    void concurrentRequests_AllFollowersReceiveResult() throws Exception {
        // Arrange
        String cacheKey = "test:concurrent:result";
        String expectedResult = "{\"data\": \"concurrent result\"}";
        int followerCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(followerCount + 1);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch registrationDone = new CountDownLatch(followerCount);
        CountDownLatch resultsDone = new CountDownLatch(followerCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<String> leaderError = new AtomicReference<>();

        // Register leader first
        coalescingService.registerRequest(cacheKey);

        // Register followers concurrently
        List<RegistrationResult> followers = new CopyOnWriteArrayList<>();
        for (int i = 0; i < followerCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RegistrationResult result = coalescingService.registerRequest(cacheKey);
                    followers.add(result);
                } catch (Exception e) {
                    // Ignore
                } finally {
                    registrationDone.countDown();
                }
            });
        }

        startLatch.countDown();
        registrationDone.await(5, TimeUnit.SECONDS);

        // Complete the request
        coalescingService.completeRequest(cacheKey, expectedResult);

        // All followers await result
        for (RegistrationResult follower : followers) {
            executor.submit(() -> {
                try {
                    Optional<CoalescedResult> result = coalescingService.awaitResult(follower, 5000);
                    if (result.isPresent() && result.get().success() && expectedResult.equals(result.get().value())) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    resultsDone.countDown();
                }
            });
        }

        resultsDone.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertEquals(followerCount, successCount.get(), "All followers should receive the correct result");
    }

    @Test
    @Timeout(30)
    void concurrentRequests_DifferentKeys_IndependentCoalescing() throws Exception {
        // Arrange
        int keyCount = 10;
        int requestsPerKey = 5;
        ExecutorService executor = Executors.newFixedThreadPool(keyCount * requestsPerKey);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(keyCount * requestsPerKey);
        ConcurrentHashMap<String, AtomicInteger> leadersPerKey = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, AtomicInteger> followersPerKey = new ConcurrentHashMap<>();

        // Initialize counters
        for (int i = 0; i < keyCount; i++) {
            leadersPerKey.put("key" + i, new AtomicInteger(0));
            followersPerKey.put("key" + i, new AtomicInteger(0));
        }

        // Act
        for (int k = 0; k < keyCount; k++) {
            final String key = "key" + k;
            for (int r = 0; r < requestsPerKey; r++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        RegistrationResult result = coalescingService.registerRequest(key);
                        if (result.isLeader()) {
                            leadersPerKey.get(key).incrementAndGet();
                        } else {
                            followersPerKey.get(key).incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert - each key should have exactly 1 leader
        for (int i = 0; i < keyCount; i++) {
            String key = "key" + i;
            assertEquals(1, leadersPerKey.get(key).get(),
                    "Key " + key + " should have exactly 1 leader");
            assertEquals(requestsPerKey - 1, followersPerKey.get(key).get(),
                    "Key " + key + " should have " + (requestsPerKey - 1) + " followers");
        }
    }

    // ========== Edge Cases ==========

    @Test
    void registerRequest_WithNullKey_ThrowsNullPointerException() {
        // ConcurrentHashMap does not allow null keys, so NPE is expected
        assertThrows(NullPointerException.class,
                () -> coalescingService.registerRequest(null),
                "Null key should throw NullPointerException");
    }

    @Test
    void registerRequest_WithEmptyKey_Works() {
        // Arrange
        String emptyKey = "";

        // Act
        RegistrationResult result = coalescingService.registerRequest(emptyKey);

        // Assert
        assertTrue(result.isLeader(), "Empty key request should become leader");
    }

    @Test
    void completeRequest_WithNullResult_NotifiesFollowers() throws Exception {
        // Arrange
        String cacheKey = "test:null:result";
        coalescingService.registerRequest(cacheKey);
        RegistrationResult follower = coalescingService.registerRequest(cacheKey);

        // Act
        coalescingService.completeRequest(cacheKey, null);

        // Assert
        Optional<CoalescedResult> result = coalescingService.awaitResult(follower, 1000);
        assertTrue(result.isPresent(), "Result should be present");
        assertTrue(result.get().success(), "Should indicate success");
        assertNull(result.get().value(), "Value should be null");
    }

    @Test
    void registrationResult_AwaitOnLeader_ThrowsIllegalStateException() {
        // Arrange
        RegistrationResult leader = coalescingService.registerRequest("test:key");

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> leader.await(1000));
        assertTrue(exception.getMessage().contains("Leader"),
                "Exception message should mention leader");
    }

    @Test
    void rapidRegisterAndComplete_DoesNotLeak() {
        // Arrange & Act - rapid registration and completion cycles
        for (int i = 0; i < 1000; i++) {
            String key = "key:" + i;
            coalescingService.registerRequest(key);
            coalescingService.completeRequest(key, "result:" + i);
        }

        // Assert
        assertEquals(0, coalescingService.getInFlightCount(),
                "No in-flight requests should remain after completing all");
    }

    @Test
    @Timeout(10)
    void followerAwait_WhenLeaderTakesLong_ReturnsOnTimeout() {
        // Arrange
        String cacheKey = "test:slow:leader";
        coalescingService.registerRequest(cacheKey); // Leader that never completes
        RegistrationResult follower = coalescingService.registerRequest(cacheKey);

        long startTime = System.currentTimeMillis();

        // Act
        Optional<CoalescedResult> result = coalescingService.awaitResult(follower, 500);

        long elapsed = System.currentTimeMillis() - startTime;

        // Assert
        assertTrue(result.isEmpty(), "Should return empty on timeout");
        assertTrue(elapsed >= 450 && elapsed < 1000,
                "Should wait approximately the timeout duration");
    }

    @Test
    void disabledService_DoesNotTrackInFlight() {
        // Arrange
        RequestCoalescingService disabled = new RequestCoalescingService(false, 5000);

        // Act
        disabled.registerRequest("key1");
        disabled.registerRequest("key2");

        // Assert - when disabled, nothing should be tracked
        assertEquals(0, disabled.getInFlightCount(),
                "Disabled service should not track in-flight requests");
    }
}

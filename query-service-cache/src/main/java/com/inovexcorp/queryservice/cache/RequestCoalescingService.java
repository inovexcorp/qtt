package com.inovexcorp.queryservice.cache;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service that coalesces duplicate requests for the same cache key.
 * <p>
 * When multiple requests arrive for the same cache key while the first request
 * is still in-flight (fetching from the backend), subsequent requests will wait
 * for the first request to complete rather than making duplicate backend calls.
 * <p>
 * This prevents the "thundering herd" problem where a cache miss causes multiple
 * simultaneous requests to hit the backend.
 */
@Slf4j
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RequestCoalescingService {

    /**
     * Result of a coalesced request.
     */
    public record CoalescedResult(String value, boolean success, String errorMessage) {
        public static CoalescedResult success(String value) {
            return new CoalescedResult(value, true, null);
        }

        public static CoalescedResult failure(String errorMessage) {
            return new CoalescedResult(null, false, errorMessage);
        }
    }

    /**
     * Internal record to track in-flight request metadata.
     */
    private record InFlightRequest(CompletableFuture<CoalescedResult> future, long createdAt) {
        static InFlightRequest create() {
            return new InFlightRequest(new CompletableFuture<>(), System.currentTimeMillis());
        }
    }

    /**
     * Result of attempting to register an in-flight request.
     */
    public record RegistrationResult(boolean isLeader, CompletableFuture<CoalescedResult> future) {
        /**
         * @return true if this request should proceed to the backend (it's the leader)
         */
        public boolean shouldProceed() {
            return isLeader;
        }

        /**
         * Wait for the leader request to complete and return the result.
         * Only call this if shouldProceed() returns false.
         *
         * @param timeoutMs timeout in milliseconds
         * @return the coalesced result
         * @throws TimeoutException if the leader takes too long
         * @throws InterruptedException if the thread is interrupted
         */
        public Optional<CoalescedResult> await(long timeoutMs) throws TimeoutException, InterruptedException {
            if (isLeader) {
                throw new IllegalStateException("Leader should not await its own future");
            }
            try {
                return Optional.of(future.get(timeoutMs, TimeUnit.MILLISECONDS));
            } catch (ExecutionException e) {
                log.warn("Coalesced request failed: {}", e.getCause().getMessage());
                return Optional.empty();
            }
        }
    }

    // Map of cache key -> in-flight request metadata
    private final ConcurrentHashMap<String, InFlightRequest> inFlightRequests = new ConcurrentHashMap<>();

    // Statistics
    private final AtomicLong coalescedRequests = new AtomicLong(0);
    private final AtomicLong leaderRequests = new AtomicLong(0);
    private final AtomicLong timeouts = new AtomicLong(0);
    private final AtomicLong failures = new AtomicLong(0);
    private final AtomicLong forcedTakeovers = new AtomicLong(0);
    private final AtomicLong staleEntriesCleaned = new AtomicLong(0);

    @Getter
    @Builder.Default
    private final boolean enabled = false;

    @Getter
    @Builder.Default
    private final long defaultTimeoutMs = 30000L;

    @Getter
    @Builder.Default
    private final long staleEntryThresholdMs = 120000L; // 2 minutes - entries older than this can be cleaned up


    /**
     * Attempts to register an in-flight request for the given cache key.
     * <p>
     * If no request is currently in-flight for this key, this request becomes
     * the "leader" and should proceed to the backend. The returned future should
     * be completed when the result is available.
     * <p>
     * If a request is already in-flight, this request becomes a "follower" and
     * should wait for the leader's future to complete.
     *
     * @param cacheKey the cache key being requested
     * @return registration result indicating whether to proceed or wait
     */
    public RegistrationResult registerRequest(String cacheKey) {
        if (!enabled) {
            // If disabled, always proceed (no coalescing)
            return new RegistrationResult(true, new CompletableFuture<>());
        }

        InFlightRequest newRequest = InFlightRequest.create();
        InFlightRequest existingRequest = inFlightRequests.putIfAbsent(cacheKey, newRequest);

        if (existingRequest == null) {
            // No existing request - this is the leader
            leaderRequests.incrementAndGet();
            log.debug("Registered as leader for cache key: {}", cacheKey);
            return new RegistrationResult(true, newRequest.future());
        } else {
            // Existing request found - this is a follower
            coalescedRequests.incrementAndGet();
            log.debug("Coalescing request for cache key: {} (waiting for leader)", cacheKey);
            return new RegistrationResult(false, existingRequest.future());
        }
    }

    /**
     * Completes an in-flight request with a successful result.
     * This notifies all waiting (coalesced) requests.
     *
     * @param cacheKey the cache key
     * @param result   the result value
     */
    public void completeRequest(String cacheKey, String result) {
        InFlightRequest request = inFlightRequests.remove(cacheKey);
        if (request != null) {
            request.future().complete(CoalescedResult.success(result));
            log.debug("Completed in-flight request for cache key: {}", cacheKey);
        } else {
            log.warn("No in-flight request found for cache key: {}", cacheKey);
        }
    }

    /**
     * Completes an in-flight request with a failure.
     * Waiting requests will be notified and may proceed to make their own backend calls.
     *
     * @param cacheKey     the cache key
     * @param errorMessage the error message
     */
    public void failRequest(String cacheKey, String errorMessage) {
        InFlightRequest request = inFlightRequests.remove(cacheKey);
        if (request != null) {
            failures.incrementAndGet();
            request.future().complete(CoalescedResult.failure(errorMessage));
            log.debug("Failed in-flight request for cache key: {}", cacheKey);
        }
    }

    /**
     * Cancels an in-flight request without providing a result.
     * This is used when the request needs to be abandoned.
     *
     * @param cacheKey the cache key
     */
    public void cancelRequest(String cacheKey) {
        InFlightRequest request = inFlightRequests.remove(cacheKey);
        if (request != null) {
            request.future().cancel(false);
            log.debug("Cancelled in-flight request for cache key: {}", cacheKey);
        }
    }

    /**
     * Checks if a request is currently in-flight for the given cache key.
     *
     * @param cacheKey the cache key
     * @return true if a request is in-flight
     */
    public boolean isInFlight(String cacheKey) {
        return inFlightRequests.containsKey(cacheKey);
    }

    /**
     * Forces takeover of leadership for a cache key.
     * <p>
     * This is used when a follower times out and needs to proceed to the backend.
     * It atomically removes any existing in-flight request (notifying its waiters of failure)
     * and registers a new request with this caller as leader.
     * <p>
     * This prevents the race condition where a timed-out follower proceeds to the backend
     * while the original leader is still running.
     *
     * @param cacheKey the cache key
     * @return registration result with this caller as leader
     */
    public RegistrationResult forceLeadership(String cacheKey) {
        if (!enabled) {
            return new RegistrationResult(true, new CompletableFuture<>());
        }

        // Atomically replace any existing request
        InFlightRequest newRequest = InFlightRequest.create();
        InFlightRequest oldRequest = inFlightRequests.put(cacheKey, newRequest);

        if (oldRequest != null) {
            // Notify waiters of the old request that it's being superseded
            oldRequest.future().complete(CoalescedResult.failure("Request superseded by forced takeover after timeout"));
            forcedTakeovers.incrementAndGet();
            log.debug("Forced takeover for cache key: {} (superseded previous in-flight request)", cacheKey);
        }

        leaderRequests.incrementAndGet();
        log.debug("Registered as leader via forced takeover for cache key: {}", cacheKey);
        return new RegistrationResult(true, newRequest.future());
    }

    /**
     * Cleans up stale in-flight requests that have exceeded the threshold.
     * <p>
     * This should be called periodically to prevent memory leaks from requests
     * that were never completed (e.g., due to exceptions in the processing pipeline).
     *
     * @return the number of stale entries cleaned up
     */
    public int cleanupStaleEntries() {
        if (!enabled) {
            return 0;
        }

        long now = System.currentTimeMillis();
        List<String> staleKeys = new ArrayList<>();

        for (var entry : inFlightRequests.entrySet()) {
            if (now - entry.getValue().createdAt() > staleEntryThresholdMs) {
                staleKeys.add(entry.getKey());
            }
        }

        int cleaned = 0;
        for (String key : staleKeys) {
            InFlightRequest request = inFlightRequests.remove(key);
            if (request != null) {
                request.future().complete(CoalescedResult.failure("Request cleaned up as stale after " + staleEntryThresholdMs + "ms"));
                cleaned++;
                log.warn("Cleaned up stale in-flight request for cache key: {} (age: {}ms)",
                        key, now - request.createdAt());
            }
        }

        if (cleaned > 0) {
            staleEntriesCleaned.addAndGet(cleaned);
            log.info("Cleaned up {} stale in-flight requests", cleaned);
        }

        return cleaned;
    }

    /**
     * Waits for an in-flight request to complete using the default timeout.
     *
     * @param registration the registration result from registerRequest()
     * @return the coalesced result, or empty if timeout/failure
     */
    public Optional<CoalescedResult> awaitResult(RegistrationResult registration) {
        return awaitResult(registration, defaultTimeoutMs);
    }

    /**
     * Waits for an in-flight request to complete.
     *
     * @param registration the registration result from registerRequest()
     * @param timeoutMs    timeout in milliseconds
     * @return the coalesced result, or empty if timeout/failure
     */
    public Optional<CoalescedResult> awaitResult(RegistrationResult registration, long timeoutMs) {
        if (registration.isLeader()) {
            throw new IllegalStateException("Leader should not await its own result");
        }

        try {
            return registration.await(timeoutMs);
        } catch (TimeoutException e) {
            timeouts.incrementAndGet();
            log.warn("Timeout waiting for coalesced request ({}ms)", timeoutMs);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for coalesced request");
            return Optional.empty();
        }
    }

    /**
     * Gets the number of requests that were coalesced (waited for a leader).
     *
     * @return coalesced request count
     */
    public long getCoalescedCount() {
        return coalescedRequests.get();
    }

    /**
     * Gets the number of leader requests (first request for a key).
     *
     * @return leader request count
     */
    public long getLeaderCount() {
        return leaderRequests.get();
    }

    /**
     * Gets the number of timeouts while waiting for coalesced results.
     *
     * @return timeout count
     */
    public long getTimeoutCount() {
        return timeouts.get();
    }

    /**
     * Gets the number of failed in-flight requests.
     *
     * @return failure count
     */
    public long getFailureCount() {
        return failures.get();
    }

    /**
     * Gets the current number of in-flight requests.
     *
     * @return current in-flight count
     */
    public int getInFlightCount() {
        return inFlightRequests.size();
    }

    /**
     * Gets the number of forced leadership takeovers.
     *
     * @return forced takeover count
     */
    public long getForcedTakeoverCount() {
        return forcedTakeovers.get();
    }

    /**
     * Gets the number of stale entries that have been cleaned up.
     *
     * @return stale entries cleaned count
     */
    public long getStaleEntriesCleanedCount() {
        return staleEntriesCleaned.get();
    }

    /**
     * Resets all statistics counters.
     */
    public void resetStats() {
        coalescedRequests.set(0);
        leaderRequests.set(0);
        timeouts.set(0);
        failures.set(0);
        forcedTakeovers.set(0);
        staleEntriesCleaned.set(0);
    }
}

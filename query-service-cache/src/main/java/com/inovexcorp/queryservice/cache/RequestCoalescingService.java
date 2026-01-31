package com.inovexcorp.queryservice.cache;

import lombok.extern.slf4j.Slf4j;

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

    // Map of cache key -> future that will complete when the in-flight request finishes
    private final ConcurrentHashMap<String, CompletableFuture<CoalescedResult>> inFlightRequests = new ConcurrentHashMap<>();

    // Statistics
    private final AtomicLong coalescedRequests = new AtomicLong(0);
    private final AtomicLong leaderRequests = new AtomicLong(0);
    private final AtomicLong timeouts = new AtomicLong(0);
    private final AtomicLong failures = new AtomicLong(0);

    private final boolean enabled;
    private final long defaultTimeoutMs;

    /**
     * Creates a new RequestCoalescingService.
     *
     * @param enabled         whether coalescing is enabled
     * @param defaultTimeoutMs default timeout for waiting on in-flight requests
     */
    public RequestCoalescingService(boolean enabled, long defaultTimeoutMs) {
        this.enabled = enabled;
        this.defaultTimeoutMs = defaultTimeoutMs;
        log.info("RequestCoalescingService initialized: enabled={}, defaultTimeoutMs={}", enabled, defaultTimeoutMs);
    }

    /**
     * Checks if coalescing is enabled.
     *
     * @return true if coalescing is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

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

        CompletableFuture<CoalescedResult> newFuture = new CompletableFuture<>();
        CompletableFuture<CoalescedResult> existingFuture = inFlightRequests.putIfAbsent(cacheKey, newFuture);

        if (existingFuture == null) {
            // No existing request - this is the leader
            leaderRequests.incrementAndGet();
            log.debug("Registered as leader for cache key: {}", cacheKey);
            return new RegistrationResult(true, newFuture);
        } else {
            // Existing request found - this is a follower
            coalescedRequests.incrementAndGet();
            log.debug("Coalescing request for cache key: {} (waiting for leader)", cacheKey);
            return new RegistrationResult(false, existingFuture);
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
        CompletableFuture<CoalescedResult> future = inFlightRequests.remove(cacheKey);
        if (future != null) {
            future.complete(CoalescedResult.success(result));
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
        CompletableFuture<CoalescedResult> future = inFlightRequests.remove(cacheKey);
        if (future != null) {
            failures.incrementAndGet();
            future.complete(CoalescedResult.failure(errorMessage));
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
        CompletableFuture<CoalescedResult> future = inFlightRequests.remove(cacheKey);
        if (future != null) {
            future.cancel(false);
            log.debug("Cancelled in-flight request for cache key: {}", cacheKey);
        }
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
     * Gets the default timeout for waiting on in-flight requests.
     *
     * @return timeout in milliseconds
     */
    public long getDefaultTimeoutMs() {
        return defaultTimeoutMs;
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
     * Resets all statistics counters.
     */
    public void resetStats() {
        coalescedRequests.set(0);
        leaderRequests.set(0);
        timeouts.set(0);
        failures.set(0);
    }
}

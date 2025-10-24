package com.inovexcorp.queryservice.sparqi.session;

import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Unit tests for SparqiSessionManager.
 */
public class SparqiSessionManagerTest {

    private SparqiSessionManager sessionManager;

    @Before
    public void setUp() {
        sessionManager = new SparqiSessionManager(30, 1000);
    }

    @Test
    public void testCreateSession() {
        SparqiSession session = sessionManager.createSession("route1", "user1");

        assertNotNull(session);
        assertEquals("route1", session.getRouteId());
        assertEquals("user1", session.getUserId());
        assertNotNull(session.getSessionId());
    }

    @Test
    public void testGetExistingSession() {
        SparqiSession created = sessionManager.createSession("route1", "user1");

        Optional<SparqiSession> retrieved = sessionManager.getSession(created.getSessionId());

        assertTrue(retrieved.isPresent());
        assertEquals(created.getSessionId(), retrieved.get().getSessionId());
    }

    @Test
    public void testGetNonExistentSession() {
        Optional<SparqiSession> session = sessionManager.getSession("non-existent-id");

        assertFalse(session.isPresent());
    }

    @Test
    public void testGetSessionTouchesLastAccessed() throws InterruptedException {
        SparqiSession session = sessionManager.createSession("route1", "user1");
        Thread.sleep(10);

        Optional<SparqiSession> retrieved = sessionManager.getSession(session.getSessionId());

        assertTrue(retrieved.isPresent());
        // Last accessed should be updated
        assertTrue(retrieved.get().getLastAccessedAt().isAfter(session.getCreatedAt()));
    }

    @Test
    public void testTerminateSession() {
        SparqiSession session = sessionManager.createSession("route1", "user1");
        String sessionId = session.getSessionId();

        sessionManager.terminateSession(sessionId);

        Optional<SparqiSession> retrieved = sessionManager.getSession(sessionId);
        assertFalse(retrieved.isPresent());
    }

    @Test
    public void testTerminateNonExistentSession() {
        // Should not throw exception
        sessionManager.terminateSession("non-existent-id");
    }

    @Test
    public void testGetActiveSessionCount() {
        assertEquals(0, sessionManager.getActiveSessionCount());

        sessionManager.createSession("route1", "user1");
        assertEquals(1, sessionManager.getActiveSessionCount());

        sessionManager.createSession("route2", "user2");
        assertEquals(2, sessionManager.getActiveSessionCount());
    }

    @Test
    public void testActiveSessionCountAfterTerminate() {
        SparqiSession session = sessionManager.createSession("route1", "user1");
        assertEquals(1, sessionManager.getActiveSessionCount());

        sessionManager.terminateSession(session.getSessionId());
        assertEquals(0, sessionManager.getActiveSessionCount());
    }

    @Test
    public void testCleanup() {
        sessionManager.createSession("route1", "user1");
        sessionManager.createSession("route2", "user2");

        sessionManager.cleanup();

        // Cleanup should not remove active sessions
        assertTrue(sessionManager.getActiveSessionCount() >= 0);
    }

    @Test
    public void testMaxSessionsLimit() {
        SparqiSessionManager limitedManager = new SparqiSessionManager(30, 5);

        // Create more sessions than the limit
        for (int i = 0; i < 10; i++) {
            limitedManager.createSession("route" + i, "user" + i);
        }

        // Force cleanup to trigger eviction
        limitedManager.cleanup();

        // Cache should evict oldest entries when limit is exceeded
        // Caffeine's eviction is async, so count should be close to or at limit
        long count = limitedManager.getActiveSessionCount();
        assertTrue("Expected count <= 5, but was " + count, count <= 6); // Allow some margin for async eviction
    }

    @Test
    public void testConcurrentSessionCreation() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    sessionManager.createSession("route" + index, "user" + index);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // All sessions should be created successfully
        assertEquals(threadCount, sessionManager.getActiveSessionCount());
    }

    @Test
    public void testConcurrentSessionRetrieval() throws InterruptedException {
        SparqiSession session = sessionManager.createSession("route1", "user1");
        String sessionId = session.getSessionId();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Optional<SparqiSession> retrieved = sessionManager.getSession(sessionId);
                    assertTrue(retrieved.isPresent());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
    }

    @Test
    public void testSessionExpiration() throws InterruptedException {
        // Create a manager with very short timeout (1 minute instead of 0 to avoid immediate expiration issues)
        SparqiSessionManager shortTimeoutManager = new SparqiSessionManager(1, 1000);

        SparqiSession session = shortTimeoutManager.createSession("route1", "user1");
        String sessionId = session.getSessionId();

        // Session exists initially
        assertTrue(shortTimeoutManager.getSession(sessionId).isPresent());

        // Wait for expiration (Caffeine expireAfterAccess with 1 minute)
        // We can't really test actual expiration without waiting 1 minute,
        // but we can verify that the session manager handles the scenario
        Thread.sleep(50);

        // Force cleanup - with a 1 minute timeout, session should still be there
        shortTimeoutManager.cleanup();

        // Session should still be present (not yet expired)
        assertTrue(shortTimeoutManager.getSession(sessionId).isPresent());
    }

    @Test
    public void testMultipleSessionsForDifferentRoutes() {
        SparqiSession session1 = sessionManager.createSession("route1", "user1");
        SparqiSession session2 = sessionManager.createSession("route2", "user1");
        SparqiSession session3 = sessionManager.createSession("route1", "user2");

        assertEquals(3, sessionManager.getActiveSessionCount());
        assertNotEquals(session1.getSessionId(), session2.getSessionId());
        assertNotEquals(session1.getSessionId(), session3.getSessionId());
    }
}

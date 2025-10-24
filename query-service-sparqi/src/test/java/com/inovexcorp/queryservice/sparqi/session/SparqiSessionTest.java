package com.inovexcorp.queryservice.sparqi.session;

import com.inovexcorp.queryservice.sparqi.model.SparqiContext;
import com.inovexcorp.queryservice.sparqi.model.SparqiMessage;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for SparqiSession.
 */
public class SparqiSessionTest {

    private SparqiSession session;

    @Before
    public void setUp() {
        session = new SparqiSession("test-route", "test-user");
    }

    @Test
    public void testSessionInitialization() {
        assertNotNull(session.getSessionId());
        assertEquals("test-route", session.getRouteId());
        assertEquals("test-user", session.getUserId());
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getLastAccessedAt());
        assertTrue(session.getMessages().isEmpty());
    }

    @Test
    public void testSessionIdIsUnique() {
        SparqiSession session1 = new SparqiSession("route1", "user1");
        SparqiSession session2 = new SparqiSession("route1", "user1");

        assertNotEquals(session1.getSessionId(), session2.getSessionId());
    }

    @Test
    public void testAddMessage() {
        SparqiMessage message = SparqiMessage.userMessage("Hello");

        session.addMessage(message);

        assertEquals(1, session.getMessages().size());
        assertEquals(message, session.getMessages().get(0));
    }

    @Test
    public void testAddMessageUpdatesLastAccessed() throws InterruptedException {
        Instant before = session.getLastAccessedAt();
        Thread.sleep(10);

        session.addMessage(SparqiMessage.userMessage("Test"));

        assertTrue(session.getLastAccessedAt().isAfter(before));
    }

    @Test
    public void testAddMultipleMessages() {
        session.addMessage(SparqiMessage.userMessage("Message 1"));
        session.addMessage(SparqiMessage.assistantMessage("Response 1"));
        session.addMessage(SparqiMessage.userMessage("Message 2"));

        assertEquals(3, session.getMessages().size());
    }

    @Test
    public void testTouch() throws InterruptedException {
        Instant before = session.getLastAccessedAt();
        Thread.sleep(10);

        session.touch();

        assertTrue(session.getLastAccessedAt().isAfter(before));
    }

    @Test
    public void testContextManagement() {
        SparqiContext context = new SparqiContext(
                "test-route",
                "template",
                "description",
                "http://graphmart.com",
                Arrays.asList("layer1"),
                "http://datasource.com",
                10
        );

        session.setContext(context);

        assertEquals(context, session.getContext());
        assertEquals("test-route", session.getContext().getRouteId());
    }

    @Test
    public void testIsExpired() throws InterruptedException {
        // Not expired immediately
        assertFalse(session.isExpired(30));

        // Simulate old session by not touching it
        Thread.sleep(100);
        assertFalse(session.isExpired(30)); // Still not expired with 30 min timeout

        // Test with very short timeout (less than 1 second = 0 minutes)
        assertTrue(session.isExpired(0));
    }

    @Test
    public void testGetRecentMessagesWithLessThanLimit() {
        session.addMessage(SparqiMessage.userMessage("Message 1"));
        session.addMessage(SparqiMessage.assistantMessage("Response 1"));
        session.addMessage(SparqiMessage.userMessage("Message 2"));

        List<SparqiMessage> recent = session.getRecentMessages(10);

        assertEquals(3, recent.size());
    }

    @Test
    public void testGetRecentMessagesWithExactLimit() {
        session.addMessage(SparqiMessage.userMessage("Message 1"));
        session.addMessage(SparqiMessage.assistantMessage("Response 1"));
        session.addMessage(SparqiMessage.userMessage("Message 2"));

        List<SparqiMessage> recent = session.getRecentMessages(3);

        assertEquals(3, recent.size());
    }

    @Test
    public void testGetRecentMessagesWithExceedingLimit() {
        for (int i = 0; i < 10; i++) {
            session.addMessage(SparqiMessage.userMessage("Message " + i));
        }

        List<SparqiMessage> recent = session.getRecentMessages(5);

        assertEquals(5, recent.size());
        // Should get the last 5 messages
        assertEquals("Message 5", recent.get(0).getContent());
        assertEquals("Message 9", recent.get(4).getContent());
    }

    @Test
    public void testGetRecentMessagesEmptySession() {
        List<SparqiMessage> recent = session.getRecentMessages(10);

        assertTrue(recent.isEmpty());
    }

    @Test
    public void testGetRecentMessagesIsCopy() {
        session.addMessage(SparqiMessage.userMessage("Test"));

        List<SparqiMessage> recent = session.getRecentMessages(10);
        recent.clear();

        // Original messages should not be affected
        assertEquals(1, session.getMessages().size());
    }
}

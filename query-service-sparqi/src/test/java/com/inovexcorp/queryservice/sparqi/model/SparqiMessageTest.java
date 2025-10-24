package com.inovexcorp.queryservice.sparqi.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.*;

/**
 * Unit tests for SparqiMessage.
 */
public class SparqiMessageTest {

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    public void testUserMessageFactory() {
        String content = "Hello, SPARQi!";
        SparqiMessage message = SparqiMessage.userMessage(content);

        assertEquals(SparqiMessage.MessageRole.USER, message.getRole());
        assertEquals(content, message.getContent());
        assertNotNull(message.getTimestamp());
    }

    @Test
    public void testAssistantMessageFactory() {
        String content = "Hello! How can I help you?";
        SparqiMessage message = SparqiMessage.assistantMessage(content);

        assertEquals(SparqiMessage.MessageRole.ASSISTANT, message.getRole());
        assertEquals(content, message.getContent());
        assertNotNull(message.getTimestamp());
    }

    @Test
    public void testSystemMessageFactory() {
        String content = "System initialized";
        SparqiMessage message = SparqiMessage.systemMessage(content);

        assertEquals(SparqiMessage.MessageRole.SYSTEM, message.getRole());
        assertEquals(content, message.getContent());
        assertNotNull(message.getTimestamp());
    }

    @Test
    public void testJsonSerialization() throws Exception {
        SparqiMessage original = new SparqiMessage(
                SparqiMessage.MessageRole.USER,
                "Test message",
                new Date()
        );

        String json = objectMapper.writeValueAsString(original);
        assertNotNull(json);
        assertTrue(json.contains("\"role\":\"user\""));
        assertTrue(json.contains("Test message"));
    }

    @Test
    public void testJsonDeserialization() throws Exception {
        String json = "{\"role\":\"assistant\",\"content\":\"Test response\",\"timestamp\":\"2024-01-15T10:30:00.000Z\"}";

        SparqiMessage message = objectMapper.readValue(json, SparqiMessage.class);

        assertEquals(SparqiMessage.MessageRole.ASSISTANT, message.getRole());
        assertEquals("Test response", message.getContent());
        assertNotNull(message.getTimestamp());
    }

    @Test
    public void testMessageRoleEnum() {
        assertEquals(SparqiMessage.MessageRole.USER, SparqiMessage.MessageRole.valueOf("USER"));
        assertEquals(SparqiMessage.MessageRole.ASSISTANT, SparqiMessage.MessageRole.valueOf("ASSISTANT"));
        assertEquals(SparqiMessage.MessageRole.SYSTEM, SparqiMessage.MessageRole.valueOf("SYSTEM"));
    }

    @Test
    public void testTimestampGeneration() throws InterruptedException {
        SparqiMessage message1 = SparqiMessage.userMessage("First");
        Thread.sleep(10); // Small delay to ensure different timestamps
        SparqiMessage message2 = SparqiMessage.userMessage("Second");

        assertTrue(message2.getTimestamp().getTime() >= message1.getTimestamp().getTime());
    }
}

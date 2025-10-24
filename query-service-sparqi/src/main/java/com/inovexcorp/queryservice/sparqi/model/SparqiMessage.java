package com.inovexcorp.queryservice.sparqi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Represents a single message in a SPARQi conversation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SparqiMessage {

    @JsonProperty("role")
    private MessageRole role;

    @JsonProperty("content")
    private String content;

    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Date timestamp;

    /**
     * Role of the message sender.
     */
    public enum MessageRole {
        @JsonProperty("user")
        USER,

        @JsonProperty("assistant")
        ASSISTANT,

        @JsonProperty("system")
        SYSTEM
    }

    /**
     * Creates a new user message with the given content.
     * The message is assigned the role of 'USER' and a timestamp representing the current date and time.
     *
     * @param content the content of the user message
     * @return a new instance of {@code SparqiMessage} with a role of {@code MessageRole.USER},
     *         the given content, and a timestamp of the current date and time
     */
    public static SparqiMessage userMessage(String content) {
        return new SparqiMessage(MessageRole.USER, content, new Date());
    }

    /**
     * Creates a new assistant message with the given content.
     * The message is assigned the role of 'ASSISTANT' and a timestamp representing the current date and time.
     *
     * @param content the content of the assistant message
     * @return a new instance of {@code SparqiMessage} with a role of {@code MessageRole.ASSISTANT},
     *         the given content, and a timestamp of the current date and time
     */
    public static SparqiMessage assistantMessage(String content) {
        return new SparqiMessage(MessageRole.ASSISTANT, content, new Date());
    }

    /**
     * Creates a new system message with the specified content.
     * The message is assigned the role of 'SYSTEM' and a timestamp representing the current date and time.
     *
     * @param content the content of the system message
     * @return a new instance of {@code SparqiMessage} with a role of {@code MessageRole.SYSTEM},
     *         the given content, and a timestamp of the current date and time
     */
    public static SparqiMessage systemMessage(String content) {
        return new SparqiMessage(MessageRole.SYSTEM, content, new Date());
    }
}

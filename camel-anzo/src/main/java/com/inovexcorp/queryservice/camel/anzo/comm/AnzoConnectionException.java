package com.inovexcorp.queryservice.camel.anzo.comm;

/**
 * Exception thrown when there is a network connectivity issue reaching the Anzo server.
 * This includes timeouts, connection refused, and other network-level failures.
 */
public class AnzoConnectionException extends QueryException {

    private final String host;
    private final long durationMs;

    public AnzoConnectionException(String msg, String host, long durationMs) {
        super(msg);
        this.host = host;
        this.durationMs = durationMs;
    }

    public AnzoConnectionException(String msg, Throwable cause, String host, long durationMs) {
        super(msg, cause);
        this.host = host;
        this.durationMs = durationMs;
    }

    public String getHost() {
        return host;
    }

    public long getDurationMs() {
        return durationMs;
    }

    @Override
    public String getMessage() {
        return String.format("%s [host=%s, duration=%dms]", super.getMessage(), host, durationMs);
    }
}

package com.inovexcorp.queryservice.camel.anzo.comm;

/**
 * Exception thrown when authentication with the Anzo server fails (HTTP 401).
 */
public class AnzoAuthenticationException extends QueryException {

    private final String host;
    private final int httpStatus;

    public AnzoAuthenticationException(String msg, String host, int httpStatus) {
        super(msg);
        this.host = host;
        this.httpStatus = httpStatus;
    }

    public String getHost() {
        return host;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getMessage() {
        return String.format("%s [host=%s, httpStatus=%d]", super.getMessage(), host, httpStatus);
    }
}

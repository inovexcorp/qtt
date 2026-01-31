package com.inovexcorp.queryservice.camel.anzo.auth;

import lombok.Getter;

/**
 * Result of a bearer token validation attempt.
 */
@Getter
public class TokenValidationResult {

    private final boolean valid;
    private final String token;
    private final String errorMessage;

    private TokenValidationResult(boolean valid, String token, String errorMessage) {
        this.valid = valid;
        this.token = token;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful validation result.
     *
     * @param token The validated token
     * @return A successful TokenValidationResult
     */
    public static TokenValidationResult success(String token) {
        return new TokenValidationResult(true, token, null);
    }

    /**
     * Creates a failed validation result.
     *
     * @param errorMessage The reason for failure
     * @return A failed TokenValidationResult
     */
    public static TokenValidationResult failure(String errorMessage) {
        return new TokenValidationResult(false, null, errorMessage);
    }

    @Override
    public String toString() {
        if (valid) {
            return "TokenValidationResult{valid=true}";
        } else {
            return "TokenValidationResult{valid=false, error='" + errorMessage + "'}";
        }
    }
}

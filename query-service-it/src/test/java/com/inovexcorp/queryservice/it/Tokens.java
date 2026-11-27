package com.inovexcorp.queryservice.it;

import io.restassured.response.Response;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.restassured.RestAssured.given;

/** Access tokens from the Keycloak realm of an OIDC stack, cached until shortly before they expire. */
final class Tokens {

    private record Cached(String token, long expiresAtMillis) {
    }

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private Tokens() {
    }

    /** A token for a service account (client-credentials grant). */
    static String clientCredentials(String clientId, String secret) {
        return cached("cc:" + clientId, () -> given().formParam("grant_type", "client_credentials")
                .formParam("client_id", clientId).formParam("client_secret", secret)
                .post(Qtt.OIDC_REALM + "/protocol/openid-connect/token"));
    }

    /** A token for a test user (password grant on the qtt client; the realm's passwords are "<user>-pw"). */
    static String user(String username) {
        return cached("user:" + username, () -> given().formParam("grant_type", "password")
                .formParam("client_id", "qtt").formParam("client_secret", "qtt-test-secret")
                .formParam("username", username).formParam("password", username + "-pw")
                .formParam("scope", "openid")
                .post(Qtt.OIDC_REALM + "/protocol/openid-connect/token"));
    }

    private static String cached(String key, java.util.function.Supplier<Response> request) {
        Cached c = CACHE.get(key);
        if (c != null && System.currentTimeMillis() < c.expiresAtMillis()) {
            return c.token();
        }
        Response r = request.get();
        if (r.statusCode() != 200) {
            throw new AssertionError("token request for " + key + " failed: HTTP " + r.statusCode() + " " + r.asString());
        }
        String token = r.jsonPath().getString("access_token");
        long lifetime = r.jsonPath().getLong("expires_in") * 1000;
        CACHE.put(key, new Cached(token, System.currentTimeMillis() + Math.max(0, lifetime - 30_000)));
        return token;
    }
}

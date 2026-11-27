package com.inovexcorp.queryservice.it;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.CookieHandler;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal browser for the sign-in flow: keeps cookies, follows no redirects on its own (tests look at each hop),
 * and trusts the stack's self-signed certificate.
 */
final class Browser {

    static {
        // The stack's certificate is self-signed and not issued for "localhost"; read once, when the JDK's HTTP
        // client is first used.
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
    }

    private final HttpClient client;

    Browser() {
        try {
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, null);
            client = HttpClient.newBuilder()
                    .cookieHandler(new Jar())
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .sslContext(ssl)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    HttpResponse<String> get(String url, String... headers) {
        return send(HttpRequest.newBuilder(URI.create(url)).GET(), headers);
    }

    HttpResponse<String> send(String method, String url, String... headers) {
        return send(HttpRequest.newBuilder(URI.create(url)).method(method, HttpRequest.BodyPublishers.noBody()), headers);
    }

    /**
     * Signs in through the Keycloak login form, starting at a QTT page, and returns the final response after the
     * IdP has sent the browser back (the page that was asked for).
     */
    HttpResponse<String> signIn(String startUrl, String username, String password) {
        HttpResponse<String> toIdp = get(startUrl);
        assertRedirect(toIdp, Qtt.OIDC_REALM + "/protocol/openid-connect/auth");
        HttpResponse<String> form = get(location(toIdp));
        Matcher action = Pattern.compile("action=\"([^\"]+)\"").matcher(form.body());
        if (!action.find()) {
            throw new AssertionError("no login form at " + location(toIdp) + ": " + form.body());
        }
        String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpResponse<String> back = send(HttpRequest.newBuilder(URI.create(action.group(1).replace("&amp;", "&")))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
        assertRedirect(back, Qtt.UI);
        HttpResponse<String> callback = get(location(back));
        assertRedirect(callback, Qtt.UI);
        return get(location(callback));
    }

    static String location(HttpResponse<?> response) {
        return response.headers().firstValue("Location")
                .orElseThrow(() -> new AssertionError("no Location header; HTTP " + response.statusCode()));
    }

    static void assertRedirect(HttpResponse<?> response, String locationPrefix) {
        if (response.statusCode() != 302 && response.statusCode() != 303) {
            throw new AssertionError("expected a redirect to " + locationPrefix + ", got HTTP " + response.statusCode());
        }
        if (!location(response).startsWith(locationPrefix)) {
            throw new AssertionError("expected a redirect to " + locationPrefix + ", got " + location(response));
        }
    }

    /**
     * Cookies per host (ports and attributes ignored). The JDK's CookieManager will not send Keycloak's Secure cookies
     * back over the test realm's plain http://localhost, which browsers allow as a secure context.
     */
    private static final class Jar extends CookieHandler {
        private final Map<String, Map<String, String>> byHost = new ConcurrentHashMap<>();

        @Override
        public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) {
            Map<String, String> cookies = byHost.getOrDefault(uri.getHost(), Map.of());
            if (cookies.isEmpty()) {
                return Map.of();
            }
            StringBuilder header = new StringBuilder();
            cookies.forEach((name, value) -> header.append(header.isEmpty() ? "" : "; ").append(name).append('=').append(value));
            return Map.of("Cookie", List.of(header.toString()));
        }

        @Override
        public void put(URI uri, Map<String, List<String>> responseHeaders) {
            Map<String, String> cookies = byHost.computeIfAbsent(uri.getHost(), h -> new LinkedHashMap<>());
            responseHeaders.forEach((name, values) -> {
                if (!"set-cookie".equalsIgnoreCase(name)) {
                    return;
                }
                for (String value : values) {
                    String[] parts = value.split(";");
                    int eq = parts[0].indexOf('=');
                    String cookie = parts[0].substring(0, eq).trim();
                    String content = parts[0].substring(eq + 1).trim();
                    boolean expired = content.isEmpty();
                    for (String attribute : parts) {
                        expired |= attribute.trim().equalsIgnoreCase("Max-Age=0");
                    }
                    if (expired) {
                        cookies.remove(cookie);
                    } else {
                        cookies.put(cookie, content);
                    }
                }
            });
        }
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String... headers) {
        for (int i = 0; i + 1 < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        try {
            return client.send(request.timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

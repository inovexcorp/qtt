package com.inovexcorp.queryservice.it;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Thin HTTP client for the QTT management API, the query port and the WireMock admin API.
 * Everything goes over HTTP so the suite is independent of the runtime under test.
 */
public final class Qtt {

    public static final String API = System.getProperty("qtt.api", "https://localhost:18443/queryrest/api");
    public static final String UI = System.getProperty("qtt.ui", "https://localhost:18443");
    /** Base URL of the query routes; a route is at QUERY + "/" + routeId. */
    public static final String QUERY = System.getProperty("qtt.query", "http://localhost:18082/query");
    public static final String WIREMOCK = System.getProperty("qtt.wiremock", "http://localhost:18080");
    /** Anzo base URL as seen from inside the QTT container. */
    public static final String ANZO_INTERNAL = System.getProperty("qtt.anzo.internal", "http://wiremock:8080");
    public static final boolean REDIS = Boolean.parseBoolean(System.getProperty("qtt.redis", "false"));
    /** Database behind the stack under test: derby, postgres or mssql. */
    public static final String DB = System.getProperty("qtt.db", "derby");
    /** True when the stack requires sign-in (scripts/stack.sh with QTT_OIDC=true). */
    public static final boolean OIDC = Boolean.parseBoolean(System.getProperty("qtt.oidc", "false"));
    /** The Keycloak realm of an OIDC stack, as seen from the test (scripts/keycloak/qtt-realm.json). */
    public static final String OIDC_REALM = System.getProperty("qtt.oidc.realm", "http://localhost:18180/realms/qtt");

    /** Graphmart and layer recorded from the Anzo test system (see wiremock/__files). */
    public static final String GRAPHMART = "http://cambridgesemantics.com/Graphmart/93794a1ba2a24246a0f33c4340858d80";
    public static final String LAYER = "http://cambridgesemantics.com/Layer/67c99b630d494b4da6dc5a0a11cb717e";

    /** A template that renders a CONSTRUCT using a header (query string) parameter and a body value. */
    public static final String TEMPLATE = """
            <#ftl output_format="plainText" encoding="UTF-8" strip_whitespace=true>
            <#if body?has_content><#assign b = body?eval_json><#else><#assign b = {}></#if>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            CONSTRUCT { ?s a ?t ; rdfs:label ?l }
            WHERE {
              ?s a ?t ; rdfs:label ?l .
              <#if headers.label??>FILTER(CONTAINS(STR(?l), "${headers.label}"))</#if>
              <#if b.marker??># marker: ${b.marker}</#if>
            }
            LIMIT 25""";

    static {
        RestAssured.config = RestAssuredConfig.config()
                .sslConfig(SSLConfig.sslConfig().relaxedHTTPSValidation().allowAllHostnames())
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 10_000)
                        .setParam("http.socket.timeout", 60_000));
    }

    private Qtt() {
    }

    public static RequestSpecification api() {
        return signedIn().spec(new RequestSpecBuilder().setBaseUri(API).setAccept("application/json").build());
    }

    /**
     * A request that is authenticated when the stack requires sign-in: it carries a bearer token for the
     * {@code qtt-ci} service account (qtt-admin), so every test runs unchanged with OIDC on or off.
     */
    public static RequestSpecification signedIn() {
        RequestSpecification spec = given();
        return OIDC ? spec.header("Authorization", "Bearer " + Tokens.clientCredentials("qtt-ci", "qtt-ci-test-secret"))
                : spec;
    }

    public static RequestSpecification query() {
        java.net.URI base = java.net.URI.create(QUERY);
        return given().spec(new RequestSpecBuilder().setBaseUri(base.getScheme() + "://" + base.getAuthority())
                .setBasePath(base.getPath()).build());
    }

    public static String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------------------------------------------------------------- datasources

    public static Map<String, Object> datasource(String id, String url) {
        Map<String, Object> ds = new LinkedHashMap<>();
        ds.put("dataSourceId", id);
        ds.put("timeOutSeconds", "30");
        ds.put("maxQueryHeaderLength", "8192");
        ds.put("username", "sysadmin");
        ds.put("password", "mock-password");
        ds.put("url", url);
        ds.put("validateCertificate", false);
        return ds;
    }

    public static void createDatasource(String id) {
        createDatasource(id, ANZO_INTERNAL);
    }

    public static void createDatasource(String id, String url) {
        api().contentType(ContentType.JSON).body(datasource(id, url))
                .post("/datasources").then().statusCode(201);
    }

    public static void deleteDatasourceQuietly(String id) {
        api().delete("/datasources/" + id);
    }

    // ---------------------------------------------------------------- routes

    public static Response createRoute(String routeId, String datasourceId, String graphmart, String template,
                                       String layers, Map<String, ?> extraQueryParams) {
        RequestSpecification req = api()
                .queryParam("routeParams", "httpMethodRestrict=GET,POST")
                .queryParam("dataSourceId", datasourceId)
                .queryParam("description", "IT route " + routeId)
                .queryParam("graphMartUri", graphmart);
        if (extraQueryParams != null) {
            extraQueryParams.forEach(req::queryParam);
        }
        // The UI sends both fields as Blob parts of a multipart form; mirror that exactly.
        return req.multiPart("freemarker", "blob", template.getBytes(StandardCharsets.UTF_8), "multipart/form-data")
                .multiPart("layers", "blob", layers.getBytes(StandardCharsets.UTF_8), "multipart/form-data")
                .post("/routes/" + routeId);
    }

    public static void createRoute(String routeId, String datasourceId) {
        createRoute(routeId, datasourceId, GRAPHMART, TEMPLATE, LAYER, null).then().statusCode(201);
    }

    public static Response modifyRoute(String routeId, String datasourceId, String graphmart, String template,
                                       String layers, Map<String, ?> extraQueryParams) {
        RequestSpecification req = api()
                .queryParam("routeParams", "httpMethodRestrict=GET,POST")
                .queryParam("dataSourceId", datasourceId)
                .queryParam("description", "IT route modified " + routeId)
                .queryParam("graphMartUri", graphmart);
        if (extraQueryParams != null) {
            extraQueryParams.forEach(req::queryParam);
        }
        return req.multiPart("freemarker", "blob", template.getBytes(StandardCharsets.UTF_8), "multipart/form-data")
                .multiPart("layers", "blob", layers.getBytes(StandardCharsets.UTF_8), "multipart/form-data")
                .put("/routes/" + routeId);
    }

    public static void deleteRouteQuietly(String routeId) {
        api().delete("/routes/" + routeId);
    }

    /**
     * Asserts nothing serves the route. An anonymous caller gets 401 for a route that doesn't exist (the same as for a
     * protected one, so route names can't be probed) and 404 for a stopped open route; a caller with a token (OIDC
     * stacks) gets 404 either way.
     */
    public static void assertNotServed(String routeId) {
        try {
            int status = query().get("/" + routeId).statusCode();
            if (status != 401 && status != 404) {
                throw new AssertionError("expected route " + routeId + " not to be served (anonymous: 401 or 404), "
                        + "got HTTP " + status);
            }
            if (OIDC) {
                int withToken = query().auth().oauth2(Tokens.clientCredentials("qtt-reader", "qtt-reader-test-secret"))
                        .get("/" + routeId).statusCode();
                if (withToken != 404) {
                    throw new AssertionError("expected route " + routeId + " not to be served (with a token: 404), "
                            + "got HTTP " + withToken);
                }
            }
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            // connection refused / reset: nothing listening
        }
    }

    // ---------------------------------------------------------------- WireMock (mock Anzo)

    public static int anzoRequestCount(String urlPathPattern) {
        return given().baseUri(WIREMOCK).contentType(ContentType.JSON)
                .body(Map.of("method", "POST", "urlPathPattern", urlPathPattern))
                .post("/__admin/requests/count").then().statusCode(200)
                .extract().path("count");
    }

    public static int anzoRequestCountWithBody(String urlPathPattern, String bodyContains) {
        return given().baseUri(WIREMOCK).contentType(ContentType.JSON)
                .body(Map.of("method", "POST", "urlPathPattern", urlPathPattern,
                        "bodyPatterns", List.of(Map.of("contains", bodyContains))))
                .post("/__admin/requests/count").then().statusCode(200)
                .extract().path("count");
    }

    /** Bodies of all requests to the mock that match the given path pattern, newest first. */
    public static List<String> anzoRequestBodies(String urlPathPattern) {
        return given().baseUri(WIREMOCK).contentType(ContentType.JSON)
                .body(Map.of("method", "POST", "urlPathPattern", urlPathPattern))
                .post("/__admin/requests/find").then().statusCode(200)
                .extract().jsonPath().getList("requests.body", String.class);
    }

    /** The decoded SPARQL of the newest request to the mock whose body contains the (unencoded) marker. */
    public static String anzoQueryContaining(String marker) {
        List<String> bodies = anzoRequestBodies("/sparql/graphmart/.*");
        String found = null;
        for (String form : bodies) {
            for (String part : form.split("&")) {
                if (part.startsWith("query=")) {
                    String q = java.net.URLDecoder.decode(part.substring(6), StandardCharsets.UTF_8);
                    if (q.contains(marker)) {
                        found = q;
                    }
                }
            }
        }
        if (found == null) {
            throw new AssertionError("no Anzo request contained " + marker);
        }
        return found;
    }

    /** The Authorization header of the newest request to the mock whose (form-encoded) body contains the marker. */
    public static String anzoAuthorizationForBodyContaining(String marker) {
        List<String> headers = given().baseUri(WIREMOCK).contentType(ContentType.JSON)
                .body(Map.of("method", "POST", "urlPathPattern", "/sparql/graphmart/.*",
                        "bodyPatterns", List.of(Map.of("contains", marker))))
                .post("/__admin/requests/find").then().statusCode(200)
                .extract().jsonPath().getList("requests.headers.Authorization", String.class);
        if (headers.isEmpty()) {
            throw new AssertionError("no Anzo request contained " + marker);
        }
        return headers.get(0);
    }

    /** Path segment for a graphmart, URL-encoded the way the Anzo client encodes it. */
    public static String encodedGraphmart(String graphmart) {
        return java.net.URLEncoder.encode(graphmart, StandardCharsets.UTF_8);
    }
}

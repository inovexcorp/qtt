package com.inovexcorp.queryservice.app.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/** A WireMock stand-in for Anzo answering every request with a small RDF graph, plus route/datasource helpers. */
final class AnzoStub {

    static final String RDF = "<?xml version=\"1.0\"?><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" "
            + "xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema#\"><rdf:Description rdf:about=\"urn:x\">"
            + "<rdfs:label>X</rdfs:label></rdf:Description></rdf:RDF>";

    private AnzoStub() {
    }

    static WireMockServer start() {
        WireMockServer anzo = new WireMockServer(wireMockConfig().dynamicPort());
        anzo.start();
        anzo.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/rdf+xml;charset=utf-8").withBody(RDF)));
        return anzo;
    }

    static void datasource(RequestSpecification api, String id, WireMockServer anzo) {
        api.contentType("application/json")
                .body("{\"dataSourceId\":\"" + id + "\",\"timeOutSeconds\":\"30\",\"maxQueryHeaderLength\":\"8192\","
                        + "\"username\":\"u\",\"password\":\"p\",\"url\":\"http://localhost:" + anzo.port()
                        + "\",\"validateCertificate\":false}")
                .when().post("/queryrest/api/datasources").then().statusCode(201);
    }

    /** Creates (or with {@code put}, replaces) a route; {@code extra} adds query parameters such as accessPolicy. */
    static Response route(RequestSpecification api, boolean put, String routeId, String datasource, String methods,
                          String template, Map<String, String> extra) {
        RequestSpecification req = api.queryParam("routeParams", "httpMethodRestrict=" + methods)
                .queryParam("dataSourceId", datasource)
                .queryParam("description", "test route")
                .queryParam("graphMartUri", "urn:graphmart");
        extra.forEach(req::queryParam);
        req.multiPart("freemarker", "blob", template.getBytes(StandardCharsets.UTF_8), "multipart/form-data")
                .multiPart("layers", "blob", "urn:layer".getBytes(StandardCharsets.UTF_8), "multipart/form-data");
        return put ? req.when().put("/queryrest/api/routes/" + routeId)
                : req.when().post("/queryrest/api/routes/" + routeId);
    }
}

package com.inovexcorp.queryservice.camel.anzo.comm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** HTTP behaviour of SimpleAnzoClient against a real (in-process) server. */
class SimpleAnzoClientConnectionTest {

    private HttpServer server;
    private final List<Integer> clientPorts = new CopyOnWriteArrayList<>();
    private final List<String> contentLengths = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sparql/graphmart/", exchange -> {
            clientPorts.add(exchange.getRemoteAddress().getPort());
            contentLengths.add(exchange.getRequestHeaders().getFirst("Content-Length"));
            try (InputStream in = exchange.getRequestBody()) {
                bodies.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] out = "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"/>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/rdf+xml");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void query(SimpleAnzoClient client, String sparql) throws Exception {
        QueryResponse r = client.queryGraphmart(sparql, "urn:gm", "", AnzoClient.RESPONSE_FORMAT.RDFXML, 10, false);
        try (InputStream body = r.getResponse().body()) {
            body.readAllBytes();
        }
    }

    @Test
    void sendsBodyWithContentLengthAndReusesTheConnection() throws Exception {
        SimpleAnzoClient client = new SimpleAnzoClient("http://127.0.0.1:" + server.getAddress().getPort(),
                "user", "pass", 10);
        query(client, "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } # é 日本");
        query(client, "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 1");
        query(client, "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 2");

        assertThat(bodies.get(0)).startsWith("query=").contains("format=application%2Frdf%2Bxml");
        assertThat(java.net.URLDecoder.decode(bodies.get(0), StandardCharsets.UTF_8)).contains("# é 日本");
        assertThat(contentLengths).allSatisfy(cl -> assertThat(cl).isNotNull());
        assertThat(Integer.parseInt(contentLengths.get(0))).isEqualTo(bodies.get(0).getBytes(StandardCharsets.UTF_8).length);
        // keep-alive: all three queries arrived over the same client connection
        assertThat(clientPorts).hasSize(3);
        assertThat(clientPorts.stream().distinct().count()).isEqualTo(1);
    }
}

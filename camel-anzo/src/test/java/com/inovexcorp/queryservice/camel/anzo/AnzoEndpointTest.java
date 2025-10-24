package com.inovexcorp.queryservice.camel.anzo;

import com.inovexcorp.queryservice.camel.anzo.comm.AnzoClient;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AnzoEndpointTest {

    @Mock
    private AnzoComponent component;

    private AnzoEndpoint endpoint;
    private static final String TEST_URI = "anzo://localhost:8080";
    private static final String TEST_SERVER = "localhost:8080";

    @BeforeEach
    void setUp() {
        endpoint = new AnzoEndpoint(TEST_URI, component, TEST_SERVER);
    }

    @Test
    void shouldCreateEndpointWithCorrectUri() {
        assertThat(endpoint.getEndpointUri()).isEqualTo(TEST_URI);
    }

    @Test
    void shouldSetAndGetServer() {
        assertThat(endpoint.getServer()).isEqualTo(TEST_SERVER);
    }

    @Test
    void shouldSetAndGetGraphmartUri() {
        String graphmartUri = "http://example.com/graphmart";
        endpoint.setGraphmartUri(graphmartUri);
        assertThat(endpoint.getGraphmartUri()).isEqualTo(graphmartUri);
    }

    @Test
    void shouldSetAndGetLayerUris() {
        String layerUris = "http://layer1.com,http://layer2.com";
        endpoint.setLayerUris(layerUris);
        assertThat(endpoint.getLayerUris()).isEqualTo(layerUris);
    }

    @Test
    void shouldSetAndGetUser() {
        String user = "testuser";
        endpoint.setUser(user);
        assertThat(endpoint.getUser()).isEqualTo(user);
    }

    @Test
    void shouldSetAndGetPassword() {
        String password = "testpassword";
        endpoint.setPassword(password);
        assertThat(endpoint.getPassword()).isEqualTo(password);
    }

    @Test
    void shouldSetAndGetTimeoutSeconds() {
        int timeout = 60;
        endpoint.setTimeoutSeconds(timeout);
        assertThat(endpoint.getTimeoutSeconds()).isEqualTo(timeout);
    }

    @Test
    void shouldHaveDefaultTimeoutOf30Seconds() {
        assertThat(endpoint.getTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    void shouldSetAndGetMaxQueryHeaderLength() {
        long maxLength = 16384L;
        endpoint.setMaxQueryHeaderLength(maxLength);
        assertThat(endpoint.getMaxQueryHeaderLength()).isEqualTo(maxLength);
    }

    @Test
    void shouldHaveDefaultMaxQueryHeaderLengthOf8192() {
        assertThat(endpoint.getMaxQueryHeaderLength()).isEqualTo(8192L);
    }

    @Test
    void shouldSetAndGetResponseFormat() {
        endpoint.setResponseFormat(AnzoEndpoint.FORMAT.JSON);
        assertThat(endpoint.getResponseFormat()).isEqualTo(AnzoEndpoint.FORMAT.JSON);
    }

    @Test
    void shouldHaveDefaultResponseFormatOfRDF() {
        assertThat(endpoint.getResponseFormat()).isEqualTo(AnzoEndpoint.FORMAT.RDF);
    }

    @Test
    void shouldSetAndGetSkipCache() {
        endpoint.setSkipCache(true);
        assertThat(endpoint.isSkipCache()).isTrue();
    }

    @Test
    void shouldHaveDefaultSkipCacheOfFalse() {
        assertThat(endpoint.isSkipCache()).isFalse();
    }

    @Test
    void shouldSetAndGetValidateCert() {
        endpoint.setValidateCert(true);
        assertThat(endpoint.isValidateCert()).isTrue();
    }

    @Test
    void shouldSetAndGetQueryLocation() {
        String queryLocation = "${header.myQuery}";
        endpoint.setQueryLocation(queryLocation);
        assertThat(endpoint.getQueryLocation()).isEqualTo(queryLocation);
    }

    @Test
    void shouldHaveDefaultQueryLocationOfBody() {
        assertThat(endpoint.getQueryLocation()).isEqualTo("${body}");
    }

    @Test
    void shouldCreateProducer() throws Exception {
        // Set required fields
        String encodedUser = Base64.getEncoder().encodeToString("testuser".getBytes(StandardCharsets.UTF_8));
        String encodedPassword = Base64.getEncoder().encodeToString("testpass".getBytes(StandardCharsets.UTF_8));
        endpoint.setUser(encodedUser);
        endpoint.setPassword(encodedPassword);
        endpoint.setGraphmartUri("http://example.com/graphmart");

        Producer producer = endpoint.createProducer();
        assertThat(producer).isNotNull();
        assertThat(producer).isInstanceOf(AnzoProducer.class);
    }

    @Test
    void shouldThrowExceptionWhenCreatingConsumer() {
        Processor processor = exchange -> {};
        assertThatThrownBy(() -> endpoint.createConsumer(processor))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Anzo component only supports the Producers");
    }

    @Test
    void shouldCreateAnzoClient() {
        String encodedUser = Base64.getEncoder().encodeToString("testuser".getBytes(StandardCharsets.UTF_8));
        String encodedPassword = Base64.getEncoder().encodeToString("testpass".getBytes(StandardCharsets.UTF_8));
        endpoint.setUser(encodedUser);
        endpoint.setPassword(encodedPassword);

        AnzoClient client = endpoint.getClient();
        assertThat(client).isNotNull();
    }

    @Test
    void shouldHandleSpecialCharactersInUserAndPassword() {
        String specialUser = "test@user.com";
        String specialPassword = "p@ssw0rd!#&*()";
        String encodedUser = Base64.getEncoder().encodeToString(specialUser.getBytes(StandardCharsets.UTF_8));
        String encodedPassword = Base64.getEncoder().encodeToString(specialPassword.getBytes(StandardCharsets.UTF_8));

        endpoint.setUser(encodedUser);
        endpoint.setPassword(encodedPassword);

        assertThat(endpoint.getUser()).isEqualTo(encodedUser);
        assertThat(endpoint.getPassword()).isEqualTo(encodedPassword);
    }

    @Test
    void shouldHandleEmptyLayerUris() {
        endpoint.setLayerUris("");
        assertThat(endpoint.getLayerUris()).isEmpty();
    }

    @Test
    void shouldHandleNullLayerUris() {
        endpoint.setLayerUris(null);
        assertThat(endpoint.getLayerUris()).isNull();
    }

    @Test
    void shouldHandleMultipleLayerUris() {
        String layers = "http://layer1.com,http://layer2.com,http://layer3.com";
        endpoint.setLayerUris(layers);
        assertThat(endpoint.getLayerUris()).isEqualTo(layers);
    }

    @Test
    void shouldGetCorrectFormatKeyForRDF() {
        assertThat(AnzoEndpoint.FORMAT.RDF.getKey()).isEqualTo("rdf");
    }

    @Test
    void shouldGetCorrectFormatKeyForJSON() {
        assertThat(AnzoEndpoint.FORMAT.JSON.getKey()).isEqualTo("json");
    }

    @Test
    void shouldAllowSettingZeroTimeout() {
        endpoint.setTimeoutSeconds(0);
        assertThat(endpoint.getTimeoutSeconds()).isEqualTo(0);
    }

    @Test
    void shouldAllowSettingLargeTimeout() {
        endpoint.setTimeoutSeconds(600);
        assertThat(endpoint.getTimeoutSeconds()).isEqualTo(600);
    }

    @Test
    void shouldAllowSettingSmallMaxQueryHeaderLength() {
        endpoint.setMaxQueryHeaderLength(1024L);
        assertThat(endpoint.getMaxQueryHeaderLength()).isEqualTo(1024L);
    }

    @Test
    void shouldAllowSettingLargeMaxQueryHeaderLength() {
        endpoint.setMaxQueryHeaderLength(1048576L); // 1MB
        assertThat(endpoint.getMaxQueryHeaderLength()).isEqualTo(1048576L);
    }
}

package com.inovexcorp.queryservice.camel.anzo;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

import java.util.Map;

@Slf4j
@Component("anzo")
public class AnzoComponent extends DefaultComponent {

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        log.trace("Creating endpoint: {}", uri);
        Endpoint endpoint = new AnzoEndpoint(uri, this, remaining);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}

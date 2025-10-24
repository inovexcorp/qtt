package com.inovexcorp.queryservice.camel.anzo.comm;

import lombok.Builder;
import lombok.Getter;

import java.io.InputStream;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;

@Getter
public class QueryResponse {

    private final String query;
    private final HttpResponse<InputStream> response;
    private final long queryDuration;
    private final HttpHeaders headers;

    @Builder
    private QueryResponse(String query, HttpResponse<InputStream> response, long queryDuration) {
        this.query = query;
        this.response = response;
        this.queryDuration = queryDuration;
        this.headers = response.headers();
    }

    public HttpResponse<InputStream> getHttpResponse() {
        return response;
    }

    public InputStream getResult() {
        return response.body();
    }
}

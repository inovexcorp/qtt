package com.inovexcorp.queryservice.app.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CsrfHeaderFilterTest {

    private static final CsrfHeaderFilter ON = new CsrfHeaderFilter(AuthSettings.of(true, List.of(), List.of(), List.of()));
    private static final CsrfHeaderFilter OFF = new CsrfHeaderFilter(AuthSettings.of(false, List.of(), List.of(), List.of()));

    @Test
    void cookieRequestWithoutHeaderIsRejected() {
        ContainerRequestContext request = request("DELETE", null, null);
        ON.filter(request);
        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(request).abortWith(response.capture());
        assertEquals(403, response.getValue().getStatus());
    }

    @Test
    void cookieRequestWithHeaderPasses() {
        assertPasses(ON, request("POST", null, "JavaScript"));
        assertPasses(ON, request("PUT", null, "XMLHttpRequest"));
    }

    @Test
    void bearerRequestsAndSafeMethodsPass() {
        assertPasses(ON, request("POST", "Bearer eyJ...", null));
        assertPasses(ON, request("PATCH", "bearer eyJ...", null));
        assertPasses(ON, request("GET", null, null));
        assertPasses(ON, request("HEAD", null, null));
    }

    @Test
    void basicAuthIsNotABearerToken() {
        ContainerRequestContext request = request("POST", "Basic dXNlcjpwdw==", null);
        ON.filter(request);
        verify(request).abortWith(any());
    }

    @Test
    void inactiveWhenSignInIsOff() {
        assertPasses(OFF, request("DELETE", null, null));
    }

    private static void assertPasses(CsrfHeaderFilter filter, ContainerRequestContext request) {
        filter.filter(request);
        verify(request, never()).abortWith(any());
    }

    private static ContainerRequestContext request(String method, String authorization, String requestedWith) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getHeaderString("Authorization")).thenReturn(authorization);
        when(request.getHeaderString(CsrfHeaderFilter.HEADER)).thenReturn(requestedWith);
        return request;
    }
}

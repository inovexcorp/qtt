package com.inovexcorp.queryservice.ontology;

import com.inovexcorp.queryservice.ontology.model.CacheStatistics;
import com.inovexcorp.queryservice.ontology.model.OntologyElement;
import com.inovexcorp.queryservice.ontology.model.OntologyElementType;
import com.inovexcorp.queryservice.ontology.model.OntologyMetadata;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OntologyController.
 */
public class OntologyControllerTest {

    @Mock
    private OntologyService ontologyService;

    private OntologyController controller;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new OntologyController() {
            {
                this.ontologyService = OntologyControllerTest.this.ontologyService;
            }
        };
    }

    @Test
    public void testGetOntologyElementsSuccess() throws Exception {
        // Setup
        String routeId = "test-route";
        List<OntologyElement> elements = new ArrayList<>();
        elements.add(new OntologyElement(
                "http://example.org/Class1",
                "Class1",
                OntologyElementType.CLASS
        ));

        when(ontologyService.getOntologyElements(
                eq(routeId),
                eq(OntologyElementType.ALL),
                isNull(),
                eq(100)
        )).thenReturn(elements);

        // Execute
        Response response = controller.getOntologyElements(routeId, "all", null, 100);

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertNotNull(response.getEntity());
    }

    @Test
    public void testGetOntologyElementsWithInvalidType() {
        // Setup
        String routeId = "test-route";

        // Execute
        Response response = controller.getOntologyElements(routeId, "invalid-type", null, 100);

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        // Ensure no data is returned
        assertEquals("[]", response.getEntity().toString());
    }

    @Test
    public void testGetOntologyElementsWithException() throws Exception {
        // Setup
        String routeId = "test-route";
        when(ontologyService.getOntologyElements(
                anyString(),
                any(OntologyElementType.class),
                isNull(),
                anyInt()
        )).thenThrow(new OntologyServiceException("Test error"));

        // Execute
        Response response = controller.getOntologyElements(routeId, "all", null, 100);

        // Verify
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    public void testGetOntologyMetadataSuccess() throws Exception {
        // Setup
        String routeId = "test-route";
        OntologyMetadata metadata = new OntologyMetadata(
                routeId,
                "http://example.org/graphmart",
                "layer1,layer2",
                100,
                Instant.now(),
                true,
                "cached"
        );

        when(ontologyService.getOntologyMetadata(routeId)).thenReturn(metadata);

        // Execute
        Response response = controller.getOntologyMetadata(routeId);

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertNotNull(response.getEntity());
    }

    @Test
    public void testRefreshOntologyCacheSuccess() throws Exception {
        // Setup
        String routeId = "test-route";
        doNothing().when(ontologyService).refreshOntologyCache(routeId);

        // Execute
        Response response = controller.refreshOntologyCache(routeId);

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(ontologyService, times(1)).refreshOntologyCache(routeId);
    }

    @Test
    public void testRefreshOntologyCacheWithException() throws Exception {
        // Setup
        String routeId = "test-route";
        doThrow(new OntologyServiceException("Test error"))
                .when(ontologyService).refreshOntologyCache(routeId);

        // Execute
        Response response = controller.refreshOntologyCache(routeId);

        // Verify
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    public void testClearOntologyCache() {
        // Setup
        String routeId = "test-route";
        doNothing().when(ontologyService).clearOntologyCache(routeId);

        // Execute
        Response response = controller.clearOntologyCache(routeId);

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(ontologyService, times(1)).clearOntologyCache(routeId);
    }

    @Test
    public void testGetCacheStatistics() {
        // Setup
        CacheStatistics stats = new CacheStatistics(100, 10, 5000, 5, 50);
        when(ontologyService.getCacheStatistics()).thenReturn(stats);

        // Execute
        Response response = controller.getCacheStatistics();

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertNotNull(response.getEntity());
    }

    @Test
    public void testWarmCache() {
        // Setup
        String routeId = "test-route";
        doNothing().when(ontologyService).warmCache(routeId);

        // Execute
        Response response = controller.warmCache(routeId);

        // Verify
        assertEquals(Response.Status.ACCEPTED.getStatusCode(), response.getStatus());
        verify(ontologyService, times(1)).warmCache(routeId);
    }
}

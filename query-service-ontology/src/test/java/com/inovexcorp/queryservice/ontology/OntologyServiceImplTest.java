package com.inovexcorp.queryservice.ontology;

import com.inovexcorp.queryservice.camel.anzo.comm.AnzoClient;
import com.inovexcorp.queryservice.camel.anzo.comm.QueryResponse;
import com.inovexcorp.queryservice.ontology.model.CacheStatistics;
import com.inovexcorp.queryservice.ontology.model.OntologyElement;
import com.inovexcorp.queryservice.ontology.model.OntologyElementType;
import com.inovexcorp.queryservice.ontology.model.OntologyMetadata;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.LayerService;
import com.inovexcorp.queryservice.persistence.RouteService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OntologyServiceImpl.
 */
public class OntologyServiceImplTest {

    @Mock
    private RouteService routeService;

    @Mock
    private LayerService layerService;

    @Mock
    private HttpResponse<InputStream> httpResponse;

    private OntologyServiceImpl ontologyService;
    private OntologyServiceConfig config;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create test configuration
        config = new OntologyServiceConfig() {
            @Override
            public long cacheTtlMinutes() {
                return 60;
            }

            @Override
            public long cacheMaxEntries() {
                return 100;
            }

            @Override
            public boolean cacheEnable() {
                return true;
            }

            @Override
            public int ontologyQueryTimeout() {
                return 30;
            }

            @Override
            public int ontologyMaxResults() {
                return 1000;
            }

            @Override
            public Class<OntologyServiceConfig> annotationType() {
                return OntologyServiceConfig.class;
            }
        };

        ontologyService = new OntologyServiceImpl() {
            {
                this.routeService = OntologyServiceImplTest.this.routeService;
                this.layerService = OntologyServiceImplTest.this.layerService;
            }
        };
        ontologyService.activate(config);
    }

    @Test
    public void testGetOntologyElementsWithNonExistentRoute() {
        // Setup
        String routeId = "nonexistent";
        when(routeService.routeExists(routeId)).thenReturn(false);

        // Execute and verify
        try {
            ontologyService.getOntologyElements(routeId, OntologyElementType.ALL, null, 100);
            fail("Should have thrown OntologyServiceException");
        } catch (OntologyServiceException e) {
            assertTrue(e.getMessage().contains("Route not found"));
        }
    }

    @Test
    public void testClearOntologyCache() {
        // Setup
        String routeId = "test-route";

        // Execute
        ontologyService.clearOntologyCache(routeId);

        // Verify - no exception should be thrown
        // Cache should be cleared (though we can't directly verify internal state)
    }

    @Test
    public void testGetCacheStatistics() {
        // Execute
        CacheStatistics stats = ontologyService.getCacheStatistics();

        // Verify
        assertNotNull(stats);
        assertTrue(stats.getHitCount() >= 0);
        assertTrue(stats.getMissCount() >= 0);
        assertTrue(stats.getSize() >= 0);
    }

    @Test
    public void testGetOntologyMetadata() throws Exception {
        // Setup
        String routeId = "test-route";
        String graphmartUri = "http://example.org/graphmart";
        CamelRouteTemplate route = new CamelRouteTemplate();
        route.setRouteId(routeId);
        route.setGraphMartUri(graphmartUri);

        Datasources datasource = new Datasources();
        datasource.setDataSourceId("test-ds");
        route.setDatasources(datasource);

        when(routeService.routeExists(routeId)).thenReturn(true);
        when(routeService.getRoute(routeId)).thenReturn(route);
        when(layerService.getLayerUris(route)).thenReturn(new ArrayList<>());

        // Execute
        OntologyMetadata metadata = ontologyService.getOntologyMetadata(routeId);

        // Verify
        assertNotNull(metadata);
        assertEquals(routeId, metadata.getRouteId());
        assertEquals(graphmartUri, metadata.getGraphmartUri());
    }

    @Test
    public void testWarmCache() {
        // Setup
        String routeId = "test-route";

        // Execute - should not throw exception even if route doesn't exist
        ontologyService.warmCache(routeId);

        // Verify - method completes without exception
    }

    @Test
    public void testConfigurationModification() {
        // Setup - create new config with different values
        OntologyServiceConfig newConfig = new OntologyServiceConfig() {
            @Override
            public long cacheTtlMinutes() {
                return 120;
            }

            @Override
            public long cacheMaxEntries() {
                return 200;
            }

            @Override
            public boolean cacheEnable() {
                return false;
            }

            @Override
            public int ontologyQueryTimeout() {
                return 60;
            }

            @Override
            public int ontologyMaxResults() {
                return 500;
            }

            @Override
            public Class<OntologyServiceConfig> annotationType() {
                return OntologyServiceConfig.class;
            }
        };

        // Execute
        ontologyService.modified(newConfig);

        // Verify - should complete without exception
        // Cache should be rebuilt with new settings
    }
}

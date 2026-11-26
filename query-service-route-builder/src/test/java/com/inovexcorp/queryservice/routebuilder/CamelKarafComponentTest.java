package com.inovexcorp.queryservice.routebuilder;

import com.inovexcorp.queryservice.RdfResultsJsonifier;
import com.inovexcorp.queryservice.cache.CacheInfo;
import com.inovexcorp.queryservice.cache.CacheService;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.LayerService;
import com.inovexcorp.queryservice.persistence.RouteService;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RouteController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Startup and shutdown behaviour of {@link CamelKarafComponent}: stored routes are loaded into Camel, stopped routes
 * stay stopped, and shutdown removes every route even when one fails.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CamelKarafComponentTest {

    @Mock
    CamelContext camelContext;
    @Mock
    RouteController routeController;
    @Mock
    Registry registry;
    @Mock
    RdfResultsJsonifier jsonifier;
    @Mock
    RouteService routeService;
    @Mock
    LayerService layerService;
    @Mock
    CacheService cacheService;

    @InjectMocks
    CamelKarafComponent component;

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() {
        when(camelContext.getRouteController()).thenReturn(routeController);
        when(camelContext.getRegistry()).thenReturn(registry);
        when(cacheService.getInfo()).thenReturn(CacheInfo.builder().type("noop").build());
    }

    private static RouteBuilderConfig config(String location) {
        return new RouteBuilderConfig() {
            @Override
            public String templateLocation() {
                return location;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RouteBuilderConfig.class;
            }
        };
    }

    private static CamelRouteTemplate route(String id, String status) {
        Datasources ds = new Datasources();
        ds.setDataSourceId("ds");
        ds.setUrl("http://anzo");
        ds.setUsername("u");
        ds.setPassword("p");
        ds.setTimeOutSeconds("30");
        ds.setMaxQueryHeaderLength("8192");
        CamelRouteTemplate t = new CamelRouteTemplate(id, "httpMethodRestrict=GET", "SELECT 1", "d", "urn:gm", ds);
        t.setStatus(status);
        return t;
    }

    @Test
    void startLoadsEveryStoredRoute() throws Exception {
        when(routeService.getAll()).thenReturn(List.of(route("a", "Started"), route("b", "Started")));
        when(layerService.getLayerUris(any())).thenReturn(List.of("urn:layer1", "urn:layer2"));

        component.start(config(tmp.resolve("templates").toString()));

        verify(camelContext, times(2)).addRoutes(any());
        verify(routeController, never()).stopRoute(any());
        assertTrue(Files.isDirectory(tmp.resolve("templates")), "template directory is created");
        assertEquals(tmp.resolve("templates").toFile(), component.getTemplateLocation());
    }

    @Test
    void startKeepsStoppedRoutesStopped() throws Exception {
        when(routeService.getAll()).thenReturn(List.of(route("running", "Started"), route("halted", "Stopped")));

        component.start(config(tmp.toString()));

        verify(routeController).stopRoute("halted");
        verify(routeController, never()).stopRoute("running");
    }

    @Test
    void startBindsJsonifierOnce() throws Exception {
        when(routeService.getAll()).thenReturn(List.of());
        component.start(config(tmp.toString()));
        verify(registry).bind(RdfResultsJsonifier.BEAN_REFERENCE, jsonifier);

        when(registry.lookupByName(RdfResultsJsonifier.BEAN_REFERENCE)).thenReturn(jsonifier);
        component.start(config(tmp.toString()));
        verify(registry, times(1)).bind(any(), any());
    }

    @Test
    void startRejectsTemplateLocationThatIsAFile() throws Exception {
        File file = Files.createFile(tmp.resolve("not-a-dir")).toFile();
        assertThrows(FileNotFoundException.class, () -> component.start(config(file.getPath())));
        verify(camelContext, never()).addRoutes(any());
    }

    @Test
    void stopRemovesAllRoutes() throws Exception {
        Route a = mock(Route.class);
        Route b = mock(Route.class);
        when(a.getRouteId()).thenReturn("a");
        when(b.getRouteId()).thenReturn("b");
        when(camelContext.getRoutes()).thenReturn(List.of(a, b));

        component.stop();

        verify(routeController).stopRoute("a");
        verify(routeController).stopRoute("b");
        verify(camelContext).removeRoute("a");
        verify(camelContext).removeRoute("b");
    }

    @Test
    void stopContinuesWhenOneRouteFails() throws Exception {
        Route a = mock(Route.class);
        Route b = mock(Route.class);
        when(a.getRouteId()).thenReturn("a");
        when(b.getRouteId()).thenReturn("b");
        when(camelContext.getRoutes()).thenReturn(List.of(a, b));
        doThrow(new RuntimeException("boom")).when(routeController).stopRoute("a");

        component.stop();

        verify(camelContext).removeRoute("b");
    }

    @Test
    void stopWithNoRoutesDoesNothing() throws Exception {
        when(camelContext.getRoutes()).thenReturn(List.of());
        component.stop();
        verify(camelContext, never()).removeRoute(any());
    }
}

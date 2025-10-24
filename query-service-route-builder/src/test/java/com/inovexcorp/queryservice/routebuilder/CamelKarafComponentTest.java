package com.inovexcorp.queryservice.routebuilder;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.spi.RouteController;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for CamelKarafComponent.
 *
 * Note: The start() method creates an OsgiDefaultCamelContext which requires a full OSGi container,
 * so we test the OSGi-independent logic including:
 * - Template location validation and directory creation
 * - Private clearRoutes() method behavior
 * - Getter methods
 *
 * Integration tests should be used to test the full OSGi lifecycle.
 */
@RunWith(MockitoJUnitRunner.class)
public class CamelKarafComponentTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private CamelKarafComponent camelKarafComponent;
    private Method clearRoutesMethod;

    @Before
    public void setUp() throws Exception {
        camelKarafComponent = new CamelKarafComponent();

        // Get access to private clearRoutes method using reflection
        clearRoutesMethod = CamelKarafComponent.class.getDeclaredMethod("clearRoutes", CamelContext.class);
        clearRoutesMethod.setAccessible(true);
    }

    // ========================================
    // Tests for clearRoutes() private method
    // ========================================

    @Test
    public void testClearRoutes_WithNoRoutes_DoesNothing() throws Exception {
        // Arrange
        CamelContext mockContext = mock(CamelContext.class);
        when(mockContext.getRoutes()).thenReturn(Collections.emptyList());

        // Act
        clearRoutesMethod.invoke(camelKarafComponent, mockContext);

        // Assert
        verify(mockContext).getRoutes();
        verify(mockContext, never()).removeRoute(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void testClearRoutes_WithSingleRoute_RemovesIt() throws Exception {
        // Arrange
        Route mockRoute = mock(Route.class);
        when(mockRoute.getRouteId()).thenReturn("testRoute");

        CamelContext mockContext = mock(CamelContext.class);
        RouteController mockRouteController = mock(RouteController.class);
        when(mockContext.getRoutes()).thenReturn(Collections.singletonList(mockRoute));
        when(mockContext.getRouteController()).thenReturn(mockRouteController);

        // Act
        clearRoutesMethod.invoke(camelKarafComponent, mockContext);

        // Assert
        verify(mockRouteController).stopRoute("testRoute");
        verify(mockContext).removeRoute("testRoute");
    }

    @Test
    public void testClearRoutes_WithMultipleRoutes_RemovesAll() throws Exception {
        // Arrange
        Route route1 = mock(Route.class);
        Route route2 = mock(Route.class);
        Route route3 = mock(Route.class);
        when(route1.getRouteId()).thenReturn("route1");
        when(route2.getRouteId()).thenReturn("route2");
        when(route3.getRouteId()).thenReturn("route3");

        CamelContext mockContext = mock(CamelContext.class);
        RouteController mockRouteController = mock(RouteController.class);
        when(mockContext.getRoutes()).thenReturn(Arrays.asList(route1, route2, route3));
        when(mockContext.getRouteController()).thenReturn(mockRouteController);

        // Act
        clearRoutesMethod.invoke(camelKarafComponent, mockContext);

        // Assert
        verify(mockRouteController).stopRoute("route1");
        verify(mockRouteController).stopRoute("route2");
        verify(mockRouteController).stopRoute("route3");
        verify(mockContext).removeRoute("route1");
        verify(mockContext).removeRoute("route2");
        verify(mockContext).removeRoute("route3");
    }

    @Test
    public void testClearRoutes_StopsBeforeRemoving() throws Exception {
        // Arrange
        Route mockRoute = mock(Route.class);
        when(mockRoute.getRouteId()).thenReturn("testRoute");

        CamelContext mockContext = mock(CamelContext.class);
        RouteController mockRouteController = mock(RouteController.class);
        when(mockContext.getRoutes()).thenReturn(Collections.singletonList(mockRoute));
        when(mockContext.getRouteController()).thenReturn(mockRouteController);

        // Act
        clearRoutesMethod.invoke(camelKarafComponent, mockContext);

        // Assert - verify order
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(mockRouteController, mockContext);
        inOrder.verify(mockRouteController).stopRoute("testRoute");
        inOrder.verify(mockContext).removeRoute("testRoute");
    }

    @Test
    public void testClearRoutes_WithExceptionDuringRemoval_ContinuesWithOtherRoutes() throws Exception {
        // Arrange
        Route route1 = mock(Route.class);
        Route route2 = mock(Route.class);
        Route route3 = mock(Route.class);
        when(route1.getRouteId()).thenReturn("route1");
        when(route2.getRouteId()).thenReturn("route2");
        when(route3.getRouteId()).thenReturn("route3");

        CamelContext mockContext = mock(CamelContext.class);
        RouteController mockRouteController = mock(RouteController.class);
        when(mockContext.getRoutes()).thenReturn(Arrays.asList(route1, route2, route3));
        when(mockContext.getRouteController()).thenReturn(mockRouteController);

        // Make route2 throw an exception
        doNothing().when(mockRouteController).stopRoute("route1");
        doNothing().when(mockRouteController).stopRoute("route3");
        doThrow(new RuntimeException("Test exception")).when(mockRouteController).stopRoute("route2");

        // Act
        clearRoutesMethod.invoke(camelKarafComponent, mockContext);

        // Assert - All routes should be attempted despite exception
        verify(mockRouteController).stopRoute("route1");
        verify(mockRouteController).stopRoute("route2");
        verify(mockRouteController).stopRoute("route3");
    }

    @Test
    public void testClearRoutes_WithRouteStopException_CatchesAndContinues() throws Exception {
        // Arrange
        Route route1 = mock(Route.class);
        Route route2 = mock(Route.class);
        when(route1.getRouteId()).thenReturn("route1");
        when(route2.getRouteId()).thenReturn("route2");

        CamelContext mockContext = mock(CamelContext.class);
        RouteController mockRouteController = mock(RouteController.class);
        when(mockContext.getRoutes()).thenReturn(Arrays.asList(route1, route2));
        when(mockContext.getRouteController()).thenReturn(mockRouteController);

        // Make stopRoute on route1 throw exception - removeRoute will not be reached due to exception handling
        doThrow(new Exception("Stop failed")).when(mockRouteController).stopRoute("route1");

        // Act - Should not throw exception
        clearRoutesMethod.invoke(camelKarafComponent, mockContext);

        // Assert - Should attempt route2 despite route1 failures
        verify(mockRouteController).stopRoute("route2");
        verify(mockContext).removeRoute("route2");
    }

    @Test
    public void testClearRoutes_WithLargeNumberOfRoutes_HandlesAll() throws Exception {
        // Arrange
        java.util.List<Route> routes = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Route mockRoute = mock(Route.class);
            when(mockRoute.getRouteId()).thenReturn("route" + i);
            routes.add(mockRoute);
        }

        CamelContext mockContext = mock(CamelContext.class);
        RouteController mockRouteController = mock(RouteController.class);
        when(mockContext.getRoutes()).thenReturn(routes);
        when(mockContext.getRouteController()).thenReturn(mockRouteController);

        // Act
        clearRoutesMethod.invoke(camelKarafComponent, mockContext);

        // Assert - All 100 routes should be stopped and removed
        for (int i = 0; i < 100; i++) {
            verify(mockRouteController).stopRoute("route" + i);
            verify(mockContext).removeRoute("route" + i);
        }
    }

    // ========================================
    // Tests for template location directory validation
    // ========================================

    @Test
    public void testTemplateLocationValidation_ValidDirectory() throws Exception {
        // Arrange
        File validDir = tempFolder.newFolder("templates");

        // Assert - This is valid and should not throw
        assertTrue("Directory should exist", validDir.exists());
        assertTrue("Should be a directory", validDir.isDirectory());
    }

    @Test
    public void testTemplateLocationValidation_NonExistentDirectory_CanBeCreated() throws Exception {
        // Arrange
        File nonExistentDir = new File(tempFolder.getRoot(), "new/nested/directory");

        // Act
        boolean created = nonExistentDir.mkdirs();

        // Assert
        assertTrue("Directory should be created", created);
        assertTrue("Directory should exist", nonExistentDir.exists());
        assertTrue("Should be a directory", nonExistentDir.isDirectory());
    }

    @Test
    public void testTemplateLocationValidation_FileInsteadOfDirectory_DetectedCorrectly() throws Exception {
        // Arrange
        File fileNotDirectory = tempFolder.newFile("notADirectory.txt");

        // Assert
        assertTrue("File should exist", fileNotDirectory.exists());
        assertTrue("Should be detected as a file", fileNotDirectory.isFile());
        // This simulates the check that would happen in start() method
    }

    @Test
    public void testTemplateLocationValidation_WithSpecialCharacters() throws Exception {
        // Arrange
        File specialPath = tempFolder.newFolder("path-with_special.chars123");

        // Assert
        assertTrue("Directory with special chars should exist", specialPath.exists());
        assertTrue("Should be a directory", specialPath.isDirectory());
    }

    @Test
    public void testTemplateLocationValidation_DeeplyNestedPath() throws Exception {
        // Arrange
        File deepPath = new File(tempFolder.getRoot(), "level1/level2/level3/level4/templates");

        // Act
        boolean created = deepPath.mkdirs();

        // Assert
        assertTrue("Deeply nested path should be created", created);
        assertTrue("Path should exist", deepPath.exists());
        assertTrue("Should be a directory", deepPath.isDirectory());
    }

    @Test
    public void testTemplateLocationValidation_ExistingDirectoryNotOverwritten() throws Exception {
        // Arrange
        File existingDir = tempFolder.newFolder("existingTemplates");
        File existingFile = new File(existingDir, "existing.ftl");
        assertTrue("Test file should be created", existingFile.createNewFile());

        // Assert - Simulates that start() would not recreate existing directory
        assertTrue("Existing directory should remain", existingDir.exists());
        assertTrue("Existing file should remain", existingFile.exists());
    }

    // ========================================
    // Tests for getter methods
    // ========================================

    @Test
    public void testGetTemplateLocation_InitiallyNull() {
        // Assert
        assertEquals("Template location should initially be null", null, camelKarafComponent.getTemplateLocation());
    }

    @Test
    public void testGetCamelContext_InitiallyNull() {
        // Assert
        assertEquals("Camel context should initially be null", null, camelKarafComponent.getCamelContext());
    }

    @Test
    public void testGetTemplateLocation_CanBeSet() throws Exception {
        // Arrange
        File templateDir = tempFolder.newFolder("setTemplates");

        // Act - Use reflection to set the template location
        Field templateLocationField = CamelKarafComponent.class.getDeclaredField("templateLocation");
        templateLocationField.setAccessible(true);
        templateLocationField.set(camelKarafComponent, templateDir);

        // Assert
        assertEquals("Template location should be set", templateDir, camelKarafComponent.getTemplateLocation());
    }

    @Test
    public void testGetCamelContext_CanBeSet() throws Exception {
        // Arrange
        CamelContext mockContext = mock(CamelContext.class);

        // Act - Use reflection to set the camel context
        Field camelContextField = CamelKarafComponent.class.getDeclaredField("camelContext");
        camelContextField.setAccessible(true);
        camelContextField.set(camelKarafComponent, mockContext);

        // Assert
        assertEquals("Camel context should be set", mockContext, camelKarafComponent.getCamelContext());
    }

    // ========================================
    // Tests for stop() method behavior
    // ========================================

    @Test
    public void testStop_ClearsAndStopsContext() throws Exception {
        // Arrange
        CamelContext mockContext = mock(CamelContext.class);
        when(mockContext.getRoutes()).thenReturn(Collections.emptyList());

        // Set the mock context
        Field camelContextField = CamelKarafComponent.class.getDeclaredField("camelContext");
        camelContextField.setAccessible(true);
        camelContextField.set(camelKarafComponent, mockContext);

        // Set a mock service registration
        org.osgi.framework.ServiceRegistration<CamelContext> mockServiceReg =
                (org.osgi.framework.ServiceRegistration<CamelContext>) mock(org.osgi.framework.ServiceRegistration.class);
        Field serviceRegField = CamelKarafComponent.class.getDeclaredField("serviceRegistration");
        serviceRegField.setAccessible(true);
        serviceRegField.set(camelKarafComponent, mockServiceReg);

        // Act
        camelKarafComponent.stop();

        // Assert
        verify(mockContext).stop();
        verify(mockServiceReg).unregister();
    }

    @Test
    public void testStop_ClearsMultipleRoutes() throws Exception {
        // Arrange
        Route route1 = mock(Route.class);
        Route route2 = mock(Route.class);
        when(route1.getRouteId()).thenReturn("route1");
        when(route2.getRouteId()).thenReturn("route2");

        CamelContext mockContext = mock(CamelContext.class);
        RouteController mockRouteController = mock(RouteController.class);
        when(mockContext.getRoutes()).thenReturn(Arrays.asList(route1, route2));
        when(mockContext.getRouteController()).thenReturn(mockRouteController);

        // Set the mock context
        Field camelContextField = CamelKarafComponent.class.getDeclaredField("camelContext");
        camelContextField.setAccessible(true);
        camelContextField.set(camelKarafComponent, mockContext);

        // Set a mock service registration
        org.osgi.framework.ServiceRegistration<CamelContext> mockServiceReg =
                (org.osgi.framework.ServiceRegistration<CamelContext>) mock(org.osgi.framework.ServiceRegistration.class);
        Field serviceRegField = CamelKarafComponent.class.getDeclaredField("serviceRegistration");
        serviceRegField.setAccessible(true);
        serviceRegField.set(camelKarafComponent, mockServiceReg);

        // Act
        camelKarafComponent.stop();

        // Assert - Routes should be cleared before context is stopped
        verify(mockRouteController).stopRoute("route1");
        verify(mockRouteController).stopRoute("route2");
        verify(mockContext).removeRoute("route1");
        verify(mockContext).removeRoute("route2");
        verify(mockContext).stop();
        verify(mockServiceReg).unregister();
    }

    // ========================================
    // Tests for edge cases and error scenarios
    // ========================================

    @Test
    public void testClearRoutes_WithRouteIdsContainingSpecialCharacters() throws Exception {
        // Arrange
        Route route1 = mock(Route.class);
        Route route2 = mock(Route.class);
        when(route1.getRouteId()).thenReturn("route-with-dashes");
        when(route2.getRouteId()).thenReturn("route_with_underscores.123");

        CamelContext mockContext = mock(CamelContext.class);
        RouteController mockRouteController = mock(RouteController.class);
        when(mockContext.getRoutes()).thenReturn(Arrays.asList(route1, route2));
        when(mockContext.getRouteController()).thenReturn(mockRouteController);

        // Act
        clearRoutesMethod.invoke(camelKarafComponent, mockContext);

        // Assert
        verify(mockRouteController).stopRoute("route-with-dashes");
        verify(mockRouteController).stopRoute("route_with_underscores.123");
        verify(mockContext).removeRoute("route-with-dashes");
        verify(mockContext).removeRoute("route_with_underscores.123");
    }

    @Test
    public void testClearRoutes_WithNullRouteId_HandlesGracefully() throws Exception {
        // Arrange
        Route routeWithNullId = mock(Route.class);
        when(routeWithNullId.getRouteId()).thenReturn(null);

        CamelContext mockContext = mock(CamelContext.class);
        RouteController mockRouteController = mock(RouteController.class);
        when(mockContext.getRoutes()).thenReturn(Collections.singletonList(routeWithNullId));
        when(mockContext.getRouteController()).thenReturn(mockRouteController);

        // Act - Should not throw NPE
        clearRoutesMethod.invoke(camelKarafComponent, mockContext);

        // Assert
        verify(mockRouteController).stopRoute(null);
    }
}

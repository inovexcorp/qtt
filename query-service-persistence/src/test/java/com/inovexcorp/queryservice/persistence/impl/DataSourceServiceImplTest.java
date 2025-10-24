package com.inovexcorp.queryservice.persistence.impl;

import com.inovexcorp.queryservice.persistence.Datasources;
import org.apache.aries.jpa.template.EmConsumer;
import org.apache.aries.jpa.template.EmFunction;
import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DataSourceServiceImpl.
 * Tests CRUD operations, URL generation, and JPA interactions using mocked JpaTemplate.
 */
@RunWith(MockitoJUnitRunner.class)
public class DataSourceServiceImplTest {

    @Mock
    private JpaTemplate jpaTemplate;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private DataSourceServiceImpl dataSourceService;

    private Datasources testDatasource;

    @Before
    public void setUp() {
        testDatasource = new Datasources(
                "test-datasource", "30", "10000", "testuser", "testpass", "http://localhost:8080");
    }

    @Test
    public void testAdd_Success() {
        // Arrange
        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        dataSourceService.add(testDatasource);

        // Assert
        verify(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));
        verify(entityManager).merge(testDatasource);
        verify(entityManager).flush();
    }

    @Test
    public void testUpdate_Success() {
        // Arrange
        Datasources existingDs = new Datasources(
                "test-datasource", "30", "10000", "olduser", "oldpass", "http://old.example.com");
        when(entityManager.find(Datasources.class, "test-datasource")).thenReturn(existingDs);

        Datasources updatedDs = new Datasources(
                "test-datasource", "60", "20000", "newuser", "newpass", "http://new.example.com");
        updatedDs.setValidateCertificate(true);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        dataSourceService.update(updatedDs);

        // Assert
        verify(entityManager).find(Datasources.class, "test-datasource");
        verify(entityManager).merge(existingDs);
        verify(entityManager).flush();
        assertEquals("60", existingDs.getTimeOutSeconds());
        assertEquals("20000", existingDs.getMaxQueryHeaderLength());
        assertEquals("newuser", existingDs.getUsername());
        assertEquals("newpass", existingDs.getPassword());
        assertEquals("http://new.example.com", existingDs.getUrl());
        assertTrue(existingDs.isValidateCertificate());
    }

    @Test
    public void testDeleteAll_Success() {
        // Arrange
        javax.persistence.Query query = mock(javax.persistence.Query.class);
        when(entityManager.createQuery("delete from Datasources")).thenReturn(query);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        dataSourceService.deleteAll();

        // Assert
        verify(entityManager).createQuery("delete from Datasources");
        verify(query).executeUpdate();
        verify(entityManager).flush();
    }

    @Test
    public void testGetAll_ReturnsDatasources() {
        // Arrange
        List<Datasources> expectedDatasources = Arrays.asList(
                new Datasources("ds1", "30", "10000", "user1", "pass1", "http://localhost:8080"),
                new Datasources("ds2", "60", "20000", "user2", "pass2", "http://localhost:9090")
        );

        TypedQuery<Datasources> query = mock(TypedQuery.class);
        when(query.getResultList()).thenReturn(expectedDatasources);
        when(entityManager.createQuery("select d from Datasources d", Datasources.class))
                .thenReturn(query);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        // Act
        List<Datasources> result = dataSourceService.getAll();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("ds1", result.get(0).getDataSourceId());
        assertEquals("ds2", result.get(1).getDataSourceId());
    }

    @Test
    public void testGetAll_ReturnsEmptyList() {
        // Arrange
        TypedQuery<Datasources> query = mock(TypedQuery.class);
        when(query.getResultList()).thenReturn(Collections.emptyList());
        when(entityManager.createQuery("select d from Datasources d", Datasources.class))
                .thenReturn(query);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        // Act
        List<Datasources> result = dataSourceService.getAll();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testDataSourceExists_DatasourceFound_ReturnsTrue() {
        // Arrange
        when(entityManager.find(Datasources.class, "test-datasource")).thenReturn(testDatasource);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Supports), any(EmConsumer.class));

        // Act
        boolean result = dataSourceService.dataSourceExists("test-datasource");

        // Assert
        assertTrue(result);
    }

    @Test
    public void testDataSourceExists_DatasourceNotFound_ReturnsFalse() {
        // Arrange
        when(entityManager.find(Datasources.class, "nonExistent")).thenReturn(null);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Supports), any(EmConsumer.class));

        // Act
        boolean result = dataSourceService.dataSourceExists("nonExistent");

        // Assert
        assertFalse(result);
    }

    @Test
    public void testGetDataSourceString_ReturnsToString() {
        // Arrange
        when(entityManager.find(Datasources.class, "test-datasource")).thenReturn(testDatasource);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Supports), any(EmConsumer.class));

        // Act
        String result = dataSourceService.getDataSourceString("test-datasource");

        // Assert
        assertNotNull(result);
        assertEquals(testDatasource.toString(), result);
    }

    @Test
    public void testDelete_Success() {
        // Arrange
        when(entityManager.find(Datasources.class, "test-datasource")).thenReturn(testDatasource);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        dataSourceService.delete("test-datasource");

        // Assert
        verify(entityManager).find(Datasources.class, "test-datasource");
        verify(entityManager).remove(testDatasource);
        verify(entityManager).flush();
    }

    @Test
    public void testDelete_NonExistentDatasource_HandlesException() {
        // Arrange
        when(entityManager.find(Datasources.class, "nonExistent")).thenReturn(null);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act - should not throw exception
        dataSourceService.delete("nonExistent");

        // Assert
        verify(entityManager).find(Datasources.class, "nonExistent");
    }

    @Test
    public void testGenerateCamelUrl_Success() {
        // Arrange
        when(entityManager.find(Datasources.class, "test-datasource")).thenReturn(testDatasource);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Supports), any(EmConsumer.class));

        // Act
        String result = dataSourceService.generateCamelUrl("test-datasource");

        // Assert
        assertNotNull(result);
        assertTrue(result.startsWith("anzo:http://localhost:8080"));
        assertTrue(result.contains("timeoutSeconds=30"));
        assertTrue(result.contains("maxQueryHeaderLength=10000"));
        assertTrue(result.contains("graphmartUri=http://graphmart"));
        assertTrue(result.contains("layerUris=http://layer1,http://layer2"));
    }

    @Test
    public void testGetDataSource_DatasourceFound() {
        // Arrange
        when(entityManager.find(Datasources.class, "test-datasource")).thenReturn(testDatasource);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Supports), any(EmConsumer.class));

        // Act
        Datasources result = dataSourceService.getDataSource("test-datasource");

        // Assert
        assertNotNull(result);
        assertEquals("test-datasource", result.getDataSourceId());
        assertEquals("testuser", result.getUsername());
    }

    @Test
    public void testGetDataSource_DatasourceNotFound_ReturnsNull() {
        // Arrange
        when(entityManager.find(Datasources.class, "nonExistent")).thenReturn(null);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Supports), any(EmConsumer.class));

        // Act
        Datasources result = dataSourceService.getDataSource("nonExistent");

        // Assert
        // When find returns null, datasource.set(null) overwrites the initial value
        assertNull(result);
    }

    @Test
    public void testCountDataSources_ReturnsCount() {
        // Arrange
        Long expectedCount = 3L;
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(query.getSingleResult()).thenReturn(expectedCount);
        when(entityManager.createQuery("select count(r) from Datasources r", Long.class))
                .thenReturn(query);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        // Act
        long result = dataSourceService.countDataSources();

        // Assert
        assertEquals(expectedCount.longValue(), result);
    }

    @Test
    public void testCountDataSources_NoDatasources_ReturnsZero() {
        // Arrange
        Long expectedCount = 0L;
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(query.getSingleResult()).thenReturn(expectedCount);
        when(entityManager.createQuery("select count(r) from Datasources r", Long.class))
                .thenReturn(query);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        // Act
        long result = dataSourceService.countDataSources();

        // Assert
        assertEquals(0L, result);
    }

    @Test
    public void testUpdate_UpdatesAllFields() {
        // Arrange
        Datasources existingDs = new Datasources(
                "ds1", "30", "10000", "user1", "pass1", "http://old.com");
        existingDs.setValidateCertificate(false);

        when(entityManager.find(Datasources.class, "ds1")).thenReturn(existingDs);

        Datasources updatedDs = new Datasources(
                "ds1", "120", "50000", "admin", "secret", "https://new.com:8443");
        updatedDs.setValidateCertificate(true);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        dataSourceService.update(updatedDs);

        // Assert
        assertEquals("120", existingDs.getTimeOutSeconds());
        assertEquals("50000", existingDs.getMaxQueryHeaderLength());
        assertEquals("admin", existingDs.getUsername());
        assertEquals("secret", existingDs.getPassword());
        assertEquals("https://new.com:8443", existingDs.getUrl());
        assertTrue(existingDs.isValidateCertificate());
    }

    @Test
    public void testAdd_NewDatasource() {
        // Arrange
        Datasources newDs = new Datasources(
                "new-ds", "45", "15000", "newuser", "newpass", "http://new.example.com");

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        dataSourceService.add(newDs);

        // Assert
        verify(entityManager).merge(newDs);
        verify(entityManager).flush();
    }
}

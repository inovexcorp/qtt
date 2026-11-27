package com.inovexcorp.queryservice.persistence.impl;

import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.DatasourceHealthRecord;
import com.inovexcorp.queryservice.persistence.DatasourceStatus;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.jpa.EmConsumer;
import com.inovexcorp.queryservice.persistence.jpa.JpaTemplate;
import com.inovexcorp.queryservice.persistence.jpa.TransactionType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasourceHealthServiceImplTest {

    private final JpaTemplate jpa = mock(JpaTemplate.class);
    private final EntityManager em = mock(EntityManager.class);
    private final DataSourceService dataSourceService = mock(DataSourceService.class);
    private final DatasourceHealthServiceImpl service = new DatasourceHealthServiceImpl();
    private final Datasources ds = new Datasources("ds1", "30", "8192", "u", "p", "http://anzo");

    @BeforeEach
    void setUp() throws Exception {
        java.lang.reflect.Field jpaField = DatasourceHealthServiceImpl.class.getDeclaredField("jpa");
        jpaField.setAccessible(true);
        jpaField.set(service, jpa);
        java.lang.reflect.Field dsField = DatasourceHealthServiceImpl.class.getDeclaredField("dataSourceService");
        dsField.setAccessible(true);
        dsField.set(service, dataSourceService);
        doAnswer(inv -> {
            ((EmConsumer) inv.getArgument(1)).accept(em);
            return null;
        }).when(jpa).tx(eq(TransactionType.Required), any());
        when(dataSourceService.getDataSource("ds1")).thenReturn(ds);
    }

    @Test
    void recordsTheResultOfACheck() {
        ds.setStatus(DatasourceStatus.UNKNOWN);

        service.updateDatasourceHealth("ds1", DatasourceStatus.UP, null, 12L);

        assertEquals(DatasourceStatus.UP, ds.getStatus());
        verify(em).merge(ds);
        verify(em).merge(any(DatasourceHealthRecord.class));
    }

    /** A check that was already running when the datasource was disabled must not re-enable it. */
    @Test
    void doesNotOverwriteDisabled() {
        ds.setStatus(DatasourceStatus.DISABLED);

        service.updateDatasourceHealth("ds1", DatasourceStatus.UP, null, 12L);

        assertEquals(DatasourceStatus.DISABLED, ds.getStatus());
        verify(em, never()).merge(any());
    }
}

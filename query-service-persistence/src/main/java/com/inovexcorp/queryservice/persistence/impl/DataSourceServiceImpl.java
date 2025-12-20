package com.inovexcorp.queryservice.persistence.impl;

import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.DatasourceStatus;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.util.PasswordEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component(immediate = true, service = DataSourceService.class)
public class DataSourceServiceImpl implements DataSourceService {

    @Reference(target = "(osgi.unit.name=qtt-pu)")
    private JpaTemplate jpa;

    @Reference
    private PasswordEncryptionService encryptionService;

    @Activate
    public void activate() {
        log.info("DataSourceService activated with password encryption {}",
                encryptionService.isEncryptionEnabled() ? "ENABLED" : "DISABLED");
    }

    /**
     * Method to add a data source to the DataSource DB.
     *
     * @param datasources A {@link Datasources} object constructed prior to method call
     */
    @Override
    public void add(Datasources datasources) {
        jpa.tx(TransactionType.Required, em -> {
            // Encrypt password before saving
            if (datasources.getPassword() != null && !datasources.getPassword().isEmpty()) {
                String encryptedPassword = encryptionService.encrypt(datasources.getPassword());
                datasources.setPassword(encryptedPassword);
            }
            em.merge(datasources);
            em.flush();
        });
    }

    @Override
    public void update(Datasources datasources) {
        jpa.tx(TransactionType.Required, em -> {
            Datasources ds = em.find(Datasources.class, datasources.getDataSourceId());
            ds.setTimeOutSeconds(datasources.getTimeOutSeconds());
            ds.setMaxQueryHeaderLength(datasources.getMaxQueryHeaderLength());
            ds.setUsername(datasources.getUsername());

            // Encrypt password before saving
            if (datasources.getPassword() != null && !datasources.getPassword().isEmpty()) {
                String encryptedPassword = encryptionService.encrypt(datasources.getPassword());
                ds.setPassword(encryptedPassword);
            }

            ds.setUrl(datasources.getUrl());
            ds.setValidateCertificate(datasources.isValidateCertificate());
            em.merge(ds);
            em.flush();
        });
    }

    @Override
    public void deleteAll() {
        jpa.tx(TransactionType.Required, em -> {
            em.createQuery("delete from Datasources").executeUpdate();
            em.flush();
        });
    }

    @Override
    public List<Datasources> getAll() {
        List<Datasources> datasources = jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery("select d from Datasources d", Datasources.class).getResultList());

        // Decrypt passwords for each datasource
        datasources.forEach(this::decryptPassword);
        return datasources;
    }

    @Override
    public List<String> getAllDataSourceIds() {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery("select d.dataSourceId from Datasources d", String.class).getResultList());
    }

    @Override
    public List<String> getEnabledDataSourceIds() {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery("select d.dataSourceId from Datasources d where d.status != :status or d.status is null", String.class)
                        .setParameter("status", DatasourceStatus.DISABLED)
                        .getResultList());
    }

    /**
     * Method to verify if a datasource exists.
     *
     * @param dataSourceId Primary key for a {@link Datasources}
     * @return True or False depending on if the route exists
     */
    @Override
    public boolean dataSourceExists(String dataSourceId) {
        AtomicReference<Datasources> datasource = new AtomicReference<>(new Datasources());
        jpa.tx(TransactionType.Supports, em -> datasource.set(em.find(Datasources.class, dataSourceId)));
        //If the route is not null, return true. Otherwise, false
        return datasource.get() != null;
    }

    @Override
    public String getDataSourceString(String dataSourceId) {
        AtomicReference<Datasources> datasource = new AtomicReference<>(new Datasources());
        jpa.tx(TransactionType.Supports, em -> datasource.set(em.find(Datasources.class, dataSourceId)));
        return datasource.get().toString();
    }

    /**
     * Method to delete datasources given a {@link Datasources} routeId string
     *
     * @param dataSourceId The datasourceId to delete
     */
    @Override
    public void delete(String dataSourceId) {
        jpa.tx(TransactionType.Required, em -> {
            Datasources datasource = em.find(Datasources.class, dataSourceId);
            try {
                em.remove(datasource);
                em.flush();
            } catch (Exception e) {
                log.debug("Cannot delete non-existent datasource: {}", dataSourceId, e);
            }
        });
    }

    @Override
    public String generateCamelUrl(String dataSourceId) {
        AtomicReference<Datasources> datasource = new AtomicReference<>(new Datasources());
        jpa.tx(TransactionType.Supports, em -> datasource.set(em.find(Datasources.class, dataSourceId)));

        // Decrypt password before generating URL
        Datasources ds = datasource.get();
        if (ds != null) {
            decryptPassword(ds);
        }
        return ds.generateCamelUrl("http://graphmart", "http://layer1,http://layer2");
    }

    @Override
    public Datasources getDataSource(String dataSourceId) {
        AtomicReference<Datasources> datasource = new AtomicReference<>(new Datasources());
        jpa.tx(TransactionType.Supports, em -> datasource.set(em.find(Datasources.class, dataSourceId)));

        // Decrypt password before returning
        Datasources ds = datasource.get();
        if (ds != null) {
            decryptPassword(ds);
        }
        return ds;
    }


    @Override
    public long countDataSources() {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery("select count(r) from Datasources r", Long.class).getSingleResult());
    }

    @Override
    public void updateDatasourceStatus(String dataSourceId, DatasourceStatus status, String lastError) {
        jpa.tx(TransactionType.Required, em -> {
            Datasources ds = em.find(Datasources.class, dataSourceId);
            if (ds != null) {
                ds.setStatus(status);
                ds.setLastHealthCheck(new Date());
                ds.setLastHealthError(lastError);

                // Update consecutive failures counter
                if (status == DatasourceStatus.DOWN) {
                    ds.setConsecutiveFailures(ds.getConsecutiveFailures() + 1);
                } else if (status == DatasourceStatus.UP) {
                    ds.setConsecutiveFailures(0);
                }

                em.merge(ds);
                em.flush();
            } else {
                log.warn("Cannot update status for non-existent datasource: {}", dataSourceId);
            }
        });
    }

    @Override
    public List<Datasources> getUnhealthyDatasources() {
        List<Datasources> datasources = jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery("select d from Datasources d where d.status = :status", Datasources.class)
                    .setParameter("status", DatasourceStatus.DOWN)
                    .getResultList());

        // Decrypt passwords for each datasource
        datasources.forEach(this::decryptPassword);
        return datasources;
    }

    /**
     * Helper method to decrypt password in a Datasources object.
     */
    private void decryptPassword(Datasources datasource) {
        if (datasource != null && datasource.getPassword() != null && !datasource.getPassword().isEmpty()) {
            String decryptedPassword = encryptionService.decrypt(datasource.getPassword());
            datasource.setPassword(decryptedPassword);
        }
    }
}

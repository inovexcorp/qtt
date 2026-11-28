package com.inovexcorp.queryservice.app;

import io.quarkus.runtime.annotations.RegisterForProxy;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Reflection and proxy registrations needed only for a native image (no effect in JVM mode).
 *
 * <ul>
 *   <li>Configuration types are implemented at runtime as dynamic proxies by OsgiConfig, which also reads their
 *   method defaults reflectively.</li>
 *   <li>REST endpoints return {@code Response} with untyped entities, so Quarkus cannot discover these classes
 *   for Jackson serialization on its own.</li>
 * </ul>
 */
@RegisterForProxy(targets = {
        com.inovexcorp.queryservice.RDFSerializerConfig.class,
        com.inovexcorp.queryservice.cache.CacheConfig.class,
        com.inovexcorp.queryservice.ontology.OntologyServiceConfig.class,
        com.inovexcorp.queryservice.persistence.util.PasswordEncryptionConfig.class,
        com.inovexcorp.queryservice.routebuilder.RouteBuilderConfig.class,
        com.inovexcorp.queryservice.routebuilder.querycontrollers.RoutesControllerConfig.class,
        com.inovexcorp.queryservice.scheduler.CleanSparqiMetricsConfig.class,
        com.inovexcorp.queryservice.scheduler.MetricsConfig.class,
        com.inovexcorp.queryservice.scheduler.DatasourceHealthConfig.class,
        com.inovexcorp.queryservice.scheduler.CleanHealthRecordsConfig.class,
        com.inovexcorp.queryservice.scheduler.CleanMetricsConfig.class,
        com.inovexcorp.queryservice.sparqi.SparqiServiceConfig.class
})
@RegisterForReflection(targets = {
        // configuration types (OsgiConfig reads annotation defaults reflectively)
        com.inovexcorp.queryservice.RDFSerializerConfig.class,
        com.inovexcorp.queryservice.cache.CacheConfig.class,
        com.inovexcorp.queryservice.ontology.OntologyServiceConfig.class,
        com.inovexcorp.queryservice.persistence.util.PasswordEncryptionConfig.class,
        com.inovexcorp.queryservice.routebuilder.RouteBuilderConfig.class,
        com.inovexcorp.queryservice.routebuilder.querycontrollers.RoutesControllerConfig.class,
        com.inovexcorp.queryservice.scheduler.CleanSparqiMetricsConfig.class,
        com.inovexcorp.queryservice.scheduler.MetricsConfig.class,
        com.inovexcorp.queryservice.scheduler.DatasourceHealthConfig.class,
        com.inovexcorp.queryservice.scheduler.CleanHealthRecordsConfig.class,
        com.inovexcorp.queryservice.scheduler.CleanMetricsConfig.class,
        com.inovexcorp.queryservice.sparqi.SparqiServiceConfig.class,
        // JSON payloads
        com.inovexcorp.queryservice.cache.CacheInfo.class,
        com.inovexcorp.queryservice.cache.CacheStats.class,
        com.inovexcorp.queryservice.metrics.MetricObject.class,
        com.inovexcorp.queryservice.metrics.RouteMetrics.class,
        com.inovexcorp.queryservice.ontology.model.CacheStatistics.class,
        com.inovexcorp.queryservice.ontology.model.OntologyElement.class,
        com.inovexcorp.queryservice.ontology.model.OntologyElementType.class,
        com.inovexcorp.queryservice.ontology.model.OntologyMetadata.class,
        com.inovexcorp.queryservice.persistence.CamelRouteTemplate.class,
        com.inovexcorp.queryservice.persistence.DatasourceHealthRecord.class,
        com.inovexcorp.queryservice.persistence.Datasources.class,
        com.inovexcorp.queryservice.persistence.DatasourceStatus.class,
        com.inovexcorp.queryservice.persistence.LayerAssociations.class,
        com.inovexcorp.queryservice.persistence.LayerAssociationsKey.class,
        com.inovexcorp.queryservice.persistence.MetricRecord.class,
        com.inovexcorp.queryservice.persistence.SparqiMetricRecord.class,
        com.inovexcorp.queryservice.persistence.SparqiMetricsSummary.class,
        com.inovexcorp.queryservice.sparqi.model.SparqiContext.class,
        com.inovexcorp.queryservice.sparqi.model.SparqiMessage.class,
        com.inovexcorp.queryservice.sparqi.model.TestGenerationRequest.class,
        com.inovexcorp.queryservice.sparqi.model.TestGenerationResponse.class,
        com.inovexcorp.queryservice.sparqi.session.SparqiSession.class,
        com.inovexcorp.queryservice.sparqi.controller.SparqiController.MessageRequest.class,
        com.inovexcorp.queryservice.testing.model.TemplateVariable.class,
        com.inovexcorp.queryservice.testing.model.TestExecuteRequest.class,
        com.inovexcorp.queryservice.testing.model.TestExecuteResponse.class,
        com.inovexcorp.queryservice.testing.model.TestVariablesRequest.class,
        com.inovexcorp.queryservice.testing.model.TestVariablesResponse.class
})
public final class NativeRegistrations {

    private NativeRegistrations() {
    }
}

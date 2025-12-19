package com.inovexcorp.queryservice.routebuilder.test;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Route;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * RoutePolicy that automatically deletes the template file when a route is removed.
 * This is primarily used for temporary test routes to ensure template files don't
 * accumulate on the file system.
 */
@Slf4j
@RequiredArgsConstructor
public class TemplateCleanupRoutePolicy extends RoutePolicySupport {

    private final File templateFile;

    @Override
    public void onRemove(Route route) {
        log.info("Route {} being removed, cleaning up template file: {}",
                route.getRouteId(), templateFile.getAbsolutePath());
        try {
            if (templateFile.exists()) {
                FileUtils.forceDelete(templateFile);
                log.info("Successfully deleted template file: {}", templateFile.getAbsolutePath());
            } else {
                log.debug("Template file already deleted: {}", templateFile.getAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to delete template file: {}", templateFile.getAbsolutePath(), e);
        }
    }
}

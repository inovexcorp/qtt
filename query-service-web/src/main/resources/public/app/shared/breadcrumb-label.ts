import { effect, inject } from '@angular/core';
import { BreadcrumbService } from 'xng-breadcrumb';

/** Breadcrumb aliases of pages named after what they show (see app.routes.ts). */
export const DATASOURCE_CRUMB = 'datasource';
export const ROUTE_CRUMB = 'route';

/**
 * Labels the toolbar breadcrumb of the current page with what it shows (a datasource's or route's name) instead of
 * the generic label in the route data, kept in step as it changes. The route must give its crumb that alias:
 * `data: { breadcrumb: { alias: 'route', label: 'Route' } }`. Call in an injection context.
 */
export function breadcrumbLabel(alias: string, label: () => string): void {
  const breadcrumbs = inject(BreadcrumbService);
  effect(() => {
    const text = label();
    if (text) {
      breadcrumbs.set('@' + alias, text);
    }
  });
}

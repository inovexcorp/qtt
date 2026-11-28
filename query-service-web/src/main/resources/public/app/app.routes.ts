import { Routes } from '@angular/router';
import { unsavedChangesGuard } from './core/guards/unsaved-changes.guard';
import { DATASOURCE_CRUMB, ROUTE_CRUMB } from './shared/breadcrumb-label';
import { DatasourcesComponent } from './pages/datasources/datasources.component';
import { ConfigDatasourceComponent } from './pages/config-datasource/config-datasource.component';
import { AddDatasourceComponent } from './pages/add-datasource/add-datasource.component';
import { SettingsComponent } from './pages/settings/settings.component';

/** The app's pages; `data.breadcrumb` labels each level of the toolbar breadcrumb. */
export const routes: Routes = [
  { path: '', redirectTo: 'metrics', pathMatch: 'full' },
  {
    path: 'datasources', component: DatasourcesComponent, data: { breadcrumb: 'Datasources' },
    children: [
      { path: '', redirectTo: 'datasources', pathMatch: 'full' },
      {
        path: 'config-datasource/:dataSourceId',
        component: ConfigDatasourceComponent,
        // Labelled with the datasource's name by the page (breadcrumbLabel)
        data: { breadcrumb: { alias: DATASOURCE_CRUMB, label: 'Datasource' } }
      },
      { path: 'add-datasource', component: AddDatasourceComponent, data: { breadcrumb: 'Add datasource' } }
    ]
  },
  {
    // Lazy: the list's filters, selection and live charts load with the page.
    path: 'routes', data: { breadcrumb: 'Routes' },
    loadComponent: () => import('./pages/routes/routes.component').then(m => m.RoutesComponent),
    children: [
      {
        path: 'add',
        // Lazy, like the route page: the template editor brings in Monaco.
        loadComponent: () => import('./pages/add-route/add-route.component').then(m => m.AddRouteComponent),
        canDeactivate: [unsavedChangesGuard],
        data: { breadcrumb: 'New route' }
      },
      {
        path: 'config-route/:routeId',
        // Lazy: the route page brings in the Monaco editor, which is most of the app's code.
        loadComponent: () => import('./pages/config-route/config-route.component').then(m => m.ConfigRouteComponent),
        canDeactivate: [unsavedChangesGuard],
        // Labelled with the route's name by the page (breadcrumbLabel)
        data: { breadcrumb: { alias: ROUTE_CRUMB, label: 'Route' } }
      }
    ]
  },
  // Lazy: keeps Chart.js out of the initial bundle (it loads with the page).
  {
    path: 'metrics', data: { breadcrumb: 'Metrics' },
    loadComponent: () => import('./pages/metrics/metrics.component').then(m => m.MetricsComponent),
  },
  { path: 'settings', component: SettingsComponent, data: { breadcrumb: 'Settings' } },
];

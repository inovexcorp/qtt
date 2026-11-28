import { Routes } from '@angular/router';
import { DatasourcesComponent } from './pages/datasources/datasources.component';
import { ConfigDatasourceComponent } from './pages/config-datasource/config-datasource.component';
import { AddDatasourceComponent } from './pages/add-datasource/add-datasource.component';
import { RoutesComponent } from './pages/routes/routes.component';
import { SettingsComponent } from './pages/settings/settings.component';

/** The app's pages; `data.breadcrumb` labels each level of the toolbar breadcrumb. */
export const routes: Routes = [
  { path: '', redirectTo: 'metrics', pathMatch: 'full' },
  {
    path: 'datasources', component: DatasourcesComponent, data: { breadcrumb: 'DataSources' },
    children: [
      { path: '', redirectTo: 'datasources', pathMatch: 'full' },
      {
        path: 'config-datasource/:dataSourceId',
        component: ConfigDatasourceComponent,
        data: { breadcrumb: 'Config-Datasource' }
      },
      { path: 'add-datasource', component: AddDatasourceComponent, data: { breadcrumb: 'Add DataSource' } }
    ]
  },
  {
    path: 'routes', component: RoutesComponent, data: { breadcrumb: 'Routes' },
    children: [
      {
        path: 'config-route/:routeId',
        // Lazy: the route page brings in the Monaco editor, which is most of the app's code.
        loadComponent: () => import('./pages/config-route/config-route.component').then(m => m.ConfigRouteComponent),
        data: { breadcrumb: 'Config-Route' }
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

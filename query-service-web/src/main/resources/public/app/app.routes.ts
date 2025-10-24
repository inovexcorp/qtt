import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'metrics', pathMatch: 'full' },
  {
    path: 'datasources',
    loadComponent: () => import('./pages/datasources/datasources.component').then(m => m.DatasourcesComponent),
    data: { breadcrumb: 'DataSources' },
    children: [
      { path: '', redirectTo: 'datasources', pathMatch: 'full' },
      {
        path: 'config-datasource/:dataSourceId',
        loadComponent: () => import('./pages/config-datasource/config-datasource.component').then(m => m.ConfigDatasourceComponent),
        data: { breadcrumb: 'Config-Datasource' }
      },
      {
        path: 'add-datasource',
        loadComponent: () => import('./pages/add-datasource/add-datasource.component').then(m => m.AddDatasourceComponent),
        data: { breadcrumb: 'Add DataSource' }
      }
    ]
  },
  {
    path: 'routes',
    loadComponent: () => import('./pages/routes/routes.component').then(m => m.RoutesComponent),
    data: { breadcrumb: 'Routes' }
  },
  {
    path: 'metrics',
    loadComponent: () => import('./pages/metrics/metrics.component').then(m => m.MetricsComponent),
    data: { breadcrumb: 'Metrics' }
  },
  {
    path: 'settings',
    loadComponent: () => import('./pages/settings/settings.component').then(m => m.SettingsComponent),
    data: { breadcrumb: 'Settings' }
  }
];

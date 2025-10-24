import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';
import {DatasourcesComponent} from './pages/datasources/datasources.component';
import {ConfigDatasourceComponent} from './pages/config-datasource/config-datasource.component';
import {AddDatasourceComponent} from './pages/add-datasource/add-datasource.component';
import {RoutesComponent} from './pages/routes/routes.component';
import {ConfigRouteComponent} from './pages/config-route/config-route.component';
import {MetricsComponent} from './pages/metrics/metrics.component';
import {SettingsComponent} from "./pages/settings/settings.component";

const routes: Routes = [
  {path: '', redirectTo: 'metrics', pathMatch: 'full'},
  {
    path: 'datasources', component: DatasourcesComponent, data: {breadcrumb: 'DataSources'},
    children: [
      {path: '', redirectTo: 'datasources', pathMatch: 'full'},
      {
        path: 'config-datasource/:dataSourceId',
        component: ConfigDatasourceComponent,
        data: {breadcrumb: 'Config-Datasource'}
      },
      {path: 'add-datasource', component: AddDatasourceComponent, data: {breadcrumb: 'Add DataSource'}}
    ]
  },
  {
    path: 'routes', component: RoutesComponent, data: {breadcrumb: 'Routes'},
    children: [
      {
        path: 'config-route/:routeId',
        component: ConfigRouteComponent,
        data: {breadcrumb: 'Config-Route'}
      }
    ]
  },
  {path: 'metrics', component: MetricsComponent, data: {breadcrumb: 'Metrics'}},
  {path: 'settings', component: SettingsComponent, data: {breadcrumb: 'Settings'}},

];

@NgModule({
  imports: [RouterModule.forRoot(routes, {useHash: true})],
  exports: [RouterModule]
})
export class AppRoutingModule {
}

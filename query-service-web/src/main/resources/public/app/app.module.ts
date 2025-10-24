import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { DatasourcesComponent } from './pages/datasources/datasources.component';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ConfigDatasourceComponent } from './pages/config-datasource/config-datasource.component';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { BreadcrumbComponent } from 'xng-breadcrumb';
import { QsBreadcrumbComponent } from './shared/qs-breadcrumb/qs-breadcrumb.component';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { AddDatasourceComponent } from './pages/add-datasource/add-datasource.component';
import { DeleteDatasourceModalComponent } from './pages/delete-datasource-modal/delete-datasource-modal.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RoutesComponent } from './pages/routes/routes.component';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSortModule } from '@angular/material/sort';
import { MatDialogModule } from '@angular/material/dialog';
import { AddRouteComponent } from './pages/add-route/add-route.component';
import { MatSelectModule } from '@angular/material/select';
import { ConfigRouteComponent, CacheClearConfirmDialog } from './pages/config-route/config-route.component';
import { DeleteRouteModalComponent } from './pages/delete-route-modal/delete-route-modal.component';
import { MonacoEditorModule } from 'ngx-monaco-editor-v2';
import { ConfigDatasourceModalComponent } from './pages/config-datasource-modal/config-datasource-modal.component';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatChipsModule } from '@angular/material/chips';
import { MetricsComponent } from './pages/metrics/metrics.component'
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MarkdownModule } from 'ngx-markdown';
import { TextFieldModule } from '@angular/cdk/text-field';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { SettingsComponent } from './pages/settings/settings.component';
import { SparqiChatComponent } from './pages/config-route/sparqi-chat/sparqi-chat.component';
import { OntologyVisualizationDialogComponent } from './pages/config-route/sparqi-chat/ontology-visualization-dialog/ontology-visualization-dialog.component';
import { SparqiMetricsModalComponent } from './pages/settings/sparqi-metrics-modal/sparqi-metrics-modal.component';
import {MatSlideToggle} from "@angular/material/slide-toggle";
import {NgOptimizedImage} from "@angular/common";

@NgModule({
  declarations: [
    AppComponent,
    DatasourcesComponent,
    ConfigDatasourceComponent,
    QsBreadcrumbComponent,
    AddDatasourceComponent,
    DeleteDatasourceModalComponent,
    RoutesComponent,
    AddRouteComponent,
    ConfigRouteComponent,
    DeleteRouteModalComponent,
    ConfigDatasourceModalComponent,
    MetricsComponent,
    SettingsComponent,
    SparqiChatComponent,
    OntologyVisualizationDialogComponent,
    SparqiMetricsModalComponent,
    CacheClearConfirmDialog
  ],
    imports: [
        BrowserModule,
        AppRoutingModule,
        BrowserAnimationsModule,
        MatToolbarModule,
        MatSidenavModule,
        MatIconModule,
        MatListModule,
        MatMenuModule,
        MatButtonModule,
        MatCardModule,
        MatProgressBarModule,
        MatTabsModule,
        MatFormFieldModule,
        MatInputModule,
        BreadcrumbComponent,
        FormsModule,
        ReactiveFormsModule,
        MatTableModule,
        MatPaginatorModule,
        MatSortModule,
        MatDialogModule,
        MatSelectModule,
        MatAutocompleteModule,
        MatChipsModule,
        NgxChartsModule,
        MonacoEditorModule.forRoot({
            baseUrl: 'assets',
            defaultOptions: {
                scrollBeyondLastLine: false,
                suggestOnTriggerCharacters: true,
                quickSuggestionsDelay: 0
            }
        }),
        MatCheckboxModule,
        MatTooltipModule,
        MatProgressSpinnerModule,
        TextFieldModule,
        MatExpansionModule,
        MatSnackBarModule,
        MarkdownModule.forRoot(),
        MatSlideToggle,
        NgOptimizedImage
    ],
  providers: [
    provideHttpClient(withInterceptorsFromDi())
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }

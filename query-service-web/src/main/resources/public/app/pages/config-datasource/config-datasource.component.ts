import {Component, OnInit, ViewEncapsulation, ViewChild, AfterViewInit} from '@angular/core';
import {FormControl, FormGroup, Validators} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {DatasourceService} from '../../core/services/datasource.service';
import {Datasources} from '../../core/models/datasources';
import {MatDialog} from '@angular/material/dialog';
import {DeleteDatasourceModalComponent} from '../delete-datasource-modal/delete-datasource-modal.component';
import {ConfigDatasourceModalComponent} from '../config-datasource-modal/config-datasource-modal.component';
import {MetricsService} from '../../core/services/metrics.service';
import {RouteMetric, RouteMetricsResponse} from '../../core/models/metric-data';
import {MatTableDataSource} from '@angular/material/table';
import {MatPaginator} from '@angular/material/paginator';
import {MatSort} from '@angular/material/sort';

@Component({
  selector: 'app-config-datasource',
  templateUrl: './config-datasource.component.html',
  styleUrls: ['./config-datasource.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class ConfigDatasourceComponent implements OnInit, AfterViewInit {
  private sub: any;
  dataSourceId: string = '';
  private associatedRoutes: string[] = [];
  testResponse: any;
  testResponseStatus: 'success' | 'error' | null = null;

  // Health status fields
  datasourceStatus?: string;
  isEnabling: boolean = false;
  isDisabling: boolean = false;
  healthCheckEnabled: boolean = true;

  // Usage tab data
  routeMetrics: RouteMetric[] = [];
  routeMetricsDataSource = new MatTableDataSource<RouteMetric>([]);
  displayedColumns: string[] = ['route', 'state', 'processingTime', 'exchanges', 'uptime'];

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  aggregateMetrics: {
    totalRoutes: number;
    totalExchanges: number;
    avgLatency: number;
    successRate: number;
    failedExchanges: number;
  } = {
    totalRoutes: 0,
    totalExchanges: 0,
    avgLatency: 0,
    successRate: 0,
    failedExchanges: 0
  };
  isLoadingMetrics: boolean = false;

  configDatasource = new FormGroup({
    url: new FormControl('', Validators.required),
    timeOutSeconds: new FormControl('', Validators.required),
    maxQueryHeaderLength: new FormControl('', Validators.required),
    username: new FormControl('', Validators.required),
    password: new FormControl('', Validators.required),
    validateCertificate: new FormControl('true', Validators.required)
  })
  hide = true;

  get url() {
    return this.configDatasource.get('url');
  }

  get timeOutSeconds() {
    return this.configDatasource.get('timeOutSeconds');
  }

  get maxQueryHeaderLength() {
    return this.configDatasource.get('maxQueryHeaderLength');
  }

  get username() {
    return this.configDatasource.get('username');
  }

  get password() {
    return this.configDatasource.get('password');
  }

  get validateCertificate() {
    return this.configDatasource.get('validateCertificate');
  }

  constructor(
    private route: ActivatedRoute,
    public router: Router,
    private datasourceService: DatasourceService,
    private dialogRef: MatDialog,
    private metricsService: MetricsService) {
  }

  ngOnInit(): void {
    // Grab dataSourceId from URL
    this.sub = this.route.params.subscribe(params => {
      this.dataSourceId = params['dataSourceId'];
    });
    this.getDatasource();
    this.loadUsageMetrics();
    this.checkHealthCheckStatus();
  }

  // Check if health checks are globally enabled
  private checkHealthCheckStatus(): void {
    this.datasourceService.getHealthCheckConfig().subscribe({
      next: (config) => {
        this.healthCheckEnabled = config.enabled && config.available;
      },
      error: (err) => {
        console.error('Failed to fetch health check configuration:', err);
        // Default to enabled if we can't fetch the config
        this.healthCheckEnabled = true;
      }
    });
  }

  ngAfterViewInit(): void {
    // Set up paginator and sort after view is initialized
    this.routeMetricsDataSource.paginator = this.paginator;
    this.routeMetricsDataSource.sort = this.sort;
  }

  // Set the values of all the form fields of the datasource you are trying to edit
  public getDatasource(): void {
    this.datasourceService.getDatasource(this.dataSourceId)
      .subscribe((datasources) => {
        this.dataSourceId = datasources.dataSourceId;
        this.configDatasource.controls['url'].setValue(datasources.url);
        this.configDatasource.controls['timeOutSeconds'].setValue(datasources.timeOutSeconds);
        this.configDatasource.controls['maxQueryHeaderLength'].setValue(datasources.maxQueryHeaderLength);
        this.configDatasource.controls['username'].setValue(datasources.username);
        this.configDatasource.controls['password'].setValue(datasources.password);
        this.configDatasource.controls['validateCertificate'].setValue(datasources.validateCertificate.toString())
        this.associatedRoutes = datasources.camelRouteTemplate;
        this.datasourceStatus = datasources.status;
      });
  }

  // Get all form fields and submit edit to datasource
  public config(dataSourceId: string): void {
    let url: string = this.configDatasource.value['url'] as string;
    let timeOutSeconds: string = this.configDatasource.value['timeOutSeconds'] as string;
    let maxQueryHeaderLength: string = this.configDatasource.value['maxQueryHeaderLength'] as string;
    let username: string = this.configDatasource.value['username'] as string;
    let password: string = this.configDatasource.value['password'] as string;
    let validateCertificate: boolean = this.configDatasource.value['validateCertificate'] === 'true';
    if (!dataSourceId || !url || !timeOutSeconds || !maxQueryHeaderLength || !username || !password) {
      return;
    }

    this.dialogRef.open(ConfigDatasourceModalComponent, {
      data: {
        associatedRoutes: this.associatedRoutes,
        datasource: {
          dataSourceId,
          url,
          timeOutSeconds,
          maxQueryHeaderLength,
          username,
          password,
          validateCertificate
        } as Datasources
      }
    })


  }


  public openConfirmDeleteDatasourceModal() {
    this.dialogRef.open(DeleteDatasourceModalComponent, {
      data: {
        associatedRoutes: this.associatedRoutes,
        dataSourceId: this.dataSourceId

      }
    });
  }

  public openConfirmConfigDatasourceModal(datasourceId: string) {
    this.dialogRef.open(ConfigDatasourceModalComponent, {
      data: {
        associatedRoutes: this.associatedRoutes,
        dataSourceId: this.dataSourceId

      }
    });
  }

  public test(): void {
    this.datasourceService.testDatasource(this.asDataSource()).subscribe({
      next: response => {
        this.testResponseStatus = response.status;
        this.testResponse = this.testResponseStatus === 'success' ? null : response.message;
      },
      error: err => {
        this.testResponseStatus = 'error';
        this.testResponse = err.error ?? err;
        console.error(err);
      }
    });
  }

  private asDataSource(): Datasources {
    return {
      dataSourceId: this.dataSourceId,
      url: this.configDatasource.value['url'] as string,
      timeOutSeconds: this.configDatasource.value['timeOutSeconds'] as string,
      maxQueryHeaderLength: this.configDatasource.value['maxQueryHeaderLength'] as string,
      username: this.configDatasource.value['username'] as string,
      password: this.configDatasource.value['password'] as string,
      validateCertificate: this.configDatasource.value['validateCertificate'] === 'true'
    } as Datasources;
  }

  // Load metrics for routes using this datasource
  public loadUsageMetrics(): void {
    this.isLoadingMetrics = true;

    // Custom sorting for nested properties
    this.routeMetricsDataSource.sortingDataAccessor = (item: RouteMetric, property: string) => {
      switch (property) {
        case 'processingTime':
          return item.meanProcessingTime;
        case 'exchanges':
          return item.exchangesTotal;
        default:
          return (item as any)[property];
      }
    };

    this.metricsService.getDatasourceRouteMetrics(this.dataSourceId).subscribe({
      next: (response: RouteMetricsResponse) => {
        this.routeMetrics = response.metrics;
        this.routeMetricsDataSource.data = response.metrics;

        this.calculateAggregateMetrics(response.metrics);
        this.isLoadingMetrics = false;

        // After data is loaded and *ngIf renders the table, connect paginator and sort
        setTimeout(() => {
          this.routeMetricsDataSource.paginator = this.paginator;
          this.routeMetricsDataSource.sort = this.sort;
        });
      },
      error: (error: any) => {
        console.error('Failed to fetch datasource metrics:', error);
        this.isLoadingMetrics = false;
      }
    });
  }

  // Filter the routes table
  public applyRouteFilter(filterValue: string): void {
    this.routeMetricsDataSource.filter = filterValue.trim().toLowerCase();

    if (this.routeMetricsDataSource.paginator) {
      this.routeMetricsDataSource.paginator.firstPage();
    }
  }

  // Calculate aggregate metrics for the usage tab
  private calculateAggregateMetrics(metrics: RouteMetric[]): void {
    if (metrics.length === 0) {
      this.aggregateMetrics = {
        totalRoutes: 0,
        totalExchanges: 0,
        avgLatency: 0,
        successRate: 0,
        failedExchanges: 0
      };
      return;
    }

    let totalExchanges = 0;
    let totalCompleted = 0;
    let totalFailed = 0;
    let totalProcessingTime = 0;
    let totalMeanProcessingTime = 0;

    for (const metric of metrics) {
      totalExchanges += metric.exchangesTotal;
      totalCompleted += metric.exchangesCompleted;
      totalFailed += metric.exchangesFailed;
      totalProcessingTime += metric.totalProcessingTime;
      totalMeanProcessingTime += metric.meanProcessingTime;
    }

    this.aggregateMetrics = {
      totalRoutes: metrics.length,
      totalExchanges: totalExchanges,
      avgLatency: totalExchanges > 0 ? Math.round(totalProcessingTime / totalExchanges) : 0,
      successRate: totalExchanges > 0 ? Math.round((totalCompleted / totalExchanges) * 100) : 0,
      failedExchanges: totalFailed
    };
  }

  // Enable a datasource
  public enableDatasource(): void {
    this.isEnabling = true;
    this.datasourceService.enableDatasource(this.dataSourceId).subscribe({
      next: () => {
        this.datasourceStatus = 'UNKNOWN';
        this.isEnabling = false;
        // Refresh datasource to get updated status
        this.getDatasource();
      },
      error: (error) => {
        console.error('Failed to enable datasource:', error);
        this.isEnabling = false;
      }
    });
  }

  // Disable a datasource
  public disableDatasource(): void {
    this.isDisabling = true;
    this.datasourceService.disableDatasource(this.dataSourceId).subscribe({
      next: () => {
        this.datasourceStatus = 'DISABLED';
        this.isDisabling = false;
        // Refresh datasource to get updated status
        this.getDatasource();
      },
      error: (error) => {
        console.error('Failed to disable datasource:', error);
        this.isDisabling = false;
      }
    });
  }

  // Trigger manual health check
  public triggerHealthCheck(): void {
    this.datasourceService.triggerHealthCheck(this.dataSourceId).subscribe({
      next: () => {
        // Wait a moment then refresh to get updated status
        setTimeout(() => {
          this.getDatasource();
        }, 1000);
      },
      error: (error) => {
        console.error('Failed to trigger health check:', error);
      }
    });
  }

  // Helper methods for status display
  public getStatusIcon(status?: string): string {
    switch(status) {
      case 'UP': return 'check_circle';
      case 'DOWN': return 'error';
      case 'DISABLED': return 'block';
      case 'CHECKING': return 'sync';
      default: return 'help';
    }
  }

  public getStatusClass(status?: string): string {
    switch(status) {
      case 'UP': return 'status-up';
      case 'DOWN': return 'status-down';
      case 'DISABLED': return 'status-disabled';
      case 'CHECKING': return 'status-checking';
      default: return 'status-unknown';
    }
  }
}

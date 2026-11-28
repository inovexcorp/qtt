import { Component, ViewChild, ViewEncapsulation, ChangeDetectionStrategy } from '@angular/core';
import { MetricsService } from '../../core/services/metrics.service';
import { RouteMetric, RouteMetricsResponse } from '../../core/models/metric-data';
import { DatasourceService } from '../../core/services/datasource.service';
import { Datasources } from '../../core/models/datasources';
import { Observable, Subscription } from 'rxjs';
import { FormGroup, FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { map, startWith } from 'rxjs/operators';
import { MatSelectChange, MatOption, MatSelect } from '@angular/material/select';
import { MatTableDataSource, MatTable, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCellDef, MatCell, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow } from '@angular/material/table';
import { MetricTableData } from '../../core/models/metric-table-data';
import { MatPaginator } from '@angular/material/paginator';
import { LineGraph } from '../../core/models/persisted-interfaces/line-graph';
import { LineGraphObject } from '../../core/models/persisted-interfaces/line-graph-object';
import { ScaleType, BarChartModule, LineChartModule } from '@swimlane/ngx-charts';
import { MatTabGroup, MatTab } from '@angular/material/tabs';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatAutocompleteTrigger, MatAutocomplete } from '@angular/material/autocomplete';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatTooltip } from '@angular/material/tooltip';
import { AsyncPipe, DecimalPipe } from '@angular/common';
import { MetricsStatusComponent } from './metrics-status.component';

/** Where a section of the page is: fetching, showing data, or failed to fetch. */
export type LoadState = 'loading' | 'ready' | 'error';

/**
 * The summary tiles. A value is null when it can't be computed yet: averages and rates need at least one exchange
 * (the page used to divide by zero there and show NaN), and there is no busiest route until some route has traffic.
 */
export interface MetricsSummary {
  endpoints: number;
  totalExchanges: number;
  avgProcessingMs: number | null;
  busiestRoute: string | null;
  successRate: number | null;
}

/** Camel reports processing times of a route with no exchanges as 0 (min/max) and -1 (mean): not measurements. */
const TIMING_PROPERTIES: ReadonlySet<keyof RouteMetric> =
  new Set<keyof RouteMetric>(['totalProcessingTime', 'minProcessingTime', 'maxProcessingTime', 'meanProcessingTime']);

@Component({
    selector: 'app-metrics',
    templateUrl: './metrics.component.html',
    styleUrls: ['./metrics.component.scss'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [MatTabGroup, MatTab, FormsModule, ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatAutocompleteTrigger, MatAutocomplete, MatOption, MatSelect, BarChartModule, LineChartModule, MatIcon, MatSuffix, MatTable, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCellDef, MatCell, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow, MatPaginator, MatProgressSpinner, MatProgressBar, MatTooltip, AsyncPipe, DecimalPipe, MetricsStatusComponent]
})
export class MetricsComponent {

  constructor(private metricsService: MetricsService, private datasourceService: DatasourceService) {
  }
  persistedMetric: keyof RouteMetric = 'totalProcessingTime';
  persistedData : LineGraphObject[]=[];
  exchangeData: any[] = [];
  latencyData: any[]=[];
  latencyDisplay: any[]=[];
  exchangeDisplay: any[] = [];
  datasources: Datasources[] = [];
  filteredDatasources!: Observable<Datasources[]> | undefined;

  /** Summary tiles and table (all routes). */
  routeState: LoadState = 'loading';
  /** Exchanges and Latency charts (all routes, or one datasource's). */
  chartState: LoadState = 'loading';
  /** Trends chart (persisted history). */
  trendState: LoadState = 'loading';
  summary: MetricsSummary | null = null;
  /** Some route in the charts has handled an exchange; without one the bars are all zero-height. */
  chartsHaveExchanges = false;
  private routeRequest?: Subscription;
  private chartRequest?: Subscription;
  private trendRequest?: Subscription;
  private selectedRoutes: string[] = [];

  // Custom purple color scheme matching the app's purple motif
  purpleColorScheme = {
    name: 'purple',
    selectable: true,
    group: ScaleType.Ordinal,
    domain: ['#4a148c', '#6a1b9a', '#7b1fa2', '#8e24aa', '#9c27b0', '#ab47bc', '#ba68c8', '#ce93d8']
  };
  dataSource = new MatTableDataSource<MetricTableData>;
  displayedColumns: string[] = ['Name','Processing Time','Successful Exchanges','Failed Exchanges','State','Uptime'];

  // The table only exists once routes have loaded, so attach the paginator whenever it appears.
  @ViewChild(MatPaginator) set paginator(paginator: MatPaginator | undefined) {
    if (paginator) {
      this.dataSource.paginator = paginator;
    }
  }

  metrics = new FormGroup({
    exchangeDatasourceId: new FormControl(''),
    latencyDatasourceId: new FormControl(''),
    routeId: new FormControl('')
  })
  get exchangeDatasourceId() { return this.metrics.get('datasourceId') };
  get routeId() { return this.metrics.get('routeId') };
  get latencyDatasourceId() { return this.metrics.get('datasourceId') };



  ngOnInit(): void {
    this.fetchRouteMetrics();
    this.getDatasources();
    this.getPersistedMetrics(this.persistedMetric);
  }

  ngOnDestroy(): void {
    this.routeRequest?.unsubscribe();
    this.chartRequest?.unsubscribe();
    this.trendRequest?.unsubscribe();
  }

  getPersistedMetrics(property: keyof RouteMetric): void {
    this.persistedMetric = property;
    this.trendState = 'loading';
    this.trendRequest?.unsubscribe();
    this.trendRequest = this.metricsService.getPersistedMetrics().subscribe({
      next: (response: RouteMetricsResponse) => {
        this.persistedData = this.buildTrendSeries(response, property);
        this.trendState = 'ready';
      },
      error: (error: any) => {
        console.error('Failed to fetch persisted metrics:', error);
        this.trendState = 'error';
      }
    });
  }

  /** One line per route; snapshots without a measurement for the property (see TIMING_PROPERTIES) are left out. */
  buildTrendSeries(response: RouteMetricsResponse, property: keyof RouteMetric): LineGraphObject[] {
    const timing = TIMING_PROPERTIES.has(property);
    const mapping : {[routeId: string]: LineGraph[]} = {};
    for (const entry of response.metrics) {
      const value = Number(entry[property]);
      if (!Number.isFinite(value) || value < 0 || (timing && entry.exchangesTotal === 0)) {
        continue;
      }
      (mapping[entry.route] ??= []).push({ value, name: entry.timeStamp.substring(11,16) });
    }
    return Object.entries(mapping).map(([name, series]) => ({ name, series: series.sort(this.sortByTimestamp) }));
  }

  /** All routes: summary tiles and table, and the charts unless a datasource's metrics were asked for meanwhile. */
  fetchRouteMetrics(): void {
    this.routeRequest?.unsubscribe();
    this.chartRequest?.unsubscribe();
    this.chartRequest = undefined;
    this.routeState = 'loading';
    this.chartState = 'loading';
    this.routeRequest = this.metricsService.getRouteMetrics().subscribe({
      next: (response: RouteMetricsResponse) => {
        this.generateCustomMetrics(response);
        this.populateTableData(response);
        this.routeState = 'ready';
        if (!this.chartRequest) {
          this.populateBarCharts(response);
          this.chartState = 'ready';
        }
      },
      error: (error: any) => {
        console.error('Failed to fetch route metrics:', error);
        this.routeState = 'error';
        if (!this.chartRequest) {
          this.chartState = 'error';
        }
      }
    });
  }

  /** One datasource's routes, for the Exchanges and Latency charts only. */
  fetchDatasourceSpecificMetrics(datasource: any): void {
    if (!datasource) {
      this.fetchRouteMetrics();
      return;
    }
    this.chartState = 'loading';
    this.chartRequest?.unsubscribe();
    this.chartRequest = this.metricsService.getDatasourceRouteMetrics(datasource).subscribe({
      next: (response: RouteMetricsResponse) => {
        this.populateBarCharts(response);
        this.chartState = 'ready';
      },
      error: (error: any) => {
        console.error('Failed to fetch route metrics:', error);
        this.chartState = 'error';
      }
    });
  }

  /** Retry for the Exchanges and Latency tabs: whichever datasource is selected. */
  retryCharts(): void {
    this.fetchDatasourceSpecificMetrics(this.metrics.get('exchangeDatasourceId')?.value
      || this.metrics.get('latencyDatasourceId')?.value);
  }

  getDatasources(): void {
    this.datasourceService.getDatasources()
      .subscribe(datasources => {
        this.datasources = datasources;
        this.filteredDatasources = this.metrics.get('exchangeDatasourceId')?.valueChanges.pipe(
          startWith(''),
          map(value => this.filterDatasources(value || '')),
        )
      });
  }

  populateBarCharts(response: RouteMetricsResponse): void {
    this.exchangeData = response.metrics.map((metric: RouteMetric) => ({
      name: metric.route,
      series: [
        { name: 'Failed Exchanges', value: metric.exchangesFailed },
        { name: 'Successful Exchanges', value: metric.exchangesCompleted },
      ]
    }));
    // A route with no exchanges has no latency to plot (Camel reports its mean as -1).
    this.latencyData = response.metrics.filter(metric => metric.exchangesTotal > 0).map((metric: RouteMetric) => ({
      name: metric.route,
      series: [
        { name: 'Max Processing Time', value: metric.maxProcessingTime },
        { name: 'Avg Processing Time', value: metric.meanProcessingTime },
        { name: 'Min Processing Time', value: metric.minProcessingTime }
      ]
    })).sort((a, b) => b.series[0].value - a.series[0].value);
    this.chartsHaveExchanges = response.metrics.some(metric => metric.exchangesTotal > 0);
    this.applyRouteFilter();
  }

  populateTableData(response: RouteMetricsResponse): void {
    const tableData = response.metrics.map((value: RouteMetric) => <MetricTableData>{
      name: value.route,
      minProcessingTime: value.minProcessingTime,
      maxProcessingTime: value.maxProcessingTime,
      averageProcessingTime: value.meanProcessingTime,
      successfulExchanges: value.exchangesCompleted,
      failedExchanges: value.exchangesFailed,
      hasExchanges: value.exchangesTotal > 0,
      state: value.state,
      uptime: value.uptime
    });
    this.dataSource.data = tableData;
  }


  filterByRoutes(event: MatSelectChange): void {
    this.selectedRoutes = event.value ?? [];
    this.applyRouteFilter();
  }

  /** No routes selected shows them all (clearing the selection used to empty the charts). */
  private applyRouteFilter(): void {
    const shown = (item: { name: string }) => this.selectedRoutes.length === 0 || this.selectedRoutes.includes(item.name);
    this.exchangeDisplay = this.exchangeData.filter(shown);
    this.latencyDisplay = this.latencyData.filter(shown);
  }

  generateCustomMetrics(response: RouteMetricsResponse): void {
    let totalExchanges = 0;
    let completedExchanges = 0;
    let totalProcessingTime = 0;
    let busiest: RouteMetric | null = null;

    for (const metric of response.metrics) {
      totalExchanges += metric.exchangesTotal;
      completedExchanges += metric.exchangesCompleted;
      totalProcessingTime += metric.totalProcessingTime;
      if (busiest === null || metric.exchangesTotal > busiest.exchangesTotal) {
        busiest = metric;
      }
    }

    // Averages and rates over zero exchanges are undefined (0/0): leave them null so the tiles say so.
    const hasExchanges = totalExchanges > 0;
    this.summary = {
      endpoints: response.metrics.length,
      totalExchanges,
      avgProcessingMs: hasExchanges ? totalProcessingTime / totalExchanges : null,
      busiestRoute: busiest && busiest.exchangesTotal > 0 ? busiest.route : null,
      successRate: hasExchanges ? Number(((completedExchanges / totalExchanges) * 100).toFixed(2)) : null,
    };
  }

  /** What the Exchanges or Latency tab shows instead of its chart, or null to draw the chart. */
  chartMode(display: any[]): 'loading' | 'error' | 'empty' | null {
    if (this.chartState !== 'ready') {
      return this.chartState;
    }
    return display.length > 0 && this.chartsHaveExchanges ? null : 'empty';
  }

  emptyChartTitle(): string {
    return this.exchangeData.length > 0 ? 'No exchanges recorded yet' : 'No routes yet';
  }

  emptyChartDetail(): string {
    return this.exchangeData.length > 0
      ? 'These charts fill in once a route has handled a query.'
      : 'Routes appear here once they are created.';
  }

  trendMode(): 'loading' | 'error' | 'empty' | null {
    if (this.trendState !== 'ready') {
      return this.trendState;
    }
    return this.persistedData.length > 0 ? null : 'empty';
  }

  tableMode(): 'loading' | 'error' | 'empty' | null {
    if (this.routeState !== 'ready') {
      return this.routeState;
    }
    return this.dataSource.data.length > 0 ? null : 'empty';
  }

  /** Why a summary value is missing, shown under it. */
  missingReason(): string {
    if (this.routeState === 'error') {
      return 'Unavailable';
    }
    return this.summary?.endpoints ? 'No exchanges yet' : 'No routes yet';
  }


  filterDatasources(searchTerm: string): Datasources[] {
    const filteredOptions = this.datasources.filter(datasource => {
      return datasource.dataSourceId.toLowerCase().includes(searchTerm.toLowerCase());
    });
    return filteredOptions;
  }

  yAxisTickFormatting(value: number): string {

    if (Number.isInteger(value)) {
      return Math.round(value).toString();
    } else {
      return '';
    }
  }

  verticalGroupedTickFormat(data:any): string {
    return data+'ms';
  }

    //On key press, update the table based on search
    applyFilter(filterValue: string) {
      this.dataSource.filter = filterValue.trim();
    }

    sortByTimestamp = (a: any, b: any) => {
      const timeA = new Date(`2023-07-31T${a.name}`).getTime();
      const timeB = new Date(`2023-07-31T${b.name}`).getTime();
      return timeA - timeB;
    };

}

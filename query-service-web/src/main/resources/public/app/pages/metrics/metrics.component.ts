import { Component, ViewChild, ViewEncapsulation } from '@angular/core';
import { MetricsService } from '../../core/services/metrics.service';
import { RouteMetric, RouteMetricsResponse } from '../../core/models/metric-data';
import { DatasourceService } from '../../core/services/datasource.service';
import { Datasources } from '../../core/models/datasources';
import { Observable } from 'rxjs/internal/Observable';
import { FormGroup, FormControl } from '@angular/forms';
import { map, startWith } from 'rxjs/operators';
import { MatSelectChange } from '@angular/material/select';
import { MatTableDataSource } from '@angular/material/table';
import { MetricTableData } from '../../core/models/metric-table-data';
import { MatPaginator } from '@angular/material/paginator';
import { LineGraph } from '../../core/models/persisted-interfaces/line-graph';
import { LineGraphObject } from '../../core/models/persisted-interfaces/line-graph-object';
import { ScaleType } from '@swimlane/ngx-charts';






@Component({
  selector: 'app-metrics',
  templateUrl: './metrics.component.html',
  styleUrls: ['./metrics.component.scss'],
  encapsulation: ViewEncapsulation.None

})
export class MetricsComponent {

  constructor(private metricsService: MetricsService, private datasourceService: DatasourceService) {
  }
  persistedMetric: string = 'totalExchanges'
  persistedData : LineGraphObject[]=[];
  exchangeData: any[] = [];
  latencyData: any[]=[];
  latencyDisplay: any[]=[];
  exchangeDisplay: any[] = [];
  cardMetrics: any[] = [];
  datasources: Datasources[] = [];
  filteredDatasources!: Observable<Datasources[]> | undefined;
  cardColor = '#4a148c';
  units: string = 'Average Success Rate'
  gaugeMetric: number = 0;

  // Custom purple color scheme matching the app's purple motif
  purpleColorScheme = {
    name: 'purple',
    selectable: true,
    group: ScaleType.Ordinal,
    domain: ['#4a148c', '#6a1b9a', '#7b1fa2', '#8e24aa', '#9c27b0', '#ab47bc', '#ba68c8', '#ce93d8']
  };
  dataSource = new MatTableDataSource<MetricTableData>;
  displayedColumns: string[] = ['Name','Processing Time','Successful Exchanges','Failed Exchanges','State','Uptime'];
  @ViewChild(MatPaginator) paginator!: MatPaginator;




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
    this.getPersistedMetrics("totalProcessingTime");
  }

  getPersistedMetrics(property: keyof RouteMetric): void {
    this.persistedData=[];

    this.metricsService.getPersistedMetrics().subscribe({
      next: (response: RouteMetricsResponse) => {
        const mapping : {[routeId: string]: LineGraph[]} = {};
        for (const entry of response.metrics) {
          const {route, timeStamp} = entry;
          const value = entry[property];
          if (!mapping[route]){
            mapping[route]=[];
          }
          mapping[route].push({ value: value, name: timeStamp.substring(11,16)})
        }
        this.persistedData = Object.entries(mapping).map(([name, series])=> ({name,series}));
        this.persistedData.forEach((entry: any) => {
          entry.series.sort(this.sortByTimestamp);
        });
      }
    })
  }

  fetchRouteMetrics(): void {
    this.metricsService.getRouteMetrics().subscribe({
      next: (response: RouteMetricsResponse) => {
        this.populateBarCharts(response)
        this.generateCustomMetrics(response)
        this.populateTableData(response);
      },
      error: (error: any) => {
        console.error('Failed to fetch route metrics:', error);
      }
    });
  }

  fetchDatasourceSpecificMetrics(datasource: any): void {
    if (!datasource) {
      this.fetchRouteMetrics();
    } else {

      this.metricsService.getDatasourceRouteMetrics(datasource).subscribe({
        next: (response: RouteMetricsResponse) => {
          this.populateBarCharts(response)
        },
        error: (error: any) => {
          console.error('Failed to fetch route metrics:', error);
        }

      })
    }
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
    this.latencyData = response.metrics.map((metric: RouteMetric) => ({
      name: metric.route,
      series: [
        { name: 'Max Processing Time', value: metric.maxProcessingTime },
        { name: 'Avg Processing Time', value: metric.meanProcessingTime },
        { name: 'Min Processing Time', value: metric.minProcessingTime }
      ]
    })).sort((a, b) => b.series[0].value - a.series[0].value);
    this.exchangeDisplay = this.exchangeData;
    this.latencyDisplay=this.latencyData;
  }

  populateTableData(response: RouteMetricsResponse): void {
    let tableData = response.metrics.map((value: RouteMetric)=>{
      return <MetricTableData>{
        name: value.route,
        minProcessingTime: value.minProcessingTime,
        maxProcessingTime: value.maxProcessingTime,
        averageProcessingTime: value.meanProcessingTime,
        successfulExchanges: value.exchangesCompleted,
        failedExchanges: value.exchangesFailed,
        state: value.state,
        uptime: value.uptime
      }
    })
    this.dataSource= new MatTableDataSource(tableData);
    this.dataSource.paginator = this.paginator;
  }


  filterByRoutes(event: MatSelectChange): void {
    let selectedOptions: string[] = event.value;
    this.exchangeDisplay = this.exchangeData.filter(item => selectedOptions.includes(item.name));
    this.latencyDisplay = this.latencyData.filter(item => selectedOptions.includes(item.name));
  }

  generateCustomMetrics(response: RouteMetricsResponse): void {
    let totalExchanges = 0;
    let completedExchanges = 0;
    let totalProcessingTime = 0;
    let busiestRoute = null;

    for (const metric of response.metrics) {
      totalExchanges += metric.exchangesTotal;
      completedExchanges += metric.exchangesCompleted;
      totalProcessingTime += metric.totalProcessingTime;
      const currentRoute = metric;

      if (busiestRoute === null || currentRoute.exchangesTotal > busiestRoute.exchangesTotal) {
        busiestRoute = currentRoute;
      }


    }


    let successRate: number = Number((((completedExchanges / totalExchanges) * 100).toFixed(2)));

    this.cardMetrics = [{
      "name": "Total Endpoints",
      "value": response.metrics.length
    },

    {
      "name": "Avg Processing Time",
      "value": (totalProcessingTime / totalExchanges).toFixed(2) + "ms"
    },

    {
      "name": "Busiest Route",
      "value": busiestRoute?.route
    }
    ]

    this.gaugeMetric = successRate

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

  percentageFormat(data:any): string {
    return data+'%'
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
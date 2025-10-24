import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RouteMetricsResponse } from '../models/metric-data';


@Injectable({
  providedIn: 'root'
})
export class MetricsService {
  private metricsUrl = '/queryrest/api/metrics/routes/';

  constructor(private http: HttpClient) { }

  getRouteMetrics(): Observable<RouteMetricsResponse> {
    return this.http.get<RouteMetricsResponse>(this.metricsUrl);
  }

  getPersistedMetrics(): Observable<RouteMetricsResponse> {
    return this.http.get<RouteMetricsResponse>("/queryrest/api/metrics/route/persisted");
  }

  getDatasourceRouteMetrics(dataSource: any): Observable<RouteMetricsResponse> {
    return this.http.get<RouteMetricsResponse>(this.metricsUrl.concat(dataSource));
  }
}

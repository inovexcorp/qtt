import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {Datasources} from '../../core/models/datasources';


@Injectable({
  providedIn: 'root'
})
export class DatasourceService {

  private datasourcesUrl = '/queryrest/api/datasources/';

  constructor(private http: HttpClient) {
  }

  /** GET datasources from the server */
  getDatasources(): Observable<Datasources[]> {
    return this.http.get<Datasources[]>(this.datasourcesUrl)
  }

  /** GET singular datasource from the server */
  getDatasource(dataSourceId: string): Observable<Datasources> {
    return this.http.get<Datasources>(this.datasourcesUrl.concat(dataSourceId)
    )
  }

  /** PUT datasource to the server */
  modifyDatasource(datasource: Datasources) {
    return this.http.put<Datasources[]>(this.datasourcesUrl, datasource);
  }

  /** DELETE datasource from the server */
  deleteDatasource(dataSourceId: string): Observable<unknown> {
    return this.http.delete(this.datasourcesUrl.concat(dataSourceId));
  }

  testDatasource(datasource: Datasources): Observable<any> {
    return this.http.post<Datasources[]>(this.datasourcesUrl + 'test', datasource);
  }

  /** Trigger manual health check for a datasource */
  triggerHealthCheck(dataSourceId: string): Observable<any> {
    return this.http.post(`${this.datasourcesUrl}${dataSourceId}/healthcheck`, {});
  }

  /** GET health status and history for a datasource */
  getDatasourceHealth(dataSourceId: string): Observable<any> {
    return this.http.get(`${this.datasourcesUrl}${dataSourceId}/health`);
  }

  /** GET health summary for all datasources */
  getHealthSummary(): Observable<any> {
    return this.http.get(`${this.datasourcesUrl}health/summary`);
  }

  /** PUT request to disable a datasource */
  disableDatasource(dataSourceId: string): Observable<any> {
    return this.http.put(`${this.datasourcesUrl}${dataSourceId}/disable`, {});
  }

  /** PUT request to enable a datasource */
  enableDatasource(dataSourceId: string): Observable<any> {
    return this.http.put(`${this.datasourcesUrl}${dataSourceId}/enable`, {});
  }

  /** GET health check configuration (enabled/disabled status) */
  getHealthCheckConfig(): Observable<{enabled: boolean, available: boolean}> {
    return this.http.get<{enabled: boolean, available: boolean}>(`${this.datasourcesUrl}health/config`);
  }
}

import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { NewRoute } from '../../core/models/routes-interfaces/new-route';


@Injectable({
  providedIn: 'root'
})
export class ConfigRouteService {

  private routeTemplateUrl = '/queryrest/api/routes/templateContent/';
  private routesUrl = '/queryrest/api/routes/';
  private testingUrl = '/queryrest/api/testing/';

  constructor(private http: HttpClient) { }

  /** GET template content from the server */
  getTemplate(routeId: string) {
    return this.http.get(this.routeTemplateUrl.concat(routeId), { responseType: 'text' });
  }

  /** PUT (config) an existing route */
  configRoute(route: NewRoute) {
    let routeUrl = this.routesUrl.concat(route.routeId);
    let params = new HttpParams()
      .append('routeParams', route.routeParams)
      .append('dataSourceId', route.dataSourceId)
      .append('description', route.routeDescription)
      .append('graphMartUri', route.graphMartUri);

    // Add cache parameters if provided
    if (route.cacheEnabled) {
      params = params.append('cacheEnabled', route.cacheEnabled.toString());
    }
    if (route.cacheTtlSeconds) {
      params = params.append('cacheTtlSeconds', route.cacheTtlSeconds.toString());
    }
    if (route.cacheKeyStrategy) {
      params = params.append('cacheKeyStrategy', route.cacheKeyStrategy);
    }

    let formData = new FormData();
    formData.set('freemarker', new Blob([route.templateBody], { type: 'multipart/form-data' }));
    formData.set('layers', new Blob([route.layers], { type: 'multipart/form-data' }));

    return this.http.put<NewRoute[]>(routeUrl, formData, { params: params });
  }

  /** POST template content to extract variables */
  extractTestVariables(templateContent: string) {
    const url = this.testingUrl + 'variables';
    const body = { templateContent };
    return this.http.post(url, body);
  }

  /** POST route test execution request */
  executeRouteTest(request: any) {
    const url = this.testingUrl + 'execute';
    return this.http.post(url, request);
  }
}

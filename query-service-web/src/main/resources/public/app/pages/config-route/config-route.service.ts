import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';


@Injectable({
  providedIn: 'root'
})
export class ConfigRouteService {

  private routeTemplateUrl = '/queryrest/api/routes/templateContent/';
  private testingUrl = '/queryrest/api/testing/';

  constructor(private http: HttpClient) { }

  /** GET template content from the server */
  getTemplate(routeId: string) {
    return this.http.get(this.routeTemplateUrl.concat(routeId), { responseType: 'text' });
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

import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { NewRoute } from '../../core/models/routes-interfaces/new-route';
@Injectable({
  providedIn: 'root'
})
export class AddRouteService {

  private routesUrl = '/queryrest/api/routes/';

  constructor(private http: HttpClient) { }

  /** POST route to the server */
  postRoute(route: NewRoute) {
    let routeUrl = this.routesUrl.concat(route.routeId);
    let params = new HttpParams()
      .append('routeParams', route.routeParams)
      .append('dataSourceId', route.dataSourceId)
      .append('description', route.routeDescription)
      .append('graphMartUri', route.graphMartUri);

    // Add cache parameters if provided
    if (route.cacheEnabled !== undefined) {
      params = params.append('cacheEnabled', route.cacheEnabled.toString());
    }
    if (route.cacheTtlSeconds !== undefined && route.cacheTtlSeconds !== null) {
      params = params.append('cacheTtlSeconds', route.cacheTtlSeconds.toString());
    }
    if (route.cacheKeyStrategy) {
      params = params.append('cacheKeyStrategy', route.cacheKeyStrategy);
    }

    let formData = new FormData();
    formData.set('freemarker', new Blob([route.templateBody], { type: 'multipart/form-data' }));
    formData.set('layers', new Blob([route.layers], { type: 'multipart/form-data' }));

    return this.http.post<NewRoute[]>(routeUrl, formData, { params: params });
  }
}

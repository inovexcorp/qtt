import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Routes } from '../../core/models/routes-interfaces/routes';
import { RoutesMetadata } from '../../core/models/routes-interfaces/routes-metadata';
import { NewRoute } from '../../core/models/routes-interfaces/new-route';

/** What cloning a route answers: the copy's id (the first free "<id>Clone", "<id>Clone2"...) and its URL. */
export interface CloneResult {
  routeId: string;
  endpointUrl: string;
}

@Injectable({
  providedIn: 'root'
})
export class RoutesService {

  private routesUrl = '/queryrest/api/routes/';

  constructor(private http: HttpClient) {
  }

  /** Every stored route. */
  getRoutes(): Observable<Routes[]> {
    return this.http.get<Routes[]>(this.routesUrl);
  }

  /** One stored route (404 when it isn't loaded). */
  getRoute(routeId: string): Observable<Routes> {
    return this.http.get<Routes>(this.routesUrl + encodeURIComponent(routeId));
  }

  /** Starts or stops a route ("Started" / "Stopped"). Starting answers 409 while its datasource is down or disabled. */
  updateRouteStatus(routeId: string, status: 'Started' | 'Stopped' | string) {
    return this.http.patch(this.routesUrl + encodeURIComponent(routeId), status);
  }

  /** Creates a route (multipart: the template and the layer IRIs; the rest as query parameters). */
  createRoute(route: NewRoute) {
    return this.http.post(this.routesUrl + encodeURIComponent(route.routeId), routeForm(route), { params: routeParams(route) });
  }

  /** Replaces a route's settings and template. A stopped route stays stopped. */
  updateRoute(route: NewRoute) {
    return this.http.put(this.routesUrl + encodeURIComponent(route.routeId), routeForm(route), { params: routeParams(route) });
  }

  cloneRoute(routeId: string): Observable<CloneResult> {
    return this.http.post<CloneResult>(this.routesUrl + 'clone/' + encodeURIComponent(routeId), '');
  }

  deleteRoute(routeId: string) {
    return this.http.delete(this.routesUrl + encodeURIComponent(routeId));
  }

  getBaseUrl(): Observable<RoutesMetadata> {
    return this.http.get<RoutesMetadata>(this.routesUrl + 'metadata');
  }
}

/**
 * The query parameters of a create or update. Every setting is sent explicitly, so what the form shows is what the
 * route gets: caching on or off (an update re-creates the route, so a missing value means "off", not "unchanged"),
 * and the TTL only when one is set (none: the server default).
 */
export function routeParams(route: NewRoute): HttpParams {
  let params = new HttpParams()
    .set('routeParams', route.routeParams)
    .set('dataSourceId', route.dataSourceId)
    .set('description', route.routeDescription)
    .set('graphMartUri', route.graphMartUri)
    .set('cacheEnabled', String(route.cacheEnabled === true))
    .set('cacheKeyStrategy', route.cacheKeyStrategy || 'QUERY_HASH');
  if (route.cacheTtlSeconds !== undefined && route.cacheTtlSeconds !== null) {
    params = params.set('cacheTtlSeconds', String(route.cacheTtlSeconds));
  }
  // Who may call the route. Sent as a pair: with accessPolicy given, an absent requiredRoles means none.
  params = params.set('accessPolicy', route.accessPolicy || 'default');
  if (route.requiredRoles && route.requiredRoles.trim()) {
    params = params.set('requiredRoles', route.requiredRoles.trim());
  }
  return params;
}

function routeForm(route: NewRoute): FormData {
  const form = new FormData();
  form.set('freemarker', new Blob([route.templateBody ?? ''], { type: 'multipart/form-data' }));
  form.set('layers', new Blob([route.layers ?? ''], { type: 'multipart/form-data' }));
  return form;
}

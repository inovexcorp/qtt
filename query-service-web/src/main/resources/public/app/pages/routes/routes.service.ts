import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Routes} from '../../core/models/routes-interfaces/routes';
import {Observable} from 'rxjs';
import {NewRoute} from '../../core/models/routes-interfaces/new-route';
import {RoutesMetadata} from "../../core/models/routes-interfaces/routes-metadata";


@Injectable({
  providedIn: 'root'
})
export class RoutesService {

  private routesUrl = '/queryrest/api/routes/';


  constructor(private http: HttpClient) {
  }

  /** GET routes from the server */
  getRoutes(): Observable<Routes[]> {
    return this.http.get<Routes[]>(this.routesUrl)
  }

  /** PATCH a status to a route */
  updateRouteStatus(routeId: string, status: string) {
    return this.http.patch(this.routesUrl.concat(routeId), status);
  }

  cloneRoute(routeId: string) {
    return this.http.post<NewRoute[]>(this.routesUrl + 'clone/' + routeId, "");
  }

  getBaseUrl(): Observable<RoutesMetadata> {
    return this.http.get<RoutesMetadata>(this.routesUrl + 'metadata');
  }
}

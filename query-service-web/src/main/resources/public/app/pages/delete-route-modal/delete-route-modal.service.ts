import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';


@Injectable({
  providedIn: 'root'
})
export class DeleteRouteModalService {

  private routesUrl= '/queryrest/api/routes/';

  constructor(private http: HttpClient) { }

  
    /** DELETE route from the server */
    deleteRoute(routeId: string) {
      return this.http.delete(this.routesUrl.concat(routeId))
    }
}

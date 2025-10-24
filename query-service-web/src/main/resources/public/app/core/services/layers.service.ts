import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GraphmartLayers } from '../models/graphmart-layers';

@Injectable({
  providedIn: 'root'
})
export class LayersService {
  private anzoLayersUrl: string = '/queryrest/api/anzo/layers/';
  private  routeLayersUrl: string = "/queryrest/api/layers/";


  constructor(private http: HttpClient) { }

    /** GET layers from the server with specified graphmart and dsID from anzo*/
  getGraphmartLayers(dataSourceId: string, graphmartUri: string): Observable<GraphmartLayers[]> {
    let params = new HttpParams().append('graphmartUri', graphmartUri)
    return this.http.get<GraphmartLayers[]>(this.anzoLayersUrl.concat(dataSourceId),{
    params:params
  })
}


  // Get layers tied to a route
  getRouteLayers(routeId: string): Observable <String[]>{
    return this.http.get<string[]>(this.routeLayersUrl.concat(routeId));
  }

}

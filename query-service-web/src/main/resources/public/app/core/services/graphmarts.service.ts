import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Graphmarts } from '../models/graphmarts';

@Injectable({
  providedIn: 'root'
})
export class GraphmartsService {

  private graphMartUrl= '/queryrest/api/anzo/graphmarts/';


  constructor(private http: HttpClient) { }

    /** GET datasources from the server */
getGraphMarts(dataSourceId: string): Observable<Graphmarts[]> {
  return this.http.get<Graphmarts[]>(this.graphMartUrl.concat(dataSourceId))
}
}

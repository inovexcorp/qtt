import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Datasources } from '../../core/models/datasources';

@Injectable({
  providedIn: 'root'
})
export class AddDatasourceService {

  private datasourcesUrl= '/queryrest/api/datasources/'

  constructor(private http: HttpClient) { }

  /** POST datasource to the server */
postDatasource(datasource: Datasources) {
  return this.http.post<Datasources[]>(this.datasourcesUrl,datasource);
}

}

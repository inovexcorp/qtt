import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CacheStatistics } from '../models/ontology/cache-statistics';

@Injectable({
  providedIn: 'root'
})
export class SettingsService {

  private settingsUrl = '/queryrest/api/settings/';
  private ontologyUrl = '/queryrest/api/ontology/';

  constructor(private http: HttpClient) { }

  getDatabaseInfo(): Observable<any> {
    return this.http.get<any>(this.settingsUrl + 'sysinfo');
  }

  getStats(): Observable<any> {
    return this.http.get<any>(this.settingsUrl + 'stats');
  }

  getCacheStatistics(): Observable<CacheStatistics> {
    return this.http.get<CacheStatistics>(this.ontologyUrl + 'cache/statistics');
  }
}

import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {CacheInfo, RouteCacheStats} from '../models/cache-info';

@Injectable({
  providedIn: 'root'
})
export class CacheService {

  private cacheUrl = '/queryrest/api/routes/cache/';

  constructor(private http: HttpClient) {
  }

  /** GET cache connection information */
  getCacheInfo(): Observable<CacheInfo> {
    return this.http.get<CacheInfo>(this.cacheUrl + 'info');
  }

  /** DELETE cache for a specific route */
  clearRouteCache(routeId: string): Observable<any> {
    return this.http.delete(`/queryrest/api/routes/${routeId}/cache`);
  }

  /** DELETE all cache entries with the configured prefix */
  clearAllCache(): Observable<any> {
    return this.http.delete(this.cacheUrl + 'all');
  }

  /** GET cache statistics for a specific route */
  getRouteCacheStats(routeId: string): Observable<RouteCacheStats> {
    return this.http.get<RouteCacheStats>(`/queryrest/api/routes/${routeId}/cache/stats`);
  }
}

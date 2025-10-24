import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map, shareReplay } from 'rxjs/operators';
import {
  OntologyElement,
  OntologyElementType,
  OntologyMetadata,
  CacheStatistics,
  OntologySearchParams
} from '../../models/ontology/ontology-element';

/**
 * Service for querying ontology elements from Anzo for URI autocomplete.
 * Interacts with the backend OntologyController REST API.
 */
@Injectable({
  providedIn: 'root'
})
export class OntologyService {
  private readonly API_BASE = '/queryrest/api/ontology';
  private cache = new Map<string, Observable<OntologyElement[]>>();

  constructor(private http: HttpClient) {}

  /**
   * Get ontology elements for a specific route with optional filtering.
   * Results are cached client-side to reduce server requests.
   */
  getOntologyElements(params: OntologySearchParams): Observable<OntologyElement[]> {
    const cacheKey = this.buildCacheKey(params);

    // Return cached result if available and no prefix filter
    if (!params.prefix && this.cache.has(cacheKey)) {
      return this.cache.get(cacheKey)!;
    }

    let httpParams = new HttpParams();
    if (params.type && params.type !== 'all') {
      httpParams = httpParams.set('type', params.type);
    }
    if (params.prefix) {
      httpParams = httpParams.set('prefix', params.prefix);
    }
    if (params.limit) {
      httpParams = httpParams.set('limit', params.limit.toString());
    }

    const request$ = this.http.get<OntologyElement[]>(
      `${this.API_BASE}/${params.routeId}`,
      { params: httpParams }
    ).pipe(
      catchError(error => {
        console.error('Failed to fetch ontology elements:', error);
        return of([]);
      }),
      shareReplay(1)
    );

    // Cache full queries (without prefix filter)
    if (!params.prefix) {
      this.cache.set(cacheKey, request$);
    }

    return request$;
  }

  /**
   * Search ontology elements with a prefix filter.
   * This is the main method used for autocomplete functionality.
   */
  searchOntologyElements(
    routeId: string,
    prefix: string,
    type: OntologyElementType = 'all',
    limit: number = 50
  ): Observable<OntologyElement[]> {
    return this.getOntologyElements({ routeId, type, prefix, limit });
  }

  /**
   * Get metadata about cached ontology for a route.
   */
  getOntologyMetadata(routeId: string): Observable<OntologyMetadata> {
    return this.http.get<OntologyMetadata>(`${this.API_BASE}/${routeId}/metadata`).pipe(
      catchError(error => {
        console.error('Failed to fetch ontology metadata:', error);
        throw error;
      })
    );
  }

  /**
   * Force refresh of ontology cache for a route.
   */
  refreshOntologyCache(routeId: string): Observable<any> {
    this.clearClientCache(routeId);
    return this.http.post(`${this.API_BASE}/${routeId}/refresh`, {}).pipe(
      catchError(error => {
        console.error('Failed to refresh ontology cache:', error);
        throw error;
      })
    );
  }

  /**
   * Clear ontology cache for a route.
   */
  clearOntologyCache(routeId: string): Observable<any> {
    this.clearClientCache(routeId);
    return this.http.delete(`${this.API_BASE}/${routeId}`).pipe(
      catchError(error => {
        console.error('Failed to clear ontology cache:', error);
        throw error;
      })
    );
  }

  /**
   * Get overall cache statistics.
   */
  getCacheStatistics(): Observable<CacheStatistics> {
    return this.http.get<CacheStatistics>(`${this.API_BASE}/cache/statistics`).pipe(
      catchError(error => {
        console.error('Failed to fetch cache statistics:', error);
        throw error;
      })
    );
  }

  /**
   * Warm up cache for a route (pre-load ontology data).
   */
  warmCache(routeId: string): Observable<any> {
    return this.http.post(`${this.API_BASE}/${routeId}/warm`, {}).pipe(
      catchError(error => {
        console.warn('Failed to warm cache:', error);
        return of(null);
      })
    );
  }

  /**
   * Clear client-side cache for a specific route.
   */
  private clearClientCache(routeId: string): void {
    const keysToDelete: string[] = [];
    this.cache.forEach((_, key) => {
      if (key.startsWith(routeId)) {
        keysToDelete.push(key);
      }
    });
    keysToDelete.forEach(key => this.cache.delete(key));
  }

  /**
   * Build cache key from search parameters.
   */
  private buildCacheKey(params: OntologySearchParams): string {
    return `${params.routeId}:${params.type || 'all'}`;
  }

  /**
   * Format ontology element for display in autocomplete.
   */
  formatElementForDisplay(element: OntologyElement): string {
    const typeLabel = this.getTypeLabel(element.type);
    return `${element.label} (${typeLabel})`;
  }

  /**
   * Get human-readable label for element type.
   */
  private getTypeLabel(type: OntologyElementType): string {
    const labels: Record<OntologyElementType, string> = {
      'class': 'Class',
      'objectProperty': 'Object Property',
      'datatypeProperty': 'Datatype Property',
      'annotationProperty': 'Annotation Property',
      'individual': 'Individual',
      'all': 'All Types'
    };
    return labels[type] || type;
  }
}

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** A query API key as the server lists it (never its secret). */
export interface ApiKey {
  id: string;
  name: string;
  description: string | null;
  /** Route IDs, or "*" for every authenticated route that requires no roles. */
  routes: string[];
  createdBy: string | null;
  createdAt: number | null;
  expiresAt: number | null;
  lastUsedAt: number | null;
  revokedAt: number | null;
  revokedBy: string | null;
  status: 'active' | 'expired' | 'revoked';
}

/** The response that creates a key: the only time the key itself is returned. */
export interface CreatedApiKey extends ApiKey {
  key: string;
}

export interface NewApiKey {
  name: string;
  description?: string;
  routes: string[];
  /** Omitted: the server's maximum (QUERY_API_KEY_MAX_DAYS). */
  expiresInDays?: number;
}

/** API keys for the query routes (admin only). */
@Injectable({ providedIn: 'root' })
export class ApiKeysService {
  private readonly url = '/queryrest/api/apikeys';

  constructor(private http: HttpClient) {}

  list(): Observable<ApiKey[]> {
    return this.http.get<ApiKey[]>(this.url);
  }

  create(key: NewApiKey): Observable<CreatedApiKey> {
    return this.http.post<CreatedApiKey>(this.url, key);
  }

  revoke(id: string): Observable<void> {
    return this.http.delete<void>(this.url + '/' + encodeURIComponent(id));
  }
}

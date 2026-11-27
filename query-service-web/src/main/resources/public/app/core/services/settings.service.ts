import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CacheStatistics } from '../models/ontology/cache-statistics';

/** The response of GET /queryrest/api/settings/sso: how sign-in is configured (no secrets). */
export interface SsoInfo {
  enabled: boolean;
  issuer: string | null;
  clientId: string | null;
  clientSecretConfigured: boolean;
  applicationType: string | null;
  usernameClaim: string | null;
  /** Null means the provider's default role claims are read. */
  rolesClaim: string | null;
  rolesSource: string | null;
  audience: string[];
  groupMappings: { viewer: string[]; editor: string[]; admin: string[] };
  /** Which key encrypts the session cookie: 'OIDC_SESSION_SECRET', 'client secret' or 'generated at startup'. */
  sessionKey: string;
  privateCaTrustStore: boolean;
  forceHttpsRedirect: boolean;
  logoutPath: string | null;
}

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

  getSsoInfo(): Observable<SsoInfo> {
    return this.http.get<SsoInfo>(this.settingsUrl + 'sso');
  }

  getCacheStatistics(): Observable<CacheStatistics> {
    return this.http.get<CacheStatistics>(this.ontologyUrl + 'cache/statistics');
  }
}

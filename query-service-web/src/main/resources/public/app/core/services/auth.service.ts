import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';

export interface User {
  id: number;
  username: string;
  email: string;
  displayName: string;
  roles: string[];
}

export interface AuthProvider {
  id: string;
  name: string;
  type: string;
  enabled: string;
  icon: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_BASE = '/queryrest/api/auth';
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {
    this.loadCurrentUser();
  }

  /**
   * Load current user session from server
   */
  loadCurrentUser(): void {
    this.http.get<User>(`${this.API_BASE}/session`).pipe(
      catchError(error => {
        // Not authenticated, clear user
        this.currentUserSubject.next(null);
        return throwError(() => error);
      })
    ).subscribe(user => {
      this.currentUserSubject.next(user);
    });
  }

  /**
   * Get list of enabled authentication providers
   */
  getAuthProviders(): Observable<AuthProvider[]> {
    return this.http.get<AuthProvider[]>(`${this.API_BASE}/providers`);
  }

  /**
   * Initiate OAuth/OIDC/SAML login flow
   */
  loginWithProvider(providerId: string): void {
    window.location.href = `${this.API_BASE}/login/oauth2/${providerId}`;
  }

  /**
   * Development login (REMOVE IN PRODUCTION)
   */
  devLogin(email: string): Observable<any> {
    return this.http.post(`${this.API_BASE}/dev/login`, { email }).pipe(
      tap((response: any) => {
        this.currentUserSubject.next(response.user);
      })
    );
  }

  /**
   * Logout and invalidate session
   */
  logout(): Observable<void> {
    return this.http.post<void>(`${this.API_BASE}/logout`, {}).pipe(
      tap(() => {
        this.currentUserSubject.next(null);
      })
    );
  }

  /**
   * Check if user is authenticated
   */
  isAuthenticated(): boolean {
    return this.currentUserSubject.value !== null;
  }

  /**
   * Get current user
   */
  get currentUser(): User | null {
    return this.currentUserSubject.value;
  }

  /**
   * Check if user has a specific role
   */
  hasRole(role: string): boolean {
    const user = this.currentUserSubject.value;
    return user?.roles?.includes(role) || false;
  }

  /**
   * Check if user has any of the specified roles
   */
  hasAnyRole(...roles: string[]): boolean {
    return roles.some(role => this.hasRole(role));
  }

  /**
   * Check if user has permission (based on role permissions)
   */
  hasPermission(permission: string): boolean {
    // TODO: Implement permission checking based on role permissions
    // For now, admins have all permissions
    return this.hasRole('ADMIN');
  }
}

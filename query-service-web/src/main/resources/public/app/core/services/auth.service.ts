import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, firstValueFrom } from 'rxjs';

/** QTT roles, lowest first. Each includes the ones before it. */
export type QttRole = 'viewer' | 'editor' | 'admin';

/** The roles that grant access to the UI and management API; qtt-query (calling query routes) is not one of them. */
const MANAGEMENT_ROLES = ['qtt-viewer', 'qtt-editor', 'qtt-admin'];

/** The response of GET /queryrest/api/me. */
export interface CurrentUser {
  username: string;
  displayName: string;
  /** Granted roles, e.g. ["qtt-viewer", "qtt-editor"]. */
  roles: string[];
  /** False when the server runs without sign-in (everyone is "anonymous" with every role). */
  authEnabled: boolean;
  logoutUrl: string | null;
}

/** Used until /me answers, and if it fails: hide nothing, since the server enforces roles anyway. */
const UNKNOWN_USER: CurrentUser = {
  username: 'anonymous',
  displayName: 'anonymous',
  roles: ['qtt-viewer', 'qtt-editor', 'qtt-admin'],
  authEnabled: false,
  logoutUrl: null
};

/** Where the UI was when the session ran out; restored after signing in again (the IdP round trip drops the #fragment). */
export const RETURN_TO_KEY = 'qtt.returnTo';
/** When the UI last reloaded to sign in again; stops a reload loop if signing in keeps failing. */
export const SIGN_IN_RELOAD_KEY = 'qtt.signInReloadAt';

/**
 * Who is signed in, loaded once at startup. Components use it to show the user and hide actions their roles
 * don't allow; the server enforces the roles regardless.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly meUrl = '/queryrest/api/me';
  private readonly userSubject = new BehaviorSubject<CurrentUser>(UNKNOWN_USER);
  readonly user$ = this.userSubject.asObservable();

  constructor(private http: HttpClient) {}

  get user(): CurrentUser {
    return this.userSubject.value;
  }

  /**
   * Signed in, but holding no management role: the UI shows a "no access" page instead of the app. qtt-query alone
   * (calling query routes) gives nothing to do in the UI.
   */
  get noAccess(): boolean {
    return this.user.authEnabled && !this.user.roles.some(r => MANAGEMENT_ROLES.includes(r));
  }

  hasRole(role: QttRole): boolean {
    return this.user.roles.includes('qtt-' + role);
  }

  /** The highest role, for display ("Admin", "Editor", "Viewer"), or null. */
  get roleLabel(): string | null {
    for (const role of ['admin', 'editor', 'viewer'] as QttRole[]) {
      if (this.hasRole(role)) {
        return role.charAt(0).toUpperCase() + role.slice(1);
      }
    }
    return null;
  }

  /** Loads the current user (APP_INITIALIZER). Never rejects: on failure the UI behaves as before sign-in existed. */
  async load(): Promise<void> {
    try {
      this.userSubject.next(await firstValueFrom(this.http.get<CurrentUser>(this.meUrl)));
      this.restoreLocation();
    } catch {
      // 499 (session expired) is handled by authInterceptor, which reloads the page to sign in again.
    }
  }

  private restoreLocation(): void {
    try {
      sessionStorage.removeItem(SIGN_IN_RELOAD_KEY);
      const back = sessionStorage.getItem(RETURN_TO_KEY);
      sessionStorage.removeItem(RETURN_TO_KEY);
      if (back && back.startsWith('#/') && window.location.hash !== back) {
        window.location.hash = back;
      }
    } catch {
      // sessionStorage unavailable: stay on the default page
    }
  }
}

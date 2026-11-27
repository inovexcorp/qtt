import { Injectable } from '@angular/core';
import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, catchError, throwError } from 'rxjs';
import { AuthService, RETURN_TO_KEY, SIGN_IN_RELOAD_KEY } from '../services/auth.service';

/** Sent on every API call: marks it as a script request (499 instead of a login redirect) and satisfies the CSRF check. */
export const REQUESTED_WITH = 'X-Requested-With';

/** Don't reload to sign in again more often than this; a faster repeat means signing in is failing. */
const RELOAD_GUARD_MS = 15000;

/**
 * Connects the UI to the server's sign-in handling:
 * <ul>
 *   <li>adds {@code X-Requested-With: JavaScript} to requests to QTT;</li>
 *   <li>on 499 (no session, or it expired) reloads the page, which sends the browser through the IdP login and
 *       back, remembering the current #route;</li>
 *   <li>on 403 tells the user they lack permission.</li>
 * </ul>
 */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  constructor(private snackBar: MatSnackBar, private auth: AuthService) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    if (!AuthInterceptor.isSameOrigin(request.url)) {
      return next.handle(request);
    }
    return next.handle(request.clone({ setHeaders: { [REQUESTED_WITH]: 'JavaScript' } })).pipe(
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse) {
          if (error.status === 499) {
            this.signInAgain();
          } else if (error.status === 403 && !this.auth.noAccess) {
            this.snackBar.open("You don't have permission to do that.", 'Dismiss', { duration: 6000 });
          }
        }
        return throwError(() => error);
      })
    );
  }

  /** Relative URLs and absolute ones on this origin. */
  static isSameOrigin(url: string): boolean {
    if (!/^[a-z][a-z0-9+.-]*:/i.test(url) && !url.startsWith('//')) {
      return true;
    }
    try {
      return new URL(url).origin === window.location.origin;
    } catch {
      return false;
    }
  }

  private signInAgain(): void {
    let last = 0;
    try {
      last = Number(sessionStorage.getItem(SIGN_IN_RELOAD_KEY) ?? 0);
    } catch {
      // sessionStorage unavailable
    }
    if (Date.now() - last < RELOAD_GUARD_MS) {
      this.snackBar.open('Your session has ended and signing in again did not work. Reload the page to retry.',
        'Dismiss');
      return;
    }
    try {
      sessionStorage.setItem(SIGN_IN_RELOAD_KEY, String(Date.now()));
      sessionStorage.setItem(RETURN_TO_KEY, window.location.hash);
    } catch {
      // sessionStorage unavailable: the user lands on the default page after signing in
    }
    window.location.reload();
  }
}

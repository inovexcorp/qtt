import type { MockedObject } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HTTP_INTERCEPTORS, HttpClient, provideHttpClient, withInterceptorsFromDi, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthInterceptor, REQUESTED_WITH } from './auth.interceptor';
import { SIGN_IN_RELOAD_KEY } from '../services/auth.service';

describe('AuthInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let snackBar: MockedObject<MatSnackBar>;

  beforeEach(() => {
    snackBar = {
      open: vi.fn().mockName('MatSnackBar.open')
    } as unknown as MockedObject<MatSnackBar>;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
        { provide: MatSnackBar, useValue: snackBar }
      ]
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('marks requests to QTT as script requests', () => {
    http.get('/queryrest/api/routes').subscribe();
    expect(backend.expectOne('/queryrest/api/routes').request.headers.get(REQUESTED_WITH)).toBe('JavaScript');
  });

  it('leaves requests to other sites alone', () => {
    http.get('https://example.com/data.json').subscribe();
    expect(backend.expectOne('https://example.com/data.json').request.headers.has(REQUESTED_WITH)).toBe(false);
  });

  it('tells the user when a request is forbidden', () => {
    http.delete('/queryrest/api/datasources/x').subscribe({ error: () => undefined });
    backend.expectOne('/queryrest/api/datasources/x').flush('', { status: 403, statusText: 'Forbidden' });
    expect(snackBar.open).toHaveBeenCalled();
  });

  it('does not reload again straight after a sign-in reload', () => {
    // A reload just happened: a second 499 means signing in is failing, so say so instead of looping.
    sessionStorage.setItem(SIGN_IN_RELOAD_KEY, String(Date.now()));
    http.get('/queryrest/api/me').subscribe({ error: () => undefined });
    backend.expectOne('/queryrest/api/me').flush('', { status: 499, statusText: '' });
    expect(snackBar.open).toHaveBeenCalledWith(expect.stringMatching(/session has ended/), 'Dismiss');
    sessionStorage.removeItem(SIGN_IN_RELOAD_KEY);
  });

  it('recognises same-origin URLs', () => {
    expect(AuthInterceptor.isSameOrigin('/queryrest/api/me')).toBe(true);
    expect(AuthInterceptor.isSameOrigin('assets/i18n.json')).toBe(true);
    expect(AuthInterceptor.isSameOrigin(window.location.origin + '/queryrest/api/me')).toBe(true);
    expect(AuthInterceptor.isSameOrigin('https://example.com/x')).toBe(false);
    expect(AuthInterceptor.isSameOrigin('//example.com/x')).toBe(false);
  });
});

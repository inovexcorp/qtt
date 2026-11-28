import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AuthService, CurrentUser, RETURN_TO_KEY, SIGN_IN_RELOAD_KEY } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  const editor: CurrentUser = {
    username: 'editor', displayName: 'Eddie Editor', roles: ['qtt-viewer', 'qtt-editor'],
    authEnabled: true, logoutUrl: '/logout'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    sessionStorage.removeItem(RETURN_TO_KEY);
    sessionStorage.removeItem(SIGN_IN_RELOAD_KEY);
  });

  afterEach(() => http.verify());

  it('allows everything until the user is known', () => {
    expect(service.hasRole('admin')).toBeTrue();
    expect(service.noAccess).toBeFalse();
    expect(service.user.authEnabled).toBeFalse();
  });

  it('loads the signed-in user and their roles', async () => {
    const loading = service.load();
    http.expectOne('/queryrest/api/me').flush(editor);
    await loading;

    expect(service.user.displayName).toBe('Eddie Editor');
    expect(service.hasRole('viewer')).toBeTrue();
    expect(service.hasRole('editor')).toBeTrue();
    expect(service.hasRole('admin')).toBeFalse();
    expect(service.roleLabel).toBe('Editor');
    expect(service.noAccess).toBeFalse();
  });

  it('reports no access for a signed-in user without roles', async () => {
    const loading = service.load();
    http.expectOne('/queryrest/api/me').flush({ ...editor, roles: [] });
    await loading;

    expect(service.noAccess).toBeTrue();
    expect(service.roleLabel).toBeNull();
  });

  it('reports no access for a user who may only call query routes', async () => {
    const loading = service.load();
    http.expectOne('/queryrest/api/me').flush({ ...editor, roles: ['qtt-query'] });
    await loading;

    expect(service.noAccess).toBeTrue();
  });

  it('keeps allowing everything when /me fails', async () => {
    const loading = service.load();
    http.expectOne('/queryrest/api/me').flush('boom', { status: 500, statusText: 'Server Error' });
    await loading;

    expect(service.hasRole('admin')).toBeTrue();
    expect(service.noAccess).toBeFalse();
  });

  it('clears the sign-in reload marker once signed in', async () => {
    sessionStorage.setItem(SIGN_IN_RELOAD_KEY, String(Date.now()));
    sessionStorage.setItem(RETURN_TO_KEY, window.location.hash);
    const loading = service.load();
    http.expectOne('/queryrest/api/me').flush(editor);
    await loading;

    expect(sessionStorage.getItem(SIGN_IN_RELOAD_KEY)).toBeNull();
    expect(sessionStorage.getItem(RETURN_TO_KEY)).toBeNull();
  });
});

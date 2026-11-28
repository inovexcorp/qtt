import { of } from 'rxjs';
import { Router } from '@angular/router';
import { ConfirmDialogComponent } from '../confirm-dialog/confirm-dialog.component';
import { clearAllCaches, globalActions, navigationActions } from './global-actions';

describe('quick actions: global actions', () => {
  const router = { navigate: vi.fn(), navigateByUrl: vi.fn() } as unknown as Router;

  function deps(cacheInfo: unknown, confirmed = true) {
    return {
      router,
      auth: { user: { authEnabled: true, logoutUrl: '/logout' } } as any,
      dialog: { open: vi.fn(() => ({ afterClosed: () => of(confirmed) })) } as any,
      snackBar: { open: vi.fn() } as any,
      cache: { getCacheInfo: vi.fn(() => of(cacheInfo)), clearAllCache: vi.fn(() => of({ deletedCount: 7 })) } as any
    };
  }

  it('gates each action by role', () => {
    const roles = Object.fromEntries(globalActions(deps({})).map(a => [a.id, a.minRole ?? 'viewer']));
    expect(roles).toEqual({ 'add-route': 'editor', 'add-datasource': 'admin', 'create-api-key': 'admin',
      'clear-all-caches': 'admin', shortcuts: 'viewer', 'sign-out': 'viewer' });
  });

  it('offers sign out only when sign-in is on', () => {
    const d = deps({});
    const signOut = globalActions(d).find(a => a.id === 'sign-out')!;
    expect(signOut.visible!()).toBe(true);
    d.auth.user = { authEnabled: false, logoutUrl: null };
    expect(signOut.visible!()).toBe(false);
  });

  it('opens the new-route page, and asks settings to start a new API key', () => {
    const actions = globalActions(deps({}));
    actions.find(a => a.id === 'add-route')!.run();
    expect(router.navigate).toHaveBeenCalledWith(['/routes', 'add']);
    actions.find(a => a.id === 'create-api-key')!.run();
    expect(router.navigate).toHaveBeenCalledWith(['/settings'], { queryParams: { section: 'access', apiKey: 'new' } });
  });

  it('lists Settings sections, but only once something is typed', () => {
    const sections = navigationActions(router).filter(a => a.id.startsWith('go:settings:'));
    expect(sections.map(a => a.label)).toEqual(['Settings: Sign-in', 'Settings: Query access', 'Settings: Caching',
      'Settings: SPARQi']);
    expect(sections.every(a => a.searchOnly)).toBe(true);
  });

  describe('clear all caches', () => {
    it('says so when the query cache is off, without asking', () => {
      const d = deps({ available: false });
      clearAllCaches(d);
      expect(d.dialog.open).not.toHaveBeenCalled();
      expect(d.snackBar.open.mock.lastCall[0]).toContain('query cache is off');
    });

    it('says how many results will go, and clears when confirmed', () => {
      const d = deps({ available: true, stats: { keyCount: 7 } });
      clearAllCaches(d);
      const [type, config] = d.dialog.open.mock.lastCall as any[];
      expect(type).toBe(ConfirmDialogComponent);
      expect(config.data.message[0]).toBe('7 cached results, for every route, will be deleted.');
      expect(d.cache.clearAllCache).toHaveBeenCalled();
      expect(d.snackBar.open.mock.lastCall[0]).toBe('Cleared 7 cached results.');
    });

    it('says so when there is nothing cached, without asking', () => {
      const d = deps({ available: true, stats: { keyCount: 0 } });
      clearAllCaches(d);
      expect(d.dialog.open).not.toHaveBeenCalled();
      expect(d.snackBar.open.mock.lastCall[0]).toBe('The query cache is already empty.');
    });

    it('does nothing when not confirmed', () => {
      const d = deps({ available: true, stats: { keyCount: 1 } }, false);
      clearAllCaches(d);
      expect(d.cache.clearAllCache).not.toHaveBeenCalled();
    });
  });
});

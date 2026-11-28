import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SETTINGS_SECTIONS } from '../../pages/settings/settings-sections';
import { AuthService } from '../../core/services/auth.service';
import { CacheService } from '../../core/services/cache.service';
import { saveErrorMessage } from '../../core/http-error-message';
import { confirm } from '../confirm-dialog/confirm-dialog.component';
import { QuickAction } from './quick-action';
import { ShortcutsDialogComponent } from './shortcuts-dialog.component';

/** Query parameters a page reads to start an action the bar asked for (and then removes from the URL). */
export const ADD_ROUTE_PARAM = { add: 'route' };
export const NEW_API_KEY_PARAM = { apiKey: 'new' };

/** The places the bar can go: the four pages, and each Settings section (listed once something is typed). */
export function navigationActions(router: Router): QuickAction[] {
  const page = (id: string, label: string, icon: string, path: string, keywords: string[] = []): QuickAction => ({
    id: 'go:' + id, label, icon, group: 'navigation', keywords, hint: 'Page',
    run: () => router.navigateByUrl(path)
  });
  return [
    page('metrics', 'Metrics', 'fact_check', '/metrics', ['dashboard', 'stats', 'home']),
    page('routes', 'Routes', 'dashboard', '/routes', ['queries', 'templates']),
    page('datasources', 'Datasources', 'dns', '/datasources', ['anzo', 'connections']),
    page('settings', 'Settings', 'settings', '/settings', ['configuration', 'system']),
    ...SETTINGS_SECTIONS.filter(s => s.id !== 'overview').map(s => ({
      id: 'go:settings:' + s.id,
      label: 'Settings: ' + s.label,
      icon: s.icon,
      group: 'navigation' as const,
      hint: 'Settings',
      searchOnly: true,
      run: () => router.navigate(['/settings'], { queryParams: { section: s.id } })
    }))
  ];
}

export interface GlobalActionDeps {
  router: Router;
  auth: AuthService;
  dialog: MatDialog;
  snackBar: MatSnackBar;
  cache: CacheService;
}

/** Actions available on every page. */
export function globalActions(d: GlobalActionDeps): QuickAction[] {
  return [
    {
      id: 'add-route', label: 'Add route', icon: 'add', group: 'actions', minRole: 'editor',
      keywords: ['new route', 'create route'],
      run: () => d.router.navigate(['/routes'], { queryParams: ADD_ROUTE_PARAM })
    },
    {
      id: 'add-datasource', label: 'Add datasource', icon: 'add', group: 'actions', minRole: 'admin',
      keywords: ['new datasource', 'create datasource', 'anzo'],
      run: () => d.router.navigateByUrl('/datasources/add-datasource')
    },
    {
      id: 'create-api-key', label: 'Create API key', icon: 'vpn_key', group: 'actions', minRole: 'admin',
      keywords: ['new api key', 'token'],
      run: () => d.router.navigate(['/settings'], { queryParams: { section: 'access', ...NEW_API_KEY_PARAM } })
    },
    {
      id: 'clear-all-caches', label: 'Clear all caches', icon: 'delete_sweep', group: 'actions', minRole: 'admin',
      keywords: ['flush', 'query cache', 'redis'],
      run: () => clearAllCaches(d)
    },
    {
      id: 'shortcuts', label: 'Keyboard shortcuts', icon: 'keyboard', group: 'actions', keywords: ['help', 'keys'],
      run: () => { openShortcuts(d.dialog); }
    },
    {
      id: 'sign-out', label: 'Sign out', icon: 'logout', group: 'actions', keywords: ['log out', 'logout'],
      visible: () => d.auth.user.authEnabled && !!d.auth.user.logoutUrl,
      run: () => window.location.assign(d.auth.user.logoutUrl!)
    }
  ];
}

export function openShortcuts(dialog: MatDialog): void {
  dialog.open(ShortcutsDialogComponent, { width: '520px', maxWidth: 'calc(100vw - 32px)' });
}

/** GET /routes/cache/info. */
interface CacheInfoResponse {
  available?: boolean;
  stats?: { keyCount?: number };
}

/** Says how much is cached, asks, then clears the query cache of every route. */
export function clearAllCaches(d: Pick<GlobalActionDeps, 'dialog' | 'snackBar' | 'cache'>): void {
  const failed = (error: unknown) => d.snackBar.open(`Caches not cleared: ${saveErrorMessage(error)}`, 'Close',
    { duration: 10000, panelClass: ['error-snackbar'] });
  d.cache.getCacheInfo().subscribe({
    next: response => {
      const info = response as unknown as CacheInfoResponse;
      if (!info.available) {
        d.snackBar.open('The query cache is off: there is nothing to clear.', 'Close', { duration: 5000 });
        return;
      }
      const count = info.stats?.keyCount;
      if (count === 0) {
        d.snackBar.open('The query cache is already empty.', 'Close', { duration: 5000 });
        return;
      }
      confirm(d.dialog, {
        title: 'Clear all caches?',
        message: [
          count === undefined ? 'Every route\'s cached results will be deleted.'
            : `${count} cached ${count === 1 ? 'result' : 'results'}, for every route, will be deleted.`,
          'Queries go to the datasources again until the cache fills back up.'
        ],
        confirmLabel: 'Clear all caches'
      }).subscribe(confirmed => {
        if (!confirmed) {
          return;
        }
        d.cache.clearAllCache().subscribe({
          next: (result: { deletedCount?: number }) => {
            const deleted = result?.deletedCount ?? 0;
            d.snackBar.open(`Cleared ${deleted} cached ${deleted === 1 ? 'result' : 'results'}.`, 'Close', { duration: 5000 });
          },
          error: failed
        });
      });
    },
    error: failed
  });
}

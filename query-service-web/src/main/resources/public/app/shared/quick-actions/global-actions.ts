import { Router } from '@angular/router';
import { SETTINGS_SECTIONS } from '../../pages/settings/settings-sections';
import { QuickAction } from './quick-action';

/** The places the bar can go: the four pages, and each Settings section. */
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

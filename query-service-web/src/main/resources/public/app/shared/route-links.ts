/** Links to the route pages, in one place (the list, the quick actions bar, recents and the datasource page use them). */

/** The tabs of a route's page, as they appear in its URL (`?tab=`). */
export type RouteTab = 'overview' | 'template' | 'test';

export const ROUTE_TABS: readonly RouteTab[] = ['overview', 'template', 'test'];

/** Router commands for a route's page. */
export function routeLink(routeId: string): string[] {
  return ['/routes', 'config-route', routeId];
}

/** Query params for a tab of a route's page (none for the default tab, so plain links stay plain). */
export function routeTabParams(tab: RouteTab | undefined): { tab?: RouteTab } {
  return tab && tab !== 'overview' ? { tab } : {};
}

/** The tab a `?tab=` value names; the overview for anything else. */
export function parseRouteTab(value: string | null | undefined): RouteTab {
  return ROUTE_TABS.includes(value as RouteTab) ? value as RouteTab : 'overview';
}

/** Router commands for the new-route page. */
export const ADD_ROUTE_LINK = ['/routes', 'add'];

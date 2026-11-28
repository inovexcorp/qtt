import { Range, match } from './match';
import { DatasourceEntry, QuickAction, RouteEntry } from './quick-action';

/** One row of the results. */
export interface ResultItem {
  /** Unique in the results; also the option's DOM id suffix. */
  key: string;
  kind: 'action' | 'route' | 'datasource' | 'help';
  label: string;
  ranges: Range[];
  /** Muted second line, e.g. a route's description. */
  detail?: string;
  icon: string;
  /** Text at the end of the row. */
  hint?: string;
  /** Tone of the hint: a route's or datasource's status. */
  tone?: 'ok' | 'warn' | 'off';
  /** When set, the row is shown greyed out with this reason and can't be run. */
  disabledReason?: string | null;
  action?: QuickAction;
  /** The route or datasource id, for kind "route" / "datasource". */
  entityId?: string;
  /** For kind "help": the scope the row switches to, or null for the shortcut list. */
  helpScope?: Scope | null;
  score: number;
}

/** What a search is narrowed to, by its first character: ">" actions, "#" routes, "@" datasources. */
export type Scope = 'actions' | 'routes' | 'datasources';

export const SCOPE_PREFIXES: Record<string, Scope> = { '>': 'actions', '#': 'routes', '@': 'datasources' };

export const SCOPE_LABELS: Record<Scope, string> = { actions: 'Actions', routes: 'Routes', datasources: 'Datasources' };

/** A route or datasource page the user opened lately. */
export interface RecentEntry {
  kind: 'route' | 'datasource';
  id: string;
}

export type GroupId = 'page' | 'recent' | 'routes' | 'datasources' | 'actions' | 'navigation' | 'help';

export interface ResultGroup {
  id: GroupId;
  label: string;
  items: ResultItem[];
  /** Matches left out by the per-group limit. */
  more: number;
}

export const GROUP_LABELS: Record<GroupId, string> = {
  page: 'This page',
  recent: 'Recent',
  routes: 'Routes',
  datasources: 'Datasources',
  actions: 'Actions',
  navigation: 'Go to',
  help: 'Narrow the search'
};

/** Group order between groups whose best matches score the same. */
const GROUP_ORDER: GroupId[] = ['page', 'routes', 'datasources', 'actions', 'navigation'];
/** Group order when nothing is typed: this page's actions, recent pages, then where to go, then the rest. */
const EMPTY_ORDER: GroupId[] = ['page', 'recent', 'routes', 'datasources', 'navigation', 'actions'];

/** Rows per group when the search is narrowed to one kind (the list scrolls). */
export const SCOPED_LIMIT = 50;

export interface SearchInput {
  query: string;
  actions: readonly QuickAction[];
  routes: readonly RouteEntry[];
  datasources: readonly DatasourceEntry[];
  /** Narrows the search to one kind; with it, an empty query lists everything of that kind. */
  scope?: Scope | null;
  /** Recently opened route and datasource pages, newest first; listed when nothing is typed. */
  recents?: readonly RecentEntry[];
  /**
   * True once the route and datasource lists have loaded: recent entries that are no longer in them (deleted) are
   * then left out. Before that, recents are listed without their status.
   */
  entitiesLoaded?: boolean;
  /** Most rows per group (default 5). */
  limit?: number;
}

/**
 * The bar's results, grouped. With nothing typed: the page's actions, recent pages, the places to go and the global
 * actions. With a query: every match, each group sorted best first and the groups ordered by their best match.
 * With a scope, only that kind, up to SCOPED_LIMIT rows.
 */
export function search(input: SearchInput): ResultGroup[] {
  const scope = input.scope ?? null;
  const limit = scope ? SCOPED_LIMIT : input.limit ?? 5;
  const query = input.query.trim();
  // A scope lists its whole kind even before anything is typed.
  const listAll = !!query || !!scope;
  const buckets = new Map<GroupId, ResultItem[]>();
  const add = (group: GroupId, item: ResultItem | null) => {
    if (item) {
      buckets.set(group, [...(buckets.get(group) ?? []), item]);
    }
  };

  if (!scope || scope === 'actions') {
    for (const action of input.actions) {
      if ((action.searchOnly && !listAll) || (action.visible && !action.visible())) {
        continue;
      }
      add(action.group, actionItem(action, query));
    }
  }
  if (listAll && (!scope || scope === 'routes')) {
    for (const route of input.routes) {
      add('routes', routeItem(route, query));
    }
  }
  if (listAll && (!scope || scope === 'datasources')) {
    for (const ds of input.datasources) {
      add('datasources', datasourceItem(ds, query));
    }
  }
  if (!listAll) {
    for (const recent of input.recents ?? []) {
      add('recent', recentItem(recent, input));
    }
  }

  const groups: ResultGroup[] = [];
  for (const id of query ? GROUP_ORDER : EMPTY_ORDER) {
    const items = buckets.get(id);
    if (!items?.length) {
      continue;
    }
    const sorted = query ? [...items].sort((a, b) => b.score - a.score) : items;
    // With nothing typed, the page's own actions are listed in full: they are what the bar is usually opened for.
    const max = !query && id === 'page' ? sorted.length : limit;
    groups.push({ id, label: GROUP_LABELS[id], items: sorted.slice(0, max), more: Math.max(0, sorted.length - max) });
  }
  if (query) {
    // Stable sort: equal best scores keep GROUP_ORDER.
    groups.sort((a, b) => b.items[0].score - a.items[0].score);
  }
  return groups;
}

/**
 * Matches the label (highlighted), else a keyword (not highlighted, ranked a little lower). Keywords must contain the
 * query's words as written: scattered letters in a URL or description would match almost anything.
 */
function best(query: string, label: string, keywords: readonly string[] = []): { score: number; ranges: Range[] } | null {
  const onLabel = match(query, label);
  if (onLabel) {
    return onLabel;
  }
  let found: { score: number; ranges: Range[] } | null = null;
  for (const k of keywords) {
    const m = match(query, k, false);
    if (m && (!found || m.score - 50 > found.score)) {
      found = { score: m.score - 50, ranges: [] };
    }
  }
  return found;
}

function actionItem(action: QuickAction, query: string): ResultItem | null {
  const m = best(query, action.label, action.keywords);
  if (!m) {
    return null;
  }
  return {
    key: 'action:' + action.id, kind: 'action', label: action.label, ranges: m.ranges, icon: action.icon,
    hint: action.hint, disabledReason: action.disabledReason?.() ?? null, action, score: m.score
  };
}

function routeItem(route: RouteEntry, query: string): ResultItem | null {
  const m = best(query, route.routeId, [route.description, route.dataSourceId]);
  if (!m) {
    return null;
  }
  return {
    key: 'route:' + route.routeId, kind: 'route', label: route.routeId, ranges: m.ranges,
    detail: route.description || undefined, icon: 'dashboard', hint: route.status,
    tone: route.status === 'Started' ? 'ok' : 'off', entityId: route.routeId, score: m.score
  };
}

/** A recent page as a row, with its current status when the lists have loaded; null when it no longer exists. */
function recentItem(recent: RecentEntry, input: SearchInput): ResultItem | null {
  if (recent.kind === 'route') {
    const route = input.routes.find(r => r.routeId === recent.id);
    if (!route && input.entitiesLoaded) {
      return null;
    }
    const item = routeItem(route ?? { routeId: recent.id, description: '', status: '', dataSourceId: '' }, '')!;
    return { ...item, key: 'recent:' + item.key, icon: 'history', hint: route ? item.hint : 'Route', tone: route ? item.tone : undefined };
  }
  const ds = input.datasources.find(d => d.dataSourceId === recent.id);
  if (!ds && input.entitiesLoaded) {
    return null;
  }
  const item = datasourceItem(ds ?? { dataSourceId: recent.id, url: '' }, '')!;
  return { ...item, key: 'recent:' + item.key, icon: 'history', detail: ds?.url || undefined,
    hint: ds ? item.hint ?? 'Datasource' : 'Datasource' };
}

function datasourceItem(ds: DatasourceEntry, query: string): ResultItem | null {
  const m = best(query, ds.dataSourceId, [ds.url]);
  if (!m) {
    return null;
  }
  return {
    key: 'datasource:' + ds.dataSourceId, kind: 'datasource', label: ds.dataSourceId, ranges: m.ranges,
    detail: ds.url, icon: 'dns', hint: statusHint(ds.status), tone: statusTone(ds.status),
    entityId: ds.dataSourceId, score: m.score
  };
}

function statusHint(status?: string): string | undefined {
  switch (status) {
    case 'UP': return 'Up';
    case 'DOWN': return 'Down';
    case 'DISABLED': return 'Disabled';
    case 'CHECKING': return 'Checking';
    default: return undefined;
  }
}

function statusTone(status?: string): 'ok' | 'warn' | 'off' | undefined {
  switch (status) {
    case 'UP': return 'ok';
    case 'DOWN': return 'warn';
    case 'DISABLED': return 'off';
    default: return undefined;
  }
}

/** Every row in display order, for keyboard movement. */
export function flatten(groups: ResultGroup[]): ResultItem[] {
  return groups.flatMap(g => g.items);
}

import { Range, match } from './match';
import { DatasourceEntry, QuickAction, RouteEntry } from './quick-action';

/** One row of the results. */
export interface ResultItem {
  /** Unique in the results; also the option's DOM id suffix. */
  key: string;
  kind: 'action' | 'route' | 'datasource';
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
  score: number;
}

export type GroupId = 'page' | 'routes' | 'datasources' | 'actions' | 'navigation';

export interface ResultGroup {
  id: GroupId;
  label: string;
  items: ResultItem[];
  /** Matches left out by the per-group limit. */
  more: number;
}

export const GROUP_LABELS: Record<GroupId, string> = {
  page: 'This page',
  routes: 'Routes',
  datasources: 'Datasources',
  actions: 'Actions',
  navigation: 'Go to'
};

/** Group order when nothing is typed, and between groups whose best matches score the same. */
const GROUP_ORDER: GroupId[] = ['page', 'routes', 'datasources', 'actions', 'navigation'];

export interface SearchInput {
  query: string;
  actions: readonly QuickAction[];
  routes: readonly RouteEntry[];
  datasources: readonly DatasourceEntry[];
  /** Most rows per group (default 5). */
  limit?: number;
}

/**
 * The bar's results, grouped. With nothing typed: the page's actions, the global actions and the places to go.
 * With a query: every match, each group sorted best first and the groups ordered by their best match.
 */
export function search(input: SearchInput): ResultGroup[] {
  const limit = input.limit ?? 5;
  const query = input.query.trim();
  const buckets = new Map<GroupId, ResultItem[]>();
  const add = (group: GroupId, item: ResultItem | null) => {
    if (item) {
      buckets.set(group, [...(buckets.get(group) ?? []), item]);
    }
  };

  for (const action of input.actions) {
    if ((action.searchOnly && !query) || (action.visible && !action.visible())) {
      continue;
    }
    add(action.group, actionItem(action, query));
  }
  if (query) {
    for (const route of input.routes) {
      add('routes', routeItem(route, query));
    }
    for (const ds of input.datasources) {
      add('datasources', datasourceItem(ds, query));
    }
  }

  const groups: ResultGroup[] = [];
  for (const id of GROUP_ORDER) {
    const items = buckets.get(id);
    if (!items?.length) {
      continue;
    }
    const sorted = query ? [...items].sort((a, b) => b.score - a.score) : items;
    groups.push({ id, label: GROUP_LABELS[id], items: sorted.slice(0, limit), more: Math.max(0, sorted.length - limit) });
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

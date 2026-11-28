import { Routes, RouteStopReason } from '../../core/models/routes-interfaces/routes';
import { RouteMetric } from '../../core/models/metric-data';
import { DatasourceHealth, DatasourceStatus } from '../../core/models/datasource-health';
import { effectiveAccess, methodsOf } from '../../shared/query-endpoint';
import { buildTrends } from '../metrics/metrics-derive';

/**
 * Pure functions that turn routes, live metrics and datasource health into what the routes pages draw. No Angular:
 * everything here is unit tested on plain data.
 */

/**
 * - running: started, and its datasource can serve it (up, being checked, or not checked yet)
 * - blocked: started, but its datasource is down or disabled, so calls fail
 * - stopped: stopped (by someone, by the health check, or for lack of a template)
 */
export type RouteState = 'running' | 'blocked' | 'stopped';

/** good · bad (down) · warn (disabled, or waiting to be restarted) · idle (stopped) */
export type RouteTone = 'good' | 'bad' | 'warn' | 'idle';

/** Minutes of history the sparkline shows, and the minutes the call rate averages over. */
export const SPARK_MINUTES = 30;
export const RATE_MINUTES = 5;

/** A route's traffic over the stored history (the last 30 minutes, one snapshot a minute). */
export interface RouteTraffic {
  /** Calls per minute, averaged over the last {@link RATE_MINUTES} minutes; null without history. */
  perMinute: number | null;
  /** Failed share of the calls in the window; null without calls. */
  errorRate: number | null;
  /** Mean processing time of the calls in the window (ms); null without calls. */
  meanMs: number | null;
  /** Calls and failed calls in the window. */
  calls: number;
  failures: number;
  /** Calls per minute, oldest first, one per minute of the window; null where the route has no snapshot. */
  spark: (number | null)[];
  /** Failed calls per minute, aligned to `spark`. */
  sparkFailures: (number | null)[];
}

export const NO_TRAFFIC: RouteTraffic = {
  perMinute: null, errorRate: null, meanMs: null, calls: 0, failures: 0, spark: [], sparkFailures: [],
};

/** One route as the list shows it. */
export interface RouteRow {
  id: string;
  description: string;
  methods: string[];
  datasourceId: string;
  /** The datasource's health: live when known, otherwise as the route list reported it. */
  datasourceStatus: DatasourceStatus;
  access: 'open' | 'authenticated';
  state: RouteState;
  tone: RouteTone;
  /** Started in Camel (the live metrics state when known, otherwise the stored status). */
  started: boolean;
  stopReason: RouteStopReason | null;
  /** When it was stopped (ms since epoch), if known. */
  stoppedAt: number | null;
  /** Stopped by the health check, and its datasource is up again: waiting for someone to start it. */
  awaitingRestart: boolean;
  /** Why it can't be started now (its datasource is down or disabled); null when it can. */
  startBlocked: string | null;
  /** What to say about its state instead of the description (blocked, stopped by the health check, no template). */
  note: string | null;
  traffic: RouteTraffic;
  route: Routes;
}

export interface RowInputs {
  routes: Routes[];
  /** Live datasource health (null until it arrives). */
  datasources: DatasourceHealth[] | null;
  /** Live route metrics (null until they arrive): fresher started/stopped state. */
  metrics: RouteMetric[] | null;
  /** Stored metric snapshots (null until they arrive). */
  history: RouteMetric[] | null;
  serverDefaultAccess: 'open' | 'authenticated';
}

const KNOWN_STATUSES: DatasourceStatus[] = ['UP', 'DOWN', 'UNKNOWN', 'CHECKING', 'DISABLED'];

function asStatus(status: string | null | undefined): DatasourceStatus {
  return KNOWN_STATUSES.includes(status as DatasourceStatus) ? status as DatasourceStatus : 'UNKNOWN';
}

/** Why a route on this datasource can't start (the server refuses with 409), or null if it can. */
export function startBlockedReason(datasourceId: string, status: DatasourceStatus): string | null {
  switch (status) {
    case 'DOWN': return `Can't start: datasource ${datasourceId} is down`;
    case 'DISABLED': return `Can't start: datasource ${datasourceId} is disabled`;
    default: return null;
  }
}

/** The state of a route from whether it runs and its datasource's health. */
export function routeState(started: boolean, datasourceStatus: DatasourceStatus): RouteState {
  if (!started) {
    return 'stopped';
  }
  return datasourceStatus === 'DOWN' || datasourceStatus === 'DISABLED' ? 'blocked' : 'running';
}

function toMillis(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const ms = typeof value === 'number' ? value : Date.parse(value);
  return Number.isFinite(ms) ? ms : null;
}

/** Each route's traffic from the stored history, by route id. */
export function trafficByRoute(history: RouteMetric[] | null): Map<string, RouteTraffic> {
  const result = new Map<string, RouteTraffic>();
  if (!history?.length) {
    return result;
  }
  const trends = buildTrends(history);
  const from = Math.max(0, trends.times.length - SPARK_MINUTES);
  const rateFrom = Math.max(0, trends.times.length - RATE_MINUTES);
  for (const series of trends.series) {
    const spark = series.exchanges.slice(from);
    const sparkFailures = series.failures.slice(from);
    const processing = series.processingMs.slice(from);
    const sum = (values: (number | null)[]) => values.reduce<number>((total, v) => total + (v ?? 0), 0);
    const calls = sum(spark);
    const failures = sum(sparkFailures);
    const recent = series.exchanges.slice(rateFrom).filter((v): v is number => v !== null);
    result.set(series.route, {
      perMinute: recent.length ? sum(recent) / recent.length : null,
      errorRate: calls > 0 ? Math.min(1, failures / calls) : null,
      meanMs: calls > 0 ? sum(processing) / calls : null,
      calls: Math.round(calls),
      failures: Math.round(failures),
      spark,
      sparkFailures,
    });
  }
  return result;
}

/** The list's rows, in the routes' order. */
export function toRows(inputs: RowInputs): RouteRow[] {
  const health = new Map((inputs.datasources ?? []).map(d => [d.dataSourceId, d.status]));
  const live = new Map((inputs.metrics ?? []).map(m => [m.route, m.state]));
  const traffic = trafficByRoute(inputs.history);
  return inputs.routes.map(route => {
    const datasourceId = route.datasources?.dataSourceId ?? '';
    const datasourceStatus = asStatus(health.get(datasourceId) ?? route.datasources?.status);
    const liveState = live.get(route.routeId);
    const started = liveState ? liveState === 'Started' : route.status === 'Started';
    const state = routeState(started, datasourceStatus);
    // The stored reason belongs to the stored status: a route the live state shows started has none.
    const stopReason = started ? null : route.stopReason ?? null;
    const awaitingRestart = stopReason === 'HEALTH_CHECK' && datasourceStatus === 'UP';
    return {
      id: route.routeId,
      description: route.description ?? '',
      methods: methodsOf(route.routeParams),
      datasourceId,
      datasourceStatus,
      access: effectiveAccess(route.accessPolicy, inputs.serverDefaultAccess),
      state,
      tone: toneOf(state, datasourceStatus, awaitingRestart),
      started,
      stopReason,
      stoppedAt: started ? null : toMillis(route.stoppedAt),
      awaitingRestart,
      startBlocked: started ? null : startBlockedReason(datasourceId, datasourceStatus),
      note: noteOf(state, stopReason, datasourceId, datasourceStatus),
      traffic: traffic.get(route.routeId) ?? NO_TRAFFIC,
      route,
    };
  });
}

function toneOf(state: RouteState, datasourceStatus: DatasourceStatus, awaitingRestart: boolean): RouteTone {
  switch (state) {
    case 'running': return 'good';
    case 'blocked': return datasourceStatus === 'DOWN' ? 'bad' : 'warn';
    default: return awaitingRestart ? 'warn' : 'idle';
  }
}

function noteOf(state: RouteState, reason: RouteStopReason | null, datasourceId: string,
                datasourceStatus: DatasourceStatus): string | null {
  if (state === 'blocked') {
    return datasourceStatus === 'DOWN'
      ? `Blocked: datasource ${datasourceId} is down, so calls fail`
      : `Blocked: datasource ${datasourceId} is disabled, so calls fail`;
  }
  if (state === 'stopped') {
    if (reason === 'HEALTH_CHECK') {
      return datasourceStatus === 'UP'
        ? `Stopped by the health check; ${datasourceId} is back up`
        : `Stopped by the health check while ${datasourceId} is down`;
    }
    if (reason === 'NO_TEMPLATE') {
      return 'Stopped: it has no template yet';
    }
  }
  return null;
}

// --- Filtering, sorting, grouping ----------------------------------------------------------------------------------

/** The status filter; `awaiting-restart` is reached from the recovery banner, not the toggles. */
export type StateFilter = 'all' | RouteState | 'awaiting-restart';
export type AccessFilter = 'all' | 'open' | 'authenticated';
export type RouteSort = 'name' | 'status' | 'busiest' | 'slowest' | 'errors';

export interface RouteFilter {
  term: string;
  state: StateFilter;
  /** A datasource id, or '' for all. */
  datasource: string;
  access: AccessFilter;
}

export const NO_FILTER: RouteFilter = { term: '', state: 'all', datasource: '', access: 'all' };

export function matchesTerm(row: RouteRow, term: string): boolean {
  const t = term.trim().toLowerCase();
  return !t || row.id.toLowerCase().includes(t) || row.description.toLowerCase().includes(t)
    || row.datasourceId.toLowerCase().includes(t);
}

/** Rows passing every filter except, when `ignoreState`, the status one (the toggles' counts use that). */
export function filterRows(rows: RouteRow[], filter: RouteFilter, ignoreState = false): RouteRow[] {
  return rows.filter(row => matchesTerm(row, filter.term)
    && (!filter.datasource || row.datasourceId === filter.datasource)
    && (filter.access === 'all' || row.access === filter.access)
    && (ignoreState || filter.state === 'all'
      || (filter.state === 'awaiting-restart' ? row.awaitingRestart : row.state === filter.state)));
}

const STATE_ORDER: Record<RouteState, number> = { blocked: 0, running: 2, stopped: 3 };

/** Attention first: blocked, then stopped routes waiting to be restarted, then running, then stopped. */
function statusRank(row: RouteRow): number {
  return row.awaitingRestart ? 1 : STATE_ORDER[row.state];
}

/** Sorted copy. Ties (and routes without the measure) fall back to the route id. */
export function sortRows(rows: RouteRow[], sort: RouteSort): RouteRow[] {
  const byId = (a: RouteRow, b: RouteRow) => a.id.localeCompare(b.id);
  // Highest first; routes without a value last.
  const desc = (value: (row: RouteRow) => number | null) => (a: RouteRow, b: RouteRow) => {
    const va = value(a);
    const vb = value(b);
    if (va === vb) {
      return byId(a, b);
    }
    if (va === null) {
      return 1;
    }
    if (vb === null) {
      return -1;
    }
    return vb - va || byId(a, b);
  };
  const compare = {
    name: byId,
    status: (a: RouteRow, b: RouteRow) => statusRank(a) - statusRank(b) || byId(a, b),
    busiest: desc(r => r.traffic.perMinute),
    slowest: desc(r => r.traffic.meanMs),
    errors: desc(r => r.traffic.calls ? r.traffic.failures : null),
  }[sort];
  return [...rows].sort(compare);
}

/** How many routes are in each state, and how many wait to be restarted. */
export interface StateCounts {
  total: number;
  running: number;
  blocked: number;
  stopped: number;
  awaitingRestart: number;
}

export function countStates(rows: RouteRow[]): StateCounts {
  const counts: StateCounts = { total: rows.length, running: 0, blocked: 0, stopped: 0, awaitingRestart: 0 };
  for (const row of rows) {
    counts[row.state]++;
    if (row.awaitingRestart) {
      counts.awaitingRestart++;
    }
  }
  return counts;
}

const plural = (n: number, one: string, many = one + 's') => `${n} ${n === 1 ? one : many}`;

/** The summary sentence: "42 routes · 37 running · 2 blocked by their datasource · 3 stopped". */
export function headline(counts: StateCounts): string {
  if (counts.total === 0) {
    return 'No routes yet';
  }
  const parts = [plural(counts.total, 'route')];
  if (counts.running === counts.total) {
    return `${parts[0]}, all running`;
  }
  if (counts.running) {
    parts.push(`${counts.running} running`);
  }
  if (counts.blocked) {
    parts.push(`${counts.blocked} blocked by ${counts.blocked === 1 ? 'its datasource' : 'their datasources'}`);
  }
  if (counts.stopped) {
    parts.push(`${counts.stopped} stopped`);
  }
  return parts.join(' · ');
}

/** Routes of one datasource, for the grouped view. */
export interface RouteGroup {
  datasourceId: string;
  status: DatasourceStatus;
  rows: RouteRow[];
  counts: StateCounts;
}

const GROUP_ORDER: Record<DatasourceStatus, number> = { DOWN: 0, DISABLED: 1, CHECKING: 2, UNKNOWN: 3, UP: 4 };

/** Rows grouped by datasource (each group keeps the rows' order); troubled datasources first, then by id. */
export function groupByDatasource(rows: RouteRow[]): RouteGroup[] {
  const groups = new Map<string, RouteRow[]>();
  for (const row of rows) {
    let list = groups.get(row.datasourceId);
    if (!list) {
      groups.set(row.datasourceId, list = []);
    }
    list.push(row);
  }
  return [...groups.entries()]
    .map(([datasourceId, list]) => ({
      datasourceId, status: list[0].datasourceStatus, rows: list, counts: countStates(list),
    }))
    .sort((a, b) => GROUP_ORDER[a.status] - GROUP_ORDER[b.status] || a.datasourceId.localeCompare(b.datasourceId));
}

/** Routes the health check stopped whose datasource is back up, by datasource (for the recovery banner). */
export interface Recovery {
  datasourceId: string;
  routeIds: string[];
  /** The earliest stop (ms since epoch), if known. */
  since: number | null;
}

export function recoveries(rows: RouteRow[]): Recovery[] {
  const byDatasource = new Map<string, Recovery>();
  for (const row of rows.filter(r => r.awaitingRestart)) {
    let recovery = byDatasource.get(row.datasourceId);
    if (!recovery) {
      byDatasource.set(row.datasourceId, recovery = { datasourceId: row.datasourceId, routeIds: [], since: null });
    }
    recovery.routeIds.push(row.id);
    if (row.stoppedAt !== null && (recovery.since === null || row.stoppedAt < recovery.since)) {
      recovery.since = row.stoppedAt;
    }
  }
  return [...byDatasource.values()].sort((a, b) => a.datasourceId.localeCompare(b.datasourceId));
}

// --- Formatting -----------------------------------------------------------------------------------------------------

/** "12/min", "0.4/min", "<0.1/min", "—". */
export function formatRate(perMinute: number | null): string {
  if (perMinute === null) {
    return '—';
  }
  if (perMinute === 0) {
    return '0/min';
  }
  if (perMinute < 0.1) {
    return '<0.1/min';
  }
  return (perMinute < 10 ? perMinute.toFixed(1).replace(/\.0$/, '') : Math.round(perMinute).toString()) + '/min';
}

/** "0%", "0.4%", "12%", "—". */
export function formatErrorRate(rate: number | null): string {
  if (rate === null) {
    return '—';
  }
  const percent = rate * 100;
  if (percent === 0) {
    return '0%';
  }
  return (percent < 10 ? percent.toFixed(1).replace(/\.0$/, '') : Math.round(percent).toString()) + '%';
}

/** "85 ms", "1.2 s", "—". */
export function formatMs(ms: number | null): string {
  if (ms === null) {
    return '—';
  }
  return ms < 1000 ? `${Math.round(ms)} ms` : `${(ms / 1000).toFixed(1).replace(/\.0$/, '')} s`;
}

import { RouteMetric } from '../../core/models/metric-data';

/**
 * Pure functions that turn the metrics API's route snapshots into what the page draws. No Angular, no Chart.js:
 * everything here is unit tested on plain data.
 */

/**
 * The summary tiles. A value is null when it can't be computed yet: averages and rates need at least one exchange,
 * and there is no busiest route until some route has traffic.
 */
export interface MetricsSummary {
  endpoints: number;
  totalExchanges: number;
  avgProcessingMs: number | null;
  busiestRoute: string | null;
  successRate: number | null;
}

export function summarize(routes: RouteMetric[]): MetricsSummary {
  let totalExchanges = 0;
  let completed = 0;
  let processing = 0;
  let busiest: RouteMetric | null = null;
  for (const route of routes) {
    totalExchanges += route.exchangesTotal;
    completed += route.exchangesCompleted;
    processing += route.totalProcessingTime;
    if (busiest === null || route.exchangesTotal > busiest.exchangesTotal) {
      busiest = route;
    }
  }
  const hasExchanges = totalExchanges > 0;
  return {
    endpoints: routes.length,
    totalExchanges,
    avgProcessingMs: hasExchanges ? processing / totalExchanges : null,
    busiestRoute: busiest && busiest.exchangesTotal > 0 ? busiest.route : null,
    successRate: hasExchanges ? Number(((completed / totalExchanges) * 100).toFixed(2)) : null,
  };
}

/** Routes in the current scope: all of them, or only those picked in the route filter. */
export function inScope<T extends { route: string }>(items: T[], selected: string[]): T[] {
  return selected.length === 0 ? items : items.filter(item => selected.includes(item.route));
}

// --- Exchanges by route ---------------------------------------------------------------------------------------

export interface ExchangeBar {
  route: string;
  completed: number;
  failed: number;
  total: number;
}

/** Busiest first. */
export function exchangeBars(routes: RouteMetric[], limit: number): { bars: ExchangeBar[]; hidden: number } {
  const bars = routes
    .map(r => ({ route: r.route, completed: r.exchangesCompleted, failed: r.exchangesFailed, total: r.exchangesTotal }))
    .sort((a, b) => b.total - a.total || a.route.localeCompare(b.route));
  return { bars: bars.slice(0, limit), hidden: Math.max(0, bars.length - limit) };
}

// --- Latency by route -----------------------------------------------------------------------------------------

export interface LatencyRange {
  route: string;
  min: number;
  mean: number;
  max: number;
}

/**
 * Slowest average first. Routes that have not handled an exchange have no latency (Camel reports their mean as -1)
 * and are counted in `idle` instead.
 */
export function latencyRanges(routes: RouteMetric[], limit: number): { ranges: LatencyRange[]; idle: number; hidden: number } {
  const measured = routes.filter(r => r.exchangesTotal > 0 && r.meanProcessingTime >= 0);
  const ranges = measured
    .map(r => ({ route: r.route, min: r.minProcessingTime, mean: r.meanProcessingTime, max: r.maxProcessingTime }))
    .sort((a, b) => b.mean - a.mean || a.route.localeCompare(b.route));
  return {
    ranges: ranges.slice(0, limit),
    idle: routes.length - measured.length,
    hidden: Math.max(0, ranges.length - limit),
  };
}

// --- Trends: per-minute rates from the 1-minute snapshots -----------------------------------------------------

export type TrendMetric = 'exchanges' | 'failures' | 'avgTime';

/** One route's per-minute values, aligned to {@link TrendData.times}; null where there is no value. */
export interface TrendSeries {
  route: string;
  /** Exchanges per minute. */
  exchanges: (number | null)[];
  /** Failed exchanges per minute. */
  failures: (number | null)[];
  /** Processing time over the minute (ms): the inputs, so averages can be folded correctly. */
  processingMs: (number | null)[];
}

export interface TrendData {
  /** Minute buckets, oldest first, as `YYYY-MM-DDTHH:MM` (the server's local time). */
  times: string[];
  series: TrendSeries[];
}

const minuteOf = (timeStamp: string) => timeStamp.substring(0, 16);

/**
 * The snapshots hold running totals since each route started; the difference between consecutive snapshots is what
 * happened in that minute. A total that went down means the route restarted (its counters reset), so the newer
 * snapshot's own totals are that interval's values. The first snapshot of a route has nothing to compare with.
 */
export function buildTrends(history: RouteMetric[]): TrendData {
  const byRoute = new Map<string, RouteMetric[]>();
  for (const snapshot of history) {
    if (!snapshot.timeStamp) {
      continue;
    }
    let list = byRoute.get(snapshot.route);
    if (!list) {
      byRoute.set(snapshot.route, list = []);
    }
    list.push(snapshot);
  }

  const rates = new Map<string, Map<string, { exchanges: number; failures: number; processingMs: number }>>();
  const times = new Set<string>();
  for (const [route, snapshots] of byRoute) {
    snapshots.sort((a, b) => a.timeStamp.localeCompare(b.timeStamp));
    const perMinute = new Map<string, { exchanges: number; failures: number; processingMs: number }>();
    for (let i = 1; i < snapshots.length; i++) {
      const prev = snapshots[i - 1];
      const cur = snapshots[i];
      const minutes = (Date.parse(cur.timeStamp) - Date.parse(prev.timeStamp)) / 60000;
      if (!(minutes > 0)) {
        continue;
      }
      const reset = cur.exchangesTotal < prev.exchangesTotal;
      const delta = (now: number, before: number) => Math.max(0, reset ? now : now - before);
      const bucket = minuteOf(cur.timeStamp);
      perMinute.set(bucket, {
        exchanges: delta(cur.exchangesTotal, prev.exchangesTotal) / minutes,
        failures: delta(cur.exchangesFailed, prev.exchangesFailed) / minutes,
        processingMs: delta(cur.totalProcessingTime, prev.totalProcessingTime) / minutes,
      });
      times.add(bucket);
    }
    rates.set(route, perMinute);
  }

  const sortedTimes = [...times].sort();
  const series = [...rates.entries()].map(([route, perMinute]) => {
    const at = (key: 'exchanges' | 'failures' | 'processingMs') =>
      sortedTimes.map(t => perMinute.get(t)?.[key] ?? null);
    return { route, exchanges: at('exchanges'), failures: at('failures'), processingMs: at('processingMs') };
  });
  return { times: sortedTimes, series };
}

/** A line to draw: a route, or the remaining routes folded together. */
export interface TrendLine {
  label: string;
  values: (number | null)[];
  /** The folded "other routes" line. */
  other: boolean;
}

/**
 * The lines for one metric: the busiest `maxRoutes` routes (by exchanges over the window), plus one line folding the
 * rest when there are more. Average time is total processing time over exchanges, also for the folded line; a minute
 * without exchanges has no average (null, drawn as a gap).
 */
export function trendLines(data: TrendData, metric: TrendMetric, maxRoutes: number): TrendLine[] {
  const volume = (s: TrendSeries) => s.exchanges.reduce<number>((sum, v) => sum + (v ?? 0), 0);
  const ranked = [...data.series].sort((a, b) => volume(b) - volume(a) || a.route.localeCompare(b.route));
  const shown = ranked.slice(0, maxRoutes);
  const rest = ranked.slice(maxRoutes);

  const valuesOf = (group: TrendSeries[]): (number | null)[] => data.times.map((_, i) => {
    const present = group.filter(s => s.exchanges[i] !== null);
    if (present.length === 0) {
      return null;
    }
    const sum = (key: 'exchanges' | 'failures' | 'processingMs') =>
      present.reduce((total, s) => total + (s[key][i] ?? 0), 0);
    if (metric === 'avgTime') {
      const exchanges = sum('exchanges');
      return exchanges > 0 ? sum('processingMs') / exchanges : null;
    }
    return sum(metric);
  });

  const lines: TrendLine[] = shown.map(s => ({ label: s.route, values: valuesOf([s]), other: false }));
  if (rest.length > 0) {
    lines.push({ label: `${rest.length} other routes`, values: valuesOf(rest), other: true });
  }
  return lines;
}

/** `HH:MM` from a minute bucket. */
export const clockLabel = (bucket: string) => bucket.substring(11, 16);

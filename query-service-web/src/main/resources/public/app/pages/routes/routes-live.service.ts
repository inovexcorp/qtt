import { computed, DestroyRef, effect, inject, Injectable, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Routes } from '../../core/models/routes-interfaces/routes';
import { RouteMetric } from '../../core/models/metric-data';
import { DatasourceLiveService } from '../../core/services/datasource-live.service';
import { LiveStatus, MetricsLiveService } from '../../core/services/metrics-live.service';
import { SettingsService } from '../../core/services/settings.service';
import { RoutesService } from './routes.service';
import { RouteRow, toRows } from './routes-derive';

/** How long to wait for more changes before reloading the route list (a bulk action changes many routes at once). */
export const RELOAD_DEBOUNCE_MS = 400;

/** "id:state" of every route Camel runs, sorted: changes when a route is added, removed, started or stopped. */
export function liveSignature(metrics: RouteMetric[]): string {
  return metrics.map(m => `${m.route}:${m.state}`).sort().join('|');
}

/**
 * Whether the live metrics show a change the route list doesn't: a route started or stopped elsewhere, a new
 * route, a deleted one. Only a *change* of the live state counts (`previous` is the last signature seen): the
 * metrics also list routes that have no stored row (temporary test routes), and stored routes Camel couldn't load
 * are missing from them, so a standing difference is normal and must not reload the list on every event.
 */
export function listIsBehind(list: Routes[], metrics: RouteMetric[], previous: string | null): boolean {
  if (previous === null || liveSignature(metrics) === previous) {
    return false;
  }
  const stored = new Map(list.map(r => [r.routeId, r.status]));
  const before = new Map(previous ? previous.split('|').map(entry => {
    const at = entry.lastIndexOf(':');
    return [entry.substring(0, at), entry.substring(at + 1)] as const;
  }) : []);
  const now = new Map(metrics.map(m => [m.route, m.state]));
  for (const [id, state] of now) {
    if (before.get(id) === state) {
      continue; // unchanged since last time: any difference with the list is a standing one
    }
    if (!stored.has(id) || stored.get(id) !== state) {
      return true; // new, or started/stopped elsewhere
    }
  }
  for (const id of before.keys()) {
    if (!now.has(id) && stored.has(id)) {
      return true; // removed (deleted, or being replaced by a save)
    }
  }
  return false;
}

/**
 * The routes pages' data: the stored routes (REST, as there is no routes topic), live route metrics and datasource
 * health (one /api/events stream through the page's LiveHub), and the rows the list draws from all three.
 *
 * The route list is reloaded when the live metrics show a route was added, removed, started or stopped somewhere
 * else (see {@link listIsBehind}), after this page's own actions, and on refresh.
 *
 * Provided by the routes page with LiveHub, DatasourceLiveService and MetricsLiveService, so the list and the route
 * pages under it share one connection.
 */
@Injectable()
export class RoutesLiveService {
  private readonly api = inject(RoutesService);
  private readonly settings = inject(SettingsService);
  readonly datasources = inject(DatasourceLiveService);
  readonly metrics = inject(MetricsLiveService);

  /** The stored routes; null until the first load. */
  readonly routes = signal<Routes[] | null>(null);
  /** The first load failed (it retries on the next change or refresh). */
  readonly failed = signal(false);
  /** QUERY_BASE_URL, for endpoint URLs. */
  readonly baseUrl = signal<string>('');
  /** QUERY_AUTH_DEFAULT: what routes left at "default" require. */
  readonly serverDefaultAccess = signal<'open' | 'authenticated'>('open');

  readonly rows = computed<RouteRow[] | null>(() => {
    const routes = this.routes();
    return routes === null ? null : toRows({
      routes,
      datasources: this.datasources.datasources(),
      metrics: this.metrics.routes(),
      history: this.metrics.history(),
      serverDefaultAccess: this.serverDefaultAccess(),
    });
  });

  /** One status for the page: stale or connecting if either feed is (they share one stream). */
  readonly status = computed<LiveStatus>(() => {
    const statuses = [this.metrics.status(), this.datasources.status()];
    for (const status of ['paused', 'connecting', 'stale'] as LiveStatus[]) {
      if (statuses.includes(status)) {
        return status;
      }
    }
    return 'live';
  });

  readonly updatedAt = computed(() => Math.max(this.metrics.updatedAt() ?? 0, this.datasources.updatedAt() ?? 0) || null);

  private signature: string | null = null;
  private reloadTimer: ReturnType<typeof setTimeout> | undefined;
  private loading: Promise<void> | null = null;
  private loadAgain = false;

  constructor() {
    void this.load();
    this.api.getBaseUrl().subscribe({ next: r => this.baseUrl.set(r.baseUrl ?? ''), error: () => { /* "/query" */ } });
    this.settings.getSsoInfo().subscribe({
      next: info => this.serverDefaultAccess.set(info.queryAuthDefault === 'authenticated' ? 'authenticated' : 'open'),
      error: () => { /* keep "open"; informational only */ },
    });

    effect(() => {
      const metrics = this.metrics.routes();
      const list = this.routes();
      if (metrics === null || list === null) {
        return;
      }
      untracked(() => {
        if (listIsBehind(list, metrics, this.signature)) {
          this.reloadSoon();
        }
        this.signature = liveSignature(metrics);
      });
    });
    inject(DestroyRef).onDestroy(() => clearTimeout(this.reloadTimer));
  }

  /** One route as stored, if loaded. */
  find(routeId: string): Routes | undefined {
    return this.routes()?.find(r => r.routeId === routeId);
  }

  /** One route's row, if loaded. */
  row(routeId: string): RouteRow | undefined {
    return this.rows()?.find(r => r.id === routeId);
  }

  /** Show a change made here before the reload confirms it (e.g. stopped, after a stop). */
  patch(routeId: string, change: Partial<Routes>): void {
    this.routes.update(list => list?.map(r => r.routeId === routeId ? { ...r, ...change } : r) ?? list);
  }

  /** Reload the route list now (after an action), and the live data too. */
  refresh(): void {
    void this.load();
    this.metrics.refresh();
  }

  /** Reload the route list (one request at a time; a request during one runs once it ends). */
  load(): Promise<void> {
    if (this.loading) {
      this.loadAgain = true;
      return this.loading;
    }
    this.loading = (async () => {
      do {
        this.loadAgain = false;
        try {
          this.routes.set(await firstValueFrom(this.api.getRoutes()));
          this.failed.set(false);
        } catch (error) {
          console.error('Failed to load routes:', error);
          this.failed.set(this.routes() === null);
        }
      } while (this.loadAgain);
      this.loading = null;
    })();
    return this.loading;
  }

  private reloadSoon(): void {
    clearTimeout(this.reloadTimer);
    this.reloadTimer = setTimeout(() => void this.load(), RELOAD_DEBOUNCE_MS);
  }
}

import { inject, Injectable, InjectionToken, signal } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';
import { RouteMetric, RouteMetricsResponse } from '../models/metric-data';
import { MetricsService } from './metrics.service';
import { StreamEvent } from './event-stream';
import { LIVE_TIMINGS, LiveConnection } from './live-connection';

/** How often the page asks for fresh metrics while it has to poll (the server stream unavailable). */
export const METRICS_POLL_MS = new InjectionToken<number>('METRICS_POLL_MS', { factory: () => 15000 });

/** Whether to load the 30-minute history (a page that shows only current metrics turns it off). */
export const METRICS_HISTORY = new InjectionToken<boolean>('METRICS_HISTORY', { factory: () => true });

/** While polling: history snapshots are taken once a minute, so fetching them every few polls is enough. */
const HISTORY_EVERY_N_POLLS = 4;

/**
 * - connecting: no data yet
 * - live: connected (pushed), or the last poll succeeded
 * - stale: data is shown, but the connection dropped or the last poll failed (it keeps trying)
 * - paused: the viewer paused updates
 */
export type LiveStatus = 'connecting' | 'live' | 'stale' | 'paused';

/** The `metrics` event of /api/events: current route metrics, and the stored history when it changed. */
interface MetricsEvent {
  routes: RouteMetric[];
  history?: RouteMetric[];
}

/**
 * Live metrics for a page: current route metrics (all routes, or one datasource's) and the last 30 minutes of
 * 1-minute snapshots.
 *
 * Pushed by the server over server-sent events (/api/events, topic `metrics`, scoped with `datasource`): the first
 * event carries everything, later ones arrive when the numbers change (history only when a new minute was stored).
 * When the stream is unavailable (an older server, or a proxy that breaks streaming) it polls the metrics REST
 * endpoints instead. The connection (reconnects, hidden tabs, sign-in) is a {@link LiveConnection}. A failed
 * refresh keeps the last data. The signals are the contract with the page.
 *
 * Provided by the page, so it lives as long as the page does.
 */
@Injectable()
export class MetricsLiveService {
  private readonly api = inject(MetricsService);
  private readonly withHistory = inject(METRICS_HISTORY);

  /** Current route metrics; null until the first response. */
  readonly routes = signal<RouteMetric[] | null>(null);
  /** History snapshots; null until the first response. */
  readonly history = signal<RouteMetric[] | null>(null);
  /** The first request for routes / history failed, so there is nothing to show. */
  readonly routesFailed = signal(false);
  readonly historyFailed = signal(false);
  readonly status = signal<LiveStatus>('connecting');
  /** When data last arrived (ms since epoch). */
  readonly updatedAt = signal<number | null>(null);
  /** Data for a new scope (datasource) is on its way; what's shown is the previous scope's. */
  readonly rescoping = signal(false);

  private datasource = '';
  private polls = 0;

  private readonly connection = new LiveConnection({
    streamUrl: () => this.streamUrl(),
    onOpen: () => {
      if (this.routes() !== null && !this.rescoping() && !this.connection.isPaused) {
        this.status.set('live');
      }
    },
    onEvent: event => this.onEvent(event),
    poll: () => this.poll(),
    onFailure: () => this.markFailed(this.withHistory),
  }, { ...inject(LIVE_TIMINGS), pollMs: inject(METRICS_POLL_MS) });

  /** How updates arrive: pushed, or polled while the stream is unavailable. */
  readonly transport = this.connection.transport;

  /** Scope to one datasource's routes ('' for all). Keeps showing the current data until the new data arrives. */
  setDatasource(datasource: string): void {
    if (datasource !== this.datasource) {
      this.datasource = datasource;
      this.rescoping.set(this.routes() !== null);
      this.polls = 0;
      this.connection.reconnect();
    }
  }

  pause(): void {
    this.status.set('paused');
    this.connection.pause();
  }

  resume(): void {
    this.status.set(this.routes() === null ? 'connecting' : 'stale');
    this.polls = 0; // a full refresh, history included
    this.connection.resume();
  }

  /** Fetch now (reconnects the stream, whose first event is a full refresh). Also resumes. */
  refresh(): void {
    if (this.connection.isPaused) {
      this.resume();
    }
    this.polls = 0;
    this.connection.reconnect();
  }

  private streamUrl(): string {
    const params = new URLSearchParams({ topics: 'metrics' });
    if (this.datasource) {
      params.set('datasource', this.datasource);
    }
    if (!this.withHistory) {
      params.set('history', 'false');
    }
    return `/queryrest/api/events?${params}`;
  }

  private onEvent(event: StreamEvent): void {
    if (event.event !== 'metrics') {
      return;
    }
    try {
      const data = JSON.parse(event.data) as MetricsEvent;
      this.apply(data.routes ?? [], this.withHistory && 'history' in data ? data.history ?? [] : undefined);
    } catch (error) {
      console.error('Unreadable metrics event:', error);
    }
  }

  // --- Polling fallback: the metrics REST endpoints ---------------------------------------------------------------

  private async poll(): Promise<void> {
    const datasource = this.datasource;
    const historyDue = this.withHistory && (this.polls % HISTORY_EVERY_N_POLLS === 0 || this.history() === null);
    this.polls++;
    const [routes, history] = await Promise.all([
      this.capture(datasource ? this.api.getDatasourceRouteMetrics(datasource) : this.api.getRouteMetrics()),
      historyDue ? this.capture(this.api.getPersistedMetrics()) : Promise.resolve(undefined),
    ]);
    if (datasource !== this.datasource) {
      return; // rescoped while asking: this answer is for the old scope
    }
    if (routes) {
      this.apply(routes, history ?? undefined);
    } else {
      this.markFailed(history === null);
      if (history) {
        this.history.set(history);
        this.historyFailed.set(false);
      }
    }
    if (routes && history === null && this.history() === null) {
      this.historyFailed.set(true);
    }
  }

  /** The response's metrics, or null if the request failed. */
  private async capture(request: Observable<RouteMetricsResponse>): Promise<RouteMetric[] | null> {
    try {
      return (await firstValueFrom(request)).metrics;
    } catch (error) {
      console.error('Failed to fetch metrics:', error);
      return null;
    }
  }

  // --- State -------------------------------------------------------------------------------------------------------

  /** New data: routes always, history when it came with them. */
  private apply(routes: RouteMetric[], history: RouteMetric[] | undefined): void {
    this.routes.set(routes);
    this.routesFailed.set(false);
    this.rescoping.set(false);
    if (history !== undefined) {
      this.history.set(history);
      this.historyFailed.set(false);
    }
    this.updatedAt.set(Date.now());
    if (!this.connection.isPaused) {
      this.status.set('live');
    }
  }

  private markFailed(historyToo: boolean): void {
    if (this.routes() === null) {
      this.routesFailed.set(true);
    }
    if (historyToo && this.history() === null) {
      this.historyFailed.set(true);
    }
    if (!this.connection.isPaused) {
      this.status.set(this.routes() === null ? 'connecting' : 'stale');
    }
  }
}

import { DestroyRef, inject, Injectable, InjectionToken, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DOCUMENT } from '@angular/common';
import { BehaviorSubject, catchError, combineLatest, EMPTY, exhaustMap, forkJoin, fromEvent, map, Observable, of,
  startWith, switchMap, timer } from 'rxjs';
import { RouteMetric, RouteMetricsResponse } from '../models/metric-data';
import { MetricsService } from './metrics.service';

/** How often the page asks for fresh metrics. */
export const METRICS_POLL_MS = new InjectionToken<number>('METRICS_POLL_MS', { factory: () => 15000 });

/** History snapshots are taken once a minute, so fetching them every few polls is enough. */
const HISTORY_EVERY_N_POLLS = 4;

/**
 * - connecting: no data yet
 * - live: the last request succeeded
 * - stale: data is shown, but the last refresh failed (it keeps trying)
 * - paused: the viewer paused updates
 */
export type LiveStatus = 'connecting' | 'live' | 'stale' | 'paused';

/**
 * Live metrics for the metrics page: current route metrics (all routes, or one datasource's) and the last 30 minutes
 * of 1-minute snapshots, kept up to date while the page is open.
 *
 * Today it polls; it stops while the browser tab is hidden and refreshes as soon as the tab is shown again. A failed
 * refresh keeps the last data. The signals are the contract with the page, so a server push (SSE, see
 * docs/metrics-live-plan.md) can replace the polling here without changing the page.
 *
 * Provided by the page, so it lives as long as the page does.
 */
@Injectable()
export class MetricsLiveService {
  private readonly api = inject(MetricsService);
  private readonly pollMs = inject(METRICS_POLL_MS);
  private readonly document = inject(DOCUMENT);

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

  private readonly paused$ = new BehaviorSubject(false);
  private readonly datasource$ = new BehaviorSubject('');
  private readonly kick$ = new BehaviorSubject(0);

  constructor() {
    const visible$ = fromEvent(this.document, 'visibilitychange').pipe(
      startWith(null),
      map(() => this.document.visibilityState !== 'hidden'),
    );
    combineLatest([this.paused$, visible$, this.datasource$, this.kick$]).pipe(
      // Changing scope, pausing, hiding or refreshing restarts the schedule (and drops a request in flight).
      switchMap(([paused, visible, datasource]) => paused || !visible
        ? EMPTY
        : timer(0, this.pollMs).pipe(
            exhaustMap(poll => this.fetch(datasource, poll % HISTORY_EVERY_N_POLLS === 0)),
          )),
      takeUntilDestroyed(inject(DestroyRef)),
    ).subscribe();
  }

  /** Scope to one datasource's routes ('' for all). Keeps showing the current data until the new data arrives. */
  setDatasource(datasource: string): void {
    if (datasource !== this.datasource$.value) {
      this.rescoping.set(this.routes() !== null);
      this.datasource$.next(datasource);
    }
  }

  pause(): void {
    this.status.set('paused');
    this.paused$.next(true);
  }

  resume(): void {
    this.paused$.next(false);
  }

  /** Fetch now (and restart the schedule). Also resumes. */
  refresh(): void {
    this.paused$.next(false);
    this.kick$.next(this.kick$.value + 1);
  }

  private fetch(datasource: string, withHistory: boolean): Observable<unknown> {
    const routes$ = this.capture(datasource ? this.api.getDatasourceRouteMetrics(datasource) : this.api.getRouteMetrics());
    const history$ = withHistory || this.history() === null ? this.capture(this.api.getPersistedMetrics()) : of(undefined);
    return forkJoin([routes$, history$]).pipe(map(([routes, history]) => {
      const now = Date.now();
      if (routes) {
        this.routes.set(routes);
        this.routesFailed.set(false);
        this.rescoping.set(false);
      } else if (this.routes() === null) {
        this.routesFailed.set(true);
      }
      if (history) {
        this.history.set(history);
        this.historyFailed.set(false);
      } else if (history === null && this.history() === null) {
        this.historyFailed.set(true);
      }
      if (routes) {
        this.updatedAt.set(now);
      }
      if (!this.paused$.value) {
        this.status.set(routes ? 'live' : this.routes() ? 'stale' : 'connecting');
      }
    }));
  }

  /** The response's metrics, or null if the request failed. */
  private capture(request: Observable<RouteMetricsResponse>): Observable<RouteMetric[] | null> {
    return request.pipe(
      map(response => response.metrics),
      catchError(error => {
        console.error('Failed to fetch metrics:', error);
        return of(null);
      }),
    );
  }
}

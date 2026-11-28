import { DestroyRef, inject, Injectable, InjectionToken, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { DatasourceHealth, DatasourceHealthSnapshot } from '../models/datasource-health';
import { signInAgain } from '../interceptors/auth.interceptor';
import { readEventStream, StreamError, StreamEvent } from './event-stream';
import { LiveStatus } from './metrics-live.service';

export const DATASOURCE_STREAM_URL = '/queryrest/api/events?topics=datasources';
export const DATASOURCE_SNAPSHOT_URL = '/queryrest/api/events/datasources';

/** The fetch used for the event stream (a test replaces it). */
export const EVENT_STREAM_FETCH = new InjectionToken<typeof fetch>('EVENT_STREAM_FETCH',
  { factory: () => (input, init) => fetch(input, init) });

/** Timings, as one token so a test can shorten them. */
export interface LiveTimings {
  /** Polling interval when the stream isn't available. */
  pollMs: number;
  /** Waits between failed stream attempts (the last repeats). */
  retryMs: number[];
  /** After this many failed attempts in a row (or a 404: an older server), poll instead. */
  maxStreamFailures: number;
  /** While polling, try the stream again after this long. */
  retryStreamAfterMs: number;
  /** A hidden tab keeps its connection this long (switching tabs briefly shouldn't reconnect). */
  hiddenCloseMs: number;
}

export const LIVE_TIMINGS = new InjectionToken<LiveTimings>('LIVE_TIMINGS', {
  factory: () => ({ pollMs: 15000, retryMs: [1000, 2000, 5000, 10000, 15000], maxStreamFailures: 3,
    retryStreamAfterMs: 5 * 60000, hiddenCloseMs: 30000 }),
});

/**
 * Live health of every datasource, pushed by the server over server-sent events (/api/events, topic `datasources`):
 * the first event is the whole state, and a new one arrives whenever a status, check or setting changes.
 *
 * Reconnects with backoff when the stream drops (the server also ends each stream after a few minutes, so a
 * reconnect is routine). When the stream is unavailable (an older server, or a proxy that breaks streaming) it
 * polls the same snapshot instead and tries the stream again every few minutes. A tab hidden for 30 seconds
 * closes its connection; showing it reconnects. A 499 (session expired) reloads the page to sign in again.
 *
 * Provided by the datasources page, so the list and the datasource page under it share one connection.
 */
@Injectable()
export class DatasourceLiveService {
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fetchFn = inject(EVENT_STREAM_FETCH);
  private readonly timings = inject(LIVE_TIMINGS);
  private readonly document = inject(DOCUMENT);

  /** Every datasource's health; null until the first snapshot. */
  readonly datasources = signal<DatasourceHealth[] | null>(null);
  /** Automated health checks run on the server. */
  readonly healthCheckEnabled = signal(true);
  /** Nothing could be loaded yet (it keeps trying). */
  readonly failed = signal(false);
  readonly status = signal<LiveStatus>('connecting');
  /** When the last snapshot arrived (ms since epoch). */
  readonly updatedAt = signal<number | null>(null);
  /** How updates arrive: pushed, or polled while the stream is unavailable. */
  readonly transport = signal<'stream' | 'polling'>('stream');

  private paused = false;
  private visible: boolean;
  private destroyed = false;
  private abort: AbortController | null = null;
  private wakeUp: (() => void) | null = null;
  private hiddenTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.visible = this.document.visibilityState !== 'hidden';
    const onVisibility = () => this.visibilityChanged();
    this.document.addEventListener('visibilitychange', onVisibility);
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.document.removeEventListener('visibilitychange', onVisibility);
      clearTimeout(this.hiddenTimer);
      this.disconnect();
    });
    void this.run();
  }

  /** One datasource's health, or undefined if unknown (yet). */
  find(dataSourceId: string): DatasourceHealth | undefined {
    return this.datasources()?.find(d => d.dataSourceId === dataSourceId);
  }

  pause(): void {
    this.paused = true;
    this.status.set('paused');
    this.disconnect();
  }

  resume(): void {
    this.paused = false;
    this.status.set(this.datasources() === null ? 'connecting' : 'stale');
    this.wake();
  }

  /** Fetch the whole state again now (reconnects the stream; its first event is the full state). Also resumes. */
  refresh(): void {
    if (this.paused) {
      this.resume();
    }
    this.disconnect();
  }

  /**
   * Show a change made here before the server confirms it (e.g. "checking" while a check runs). The next snapshot
   * replaces it. While polling, also asks for that snapshot now.
   */
  patch(dataSourceId: string, change: Partial<DatasourceHealth>): void {
    this.datasources.update(list => list?.map(d => d.dataSourceId === dataSourceId ? { ...d, ...change } : d) ?? list);
    if (this.transport() === 'polling') {
      this.wake();
    }
  }

  // --- Connection loop ---------------------------------------------------------------------------------------------

  private async run(): Promise<void> {
    let failures = 0;
    let pollingSince = 0;
    while (!this.destroyed) {
      if (this.paused || !this.visible) {
        await this.sleep(Infinity);
        continue;
      }
      if (this.transport() === 'polling') {
        if (Date.now() - pollingSince < this.timings.retryStreamAfterMs) {
          await this.poll();
          await this.sleep(this.timings.pollMs);
          continue;
        }
        this.transport.set('stream');
      }
      const abort = new AbortController();
      this.abort = abort;
      try {
        await readEventStream(this.fetchFn, DATASOURCE_STREAM_URL, abort.signal, {
          onOpen: () => {
            failures = 0;
            if (this.datasources() !== null) {
              this.status.set('live');
            }
          },
          onEvent: event => this.onEvent(event),
        });
        // The server ended the stream (they are capped in length): reconnect at once.
      } catch (error) {
        if (abort.signal.aborted) {
          continue; // paused, hidden, refreshed or destroyed
        }
        const status = error instanceof StreamError ? error.status : 0;
        if (status === 499) {
          signInAgain(this.snackBar);
          return;
        }
        failures++;
        this.markFailed();
        if (status === 404 || failures >= this.timings.maxStreamFailures) {
          this.transport.set('polling');
          pollingSince = Date.now();
          failures = 0;
          continue;
        }
        const retry = this.timings.retryMs[Math.min(failures, this.timings.retryMs.length) - 1];
        await this.sleep(retry * (0.8 + Math.random() * 0.4));
      } finally {
        if (this.abort === abort) {
          this.abort = null;
        }
      }
    }
  }

  private onEvent(event: StreamEvent): void {
    if (event.event !== 'datasources') {
      return;
    }
    try {
      this.apply(JSON.parse(event.data) as DatasourceHealthSnapshot);
    } catch (error) {
      console.error('Unreadable datasource health event:', error);
    }
  }

  private async poll(): Promise<void> {
    try {
      this.apply(await firstValueFrom(this.http.get<DatasourceHealthSnapshot>(DATASOURCE_SNAPSHOT_URL)));
    } catch (error) {
      console.error('Failed to fetch datasource health:', error);
      this.markFailed();
    }
  }

  private apply(snapshot: DatasourceHealthSnapshot): void {
    this.datasources.set(snapshot.datasources ?? []);
    this.healthCheckEnabled.set(snapshot.healthCheckEnabled !== false);
    this.failed.set(false);
    this.updatedAt.set(Date.now());
    if (!this.paused) {
      this.status.set('live');
    }
  }

  private markFailed(): void {
    if (this.datasources() === null) {
      this.failed.set(true);
    }
    if (!this.paused) {
      this.status.set(this.datasources() === null ? 'connecting' : 'stale');
    }
  }

  private visibilityChanged(): void {
    clearTimeout(this.hiddenTimer);
    if (this.document.visibilityState === 'hidden') {
      this.hiddenTimer = setTimeout(() => {
        this.visible = false;
        this.disconnect();
      }, this.timings.hiddenCloseMs);
    } else if (!this.visible) {
      this.visible = true;
      this.wake();
    }
  }

  /** Close the stream (the loop then decides what to do next) and end any wait. */
  private disconnect(): void {
    this.abort?.abort();
    this.wake();
  }

  private wake(): void {
    const wake = this.wakeUp;
    this.wakeUp = null;
    wake?.();
  }

  /** Waits `ms`, or until woken (pause, resume, refresh, visibility, destroy). */
  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => {
      const timer = Number.isFinite(ms) ? setTimeout(() => done(), ms) : undefined;
      const done = () => {
        clearTimeout(timer);
        if (this.wakeUp === done) {
          this.wakeUp = null;
        }
        resolve();
      };
      this.wakeUp = done;
    });
  }
}

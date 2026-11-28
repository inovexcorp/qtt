import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { DatasourceHealth, DatasourceHealthSnapshot } from '../models/datasource-health';
import { StreamEvent } from './event-stream';
import { liveLink } from './live-hub';
import { LiveStatus } from './metrics-live.service';

export { EVENT_STREAM_FETCH, LIVE_TIMINGS } from './live-connection';
export type { LiveTimings } from './live-connection';

export const DATASOURCE_STREAM_URL = '/queryrest/api/events?topics=datasources';
export const DATASOURCE_SNAPSHOT_URL = '/queryrest/api/events/datasources';

/**
 * Live health of every datasource, pushed by the server over server-sent events (/api/events, topic `datasources`):
 * the first event is the whole state, and a new one arrives whenever a status, check or setting changes. The
 * connection (reconnects, polling fallback, hidden tabs, sign-in) is a {@link LiveConnection}, shared with the
 * page's other live services through a {@link LiveHub} when the page provides one.
 *
 * Provided by the datasources page, so the list and the datasource page under it share one connection.
 */
@Injectable()
export class DatasourceLiveService {
  private readonly http = inject(HttpClient);

  /** Every datasource's health; null until the first snapshot. */
  readonly datasources = signal<DatasourceHealth[] | null>(null);
  /** Automated health checks run on the server. */
  readonly healthCheckEnabled = signal(true);
  /** Nothing could be loaded yet (it keeps trying). */
  readonly failed = signal(false);
  readonly status = signal<LiveStatus>('connecting');
  /** When the last snapshot arrived (ms since epoch). */
  readonly updatedAt = signal<number | null>(null);

  private readonly connection = liveLink({
    streamUrl: () => DATASOURCE_STREAM_URL,
    onOpen: () => {
      if (this.datasources() !== null && !this.connection.isPaused) {
        this.status.set('live');
      }
    },
    onEvent: event => this.onEvent(event),
    poll: () => this.poll(),
    onFailure: () => this.markFailed(),
  });

  /** How updates arrive: pushed, or polled while the stream is unavailable. */
  readonly transport = this.connection.transport;

  /** One datasource's health, or undefined if unknown (yet). */
  find(dataSourceId: string): DatasourceHealth | undefined {
    return this.datasources()?.find(d => d.dataSourceId === dataSourceId);
  }

  pause(): void {
    this.status.set('paused');
    this.connection.pause();
  }

  resume(): void {
    this.status.set(this.datasources() === null ? 'connecting' : 'stale');
    this.connection.resume();
  }

  /** Fetch the whole state again now (reconnects the stream; its first event is the full state). Also resumes. */
  refresh(): void {
    if (this.connection.isPaused) {
      this.resume();
    }
    this.connection.reconnect();
  }

  /**
   * Show a change made here before the server confirms it (e.g. "checking" while a check runs). The next snapshot
   * replaces it. While polling, also asks for that snapshot now.
   */
  patch(dataSourceId: string, change: Partial<DatasourceHealth>): void {
    this.datasources.update(list => list?.map(d => d.dataSourceId === dataSourceId ? { ...d, ...change } : d) ?? list);
    if (this.transport() === 'polling') {
      this.connection.reconnect();
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
    if (!this.connection.isPaused) {
      this.status.set('live');
    }
  }

  private markFailed(): void {
    if (this.datasources() === null) {
      this.failed.set(true);
    }
    if (!this.connection.isPaused) {
      this.status.set(this.datasources() === null ? 'connecting' : 'stale');
    }
  }
}

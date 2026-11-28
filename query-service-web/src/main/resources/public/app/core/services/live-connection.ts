import { DestroyRef, inject, InjectionToken, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { MatSnackBar } from '@angular/material/snack-bar';
import { signInAgain } from '../interceptors/auth.interceptor';
import { readEventStream, StreamError, StreamEvent } from './event-stream';

/** The fetch used for event streams (a test replaces it). */
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

/** A stream the server closes sooner than this after opening, without an event, counts as a failure. */
const SHORT_STREAM_MS = 1000;

export interface LiveConnectionHandlers {
  /** The stream URL (read at each connect, so a change of scope applies on reconnect). */
  streamUrl(): string;
  /** A server-sent event arrived. */
  onEvent(event: StreamEvent): void;
  /** The stream is open. */
  onOpen(): void;
  /** One polling round (when the stream is unavailable). Resolves whether it succeeded or not. */
  poll(): Promise<void>;
  /** A stream attempt failed (polling reports its own failures). */
  onFailure(): void;
}

/**
 * Keeps a page's live data flowing from /api/events: opens the server-sent-events stream, reconnects with backoff
 * when it drops (the server also ends each stream after a few minutes, so a reconnect is routine), and when the
 * stream is unavailable (an older server answers 404, or a proxy breaks streaming) polls instead, trying the stream
 * again every few minutes. A tab hidden for 30 seconds closes its connection; showing it reconnects. A 499 (session
 * expired) reloads the page to sign in again.
 *
 * Create it in an injection context (a service constructor); it stops when that injector is destroyed.
 */
export class LiveConnection {
  /** How updates arrive: pushed, or polled while the stream is unavailable. */
  readonly transport = signal<'stream' | 'polling'>('stream');

  private readonly snackBar = inject(MatSnackBar);
  private readonly fetchFn = inject(EVENT_STREAM_FETCH);
  private readonly document = inject(DOCUMENT);

  private paused = false;
  private visible: boolean;
  private destroyed = false;
  private abort: AbortController | null = null;
  private wakeUp: (() => void) | null = null;
  private hiddenTimer: ReturnType<typeof setTimeout> | undefined;

  constructor(private readonly handlers: LiveConnectionHandlers, private readonly timings: LiveTimings = inject(LIVE_TIMINGS)) {
    this.visible = this.document.visibilityState !== 'hidden';
    const onVisibility = () => this.visibilityChanged();
    this.document.addEventListener('visibilitychange', onVisibility);
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.document.removeEventListener('visibilitychange', onVisibility);
      clearTimeout(this.hiddenTimer);
      this.reconnect();
    });
    // Connect after the current task, so the page that created this can set its scope first (one connection, not two).
    setTimeout(() => void this.run());
  }

  get isPaused(): boolean {
    return this.paused;
  }

  pause(): void {
    this.paused = true;
    this.reconnect();
  }

  resume(): void {
    this.paused = false;
    this.wake();
  }

  /** Close the stream and open it again at once (its first event is the full state); while polling, poll now. */
  reconnect(): void {
    this.abort?.abort();
    this.wake();
  }

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
          await this.handlers.poll();
          if (!this.destroyed && !this.paused) {
            await this.sleep(this.timings.pollMs);
          }
          continue;
        }
        this.transport.set('stream');
      }
      const abort = new AbortController();
      this.abort = abort;
      let openedAt = 0;
      let events = 0;
      try {
        await readEventStream(this.fetchFn, this.handlers.streamUrl(), abort.signal, {
          onOpen: () => {
            openedAt = Date.now();
            this.handlers.onOpen();
          },
          onEvent: event => {
            events++;
            failures = 0;
            this.handlers.onEvent(event);
          },
        });
        if (events === 0 && Date.now() - openedAt < SHORT_STREAM_MS) {
          throw new StreamError(0); // closed at once: not a working stream
        }
        // The server ended the stream (they are capped in length): reconnect at once.
      } catch (error) {
        if (abort.signal.aborted) {
          continue; // paused, hidden, rescoped, refreshed or destroyed
        }
        const status = error instanceof StreamError ? error.status : 0;
        if (status === 499) {
          signInAgain(this.snackBar);
          return;
        }
        failures++;
        this.handlers.onFailure();
        if (status === 404 || status === 415 || failures >= this.timings.maxStreamFailures) {
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

  private visibilityChanged(): void {
    clearTimeout(this.hiddenTimer);
    if (this.document.visibilityState === 'hidden') {
      this.hiddenTimer = setTimeout(() => {
        this.visible = false;
        this.reconnect();
      }, this.timings.hiddenCloseMs);
    } else if (!this.visible) {
      this.visible = true;
      this.wake();
    }
  }

  private wake(): void {
    const wake = this.wakeUp;
    this.wakeUp = null;
    wake?.();
  }

  /** Waits `ms`, or until woken (pause, resume, reconnect, visibility, destroy). */
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

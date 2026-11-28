import { DestroyRef, inject, Injectable, Signal } from '@angular/core';
import { LIVE_TIMINGS, LiveConnection, LiveConnectionHandlers, LiveTimings } from './live-connection';

/** What a live service needs from its connection; a {@link LiveConnection} and a hub link both provide it. */
export interface LiveLink {
  readonly transport: Signal<'stream' | 'polling'>;
  readonly isPaused: boolean;
  pause(): void;
  resume(): void;
  reconnect(): void;
}

/**
 * One /api/events stream for every live service on a page. The endpoint carries several topics on one stream, so a
 * page that needs datasource health and route metrics holds one connection, not two (browsers allow few long-lived
 * connections per server over HTTP/1.1).
 *
 * Each service still describes its own stream (its URL, its events, its polling); the hub merges the URLs, hands
 * every event to every service (each ignores topics that aren't its own), and while polling runs every service's
 * poll. Pausing, resuming or refreshing applies to the whole page, which is what its single live indicator means.
 *
 * Provide it on the page next to the services; a service created without one connects on its own.
 */
@Injectable()
export class LiveHub {
  private readonly members: LiveConnectionHandlers[] = [];

  private readonly connection = new LiveConnection({
    streamUrl: () => mergeStreamUrls(this.members.map(m => m.streamUrl())),
    onOpen: () => this.members.forEach(m => m.onOpen()),
    onEvent: event => this.members.forEach(m => m.onEvent(event)),
    poll: async () => {
      await Promise.all(this.members.map(m => m.poll()));
    },
    onFailure: () => this.members.forEach(m => m.onFailure()),
  });

  /** Adds a service to the stream (reconnecting it, so the stream includes its topic); removed when it's destroyed. */
  join(handlers: LiveConnectionHandlers, destroyRef: DestroyRef): LiveLink {
    this.members.push(handlers);
    destroyRef.onDestroy(() => {
      const at = this.members.indexOf(handlers);
      if (at >= 0) {
        this.members.splice(at, 1);
      }
    });
    // Services created in the same task share the first connect (it is deferred one task), so this only matters
    // for one that joins later.
    this.connection.reconnect();
    const connection = this.connection;
    return {
      transport: connection.transport,
      get isPaused() {
        return connection.isPaused;
      },
      pause: () => connection.pause(),
      resume: () => connection.resume(),
      reconnect: () => connection.reconnect(),
    };
  }
}

/**
 * The connection for a live service: through the page's {@link LiveHub} when there is one, otherwise its own.
 * Call in an injection context (a field initializer of the service).
 */
export function liveLink(handlers: LiveConnectionHandlers, timings?: LiveTimings): LiveLink {
  const hub = inject(LiveHub, { optional: true });
  if (hub) {
    return hub.join(handlers, inject(DestroyRef));
  }
  return new LiveConnection(handlers, timings ?? inject(LIVE_TIMINGS));
}

/**
 * One stream URL asking for everything the given ones ask for: the union of their topics, and every other
 * parameter (a parameter given twice keeps the first value). Paths are expected to be the same.
 */
export function mergeStreamUrls(urls: string[]): string {
  if (urls.length === 1) {
    return urls[0];
  }
  let path = '';
  const topics: string[] = [];
  const params = new URLSearchParams();
  for (const url of urls) {
    const [p, query = ''] = url.split('?', 2);
    path ||= p;
    new URLSearchParams(query).forEach((value, key) => {
      if (key === 'topics') {
        value.split(',').filter(t => t && !topics.includes(t)).forEach(t => topics.push(t));
      } else if (!params.has(key)) {
        params.set(key, value);
      }
    });
  }
  const merged = new URLSearchParams({ topics: topics.join(',') });
  params.forEach((value, key) => merged.set(key, value));
  return `${path}?${merged}`;
}

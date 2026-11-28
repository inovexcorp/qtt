/**
 * Reading a server-sent-events stream with fetch() rather than EventSource: EventSource can't send headers, and
 * without X-Requested-With an expired session answers with a redirect to the sign-in page, which EventSource
 * retries forever. fetch sends the header and the session cookie, and this reads the event lines.
 */

export interface StreamEvent {
  /** The `event:` name ('message' when absent). */
  event: string;
  data: string;
}

/** The stream couldn't be opened (status 0: a network error). */
export class StreamError extends Error {
  constructor(readonly status: number) {
    super(status ? `Event stream answered ${status}` : 'Event stream network error');
  }
}

/** Splits text arriving in arbitrary chunks into events (https://html.spec.whatwg.org/#event-stream-interpretation). */
export class EventStreamParser {
  private buffer = '';
  private event = '';
  private data: string[] = [];

  push(chunk: string): StreamEvent[] {
    this.buffer += chunk;
    const out: StreamEvent[] = [];
    let newline: number;
    while ((newline = this.buffer.search(/\r\n|\r|\n/)) >= 0) {
      // A lone \r at the very end may be the first half of \r\n: wait for the next chunk.
      if (this.buffer[newline] === '\r' && newline === this.buffer.length - 1) {
        break;
      }
      const line = this.buffer.slice(0, newline);
      this.buffer = this.buffer.slice(newline + (this.buffer.startsWith('\r\n', newline) ? 2 : 1));
      const event = this.line(line);
      if (event) {
        out.push(event);
      }
    }
    return out;
  }

  private line(line: string): StreamEvent | null {
    if (line === '') {
      if (this.data.length === 0) {
        this.event = '';
        return null;
      }
      const event = { event: this.event || 'message', data: this.data.join('\n') };
      this.event = '';
      this.data = [];
      return event;
    }
    if (line.startsWith(':')) {
      return null; // comment (keep-alive)
    }
    const colon = line.indexOf(':');
    const field = colon < 0 ? line : line.slice(0, colon);
    let value = colon < 0 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) {
      value = value.slice(1);
    }
    if (field === 'event') {
      this.event = value;
    } else if (field === 'data') {
      this.data.push(value);
    }
    return null;
  }
}

export interface StreamHandlers {
  /** The server accepted the stream. */
  onOpen(): void;
  onEvent(event: StreamEvent): void;
}

/**
 * Opens the stream and delivers its events until it ends. Resolves when the server closes it (streams are capped in
 * length; reconnect), rejects with a StreamError when it can't be opened, or with the abort error when `signal`
 * aborts.
 */
export async function readEventStream(fetchFn: typeof fetch, url: string, signal: AbortSignal,
                                      handlers: StreamHandlers): Promise<void> {
  let response: Response;
  try {
    response = await fetchFn(url, {
      headers: { 'X-Requested-With': 'JavaScript', Accept: 'text/event-stream' },
      credentials: 'same-origin',
      cache: 'no-store',
      signal,
    });
  } catch (error) {
    if (signal.aborted) {
      throw error;
    }
    throw new StreamError(0);
  }
  if (!response.ok || !response.body) {
    throw new StreamError(response.status);
  }
  // Something answered, but not with a stream (e.g. a proxy or dev server serving a page): treat as unsupported.
  if (!(response.headers.get('Content-Type') ?? '').includes('text/event-stream')) {
    void response.body.cancel().catch(() => undefined);
    throw new StreamError(415);
  }
  handlers.onOpen();
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  const parser = new EventStreamParser();
  // Aborting ends the read even where the body doesn't follow the fetch's signal.
  const cancel = () => void reader.cancel().catch(() => undefined);
  signal.addEventListener('abort', cancel, { once: true });
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (signal.aborted) {
        throw signal.reason ?? new DOMException('Aborted', 'AbortError');
      }
      if (done) {
        return;
      }
      for (const event of parser.push(value)) {
        handlers.onEvent(event);
      }
    }
  } catch (error) {
    if (signal.aborted) {
      throw error;
    }
    throw new StreamError(0);
  } finally {
    signal.removeEventListener('abort', cancel);
    reader.releaseLock();
  }
}

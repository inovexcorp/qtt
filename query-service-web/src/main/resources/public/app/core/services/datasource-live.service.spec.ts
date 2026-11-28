import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DATASOURCE_SNAPSHOT_URL, DatasourceLiveService, EVENT_STREAM_FETCH, LIVE_TIMINGS } from './datasource-live.service';
import { DatasourceHealthSnapshot } from '../models/datasource-health';

const snapshot = (status = 'UP'): DatasourceHealthSnapshot => ({
  healthCheckEnabled: true,
  datasources: [{ dataSourceId: 'ds1', url: 'https://a', status: status as never, lastHealthCheck: null, lastHealthError: null,
    consecutiveFailures: 0, routeCount: 0, recentChecks: [] }],
});

/** A stream response the test writes events into. */
function stream() {
  let controller!: ReadableStreamDefaultController<Uint8Array>;
  const body = new ReadableStream<Uint8Array>({ start: c => { controller = c; } });
  return {
    response: new Response(body, { headers: { 'Content-Type': 'text/event-stream' } }),
    send: (data: DatasourceHealthSnapshot) =>
      controller.enqueue(new TextEncoder().encode(`event:datasources\ndata:${JSON.stringify(data)}\n\n`)),
    end: () => controller.close(),
  };
}

const settle = () => new Promise(resolve => setTimeout(resolve, 20));

describe('DatasourceLiveService', () => {
  let fetchFn: ReturnType<typeof vi.fn>;

  function create(): DatasourceLiveService {
    TestBed.configureTestingModule({
      providers: [
        DatasourceLiveService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: EVENT_STREAM_FETCH, useValue: fetchFn },
        { provide: LIVE_TIMINGS, useValue: { pollMs: 50, retryMs: [5], maxStreamFailures: 2, retryStreamAfterMs: 60000, hiddenCloseMs: 10 } },
      ],
    });
    return TestBed.inject(DatasourceLiveService);
  }

  afterEach(() => TestBed.resetTestingModule());

  it('shows each pushed snapshot and goes live', async () => {
    const s = stream();
    fetchFn = vi.fn().mockResolvedValue(s.response);
    const live = create();
    expect(live.status()).toBe('connecting');

    s.send(snapshot('UP'));
    await settle();
    expect(live.datasources()![0].status).toBe('UP');
    expect(live.status()).toBe('live');

    s.send(snapshot('DOWN'));
    await settle();
    expect(live.find('ds1')!.status).toBe('DOWN');
  });

  it('reconnects when the server ends the stream', async () => {
    const first = stream();
    const second = stream();
    fetchFn = vi.fn().mockResolvedValueOnce(first.response).mockResolvedValueOnce(second.response);
    const live = create();
    first.send(snapshot());
    first.end();
    await settle();
    expect(fetchFn).toHaveBeenCalledTimes(2);
    expect(live.datasources()).not.toBeNull();
  });

  it('polls the snapshot when the stream endpoint is missing (an older server)', async () => {
    fetchFn = vi.fn().mockResolvedValue(new Response('', { status: 404 }));
    const live = create();
    await settle();
    expect(live.transport()).toBe('polling');

    TestBed.inject(HttpTestingController).expectOne(DATASOURCE_SNAPSHOT_URL).flush(snapshot('DOWN'));
    await settle();
    expect(live.find('ds1')!.status).toBe('DOWN');
    expect(live.status()).toBe('live');
  });

  it('marks failure while nothing has loaded, and stays stale (not failed) once data is shown', async () => {
    fetchFn = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    const live = create();
    await settle();
    expect(live.failed()).toBe(true);
    expect(live.status()).toBe('connecting');
  });

  it('closes the stream when paused and reconnects on resume', async () => {
    const s = stream();
    fetchFn = vi.fn().mockImplementation((_url: string, init: RequestInit) => {
      init.signal!.addEventListener('abort', () => s.end());
      return Promise.resolve(s.response);
    });
    const live = create();
    s.send(snapshot());
    await settle();

    live.pause();
    await settle();
    expect(live.status()).toBe('paused');
    const calls = fetchFn.mock.calls.length;

    fetchFn.mockResolvedValue(stream().response);
    live.resume();
    await settle();
    expect(fetchFn.mock.calls.length).toBe(calls + 1);
  });

  it('shows a local change until the next snapshot replaces it', async () => {
    const s = stream();
    fetchFn = vi.fn().mockResolvedValue(s.response);
    const live = create();
    s.send(snapshot('UP'));
    await settle();

    live.patch('ds1', { status: 'CHECKING' });
    expect(live.find('ds1')!.status).toBe('CHECKING');
    s.send(snapshot('DOWN'));
    await settle();
    expect(live.find('ds1')!.status).toBe('DOWN');
  });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DATASOURCE_SNAPSHOT_URL, DatasourceLiveService, EVENT_STREAM_FETCH, LIVE_TIMINGS } from './datasource-live.service';
import { MetricsLiveService } from './metrics-live.service';
import { LiveHub, mergeStreamUrls } from './live-hub';

const settle = () => new Promise(resolve => setTimeout(resolve, 20));

function stream() {
  let controller!: ReadableStreamDefaultController<Uint8Array>;
  const body = new ReadableStream<Uint8Array>({ start: c => { controller = c; } });
  return {
    response: new Response(body, { headers: { 'Content-Type': 'text/event-stream' } }),
    send: (event: string, data: unknown) =>
      controller.enqueue(new TextEncoder().encode(`event:${event}\ndata:${JSON.stringify(data)}\n\n`)),
  };
}

const metric = (route: string) => ({ route, exchangesTotal: 3, exchangesCompleted: 3, exchangesFailed: 0, exchangesInflight: 0,
  totalProcessingTime: 30, meanProcessingTime: 10, minProcessingTime: 1, maxProcessingTime: 20, uptime: '1m', state: 'Started',
  timeStamp: '' });

describe('mergeStreamUrls', () => {
  it('asks for the union of topics and keeps the other parameters', () => {
    const url = mergeStreamUrls(['/queryrest/api/events?topics=datasources',
      '/queryrest/api/events?topics=metrics&history=false', '/queryrest/api/events?topics=metrics,datasources']);
    const params = new URL(url, 'http://x').searchParams;
    expect(url.startsWith('/queryrest/api/events?')).toBe(true);
    expect(params.get('topics')).toBe('datasources,metrics');
    expect(params.get('history')).toBe('false');
  });

  it('returns a single URL unchanged', () => {
    expect(mergeStreamUrls(['/a?topics=x'])).toBe('/a?topics=x');
  });
});

describe('LiveHub', () => {
  let fetchFn: ReturnType<typeof vi.fn>;

  function create() {
    TestBed.configureTestingModule({
      providers: [
        LiveHub, DatasourceLiveService, MetricsLiveService,
        provideHttpClient(), provideHttpClientTesting(),
        { provide: EVENT_STREAM_FETCH, useValue: fetchFn },
        { provide: LIVE_TIMINGS, useValue: { pollMs: 50, retryMs: [5], maxStreamFailures: 2, retryStreamAfterMs: 60000, hiddenCloseMs: 10 } },
      ],
    });
    return { datasources: TestBed.inject(DatasourceLiveService), metrics: TestBed.inject(MetricsLiveService) };
  }

  afterEach(() => TestBed.resetTestingModule());

  it('opens one stream for every service on the page, and hands each its own events', async () => {
    const s = stream();
    fetchFn = vi.fn().mockResolvedValue(s.response);
    const { datasources, metrics } = create();
    await settle();

    expect(fetchFn).toHaveBeenCalledTimes(1);
    const topics = new URL(fetchFn.mock.calls[0][0], 'http://x').searchParams.get('topics')!.split(',');
    expect(topics.sort()).toEqual(['datasources', 'metrics']);

    s.send('datasources', { healthCheckEnabled: true, datasources: [] });
    s.send('metrics', { routes: [metric('r1')], history: [] });
    await settle();
    expect(datasources.datasources()).toEqual([]);
    expect(metrics.routes()!.map(r => r.route)).toEqual(['r1']);
    expect(datasources.status()).toBe('live');
    expect(metrics.status()).toBe('live');
  });

  it('pausing one pauses the page: the shared stream closes', async () => {
    const s = stream();
    fetchFn = vi.fn().mockResolvedValue(s.response);
    const { datasources } = create();
    await settle();
    const signal: AbortSignal = fetchFn.mock.calls[0][1].signal;

    datasources.pause();
    expect(signal.aborted).toBe(true);
  });

  it('polls for every service while the stream is unavailable', async () => {
    fetchFn = vi.fn().mockResolvedValue(new Response('', { status: 404 }));
    const { datasources, metrics } = create();
    await settle();
    expect(datasources.transport()).toBe('polling');
    expect(metrics.transport()).toBe('polling');

    const http = TestBed.inject(HttpTestingController);
    http.expectOne(DATASOURCE_SNAPSHOT_URL).flush({ healthCheckEnabled: true, datasources: [] });
    http.expectOne(r => r.url.includes('/metrics/') && !r.url.includes('persisted')).flush({ metrics: [metric('r2')] });
    http.match(r => r.url.includes('persisted')).forEach(r => r.flush({ metrics: [] }));
    await settle();
    expect(datasources.datasources()).toEqual([]);
    expect(metrics.routes()!.map(r => r.route)).toEqual(['r2']);
  });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { METRICS_HISTORY, METRICS_POLL_MS, MetricsLiveService } from './metrics-live.service';
import { EVENT_STREAM_FETCH } from './live-connection';
import { RouteMetric } from '../models/metric-data';

const ROUTES = '/queryrest/api/metrics/routes/';
const HISTORY = '/queryrest/api/metrics/route/persisted';

const metric = (route: string, total = 1): RouteMetric => ({
  route, exchangesTotal: total, exchangesCompleted: total, exchangesFailed: 0, exchangesInflight: 0,
  totalProcessingTime: total * 10, meanProcessingTime: 10, minProcessingTime: 5, maxProcessingTime: 20,
  uptime: '1m', state: 'Started', timeStamp: '2026-11-28T10:00:00',
});

/** Lets timer(0) fire. */
const tick = () => new Promise(resolve => setTimeout(resolve, 5));

describe('MetricsLiveService (polling, when the server has no event stream)', () => {
  let live: MetricsLiveService;
  let backend: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), MetricsLiveService,
        // Only the first fetch and explicit refreshes run during a test.
        { provide: METRICS_POLL_MS, useValue: 3_600_000 },
        // An older server: no event stream, so the service polls the REST endpoints.
        { provide: EVENT_STREAM_FETCH, useValue: () => Promise.resolve(new Response('', { status: 404 })) },
      ],
    });
    live = TestBed.inject(MetricsLiveService);
    backend = TestBed.inject(HttpTestingController);
    await tick();
  });

  afterEach(() => {
    backend.verify();
    TestBed.resetTestingModule();
  });

  /** Answers the pending requests, then lets the answer land. */
  const answer = async (routes: RouteMetric[] | 'error', history?: RouteMetric[] | 'error', url = ROUTES) => {
    const r = backend.expectOne(url);
    if (routes === 'error') {
      r.flush('down', { status: 503, statusText: 'Unavailable' });
    } else {
      r.flush({ metrics: routes });
    }
    if (history !== undefined) {
      const h = backend.expectOne(HISTORY);
      if (history === 'error') {
        h.flush('down', { status: 503, statusText: 'Unavailable' });
      } else {
        h.flush({ metrics: history });
      }
    }
    await tick();
  };

  it('loads routes and history, then reports live', async () => {
    expect(live.status()).toBe('connecting');
    await answer([metric('a')], [metric('a')]);
    expect(live.routes()!.map(r => r.route)).toEqual(['a']);
    expect(live.history()!.length).toBe(1);
    expect(live.status()).toBe('live');
    expect(live.transport()).toBe('polling');
    expect(live.updatedAt()).not.toBeNull();
  });

  it('reports a first failure, and recovers on refresh', async () => {
    await answer('error', 'error');
    expect(live.routesFailed()).toBe(true);
    expect(live.historyFailed()).toBe(true);
    expect(live.status()).toBe('connecting');

    live.refresh();
    await tick();
    await answer([metric('a')], []);
    expect(live.routesFailed()).toBe(false);
    expect(live.status()).toBe('live');
  });

  it('keeps showing the last data when a refresh fails', async () => {
    await answer([metric('a')], []);

    live.refresh();
    await tick();
    await answer('error', []);
    expect(live.status()).toBe('stale');
    expect(live.routes()!.map(r => r.route)).toEqual(['a']);
    expect(live.routesFailed()).toBe(false);
  });

  it('asks for one datasource, showing the previous data until it arrives', async () => {
    await answer([metric('a'), metric('b')], []);

    live.setDatasource('anzo prod');
    expect(live.rescoping()).toBe(true);
    await tick();
    await answer([metric('b')], [], ROUTES + 'anzo%20prod');
    expect(live.rescoping()).toBe(false);
    expect(live.routes()!.map(r => r.route)).toEqual(['b']);
  });

  it('stops asking while paused, and resumes', async () => {
    await answer([metric('a')], []);

    live.pause();
    expect(live.status()).toBe('paused');
    await tick();
    backend.expectNone(ROUTES);

    live.resume();
    await tick();
    await answer([metric('a', 2)], []);
    expect(live.status()).toBe('live');
    expect(live.routes()![0].exchangesTotal).toBe(2);
  });
});

describe('MetricsLiveService over the event stream', () => {
  /** A stream response the test writes events into. */
  function stream() {
    let controller!: ReadableStreamDefaultController<Uint8Array>;
    const body = new ReadableStream<Uint8Array>({ start: c => { controller = c; } });
    return {
      response: new Response(body, { headers: { 'Content-Type': 'text/event-stream' } }),
      send: (data: object) => controller.enqueue(new TextEncoder().encode(`event:metrics\ndata:${JSON.stringify(data)}\n\n`)),
    };
  }
  const settle = () => new Promise(resolve => setTimeout(resolve, 20));
  let fetchFn: ReturnType<typeof vi.fn>;

  function create(withHistory = true): MetricsLiveService {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), MetricsLiveService,
        { provide: EVENT_STREAM_FETCH, useValue: fetchFn },
        { provide: METRICS_HISTORY, useValue: withHistory },
      ],
    });
    return TestBed.inject(MetricsLiveService);
  }

  afterEach(() => TestBed.resetTestingModule());

  it('shows pushed routes and history, and keeps the history when an event has none', async () => {
    const s = stream();
    fetchFn = vi.fn().mockResolvedValue(s.response);
    const live = create();
    await settle();
    expect(fetchFn.mock.calls[0][0]).toBe('/queryrest/api/events?topics=metrics');

    s.send({ routes: [metric('a')], history: [metric('a'), metric('a')] });
    await settle();
    expect(live.routes()!.length).toBe(1);
    expect(live.history()!.length).toBe(2);
    expect(live.status()).toBe('live');
    expect(live.transport()).toBe('stream');

    s.send({ routes: [metric('a', 5)] });
    await settle();
    expect(live.routes()![0].exchangesTotal).toBe(5);
    expect(live.history()!.length).toBe(2);
  });

  it('reconnects scoped to a datasource, showing the old data until the new arrives', async () => {
    const first = stream();
    const second = stream();
    fetchFn = vi.fn().mockImplementation((_url: string, init: RequestInit) => {
      const s = fetchFn.mock.calls.length === 1 ? first : second;
      return Promise.resolve(s.response);
    });
    const live = create();
    await settle();
    first.send({ routes: [metric('a'), metric('b')], history: [] });
    await settle();

    live.setDatasource('anzo prod');
    expect(live.rescoping()).toBe(true);
    await settle();
    expect(fetchFn.mock.calls[1][0]).toBe('/queryrest/api/events?topics=metrics&datasource=anzo+prod');
    second.send({ routes: [metric('b')], history: [] });
    await settle();
    expect(live.rescoping()).toBe(false);
    expect(live.routes()!.map(r => r.route)).toEqual(['b']);
  });

  it('connects once, already scoped, when the page picks a datasource right away', async () => {
    fetchFn = vi.fn().mockResolvedValue(stream().response);
    create().setDatasource('ds1');
    await settle();
    expect(fetchFn.mock.calls.map(c => c[0])).toEqual(['/queryrest/api/events?topics=metrics&datasource=ds1']);
  });

  it('asks for no history when the page shows none', async () => {
    fetchFn = vi.fn().mockResolvedValue(stream().response);
    create(false);
    await settle();
    expect(fetchFn.mock.calls[0][0]).toBe('/queryrest/api/events?topics=metrics&history=false');
  });
});

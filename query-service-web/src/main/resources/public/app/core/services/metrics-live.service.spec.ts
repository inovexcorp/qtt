import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { METRICS_POLL_MS, MetricsLiveService } from './metrics-live.service';
import { RouteMetric } from '../models/metric-data';

const ROUTES = '/queryrest/api/metrics/routes/';
const HISTORY = '/queryrest/api/metrics/route/persisted';

const metric = (route: string, total = 1): RouteMetric => ({
  route, exchangesTotal: total, exchangesCompleted: total, exchangesFailed: 0, exchangesInflight: 0,
  totalProcessingTime: total * 10, meanProcessingTime: 10, minProcessingTime: 5, maxProcessingTime: 20,
  uptime: '1m', state: 'Started', timeStamp: '2026-11-28T10:00:00',
});

/** Lets timer(0) fire. */
const tick = () => new Promise(resolve => setTimeout(resolve));

describe('MetricsLiveService', () => {
  let live: MetricsLiveService;
  let backend: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), MetricsLiveService,
        // Only the first fetch and explicit refreshes run during a test.
        { provide: METRICS_POLL_MS, useValue: 3_600_000 },
      ],
    });
    live = TestBed.inject(MetricsLiveService);
    backend = TestBed.inject(HttpTestingController);
    await tick();
  });

  afterEach(() => backend.verify());

  it('loads routes and history, then reports live', () => {
    expect(live.status()).toBe('connecting');
    backend.expectOne(ROUTES).flush({ metrics: [metric('a')] });
    backend.expectOne(HISTORY).flush({ metrics: [metric('a')] });
    expect(live.routes()!.map(r => r.route)).toEqual(['a']);
    expect(live.history()!.length).toBe(1);
    expect(live.status()).toBe('live');
    expect(live.updatedAt()).not.toBeNull();
  });

  it('reports a first failure, and recovers on refresh', async () => {
    backend.expectOne(ROUTES).flush('down', { status: 503, statusText: 'Unavailable' });
    backend.expectOne(HISTORY).flush('down', { status: 503, statusText: 'Unavailable' });
    expect(live.routesFailed()).toBe(true);
    expect(live.historyFailed()).toBe(true);
    expect(live.status()).toBe('connecting');

    live.refresh();
    await tick();
    backend.expectOne(ROUTES).flush({ metrics: [metric('a')] });
    backend.expectOne(HISTORY).flush({ metrics: [] });
    expect(live.routesFailed()).toBe(false);
    expect(live.status()).toBe('live');
  });

  it('keeps showing the last data when a refresh fails', async () => {
    backend.expectOne(ROUTES).flush({ metrics: [metric('a')] });
    backend.expectOne(HISTORY).flush({ metrics: [] });

    live.refresh();
    await tick();
    backend.expectOne(ROUTES).flush('down', { status: 500, statusText: 'Error' });
    backend.expectOne(HISTORY).flush({ metrics: [] });
    expect(live.status()).toBe('stale');
    expect(live.routes()!.map(r => r.route)).toEqual(['a']);
    expect(live.routesFailed()).toBe(false);
  });

  it('asks for one datasource, showing the previous data until it arrives', async () => {
    backend.expectOne(ROUTES).flush({ metrics: [metric('a'), metric('b')] });
    backend.expectOne(HISTORY).flush({ metrics: [] });

    live.setDatasource('anzo prod');
    expect(live.rescoping()).toBe(true);
    await tick();
    backend.expectOne(ROUTES + 'anzo%20prod').flush({ metrics: [metric('b')] });
    backend.expectOne(HISTORY).flush({ metrics: [] });
    expect(live.rescoping()).toBe(false);
    expect(live.routes()!.map(r => r.route)).toEqual(['b']);
  });

  it('stops asking while paused, and resumes', async () => {
    backend.expectOne(ROUTES).flush({ metrics: [metric('a')] });
    backend.expectOne(HISTORY).flush({ metrics: [] });

    live.pause();
    expect(live.status()).toBe('paused');
    await tick();
    backend.expectNone(ROUTES);

    live.resume();
    await tick();
    backend.expectOne(ROUTES).flush({ metrics: [metric('a', 2)] });
    backend.expectOne(HISTORY).flush({ metrics: [] });
    expect(live.status()).toBe('live');
    expect(live.routes()![0].exchangesTotal).toBe(2);
  });
});

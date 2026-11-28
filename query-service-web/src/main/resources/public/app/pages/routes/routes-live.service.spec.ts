import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Routes } from '../../core/models/routes-interfaces/routes';
import { RouteMetric } from '../../core/models/metric-data';
import { DatasourceLiveService } from '../../core/services/datasource-live.service';
import { MetricsLiveService } from '../../core/services/metrics-live.service';
import { SettingsService } from '../../core/services/settings.service';
import { of } from 'rxjs';
import { listIsBehind, liveSignature, RELOAD_DEBOUNCE_MS, RoutesLiveService } from './routes-live.service';

const stored = (id: string, status = 'Started') => ({ routeId: id, status } as Routes);
const live = (id: string, state = 'Started') => ({ route: id, state } as RouteMetric);

describe('listIsBehind', () => {
  it('ignores the first live state (the list was just loaded) and an unchanged one', () => {
    const metrics = [live('a')];
    expect(listIsBehind([stored('a')], metrics, null)).toBe(false);
    expect(listIsBehind([], metrics, liveSignature(metrics))).toBe(false);
  });

  it('notices a route started or stopped elsewhere', () => {
    const before = liveSignature([live('a', 'Started')]);
    expect(listIsBehind([stored('a', 'Started')], [live('a', 'Stopped')], before)).toBe(true);
  });

  it('does not reload when the change is one the list already shows (this page made it)', () => {
    const before = liveSignature([live('a', 'Started')]);
    expect(listIsBehind([stored('a', 'Stopped')], [live('a', 'Stopped')], before)).toBe(false);
  });

  it('notices a new route and a deleted one', () => {
    const before = liveSignature([live('a')]);
    expect(listIsBehind([stored('a')], [live('a'), live('b')], before)).toBe(true);
    expect(listIsBehind([stored('a')], [], before)).toBe(true);
  });

  it('does not chase routes that only exist live (test routes) or only in the list (not loaded)', () => {
    // "tmp" runs in Camel without a stored row; "broken" is stored but Camel never loaded it
    const metrics = [live('a'), live('tmp')];
    const before = liveSignature([live('a', 'Stopped'), live('tmp')]);
    // a changed, and the list already shows it; tmp is unchanged, so its standing difference doesn't count
    expect(listIsBehind([stored('a'), stored('broken')], metrics, before)).toBe(false);
  });

  it('notices a new test route once, but not on every event after', () => {
    const list = [stored('a')];
    const first = liveSignature([live('a')]);
    const withTmp = [live('a'), live('tmp')];
    expect(listIsBehind(list, withTmp, first)).toBe(true);
    // After the reload the list still lacks tmp; the next events carry the same state
    expect(listIsBehind(list, withTmp, liveSignature(withTmp))).toBe(false);
  });
});

describe('RoutesLiveService', () => {
  let metrics: ReturnType<typeof signal<RouteMetric[] | null>>;
  let http: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    metrics = signal<RouteMetric[] | null>(null);
    TestBed.configureTestingModule({
      providers: [
        RoutesLiveService, provideHttpClient(), provideHttpClientTesting(),
        { provide: MetricsLiveService, useValue: { routes: metrics, history: signal(null), status: signal('live'),
          updatedAt: signal(null), refresh: vi.fn() } },
        { provide: DatasourceLiveService, useValue: { datasources: signal(null), status: signal('live'), updatedAt: signal(null) } },
        { provide: SettingsService, useValue: { getSsoInfo: () => of({ queryAuthDefault: 'authenticated' }) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => vi.useRealTimers());

  const flushList = (list: Routes[]) => http.expectOne('/queryrest/api/routes/').flush(list);

  it('loads the routes and builds rows with the server default access', async () => {
    const service = TestBed.inject(RoutesLiveService);
    http.expectOne('/queryrest/api/routes/metadata').flush({ baseUrl: 'https://q/query' });
    flushList([{ ...stored('a'), routeParams: '', description: '', datasources: { dataSourceId: 'ds' } } as Routes]);
    await vi.advanceTimersByTimeAsync(0);
    expect(service.rows()!.map(r => [r.id, r.access])).toEqual([['a', 'authenticated']]);
    expect(service.baseUrl()).toBe('https://q/query');
  });

  it('reloads once, debounced, when a route is stopped elsewhere', async () => {
    TestBed.inject(RoutesLiveService);
    http.expectOne('/queryrest/api/routes/metadata').flush({ baseUrl: '' });
    flushList([stored('a'), stored('b')]);
    await vi.advanceTimersByTimeAsync(0);

    metrics.set([live('a'), live('b')]);
    TestBed.tick();
    metrics.set([live('a', 'Stopped'), live('b', 'Stopped')]); // a bulk stop elsewhere
    TestBed.tick();
    http.expectNone('/queryrest/api/routes/');
    await vi.advanceTimersByTimeAsync(RELOAD_DEBOUNCE_MS);
    flushList([stored('a', 'Stopped'), stored('b', 'Stopped')]);
    await vi.advanceTimersByTimeAsync(0);

    // Further events with the same state (uptime ticking) don't reload
    metrics.set([live('a', 'Stopped'), live('b', 'Stopped')]);
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(RELOAD_DEBOUNCE_MS * 2);
    http.expectNone('/queryrest/api/routes/');
  });

  it('runs one load at a time, and once more for a request made during one', async () => {
    const service = TestBed.inject(RoutesLiveService);
    http.expectOne('/queryrest/api/routes/metadata').flush({ baseUrl: '' });
    void service.load();
    void service.load();
    flushList([stored('a')]);
    await vi.advanceTimersByTimeAsync(0);
    flushList([stored('a'), stored('b')]);
    await vi.advanceTimersByTimeAsync(0);
    http.expectNone('/queryrest/api/routes/');
    expect(service.routes()!.length).toBe(2);
  });

  it('keeps the last list when a reload fails, and reports a first load that failed', async () => {
    const service = TestBed.inject(RoutesLiveService);
    http.expectOne('/queryrest/api/routes/metadata').flush({ baseUrl: '' });
    http.expectOne('/queryrest/api/routes/').flush('down', { status: 503, statusText: 'Unavailable' });
    await vi.advanceTimersByTimeAsync(0);
    expect(service.failed()).toBe(true);
    void service.load();
    flushList([stored('a')]);
    await vi.advanceTimersByTimeAsync(0);
    void service.load();
    http.expectOne('/queryrest/api/routes/').flush('down', { status: 503, statusText: 'Unavailable' });
    await vi.advanceTimersByTimeAsync(0);
    expect(service.failed()).toBe(false);
    expect(service.routes()!.length).toBe(1);
  });
});

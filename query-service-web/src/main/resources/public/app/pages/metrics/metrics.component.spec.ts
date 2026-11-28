import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { METRICS_POLL_MS } from '../../core/services/metrics-live.service';
import { EVENT_STREAM_FETCH } from '../../core/services/live-connection';
import { RouteMetric } from '../../core/models/metric-data';
import { MetricsComponent } from './metrics.component';

const ROUTES = '/queryrest/api/metrics/routes/';
const HISTORY = '/queryrest/api/metrics/route/persisted';
const DATASOURCES = '/queryrest/api/datasources/';

const metric = (route: string, total: number, failed: number, processing: number): RouteMetric => ({
  route, exchangesTotal: total, exchangesCompleted: total - failed, exchangesFailed: failed, exchangesInflight: 0,
  totalProcessingTime: processing, meanProcessingTime: total ? processing / total : -1, minProcessingTime: total ? 1 : 0,
  maxProcessingTime: total ? 50 : 0, uptime: '1m', state: 'Started', timeStamp: '',
});

const tick = () => new Promise(resolve => setTimeout(resolve));

describe('MetricsComponent', () => {
  let fixture: ComponentFixture<MetricsComponent>;
  let backend: HttpTestingController;
  const el = () => fixture.nativeElement as HTMLElement;
  const text = () => el().innerText;
  const tiles = () => [...el().querySelectorAll<HTMLElement>('.metrics-tile')];
  const tileValues = () => tiles().map(t => t.querySelector('.metrics-tile__value')?.textContent?.replace(/\s+/g, '') ?? null);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MetricsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations(),
        { provide: METRICS_POLL_MS, useValue: 3_600_000 },
        // No event stream (an older server): the page polls, which these tests answer.
        { provide: EVENT_STREAM_FETCH, useValue: () => Promise.resolve(new Response('', { status: 404 })) }],
    }).compileComponents();
    fixture = TestBed.createComponent(MetricsComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await tick();
    backend.expectOne(DATASOURCES).flush([{ dataSourceId: 'anzo' }]);
  });

  /** Answers the pending routes and history requests. */
  const respond = async (routes: RouteMetric[] | 'error', history: RouteMetric[] = []) => {
    const req = backend.expectOne(r => r.url.startsWith(ROUTES));
    if (routes === 'error') {
      req.flush('down', { status: 503, statusText: 'Unavailable' });
    } else {
      req.flush({ metrics: routes });
    }
    backend.match(HISTORY).forEach(h => h.flush({ metrics: history }));
    fixture.detectChanges();
    // Not whenStable(): the live page always has timers running (polling, the "updated 4s ago" clock).
    await tick();
    fixture.detectChanges();
  };

  it('shows skeletons while loading, never NaN', () => {
    expect(tiles().length).toBe(4);
    expect(tiles().every(t => t.querySelector('.metrics-tile__skeleton'))).toBe(true);
    expect(el().querySelectorAll('.metrics-skeleton').length).toBeGreaterThanOrEqual(3);
    expect(text()).toContain('Connecting');
    expect(text()).not.toContain('NaN');
  });

  it('shows the summary, charts and table once metrics arrive', async () => {
    await respond([metric('a', 3, 0, 30), metric('b', 1, 1, 10)]);
    expect(tileValues()).toEqual(['2', '10.0ms', 'a', '75.0%']);
    expect(text()).toContain('Live · updated just now');
    expect(el().querySelectorAll('qtt-exchanges-card canvas, qtt-latency-card canvas').length).toBe(2);
    expect(el().querySelectorAll('.metrics-table tbody tr').length).toBe(2);
    expect(text()).not.toContain('NaN');
  });

  // Regression: with no traffic yet the page showed NaN, then "0.00ms" and a 0% success rate.
  it('explains values that need an exchange instead of showing zeros', async () => {
    await respond([metric('a', 0, 0, 0), metric('b', 0, 0, 0)]);
    expect(tileValues()).toEqual(['2', '—', '—', '—']);
    expect(tiles().slice(1).every(t => t.innerText.includes('No exchanges yet'))).toBe(true);
    expect(text()).toContain('No exchanges yet');
    expect(el().querySelector('qtt-exchanges-card canvas')).toBeNull();
    // The table lists the routes, with dashes for times they don't have.
    expect(el().querySelector('.metrics-table tbody')!.textContent).toContain('—');
    expect(text()).not.toContain('NaN');
  });

  it('shows one error banner with Retry when metrics fail to load', async () => {
    await respond('error');
    expect(el().querySelectorAll('.metrics-banner').length).toBe(1);
    expect(tiles().every(t => t.innerText.includes('Unavailable'))).toBe(true);
    expect(el().querySelectorAll('.metrics-status button').length).toBe(0);

    el().querySelector<HTMLButtonElement>('.metrics-banner button')!.click();
    await tick();
    await respond([metric('a', 1, 0, 5)]);
    expect(el().querySelector('.metrics-banner')).toBeNull();
    expect(tileValues()[0]).toBe('1');
  });

  it('scopes everything to the selected routes', async () => {
    await respond([metric('a', 3, 0, 30), metric('b', 1, 1, 10)]);
    (fixture.componentInstance as unknown as { selectedRoutes: { set(v: string[]): void } }).selectedRoutes.set(['b']);
    fixture.detectChanges();
    // Not whenStable(): the live page always has timers running (polling, the "updated 4s ago" clock).
    await tick();
    fixture.detectChanges();
    // Changed values count to their new number.
    await new Promise(resolve => setTimeout(resolve, 700));
    expect(tileValues()[0]).toBe('1');
    expect(tileValues()[3]).toBe('0.0%');
    expect(el().querySelectorAll('.metrics-table tbody tr').length).toBe(1);
  });

  it('asks for one datasource when it is picked', async () => {
    await respond([metric('a', 3, 0, 30)]);
    (fixture.componentInstance as unknown as { selectDatasource(ds: string): void }).selectDatasource('anzo');
    fixture.detectChanges();
    expect(el().querySelector('.metrics-page--rescoping')).not.toBeNull();
    await tick();
    backend.expectOne(ROUTES + 'anzo').flush({ metrics: [metric('a', 3, 0, 30)] });
    backend.match(HISTORY).forEach(h => h.flush({ metrics: [] }));
    await tick();
    fixture.detectChanges();
    expect(el().querySelector('.metrics-page--rescoping')).toBeNull();
  });
});

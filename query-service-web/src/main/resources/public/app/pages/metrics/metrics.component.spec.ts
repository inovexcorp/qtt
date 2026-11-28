import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

import { MetricsComponent } from './metrics.component';

describe('MetricsComponent', () => {
  let component: MetricsComponent;
  let fixture: ComponentFixture<MetricsComponent>;
  let backend: HttpTestingController;

  const ROUTES = '/queryrest/api/metrics/routes/';
  const PERSISTED = '/queryrest/api/metrics/route/persisted';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MatAutocompleteModule, BrowserAnimationsModule, MetricsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MetricsComponent);
    component = fixture.componentInstance;
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const metric = (route: string, total: number, completed: number, processing: number): any => ({
    route, exchangesTotal: total, exchangesCompleted: completed, exchangesFailed: total - completed,
    exchangesInflight: 0, totalProcessingTime: processing, meanProcessingTime: total ? processing / total : -1,
    minProcessingTime: 0, maxProcessingTime: 0, uptime: '1m', state: 'Started', timeStamp: null
  });
  const text = () => (fixture.nativeElement as HTMLElement).innerText;
  const tiles = () => [...(fixture.nativeElement as HTMLElement).querySelectorAll('.summary-tile')] as HTMLElement[];

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows a spinner in every tile while route metrics load', () => {
    expect(component.routeState).toBe('loading');
    expect(tiles().length).toBe(4);
    expect(tiles().every(t => t.querySelector('mat-spinner'))).toBe(true);
    expect(text()).not.toContain('NaN');
    expect(component.chartMode(component.exchangeDisplay)).toBe('loading');
    expect(component.trendMode()).toBe('loading');
  });

  it('shows values once route metrics arrive', () => {
    backend.expectOne(ROUTES).flush({ metrics: [metric('a', 3, 3, 30), metric('b', 1, 0, 10)] });
    fixture.detectChanges();
    expect(component.routeState).toBe('ready');
    expect(tiles().some(t => t.querySelector('mat-spinner'))).toBe(false);
    expect(tiles().map(t => t.querySelector('.summary-tile__value')!.textContent!.trim()))
      .toEqual(['2', '10.00ms', 'a', '75%']);
    expect(component.chartMode(component.exchangeDisplay)).toBeNull();
  });

  // Regression: with no traffic the summary showed NaN while the cards animated, then "0.00ms" and a 0% success
  // rate, which read as "every request failed".
  it('shows a dash and the reason for values that need an exchange', () => {
    backend.expectOne(ROUTES).flush({ metrics: [metric('a', 0, 0, 0), metric('b', 0, 0, 0)] });
    fixture.detectChanges();
    expect(component.summary).toEqual(
      { endpoints: 2, totalExchanges: 0, avgProcessingMs: null, busiestRoute: null, successRate: null });
    expect(tiles().map(t => t.querySelector('.summary-tile__value')!.textContent!.trim()))
      .toEqual(['2', '—', '—', '—']);
    expect(tiles().slice(1).every(t => t.innerText.includes('No exchanges yet'))).toBe(true);
    expect(text()).not.toContain('NaN');
    expect(component.chartMode(component.exchangeDisplay)).toBe('empty');
    expect(component.emptyChartTitle()).toBe('No exchanges recorded yet');
    expect(component.latencyData).toEqual([]);
  });

  it('says there are no routes when there are none', () => {
    backend.expectOne(ROUTES).flush({ metrics: [] });
    fixture.detectChanges();
    expect(component.summary!.endpoints).toBe(0);
    expect(component.missingReason()).toBe('No routes yet');
    expect(component.emptyChartTitle()).toBe('No routes yet');
    expect(component.tableMode()).toBe('empty');
  });

  it('shows an error with Retry when route metrics fail, and retries', () => {
    backend.expectOne(ROUTES).flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(component.routeState).toBe('error');
    expect(component.chartMode(component.exchangeDisplay)).toBe('error');
    expect(component.missingReason()).toBe('Unavailable');

    component.retryCharts();
    expect(component.routeState).toBe('loading');
    backend.expectOne(ROUTES).flush({ metrics: [metric('a', 1, 1, 5)] });
    expect(component.routeState).toBe('ready');
  });

  it('keeps the summary loading when a datasource is picked before it arrives', () => {
    component.fetchDatasourceSpecificMetrics('ds1');
    backend.expectOne(ROUTES + 'ds1').flush({ metrics: [metric('x', 2, 2, 4)] });
    backend.expectOne(ROUTES).flush({ metrics: [metric('a', 1, 1, 5), metric('b', 1, 1, 5)] });
    expect(component.summary!.endpoints).toBe(2);
    expect(component.exchangeData.map(d => d.name)).toEqual(['x']);
  });

  it('shows routes with no exchanges in the table without placeholder timings', () => {
    backend.expectOne(ROUTES).flush({ metrics: [metric('idle', 0, 0, 0)] });
    fixture.detectChanges();
    expect(component.dataSource.data[0].hasExchanges).toBe(false);
  });

  it('leaves timings of snapshots with no exchanges out of the trends', () => {
    const snap = (route: string, total: number, mean: number, time: string): any =>
      ({ ...metric(route, total, total, 0), meanProcessingTime: mean, timeStamp: `2026-11-28T${time}:00.0` });
    const response = { metrics: [snap('a', 0, -1, '10:00'), snap('a', 2, 15, '10:05'), snap('b', 0, -1, '10:00')] };

    expect(component.buildTrendSeries(response, 'meanProcessingTime'))
      .toEqual([{ name: 'a', series: [{ name: '10:05', value: 15 }] }]);
    // A count of zero is a real value.
    expect(component.buildTrendSeries(response, 'exchangesTotal').map(s => s.series.length)).toEqual([2, 1]);
  });

  it('shows all routes again when the route selection is cleared', () => {
    backend.expectOne(ROUTES).flush({ metrics: [metric('a', 1, 1, 5), metric('b', 1, 1, 5)] });
    component.filterByRoutes({ value: ['a'] } as any);
    expect(component.exchangeDisplay.map(d => d.name)).toEqual(['a']);
    component.filterByRoutes({ value: [] } as any);
    expect(component.exchangeDisplay.map(d => d.name)).toEqual(['a', 'b']);
  });

  it('shows an error for trends when history fails to load', () => {
    backend.expectOne(PERSISTED).flush('boom', { status: 500, statusText: 'Server Error' });
    expect(component.trendMode()).toBe('error');
    component.getPersistedMetrics('exchangesTotal');
    expect(component.trendMode()).toBe('loading');
    backend.expectOne(PERSISTED).flush({ metrics: [] });
    expect(component.trendMode()).toBe('empty');
  });
});

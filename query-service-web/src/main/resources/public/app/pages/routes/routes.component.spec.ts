import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Component, computed, signal } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Routes } from '../../core/models/routes-interfaces/routes';
import { DatasourceHealth, DatasourceStatus } from '../../core/models/datasource-health';
import { AuthService } from '../../core/services/auth.service';
import { DatasourceLiveService } from '../../core/services/datasource-live.service';
import { MetricsLiveService } from '../../core/services/metrics-live.service';
import { LiveHub } from '../../core/services/live-hub';
import { RoutesComponent, readPrefs } from './routes.component';
import { RoutesLiveService } from './routes-live.service';
import { RouteActionsService } from './route-actions.service';
import { toRows } from './routes-derive';

const route = (id: string, extra: Partial<Routes> = {}): Routes => ({
  routeId: id, routeParams: 'httpMethodRestrict=GET', templateContent: '', description: `About ${id}`, graphMartUri: '',
  status: 'Started', accessPolicy: 'authenticated',
  datasources: { dataSourceId: 'ds1', timeOutSeconds: '', maxQueryHeaderLength: '', username: '', url: '',
    validateCertificate: true, status: 'UP' },
  ...extra,
});
const onDs = (id: string) => ({ ...route('x').datasources, dataSourceId: id });
const health = (id: string, status: DatasourceStatus): DatasourceHealth => ({ dataSourceId: id, url: '', status,
  lastHealthCheck: null, lastHealthError: null, consecutiveFailures: 0, routeCount: 0, recentChecks: [] });

@Component({ template: '' })
class Blank {}

describe('readPrefs', () => {
  it('reads a stored view, and falls back for anything missing or broken', () => {
    expect(readPrefs({ getItem: () => '{"sort":"busiest","grouped":true,"collapsed":["ds1",3]}' }))
      .toEqual({ sort: 'busiest', grouped: true, collapsed: ['ds1'] });
    expect(readPrefs({ getItem: () => '{"sort":"nonsense"}' }).sort).toBe('name');
    expect(readPrefs({ getItem: () => 'not json' })).toEqual({ sort: 'name', grouped: false, collapsed: [] });
    expect(readPrefs(null).grouped).toBe(false);
  });
});

describe('RoutesComponent', () => {
  let fixture: ComponentFixture<RoutesComponent>;
  let roles: string[];
  const routes = signal<Routes[] | null>(null);
  const datasources = signal<DatasourceHealth[] | null>(null);
  const live = {
    routes,
    rows: computed(() => routes() === null ? null : toRows({ routes: routes()!, datasources: datasources(), metrics: null,
      history: null, serverDefaultAccess: 'open' })),
    failed: signal(false),
    status: signal('live'),
    updatedAt: signal<number | null>(null),
    baseUrl: signal('https://qtt.example/query'),
    metrics: { transport: signal('stream'), pause: vi.fn(), resume: vi.fn() },
    patch: vi.fn(), load: vi.fn(() => Promise.resolve()), refresh: vi.fn(),
  };
  const actions = {
    start: vi.fn(async (targets: { id: string }[]) => ({ done: targets.map(t => t.id), failed: [], skipped: [] })),
    stop: vi.fn(async (targets: { id: string }[]) => ({ done: targets.map(t => t.id), failed: [], skipped: [] })),
    delete: vi.fn(async () => null),
    clone: vi.fn(async () => null),
    copy: vi.fn(),
  };

  async function create(userRoles = ['qtt-editor']) {
    roles = userRoles;
    await TestBed.configureTestingModule({
      imports: [RoutesComponent],
      providers: [
        provideRouter([{ path: 'routes', component: Blank }]),
        { provide: AuthService, useValue: { user$: new BehaviorSubject({}), hasRole: (r: string) => roles.includes('qtt-' + r) } },
        { provide: RouteActionsService, useValue: actions },
      ],
    }).overrideComponent(RoutesComponent, {
      set: { providers: [
        { provide: RoutesLiveService, useValue: live },
        { provide: LiveHub, useValue: {} }, { provide: DatasourceLiveService, useValue: {} },
        { provide: MetricsLiveService, useValue: {} },
      ] },
    }).compileComponents();
    await TestBed.inject(Router).navigateByUrl('/routes');
    fixture = TestBed.createComponent(RoutesComponent);
    fixture.detectChanges();
  }

  beforeEach(() => {
    routes.set(null);
    datasources.set(null);
    live.failed.set(false);
    vi.clearAllMocks();
    try { localStorage.removeItem('qtt.routes.view'); } catch { /* none */ }
  });

  const el = () => fixture.nativeElement as HTMLElement;
  const rowEls = () => [...el().querySelectorAll('.rt-row:not(.rt-row--skeleton)')] as HTMLElement[];
  const rowIds = () => rowEls().map(r => r.dataset['routeId']);
  /** A button by its words (its icon's ligature text left out). */
  const button = (text: string, scope: ParentNode = el()) => [...scope.querySelectorAll('button')]
    .find(b => b.textContent!.replace(/\s+/g, ' ').replace(/^(stop|play_arrow|delete|add|close) /, '').trim()
      .startsWith(text)) as HTMLButtonElement | undefined;
  const settle = () => new Promise(resolve => setTimeout(resolve));

  it('shows skeleton rows until the routes load', async () => {
    await create();
    expect(el().querySelectorAll('.rt-row--skeleton').length).toBe(5);
  });

  it('shows a row per route with its state, datasource and access, and the headline', async () => {
    await create();
    routes.set([route('people'), route('assets', { datasources: onDs('staging'), accessPolicy: 'open' })]);
    datasources.set([health('ds1', 'UP'), health('staging', 'DOWN')]);
    fixture.detectChanges();

    expect(rowIds()).toEqual(['assets', 'people']);
    expect(el().textContent).toContain('2 routes · 1 running · 1 blocked by its datasource');
    const assets = rowEls()[0];
    expect(assets.dataset['tone']).toBe('bad');
    expect(assets.textContent).toContain('Blocked: datasource staging is down');
    expect(assets.querySelector('.rt-access')!.textContent!.trim()).toBe('public');
    expect(assets.querySelector('a.rt-row__link')!.getAttribute('href')).toBe('/routes/config-route/assets');
    expect(assets.querySelector('a.rt-ds')!.getAttribute('href')).toBe('/datasources/config-datasource/staging');
  });

  it('filters by state and search, and explains when nothing matches', async () => {
    await create();
    routes.set([route('alpha'), route('beta', { status: 'Stopped', stopReason: 'MANUAL' }), route('gamma')]);
    fixture.detectChanges();

    (fixture.componentInstance as any).setState('stopped');
    fixture.detectChanges();
    expect(rowIds()).toEqual(['beta']);

    (fixture.componentInstance as any).setFilter({ state: 'all', term: 'gam' });
    fixture.detectChanges();
    expect(rowIds()).toEqual(['gamma']);

    (fixture.componentInstance as any).setFilter({ term: 'nothing' });
    fixture.detectChanges();
    expect(el().textContent).toContain('No routes match');
    button('Show all routes')!.click();
    fixture.detectChanges();
    expect(rowIds().length).toBe(3);
  });

  it('starts and stops a route from its row, and shows the result at once', async () => {
    await create();
    routes.set([route('alpha'), route('beta', { status: 'Stopped', stopReason: 'MANUAL' })]);
    fixture.detectChanges();

    button('Stop', rowEls()[0])!.click();
    await settle();
    expect(actions.stop).toHaveBeenCalledWith([{ id: 'alpha', startBlocked: null, perMinute: null }]);
    expect(live.patch).toHaveBeenCalledWith('alpha', expect.objectContaining({ status: 'Stopped', stopReason: 'MANUAL' }));

    button('Start', rowEls()[1])!.click();
    await settle();
    expect(actions.start).toHaveBeenCalledWith([{ id: 'beta', startBlocked: null, perMinute: null }]);
    expect(live.patch).toHaveBeenCalledWith('beta', { status: 'Started', stopReason: null, stoppedAt: null });
  });

  it("can't start a route whose datasource is down, and says why", async () => {
    await create();
    routes.set([route('beta', { status: 'Stopped', datasources: onDs('staging') })]);
    datasources.set([health('staging', 'DOWN')]);
    fixture.detectChanges();
    expect(button('Start', rowEls()[0])!.disabled).toBe(true);
  });

  it('offers to restart the routes the health check stopped once their datasource is back', async () => {
    await create();
    routes.set([
      route('a', { status: 'Stopped', stopReason: 'HEALTH_CHECK', stoppedAt: Date.now() - 3600000 }),
      route('b', { status: 'Stopped', stopReason: 'HEALTH_CHECK' }),
      route('c'),
    ]);
    datasources.set([health('ds1', 'UP')]);
    fixture.detectChanges();

    const banner = el().querySelector('.rt-recovery') as HTMLElement;
    expect(banner.textContent).toContain('2 routes were stopped while ds1 was down');
    expect(banner.textContent).toContain('1 h ago');

    button('Review', banner)!.click();
    fixture.detectChanges();
    expect(rowIds()).toEqual(['a', 'b']);
    expect(el().textContent).toContain('Showing routes the health check stopped');

    button('Start them', banner)!.click();
    await settle();
    expect(actions.start.mock.calls[0][0].map((t: { id: string }) => t.id)).toEqual(['a', 'b']);
  });

  it('selects routes for a bulk action, and forgets the ones that are gone', async () => {
    await create();
    routes.set([route('a'), route('b'), route('c', { status: 'Stopped' })]);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;

    component.toggleSelected('a');
    component.toggleSelected('c');
    fixture.detectChanges();
    const bar = el().querySelector('.rt-bulk') as HTMLElement;
    expect(bar.textContent).toContain('2 selected');

    button('Stop', bar)!.click();
    await settle();
    expect(actions.stop.mock.calls[0][0].map((t: { id: string }) => t.id)).toEqual(['a']); // only the running one

    routes.set([route('a'), route('b')]); // c deleted elsewhere
    fixture.detectChanges();
    expect(component.selected().has('c')).toBe(false);
  });

  it("doesn't offer a bulk start when no selected route can start", async () => {
    await create();
    routes.set([route('a', { status: 'Stopped', datasources: onDs('staging') }), route('b', { status: 'Stopped' })]);
    datasources.set([health('ds1', 'UP'), health('staging', 'DOWN')]);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;

    component.toggleSelected('a');
    fixture.detectChanges();
    expect(button('Start', el().querySelector('.rt-bulk')!)!.disabled).toBe(true);

    component.toggleSelected('b');
    fixture.detectChanges();
    const start = button('Start', el().querySelector('.rt-bulk')!)!;
    expect(start.disabled).toBe(false);
    expect(start.textContent).toContain('1'); // "Start 1": one of the two can start
  });

  it('shows dashes, not zero traffic, for a stopped route', async () => {
    await create();
    routes.set([route('a', { status: 'Stopped' })]);
    fixture.detectChanges();
    expect(rowEls()[0].querySelector('.rt-figures dd')!.textContent).toBe('—');
    expect(rowEls()[0].querySelector('qtt-sparkline')!.classList).toContain('is-empty');
  });

  it('groups by datasource, troubled ones first, and remembers the choice', async () => {
    await create();
    routes.set([route('a'), route('b', { datasources: onDs('staging') })]);
    datasources.set([health('ds1', 'UP'), health('staging', 'DOWN')]);
    fixture.detectChanges();

    (fixture.componentInstance as any).setGrouped(true);
    fixture.detectChanges();
    const names = [...el().querySelectorAll('.rt-group__name')].map(n => n.textContent);
    expect(names).toEqual(['staging', 'ds1']);
    expect(JSON.parse(localStorage.getItem('qtt.routes.view')!).grouped).toBe(true);
  });

  it('gives viewers no checkboxes or start/stop, but keeps copy and open', async () => {
    await create(['qtt-viewer']);
    routes.set([route('a')]);
    fixture.detectChanges();
    expect(el().querySelector('mat-checkbox')).toBeNull();
    expect(button('Stop', rowEls()[0])).toBeUndefined();
    expect(rowEls()[0].querySelector('[aria-label^="Copy the endpoint URL"]')).not.toBeNull();
  });

  it('copies the endpoint URL from the row', async () => {
    await create();
    routes.set([route('a')]);
    fixture.detectChanges();
    (rowEls()[0].querySelector('[aria-label^="Copy the endpoint URL"]') as HTMLButtonElement).click();
    expect(actions.copy).toHaveBeenCalledWith('https://qtt.example/query/a');
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CUSTOM_ELEMENTS_SCHEMA, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject, of } from 'rxjs';
import { Routes } from '../../core/models/routes-interfaces/routes';
import { AuthService } from '../../core/services/auth.service';
import { GraphmartsService } from '../../core/services/graphmarts.service';
import { LayersService } from '../../core/services/layers.service';
import { SparqiService } from '../../core/services/sparqi.service';
import { SettingsService } from '../../core/services/settings.service';
import { RouteActionsService } from '../routes/route-actions.service';
import { RouteTemplateEditorComponent } from '../routes/form/route-template-editor.component';
import { RouteTestComponent } from './route-test.component';
import { SparqiChatComponent } from './sparqi-chat/sparqi-chat.component';
import { ConfigRouteComponent } from './config-route.component';
import { BreadcrumbService } from 'xng-breadcrumb';

const GM = 'http://gm/prod';
const stored = (extra: Partial<Routes> = {}): Routes => ({
  routeId: 'people', routeParams: 'httpMethodRestrict=GET', templateContent: '', description: 'Find people',
  graphMartUri: GM, status: 'Started', cacheEnabled: false, cacheKeyStrategy: 'QUERY_HASH', accessPolicy: null,
  datasources: { dataSourceId: 'prod', timeOutSeconds: '', maxQueryHeaderLength: '', username: '', url: '', validateCertificate: true, status: 'UP' },
  ...extra,
});

describe('ConfigRouteComponent', () => {
  let fixture: ComponentFixture<ConfigRouteComponent>;
  let http: HttpTestingController;
  let router: Router;
  const params = new BehaviorSubject(convertToParamMap({ routeId: 'people' }));
  const query = new BehaviorSubject(convertToParamMap({}));
  const actions = {
    start: vi.fn(async () => ({ done: ['people'], failed: [], skipped: [] })),
    stop: vi.fn(async () => ({ done: ['people'], failed: [], skipped: [] })),
    delete: vi.fn(async () => ({ done: ['people'], failed: [], skipped: [] })),
    clone: vi.fn(async () => 'peopleClone'),
    copy: vi.fn(),
  };
  const snack = { open: vi.fn() };
  const breadcrumbs = { set: vi.fn() };

  async function create(route: Routes | null = stored(), tab?: string) {
    params.next(convertToParamMap({ routeId: 'people' }));
    query.next(convertToParamMap(tab ? { tab } : {}));
    await TestBed.configureTestingModule({
      imports: [ConfigRouteComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: params, queryParamMap: query } },
        { provide: RouteActionsService, useValue: actions },
        { provide: MatSnackBar, useValue: snack },
        { provide: AuthService, useValue: { user$: of({}), hasRole: () => true } },
        { provide: SparqiService, useValue: { checkHealth: () => of({ status: 'disabled' }) } },
        { provide: SettingsService, useValue: { getSsoInfo: () => of({ queryAuthDefault: 'open' }) } },
        { provide: GraphmartsService, useValue: { getGraphMarts: () => of([{ iri: GM, title: 'Production', active: 'true' }]) } },
        { provide: LayersService, useValue: { getGraphmartLayers: () => of([]), getRouteLayers: () => of([]) } },
        { provide: BreadcrumbService, useValue: breadcrumbs },
      ],
    })
      // Monaco-based children have their own tests
      .overrideComponent(ConfigRouteComponent, {
        remove: { imports: [RouteTemplateEditorComponent, RouteTestComponent, SparqiChatComponent] },
        add: { schemas: [CUSTOM_ELEMENTS_SCHEMA] },
      })
      .compileComponents();
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(ConfigRouteComponent);
    fixture.detectChanges();
    http.match('/queryrest/api/routes/cache/info').forEach(r => r.flush({ available: false }));
    const get = http.expectOne('/queryrest/api/routes/people');
    if (route) {
      get.flush(route);
    } else {
      get.flush('No route with specified ID found: people', { status: 404, statusText: 'Not Found' });
    }
    http.match('/queryrest/api/routes/templateContent/people').forEach(r => r.flush('SELECT 1'));
    await settle();
  }

  const settle = async () => {
    for (let i = 0; i < 3; i++) {
      await new Promise(r => setTimeout(r));
      fixture.detectChanges();
    }
  };
  const page = () => fixture.componentInstance as any;
  const text = () => (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  const button = (label: string) => [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')]
    .find(b => b.textContent!.replace(/\s+/g, ' ').trim().endsWith(label)) as HTMLButtonElement | undefined;

  beforeEach(() => vi.clearAllMocks());

  it('loads the route with GET /routes/{id} and has nothing unsaved', async () => {
    await create();
    expect(text()).toContain('people');
    expect(text()).toContain('Find people');
    expect(text()).toContain('All changes saved');
    expect(page().hasUnsavedChanges()).toBe(false);
    expect(page().draft.snapshot().template).toBe('SELECT 1');
  });

  it("labels the toolbar breadcrumb with the route's name", async () => {
    await create();
    expect(breadcrumbs.set).toHaveBeenCalledWith('@route', 'people');
  });

  it("says when there's no such route", async () => {
    await create(null);
    expect(text()).toContain("There's no route named people");
  });

  it('marks unsaved edits, saves them with PUT, and is clean again', async () => {
    await create();
    page().draft.form.controls.description.setValue('Find people by name');
    fixture.detectChanges();
    expect(text()).toContain('Unsaved: description');
    expect(page().hasUnsavedChanges()).toBe(true);

    const saving = page().save();
    await settle();
    const put = http.expectOne(r => r.method === 'PUT' && r.url === '/queryrest/api/routes/people');
    expect(put.request.params.get('description')).toBe('Find people by name');
    expect(put.request.params.get('graphMartUri')).toBe(GM);
    put.flush({});
    await settle();
    http.expectOne('/queryrest/api/routes/people').flush(stored({ description: 'Find people by name' }));
    await saving;
    fixture.detectChanges();
    expect(page().hasUnsavedChanges()).toBe(false);
    expect(text()).toContain('All changes saved');
    expect(snack.open).toHaveBeenCalledWith('Saved people.', 'Close', expect.anything());
  });

  it('keeps the edits and says why when saving fails', async () => {
    await create();
    page().draft.form.controls.template.setValue('<#if');
    const saving = page().save();
    await settle();
    http.expectOne(r => r.method === 'PUT').flush({ message: 'Template line 1: unclosed #if' }, { status: 400, statusText: 'Bad Request' });
    await saving;
    fixture.detectChanges();
    expect(text()).toContain('Not saved. Template line 1: unclosed #if');
    expect(page().hasUnsavedChanges()).toBe(true);
  });

  it("won't save with a required field empty, and says which", async () => {
    await create();
    page().draft.form.controls.description.setValue('');
    await page().save();
    fixture.detectChanges();
    http.expectNone(r => r.method === 'PUT');
    expect(text()).toContain("Can't save yet. Describe the route");
  });

  it('discards edits back to the saved route', async () => {
    await create();
    page().draft.form.controls.description.setValue('changed');
    fixture.detectChanges();
    button('Discard')!.click();
    fixture.detectChanges();
    expect(page().draft.form.controls.description.value).toBe('Find people');
    expect(page().hasUnsavedChanges()).toBe(false);
  });

  it('opens the tab named in the URL', async () => {
    await create(stored(), 'template');
    expect(page().tab()).toBe('template');
    const pane = fixture.nativeElement.querySelector('#cr-pane-template') as HTMLElement;
    expect(pane.hidden).toBe(false);
    expect((fixture.nativeElement.querySelector('#cr-pane-overview') as HTMLElement).hidden).toBe(true);
  });

  it('stops through the shared actions (which ask first) and shows it at once', async () => {
    await create();
    button('Stop')!.click();
    await settle();
    expect(actions.stop).toHaveBeenCalledWith([{ id: 'people', startBlocked: null, perMinute: null }]);
    expect(page().started()).toBe(false);
    expect(button('Start')).toBeDefined();
  });

  it('says why a route is stopped, and blocks starting while its datasource is down', async () => {
    await create(stored({ status: 'Stopped', stopReason: 'HEALTH_CHECK', stoppedAt: Date.now() - 7200000,
      datasources: { ...stored().datasources, status: 'DOWN' } }));
    expect(text()).toContain('Stopped');
    expect(text()).toContain('by the health check · 2 h ago');
    expect(text()).toContain('Datasource prod is down.');
    expect(button('Start')!.disabled).toBe(true);
  });

  it('leaves for the list after deleting, without asking about unsaved edits', async () => {
    await create();
    page().draft.form.controls.description.setValue('changed');
    await page().remove();
    expect(router.navigate).toHaveBeenCalledWith(['/routes']);
    expect(page().hasUnsavedChanges()).toBe(false);
  });

  it('opens the copy after cloning', async () => {
    await create();
    await page().clone();
    expect(router.navigate).toHaveBeenCalledWith(['/routes', 'config-route', 'peopleClone']);
  });
});

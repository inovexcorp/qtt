import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CUSTOM_ELEMENTS_SCHEMA, signal } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { AddRouteComponent, cacheOf } from './add-route.component';
import { RouteTemplateEditorComponent } from '../routes/form/route-template-editor.component';
import { RoutesLiveService } from '../routes/routes-live.service';
import { GraphmartsService } from '../../core/services/graphmarts.service';
import { LayersService } from '../../core/services/layers.service';
import { SettingsService } from '../../core/services/settings.service';
import { DatasourceLiveService } from '../../core/services/datasource-live.service';

describe('cacheOf', () => {
  it('reads the nested info, and says "none" when the server has no cache', () => {
    expect(cacheOf({ available: true, info: { enabled: true } as never })).toEqual({ enabled: true });
    expect(cacheOf({ available: false })).toBeNull();
    expect(cacheOf({ info: { enabled: false } as never })).toBeNull();
  });
});

describe('AddRouteComponent', () => {
  let fixture: ComponentFixture<AddRouteComponent>;
  let http: HttpTestingController;
  let router: Router;
  const snack = { open: vi.fn() };
  const live = {
    routes: signal([{ routeId: 'taken' }]), baseUrl: signal('https://q.example/query'),
    serverDefaultAccess: signal<'open' | 'authenticated'>('authenticated'), load: vi.fn(),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddRouteComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: MatSnackBar, useValue: snack },
        { provide: RoutesLiveService, useValue: live },
        { provide: DatasourceLiveService, useValue: { datasources: signal([{ dataSourceId: 'prod', status: 'UP' }]) } },
        { provide: GraphmartsService, useValue: { getGraphMarts: () => of([{ iri: 'http://gm', title: 'Production', active: 'true' }]) } },
        { provide: LayersService, useValue: { getGraphmartLayers: () => of([]) } },
        { provide: SettingsService, useValue: { getSsoInfo: () => of({ queryAuthDefault: 'authenticated' }) } },
      ],
    })
      // The template editor is Monaco: left out here (it has its own tests)
      .overrideComponent(AddRouteComponent, { remove: { imports: [RouteTemplateEditorComponent] }, add: { schemas: [CUSTOM_ELEMENTS_SCHEMA] } })
      .compileComponents();
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(AddRouteComponent);
    fixture.detectChanges();
    http.expectOne('/queryrest/api/routes/cache/info').flush({ available: true, info: { enabled: true, connected: true, defaultTtlSeconds: 3600 } });
    snack.open.mockClear();
  });

  const page = () => fixture.componentInstance as any;
  const text = () => (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  const settle = async () => { await new Promise(r => setTimeout(r)); fixture.detectChanges(); };
  const fill = () => {
    const c = page().draft.form.controls;
    c.routeId.setValue('people-search');
    c.description.setValue('People');
    c.datasourceId.setValue('prod');
    c.graphmart.setValue('http://gm');
    c.template.setValue('SELECT * WHERE { ?s ?p ?o }');
  };

  it('has nothing unsaved until something is entered', () => {
    expect(page().hasUnsavedChanges()).toBe(false);
    page().draft.form.controls.description.setValue('x');
    expect(page().hasUnsavedChanges()).toBe(true);
  });

  it('says what is missing instead of creating', async () => {
    await page().create();
    fixture.detectChanges();
    expect(text()).toContain('Almost there. Name the route · Describe the route · Pick a datasource · Pick a graphmart');
    http.expectNone(r => r.method === 'POST');
  });

  it('refuses a name already taken', async () => {
    fill();
    page().draft.form.controls.routeId.setValue('taken');
    await page().create();
    fixture.detectChanges();
    expect(text()).toContain('A route named taken already exists');
  });

  it('previews the endpoint as the name is typed', () => {
    page().draft.form.controls.routeId.setValue('people-search');
    fixture.detectChanges();
    expect(text()).toContain('https://q.example/query/people-search');
  });

  it('creates the route with every setting explicit, then opens its page', async () => {
    fill();
    const done = page().create();
    await settle();
    const req = http.expectOne(r => r.method === 'POST' && r.url === '/queryrest/api/routes/people-search');
    expect(req.request.params.get('graphMartUri')).toBe('http://gm');
    expect(req.request.params.get('routeParams')).toBe('httpMethodRestrict=GET');
    expect(req.request.params.get('cacheEnabled')).toBe('false');
    expect(req.request.params.get('accessPolicy')).toBe('default');
    req.flush(null, { status: 201, statusText: 'Created' });
    await done;
    expect(router.navigate).toHaveBeenCalledWith(['/routes', 'config-route', 'people-search']);
    expect(page().hasUnsavedChanges()).toBe(false); // leaving after creating doesn't ask
    expect(live.load).toHaveBeenCalled();
  });

  it('says a route without a template is created stopped', async () => {
    fill();
    page().draft.form.controls.template.setValue('');
    const done = page().create();
    await settle();
    http.expectOne(r => r.method === 'POST').flush(null, { status: 201, statusText: 'Created' });
    await done;
    expect(snack.open.mock.calls[0][0]).toContain('stays stopped until it has a template');
  });

  it('keeps the input and says why when the server refuses', async () => {
    fill();
    const done = page().create();
    await settle();
    http.expectOne(r => r.method === 'POST').flush({ message: 'Template line 3: unexpected end' }, { status: 400, statusText: 'Bad Request' });
    await done;
    fixture.detectChanges();
    expect(text()).toContain("The route wasn't created. Template line 3: unexpected end");
    expect(page().hasUnsavedChanges()).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });
});

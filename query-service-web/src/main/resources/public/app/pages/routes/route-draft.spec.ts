import { TestBed } from '@angular/core/testing';
import { Observable, of, Subject, throwError } from 'rxjs';
import { Routes } from '../../core/models/routes-interfaces/routes';
import { GraphmartsService } from '../../core/services/graphmarts.service';
import { LayersService } from '../../core/services/layers.service';
import { RouteDraft, routeIdValidator } from './route-draft';
import { FormControl } from '@angular/forms';
import { routeParams } from './routes.service';

const GM = 'http://gm/prod';
const GM2 = 'http://gm/other';
const saved: Routes = {
  routeId: 'people', routeParams: 'httpMethodRestrict=POST,GET', templateContent: '', description: 'People',
  graphMartUri: GM, status: 'Started', cacheEnabled: true, cacheTtlSeconds: null as unknown as number,
  cacheKeyStrategy: 'QUERY_HASH', accessPolicy: null, requiredRoles: null,
  datasources: { dataSourceId: 'prod', timeOutSeconds: '', maxQueryHeaderLength: '', username: '', url: '', validateCertificate: true },
};
const settle = () => new Promise(resolve => setTimeout(resolve));

describe('routeIdValidator', () => {
  const check = (value: string, taken: string[] = []) => routeIdValidator(() => taken)(new FormControl(value));
  it('accepts what the server accepts, and refuses taken ids', () => {
    expect(check('people-search_v2.1')).toBeNull();
    expect(check('has space')).toEqual({ routeId: expect.any(String) });
    expect(check('a/b')).toEqual({ routeId: expect.any(String) });
    expect(check('..')).toEqual({ routeId: expect.any(String) });
    expect(check('x'.repeat(129))).toEqual({ routeId: expect.any(String) });
    expect(check('people', ['people'])).toEqual({ taken: 'A route named people already exists' });
    expect(check('')).toBeNull(); // "required" covers it
  });
});

describe('RouteDraft', () => {
  let graphmarts: (ds: string) => Observable<unknown>;
  let layers: (ds: string, gm: string) => Observable<unknown>;

  function create(): RouteDraft {
    TestBed.configureTestingModule({
      providers: [
        RouteDraft,
        { provide: GraphmartsService, useValue: { getGraphMarts: (ds: string) => graphmarts(ds) } },
        { provide: LayersService, useValue: { getGraphmartLayers: (ds: string, gm: string) => layers(ds, gm) } },
      ],
    });
    return TestBed.inject(RouteDraft);
  }

  beforeEach(() => {
    graphmarts = () => of([{ iri: GM, title: 'Production', active: 'true' }, { iri: GM2, title: 'Other', active: 'false' }]);
    layers = (_, gm) => of(gm === GM ? [{ iri: 'L1', title: 'Layer one', active: 'true' }, { iri: 'L2', title: 'Layer two', active: 'true' }] : []);
  });

  it('has no unsaved edits once a route is loaded, even while graphmarts and layers arrive', async () => {
    const draft = create();
    draft.load(saved, 'SELECT 1', ['L1']);
    await settle();
    expect(draft.graphmartsState()).toBe('loaded');
    expect(draft.layersState()).toBe('loaded');
    expect(draft.graphmartTitle(GM)).toBe('Production');
    expect(draft.layerTitle('L1')).toBe('Layer one');
    expect(draft.dirty()).toBe(false);
    expect(draft.form.controls.routeId.disabled).toBe(true);
  });

  it('notices edits anywhere, including layers and the template, and names them', async () => {
    const draft = create();
    draft.load(saved, 'SELECT 1', ['L1']);
    await settle();

    draft.form.controls.template.setValue('SELECT 2');
    draft.addLayer('L2');
    expect(draft.dirty()).toBe(true);
    expect(draft.changedFields()).toEqual(['layers', 'template']);

    draft.form.controls.template.setValue('SELECT 1');
    draft.removeLayer('L2');
    expect(draft.dirty()).toBe(false); // back to the saved values
  });

  it('ignores the order of methods and layers', async () => {
    const draft = create();
    draft.load(saved, '', ['L1', 'L2']);
    await settle();
    draft.form.controls.methods.setValue(['GET', 'POST']);
    draft.removeLayer('L1');
    draft.addLayer('L1');
    expect(draft.dirty()).toBe(false);
  });

  it('discards edits, reloading the graphmarts of the saved datasource', async () => {
    const draft = create();
    draft.load(saved, 'SELECT 1', ['L1']);
    await settle();
    draft.form.controls.datasourceId.setValue('staging');
    await settle();
    expect(draft.form.controls.graphmart.value).toBe(''); // a different datasource: pick again
    expect(draft.layers()).toEqual([]);

    draft.discard();
    await settle();
    expect(draft.dirty()).toBe(false);
    expect(draft.form.controls.graphmart.value).toBe(GM);
    expect(draft.layers()).toEqual(['L1']);
    expect(draft.layersState()).toBe('loaded');
  });

  it('keeps the saved graphmart and layers, and can save them, when Graph Studio is unreachable', async () => {
    graphmarts = () => throwError(() => ({ status: 502, error: 'Connection refused' }));
    const draft = create();
    draft.load(saved, 'SELECT 1', ['L1']);
    await settle();

    expect(draft.graphmartsState()).toBe('failed');
    expect(draft.graphmartsError()).toBe('Connection refused');
    expect(draft.graphmartTitle(GM)).toBe(GM); // shown as its IRI
    // Saving used to do nothing here: the fields were disabled, so the form left them out
    draft.form.controls.description.setValue('People, renamed');
    expect(draft.problems()).toEqual([]);
    expect(draft.toRequest()).toEqual(expect.objectContaining({ graphMartUri: GM, layers: 'L1' }));
  });

  it('only takes layers the graphmart has', async () => {
    const draft = create();
    draft.load(saved, '', []);
    await settle();
    draft.addLayer('nope');
    draft.addLayer('L2');
    draft.addLayer('L2');
    expect(draft.layers()).toEqual(['L2']);
  });

  it('starts a new route with nothing to save, and lists what is missing', () => {
    const draft = create();
    draft.takenIds = () => ['people'];
    draft.startNew();
    expect(draft.dirty()).toBe(false);
    expect(draft.problems()).toEqual(['Name the route', 'Describe the route', 'Pick a datasource', 'Pick a graphmart']);

    draft.form.controls.routeId.setValue('people');
    expect(draft.dirty()).toBe(true);
    expect(draft.problems()[0]).toBe('A route named people already exists');
  });

  it('ignores a graphmart list that arrives after the datasource changed again', async () => {
    const slow = new Subject<unknown>();
    graphmarts = ds => ds === 'slow' ? slow : of([{ iri: GM2, title: 'Fast', active: 'true' }]);
    const draft = create();
    draft.startNew();
    draft.form.controls.datasourceId.setValue('slow');
    draft.form.controls.datasourceId.setValue('fast');
    await settle();
    slow.next([{ iri: GM, title: 'Stale', active: 'true' }]);
    slow.complete();
    await settle();
    expect(draft.graphmarts().map(g => g.title)).toEqual(['Fast']);
  });

  it('builds the request with caching sent explicitly, so turning it off sticks', async () => {
    const draft = create();
    draft.load(saved, 'SELECT 1', ['L1', 'L2']);
    await settle();
    draft.form.controls.cacheEnabled.setValue(false);
    const request = draft.toRequest();
    expect(request).toEqual(expect.objectContaining({
      routeId: 'people', routeParams: 'httpMethodRestrict=GET,POST', layers: 'L1,L2', cacheEnabled: false,
      accessPolicy: 'default', requiredRoles: '',
    }));
    const params = routeParams(request);
    expect(params.get('cacheEnabled')).toBe('false'); // used to be left out when false
    expect(params.has('cacheTtlSeconds')).toBe(false); // empty: the server default
    expect(params.get('accessPolicy')).toBe('default');
  });
});

import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, Subject } from 'rxjs';
import { Routes } from '../../../core/models/routes-interfaces/routes';
import { GraphmartsService } from '../../../core/services/graphmarts.service';
import { LayersService } from '../../../core/services/layers.service';
import { DatasourceLiveService } from '../../../core/services/datasource-live.service';
import { RouteDraft } from '../route-draft';
import { RouteDataFieldsComponent } from './route-data-fields.component';

const route = { routeId: 'r', routeParams: 'httpMethodRestrict=GET', description: 'd', graphMartUri: 'http://gm/prod',
  status: 'Started', templateContent: '', datasources: { dataSourceId: 'prod' } } as Routes;

describe('RouteDataFieldsComponent', () => {
  it('shows the graphmart by its title once the list arrives (it showed the IRI)', async () => {
    const graphmarts = new Subject<unknown>();
    TestBed.configureTestingModule({
      imports: [RouteDataFieldsComponent],
      providers: [
        RouteDraft,
        { provide: GraphmartsService, useValue: { getGraphMarts: () => graphmarts } },
        { provide: LayersService, useValue: { getGraphmartLayers: () => of([{ iri: 'L1', title: 'Core', active: 'true' }]) } },
        { provide: DatasourceLiveService, useValue: { datasources: signal([{ dataSourceId: 'prod', status: 'UP' }]) } },
      ],
    });
    const draft = TestBed.inject(RouteDraft);
    const fixture = TestBed.createComponent(RouteDataFieldsComponent);
    draft.load(route, '', ['L1']);
    fixture.detectChanges();
    await new Promise(r => setTimeout(r)); // the autocomplete writes the field in a microtask
    const input = () => fixture.nativeElement.querySelector('input[matinput]') as HTMLInputElement;
    expect(input().value).toBe('http://gm/prod'); // not listed yet

    graphmarts.next([{ iri: 'http://gm/prod', title: 'Production', active: 'true' }]);
    graphmarts.complete();
    await new Promise(r => setTimeout(r));
    fixture.detectChanges();
    expect(input().value).toBe('Production');
    expect(draft.dirty()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Core'); // the layer chip by its title
  });
});

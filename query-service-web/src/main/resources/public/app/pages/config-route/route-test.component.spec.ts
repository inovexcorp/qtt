import { TestBed } from '@angular/core/testing';
import { Component, forwardRef, Input } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { Routes } from '../../core/models/routes-interfaces/routes';
import { GraphmartsService } from '../../core/services/graphmarts.service';
import { LayersService } from '../../core/services/layers.service';
import { SparqiService } from '../../core/services/sparqi.service';
import { CodeEditorComponent } from '../../shared/code-editor/code-editor.component';
import { RouteDraft } from '../routes/route-draft';
import { flattenJson, resultTable, RouteTestComponent } from './route-test.component';

/** Stands in for the Monaco editor: a form control that holds its value. */
@Component({
  selector: 'app-code-editor', template: '',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => StubEditor), multi: true }],
})
class StubEditor implements ControlValueAccessor {
  @Input() options: unknown;
  writeValue(): void {}
  registerOnChange(): void {}
  registerOnTouched(): void {}
}

describe('flattenJson', () => {
  it('flattens nested objects under a prefix, values as strings', () => {
    expect(flattenJson({ name: { first: 'Ada', age: 36 }, tags: ['a', 'b'], ok: true }, 'body')).toEqual({
      'body.name.first': 'Ada', 'body.name.age': '36', 'body.tags': 'a,b', 'body.ok': 'true',
    });
    expect(flattenJson('not an object', 'body')).toEqual({});
  });
});

describe('resultTable', () => {
  it('reads SELECT results as rows, limited', () => {
    const results = { head: { vars: ['p', 'n'] }, results: { bindings: [
      { p: { value: 'http://a' }, n: { value: 'Ada' } }, { p: { value: 'http://b' } }, { p: { value: 'http://c' } }] } };
    expect(resultTable(results, 2)).toEqual({ vars: ['p', 'n'], rows: [['http://a', 'Ada'], ['http://b', '']], total: 3 });
  });

  it('is null for anything else (CONSTRUCT, JSON-LD)', () => {
    expect(resultTable({ '@graph': [] })).toBeNull();
    expect(resultTable(null)).toBeNull();
  });
});

describe('RouteTestComponent', () => {
  let http: HttpTestingController;

  function create() {
    TestBed.configureTestingModule({
      imports: [RouteTestComponent],
      providers: [
        RouteDraft, provideHttpClient(), provideHttpClientTesting(),
        { provide: GraphmartsService, useValue: { getGraphMarts: () => of([]) } },
        { provide: LayersService, useValue: { getGraphmartLayers: () => of([]) } },
        { provide: SparqiService, useValue: {} },
      ],
    }).overrideComponent(RouteTestComponent, { remove: { imports: [CodeEditorComponent] }, add: { imports: [StubEditor] } });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(RouteDraft).load({
      routeId: 'people', routeParams: 'httpMethodRestrict=GET,POST', description: 'd', graphMartUri: 'http://gm',
      status: 'Started', templateContent: '', datasources: { dataSourceId: 'prod' },
    } as Routes, 'SELECT ${headers.name}', ['http://layer/1', 'http://layer/2']);
    const fixture = TestBed.createComponent(RouteTestComponent);
    fixture.detectChanges();
    return fixture;
  }

  const settle = () => new Promise(resolve => setTimeout(resolve));

  it('reads the variables, then runs with the layer IRIs (titles used to be sent)', async () => {
    const fixture = create();
    const test = fixture.componentInstance as any;
    const running = test.run();
    await settle();
    http.expectOne('/queryrest/api/testing/variables').flush({
      variables: [{ name: 'headers.name', defaultValue: 'Ada' }], sampleBodyJson: '{"filter":{"age":30}}' });
    await settle();
    const exec = http.expectOne('/queryrest/api/testing/execute');
    expect(exec.request.body).toEqual({
      templateContent: 'SELECT ${headers.name}', dataSourceId: 'prod', graphMartUri: 'http://gm',
      layers: 'http://layer/1,http://layer/2', routeParams: 'httpMethodRestrict=GET,POST',
      parameters: { 'body.filter.age': '30', name: 'Ada' },
    });
    exec.flush({ status: 'success', generatedSparql: 'SELECT "Ada"', executionTimeMs: 12,
      results: { head: { vars: ['x'] }, results: { bindings: [{ x: { value: '1' } }] } } });
    await running;
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('1 rows');
    expect(fixture.nativeElement.querySelectorAll('.tst-table tbody tr').length).toBe(1);
  });

  it('shows the error and the SPARQL it built when the test fails', async () => {
    const fixture = create();
    const test = fixture.componentInstance as any;
    test.readFrom.set('SELECT ${headers.name}');
    const running = test.run();
    await settle();
    http.expectOne('/queryrest/api/testing/execute').flush({ status: 'error', error: 'Graph Studio said no',
      generatedSparql: 'SELECT ?broken' });
    await running;
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Graph Studio said no');
    expect(fixture.nativeElement.querySelector('.tst-sparql pre').textContent).toContain('SELECT ?broken');
  });

  it("doesn't send a body that isn't JSON", async () => {
    const fixture = create();
    const test = fixture.componentInstance as any;
    test.readFrom.set('SELECT ${headers.name}');
    test.body = '{ nope';
    await test.run();
    http.expectNone('/queryrest/api/testing/execute');
    expect(test.outcome().error).toContain("isn't valid JSON");
  });

  it('notices the template changed since its variables were read, and keeps typed values on a re-read', async () => {
    const fixture = create();
    const test = fixture.componentInstance as any;
    void test.readVariables();
    await settle();
    http.expectOne('/queryrest/api/testing/variables').flush({ variables: [{ name: 'headers.name' }] });
    await settle();
    test.setParam(0, { value: 'Grace' });

    TestBed.inject(RouteDraft).form.controls.template.setValue('SELECT ${headers.name} ${headers.city}');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('The template changed since its variables were read');

    void test.readVariables();
    await settle();
    http.expectOne('/queryrest/api/testing/variables').flush({ variables: [{ name: 'headers.name' }, { name: 'headers.city' }] });
    await settle();
    expect(test.params()).toEqual([{ key: 'name', value: 'Grace' }, { key: 'city', value: '' }]);
    expect(test.stale()).toBe(false);
  });
});

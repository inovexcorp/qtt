import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NgZone } from '@angular/core';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { TemplateValidation, TemplateValidationService, ValidatedEditor, saveErrorMessage } from './template-validation.service';

const URL = '/queryrest/api/routes/template/validate';

/** A stand-in for a Monaco editor: holds text and lets the test fire change events. */
class FakeEditor implements ValidatedEditor {
  readonly model = { uri: 'inmemory://template' };
  private listeners: Array<() => void> = [];
  constructor(private text: string) { }
  getValue() { return this.text; }
  getModel() { return this.model; }
  onDidChangeModelContent(listener: () => void) {
    this.listeners.push(listener);
    return { dispose: () => this.listeners = this.listeners.filter(l => l !== listener) };
  }
  type(text: string) {
    this.text = text;
    this.listeners.forEach(l => l());
  }
}

describe('TemplateValidationService', () => {
  let service: TemplateValidationService;
  let http: HttpTestingController;
  let markers: any[][];
  let results: Array<TemplateValidation | null>;
  const setMarkers = (_model: any, m: any[]) => markers.push(m);
  const syntaxError: TemplateValidation = {
    valid: false, message: 'Unclosed "${"', line: 2, column: 9, endLine: 2, endColumn: 9
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(TemplateValidationService);
    http = TestBed.inject(HttpTestingController);
    markers = [];
    results = [];
  });

  afterEach(() => http.verify());

  it('posts the template as text', () => {
    service.validate('SELECT 1').subscribe();
    const req = http.expectOne(URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBe('SELECT 1');
    expect(req.request.headers.get('Content-Type')).toBe('text/plain');
    req.flush({ valid: true });
  });

  it('maps a syntax error to an error marker at its position', () => {
    const [marker] = TemplateValidationService.markersFor(syntaxError);
    expect(marker.severity).toBe(8);
    expect(marker.startLineNumber).toBe(2);
    expect(marker.startColumn).toBe(9);
    expect(marker.endLineNumber).toBe(2);
    expect(marker.endColumn).toBe(10); // FreeMarker's end column is inclusive
    expect(marker.message).toBe('Unclosed "${"');
    expect(TemplateValidationService.markersFor({ valid: true })).toEqual([]);
  });

  it('validates the initial content, then each change after a pause', fakeAsync(() => {
    const editor = new FakeEditor('SELECT ${headers.x');
    const handle = service.attach(editor, r => results.push(r), 400, setMarkers);

    tick(400);
    http.expectOne(r => r.body === 'SELECT ${headers.x').flush(syntaxError);
    expect(markers.pop()!.length).toBe(1);
    expect(results.pop()).toEqual(syntaxError);

    editor.type('SELECT ${headers.x}');
    tick(399);
    http.expectNone(URL);
    tick(1);
    http.expectOne(r => r.body === 'SELECT ${headers.x}').flush({ valid: true });
    expect(markers.pop()).toEqual([]);

    handle.dispose();
  }));

  it('only validates the latest text when typing quickly', fakeAsync(() => {
    const editor = new FakeEditor('');
    service.attach(editor, undefined, 400, setMarkers);
    tick(400);
    http.expectOne(URL).flush({ valid: true });

    editor.type('S');
    tick(100);
    editor.type('SE');
    tick(100);
    editor.type('SEL');
    tick(400);
    const reqs = http.match(URL);
    expect(reqs.length).toBe(1);
    expect(reqs[0].request.body).toBe('SEL');
    reqs[0].flush({ valid: true });
  }));

  it('drops a result for text that has since changed', fakeAsync(() => {
    const editor = new FakeEditor('first ${');
    service.attach(editor, r => results.push(r), 400, setMarkers);
    tick(400);
    const stale = http.expectOne(r => r.body === 'first ${');

    editor.type('second');
    tick(400);
    http.expectOne(r => r.body === 'second').flush({ valid: true });
    expect(stale.cancelled).toBeTrue();
    expect(results).toEqual([{ valid: true }]);
  }));

  it('reports results inside the Angular zone, even for editors running outside it', fakeAsync(() => {
    const zone = TestBed.inject(NgZone);
    const editor = new FakeEditor('SELECT');
    let insideZone: boolean | undefined;
    zone.runOutsideAngular(() => service.attach(editor, () => insideZone = NgZone.isInAngularZone(), 400, setMarkers));
    tick(400);
    http.expectOne(URL).flush({ valid: true });
    expect(insideZone).toBeTrue();
  }));

  it('clears markers when the server cannot be reached', fakeAsync(() => {
    const editor = new FakeEditor('SELECT');
    service.attach(editor, r => results.push(r), 400, setMarkers);
    tick(400);
    http.expectOne(URL).flush('down', { status: 503, statusText: 'Unavailable' });
    expect(markers.pop()).toEqual([]);
    expect(results.pop()).toBeNull();
  }));

  it('stops validating and clears markers on dispose', fakeAsync(() => {
    const editor = new FakeEditor('SELECT');
    const handle = service.attach(editor, undefined, 400, setMarkers);
    tick(400);
    http.expectOne(URL).flush({ valid: true });

    handle.dispose();
    expect(markers.pop()).toEqual([]);
    editor.type('changed ${');
    tick(1000);
    http.expectNone(URL);
  }));
});

describe('saveErrorMessage', () => {
  it('reads the message of a JSON error, as text or parsed', () => {
    expect(saveErrorMessage({ error: { message: 'syntax error at line 2' } })).toBe('syntax error at line 2');
    expect(saveErrorMessage({ error: '{"message":"from text"}' })).toBe('from text');
  });

  it('falls back to plain text, then the HTTP message', () => {
    expect(saveErrorMessage({ error: 'DataSource with id: x does not exist' }))
      .toBe('DataSource with id: x does not exist');
    expect(saveErrorMessage({ message: 'Http failure response' })).toBe('Http failure response');
  });
});

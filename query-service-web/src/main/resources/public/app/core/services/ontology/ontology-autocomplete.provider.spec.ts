import { of, throwError } from 'rxjs';
import * as monaco from 'monaco-editor';
import { OntologyAutocompleteProvider } from './ontology-autocomplete.provider';
import { OntologyService } from './ontology.service';
import { OntologyElement } from '../../models/ontology/ontology-element';

/** A one-line Monaco text model, enough for the provider: the cursor sits at `|` in `line`. */
function modelAt(lineWithCursor: string): { model: any; position: any } {
  const column = lineWithCursor.indexOf('|') + 1;
  const line = lineWithCursor.replace('|', '');
  const model = {
    getValueInRange: (r: any) => line.substring(r.startColumn - 1, r.endColumn - 1),
    getLineContent: () => line,
    getWordUntilPosition: (p: any) => {
      const before = line.substring(0, p.column - 1);
      const word = (before.match(/\w*$/) || [''])[0];
      return { word, startColumn: p.column - word.length, endColumn: p.column };
    }
  };
  return { model, position: { lineNumber: 1, column } };
}

const PERSON: OntologyElement = { uri: 'http://example.org/onto#Person', label: 'Person', type: 'class' };
const KNOWS: OntologyElement = { uri: 'http://example.org/onto#knows', label: 'knows', type: 'objectProperty',
  description: 'Who knows whom' };
const ALICE: OntologyElement = { uri: 'http://example.org/people/alice', label: 'Alice', type: 'individual' };

describe('OntologyAutocompleteProvider', () => {
  let ontology: jasmine.SpyObj<OntologyService>;
  let provider: OntologyAutocompleteProvider;

  beforeEach(() => {
    ontology = jasmine.createSpyObj('OntologyService', ['searchOntologyElements']);
    ontology.searchOntologyElements.and.returnValue(of([PERSON, KNOWS, ALICE]));
    provider = new OntologyAutocompleteProvider(ontology, 'route-1');
  });

  const complete = async (line: string) => {
    const { model, position } = modelAt(line);
    return await provider.provideCompletionItems(model, position, {} as any) as monaco.languages.CompletionList | undefined;
  };

  it('does not search for fewer than two characters outside a URI or prefixed name', async () => {
    expect(await complete('?s a P|')).toBeUndefined();
    expect(ontology.searchOntologyElements).not.toHaveBeenCalled();
  });

  it('wraps plain-word completions in angle brackets and replaces the typed word', async () => {
    const list = await complete('?s a Pers|');
    expect(ontology.searchOntologyElements).toHaveBeenCalledWith('route-1', 'Pers', 'all', 50);
    const person = list!.suggestions[0];
    expect(person.label).toBe('Person');
    expect(person.insertText).toBe('<http://example.org/onto#Person>');
    expect(person.detail).toBe(PERSON.uri);
    expect(person.range).toEqual(jasmine.objectContaining({ startColumn: 6, endColumn: 10 }));
    expect(person.sortText).toBe('0Person');
    expect(person.filterText).toBe('Person');
  });

  it('inside <...> inserts the URI and the closing bracket, replacing everything after the bracket', async () => {
    const list = await complete('?s a <http://example.org/on|to');
    expect(ontology.searchOntologyElements).toHaveBeenCalledWith('route-1', 'http://example.org/on', 'all', 50);
    const person = list!.suggestions[0];
    expect(person.insertText).toBe('http://example.org/onto#Person>');
    expect(person.range).toEqual(jasmine.objectContaining({ startColumn: 7, endColumn: 30 }));
  });

  it('after prefix: inserts only the local name and keeps the prefix', async () => {
    const list = await complete('?s a ex:Per|');
    expect(ontology.searchOntologyElements).toHaveBeenCalledWith('route-1', 'Per', 'all', 50);
    expect(list!.suggestions.map(s => s.insertText)).toEqual(['Person', 'knows', 'alice']);
    expect(list!.suggestions[0].range).toEqual(jasmine.objectContaining({ startColumn: 9, endColumn: 12 }));
  });

  it('with an empty local part searches by the prefix', async () => {
    await complete('?s a skos:|');
    expect(ontology.searchOntologyElements).toHaveBeenCalledWith('route-1', 'skos', 'all', 50);
  });

  it('does not treat http:// as a prefixed name', async () => {
    const list = await complete('see http://exa|');
    expect(ontology.searchOntologyElements).toHaveBeenCalledWith('route-1', 'exa', 'all', 50);
    expect(list!.suggestions[0].insertText).toBe('<http://example.org/onto#Person>');
  });

  it('maps element types to completion kinds and documents each item', async () => {
    const list = await complete('?s a Pers|');
    const [person, knows, alice] = list!.suggestions;
    expect(person.kind).toBe(monaco.languages.CompletionItemKind.Class);
    expect(knows.kind).toBe(monaco.languages.CompletionItemKind.Property);
    expect(alice.kind).toBe(monaco.languages.CompletionItemKind.Value);
    expect(person.documentation).toBe('Class from ontology');
    expect(knows.documentation).toBe('Who knows whom');
  });

  it('marks the list incomplete when the search hit its limit', async () => {
    ontology.searchOntologyElements.and.returnValue(of(Array.from({ length: 50 }, (_, i) =>
      ({ uri: `http://e/x${i}`, label: `x${i}`, type: 'class' } as OntologyElement))));
    expect((await complete('?s a xx|'))!.incomplete).toBeTrue();
    ontology.searchOntologyElements.and.returnValue(of([PERSON]));
    expect((await complete('?s a xx|'))!.incomplete).toBeFalse();
  });

  it('returns no suggestions when nothing matches or the search fails', async () => {
    ontology.searchOntologyElements.and.returnValue(of([]));
    expect((await complete('?s a Zz|'))!.suggestions).toEqual([]);
    spyOn(console, 'error');
    ontology.searchOntologyElements.and.returnValue(throwError(() => new Error('down')));
    expect((await complete('?s a Zz|'))!.suggestions).toEqual([]);
  });

  it('searches the route set last', async () => {
    provider.setRouteId('route-2');
    await complete('?s a Pers|');
    expect(ontology.searchOntologyElements).toHaveBeenCalledWith('route-2', 'Pers', 'all', 50);
  });
});

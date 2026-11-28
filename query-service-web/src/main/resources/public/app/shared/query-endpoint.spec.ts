import { curlExample, effectiveAccess, endpointUrl, methodsOf } from './query-endpoint';

describe('query endpoint helpers', () => {
  it('resolves the default access', () => {
    expect(effectiveAccess('default', 'open')).toBe('open');
    expect(effectiveAccess(null, 'authenticated')).toBe('authenticated');
    expect(effectiveAccess('open', 'authenticated')).toBe('open');
    expect(effectiveAccess('authenticated', 'open')).toBe('authenticated');
  });

  it('makes relative base URLs absolute against the UI origin', () => {
    expect(endpointUrl('/query', 'people', 'https://qtt.example.com')).toBe('https://qtt.example.com/query/people');
    expect(endpointUrl(undefined, 'people', 'https://qtt.example.com')).toBe('https://qtt.example.com/query/people');
    expect(endpointUrl('https://api.example.com/query/', 'people')).toBe('https://api.example.com/query/people');
  });

  it('writes curl commands with a token only for authenticated routes', () => {
    expect(curlExample('https://h/query/p', 'open')).toBe('curl "https://h/query/p"');
    expect(curlExample('https://h/query/p', 'authenticated'))
      .toBe('curl -H "Authorization: Bearer $QTT_TOKEN" "https://h/query/p"');
    expect(curlExample('https://h/query/p', 'open', ['POST'])).toContain('-X POST');
    expect(curlExample('https://h/query/p', 'open', ['GET', 'POST'])).not.toContain('-X POST');
  });

  it('reads the HTTP methods of a route', () => {
    expect(methodsOf('httpMethodRestrict=GET,post')).toEqual(['GET', 'POST']);
    expect(methodsOf('')).toEqual([]);
    expect(methodsOf(null)).toEqual([]);
  });
});

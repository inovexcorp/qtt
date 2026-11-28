import { match, segments } from './match';

describe('quick actions match', () => {
  it('matches an empty query with no highlight', () => {
    expect(match('', 'Routes')).toEqual({ score: 0, ranges: [] });
    expect(match('   ', 'Routes')).toEqual({ score: 0, ranges: [] });
  });

  it('returns null when the characters are not all there in order', () => {
    expect(match('xyz', 'Routes')).toBeNull();
    expect(match('sr', 'rs')).toBeNull();
  });

  it('ranks prefix, then word start, then contains, then subsequence', () => {
    const prefix = match('peo', 'people-search')!.score;
    const wordStart = match('sea', 'people-search')!.score;
    const contains = match('arc', 'people-search')!.score;
    const subsequence = match('pls', 'people-search')!.score;
    expect(prefix).toBeGreaterThan(wordStart);
    expect(wordStart).toBeGreaterThan(contains);
    expect(contains).toBeGreaterThan(subsequence);
  });

  it('ignores case', () => {
    expect(match('ROUTES', 'routes')!.ranges).toEqual([[0, 6]]);
  });

  it('prefers the starts of words in a subsequence and highlights them', () => {
    expect(match('crc', "Clear this route's cache")!.ranges).toEqual([[0, 1], [11, 12], [19, 20]]);
  });

  it('treats a case change as the start of a word', () => {
    const m = match('id', 'routeId')!;
    expect(m.ranges).toEqual([[5, 7]]);
    expect(m.score).toBeGreaterThan(match('ou', 'routeId')!.score);
  });

  it('needs every word of the query, and merges their highlights', () => {
    const m = match('route cache', "Clear this route's cache")!;
    expect(m.ranges).toEqual([[11, 16], [19, 24]]);
    expect(match('route nothing', "Clear this route's cache")).toBeNull();
  });

  it('puts the shorter text first among equal matches', () => {
    expect(match('rou', 'Routes')!.score).toBeGreaterThan(match('rou', 'Routes table settings')!.score);
  });

  it('can require each word as written, without scattered characters', () => {
    expect(match('peo', 'http://wiremock:8080', false)).toBeNull();
    expect(match('peo', 'http://wiremock:8080')).not.toBeNull();
    expect(match('wire', 'http://wiremock:8080', false)!.ranges).toEqual([[7, 11]]);
  });

  it('cuts a text into plain and highlighted segments', () => {
    expect(segments('people-search', [[0, 3], [7, 9]])).toEqual([
      { text: 'peo', hit: true },
      { text: 'ple-', hit: false },
      { text: 'se', hit: true },
      { text: 'arch', hit: false }
    ]);
    expect(segments('abc', [])).toEqual([{ text: 'abc', hit: false }]);
  });
});

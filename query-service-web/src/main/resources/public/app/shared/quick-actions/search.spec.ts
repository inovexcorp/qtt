import { QuickAction } from './quick-action';
import { SCOPED_LIMIT, search, flatten } from './search';

const action = (id: string, label: string, group: QuickAction['group'], extra: Partial<QuickAction> = {}): QuickAction =>
  ({ id, label, icon: 'bolt', group, run: () => undefined, ...extra });

const actions: QuickAction[] = [
  action('go:routes', 'Routes', 'navigation'),
  action('go:datasources', 'Datasources', 'navigation'),
  action('go:settings:caching', 'Settings: Caching', 'navigation', { searchOnly: true }),
  action('page:clear', "Clear this route's cache", 'page', { keywords: ['flush'] }),
  action('page:on', 'Turn route on', 'page', { visible: () => false }),
  action('page:test', 'Run test request', 'page', { disabledReason: () => 'The datasource is down' }),
  action('add-route', 'Add route', 'actions')
];
const routes = [
  { routeId: 'people-search', description: 'Find people', status: 'Started', dataSourceId: 'anzo-prod' },
  { routeId: 'people-by-org', description: '', status: 'Stopped', dataSourceId: 'anzo-test' }
];
const datasources = [{ dataSourceId: 'anzo-prod', url: 'https://anzo.example.org', status: 'UP' }];

describe('quick actions search', () => {
  it('lists page actions, places to go, then other actions when nothing is typed; no routes or datasources', () => {
    const groups = search({ query: '', actions, routes, datasources });
    expect(groups.map(g => g.id)).toEqual(['page', 'navigation', 'actions']);
    expect(groups[0].items.map(i => i.label)).toEqual(["Clear this route's cache", 'Run test request']);
  });

  it('leaves out actions that are not visible, and greys out disabled ones with their reason', () => {
    const items = flatten(search({ query: '', actions, routes, datasources }));
    expect(items.some(i => i.label === 'Turn route on')).toBe(false);
    expect(items.some(i => i.label === 'Settings: Caching')).toBe(false);
    expect(flatten(search({ query: 'cach', actions, routes, datasources })).map(i => i.label)).toContain('Settings: Caching');
    expect(items.find(i => i.label === 'Run test request')!.disabledReason).toBe('The datasource is down');
  });

  it('finds routes and datasources by id, with status hints', () => {
    const groups = search({ query: 'peo', actions, routes, datasources });
    expect(groups[0].id).toBe('routes');
    expect(groups[0].items.map(i => i.entityId)).toEqual(['people-search', 'people-by-org']);
    expect(groups[0].items[0]).toEqual(expect.objectContaining({ kind: 'route', hint: 'Started', tone: 'ok', detail: 'Find people' }));
    const ds = flatten(search({ query: 'anzo-p', actions, routes, datasources })).find(i => i.kind === 'datasource')!;
    expect(ds).toEqual(expect.objectContaining({ entityId: 'anzo-prod', hint: 'Up', tone: 'ok' }));
  });

  it('finds a route by its datasource or description without highlighting', () => {
    const items = flatten(search({ query: 'anzo-test', actions, routes, datasources }));
    const route = items.find(i => i.kind === 'route')!;
    expect(route.entityId).toBe('people-by-org');
    expect(route.ranges).toEqual([]);
  });

  it('does not match scattered letters in a URL or description', () => {
    const items = flatten(search({ query: 'aep', actions: [], routes, datasources }));
    expect(items).toEqual([]);
  });

  it('finds an action by keyword', () => {
    const items = flatten(search({ query: 'flush', actions, routes, datasources }));
    expect(items.map(i => i.label)).toEqual(["Clear this route's cache"]);
  });

  it('orders groups by their best match', () => {
    const groups = search({ query: 'datasources', actions, routes, datasources });
    expect(groups[0].id).toBe('navigation');
  });

  it('limits each group and counts the rest', () => {
    const many = Array.from({ length: 8 }, (_, i) => ({ routeId: 'r' + i, description: '', status: 'Started', dataSourceId: 'd' }));
    const [group] = search({ query: 'r', actions: [], routes: many, datasources: [] });
    expect(group.items.length).toBe(5);
    expect(group.more).toBe(3);
  });

  it('lists all of the page\'s actions when nothing is typed, and limits them when searching', () => {
    const many = Array.from({ length: 7 }, (_, i) => action('p' + i, 'Page action ' + i, 'page'));
    expect(search({ query: '', actions: many, routes: [], datasources: [] })[0].items.length).toBe(7);
    expect(search({ query: 'page', actions: many, routes: [], datasources: [] })[0].items.length).toBe(5);
  });

  it('returns no groups when nothing matches', () => {
    expect(search({ query: 'zzz', actions, routes, datasources })).toEqual([]);
  });

  describe('scopes', () => {
    it('lists everything of the scope\'s kind before anything is typed, and nothing else', () => {
      const groups = search({ query: '', scope: 'routes', actions, routes, datasources });
      expect(groups.map(g => g.id)).toEqual(['routes']);
      expect(groups[0].items.length).toBe(2);
    });

    it('lists more than 5 rows when narrowed', () => {
      const many = Array.from({ length: 60 }, (_, i) => ({ routeId: 'r' + i, description: '', status: 'Started', dataSourceId: 'd' }));
      const [group] = search({ query: '', scope: 'routes', actions: [], routes: many, datasources: [] });
      expect(group.items.length).toBe(SCOPED_LIMIT);
      expect(group.more).toBe(10);
    });

    it('narrows to actions, including search-only ones', () => {
      const groups = search({ query: '', scope: 'actions', actions, routes, datasources });
      expect(groups.map(g => g.id)).toEqual(['page', 'navigation', 'actions']);
      expect(flatten(groups).map(i => i.label)).toContain('Settings: Caching');
    });

    it('narrows to datasources', () => {
      expect(flatten(search({ query: 'peo', scope: 'datasources', actions, routes, datasources }))).toEqual([]);
      expect(flatten(search({ query: 'anzo', scope: 'datasources', actions, routes, datasources })).map(i => i.kind))
        .toEqual(['datasource']);
    });
  });

  describe('recent pages', () => {
    const recents = [{ kind: 'datasource' as const, id: 'anzo-prod' }, { kind: 'route' as const, id: 'gone' },
      { kind: 'route' as const, id: 'people-search' }];

    it('are listed after the page\'s actions when nothing is typed, with their status', () => {
      const groups = search({ query: '', actions, routes, datasources, recents, entitiesLoaded: true });
      expect(groups.map(g => g.id)).toEqual(['page', 'recent', 'navigation', 'actions']);
      expect(groups[1].items.map(i => [i.kind, i.entityId, i.hint])).toEqual([
        ['datasource', 'anzo-prod', 'Up'], ['route', 'people-search', 'Started']]);
    });

    it('are listed without status, and none dropped, until the lists have loaded', () => {
      const groups = search({ query: '', actions: [], routes: [], datasources: [], recents, entitiesLoaded: false });
      expect(groups[0].items.map(i => [i.entityId, i.hint])).toEqual([
        ['anzo-prod', 'Datasource'], ['gone', 'Route'], ['people-search', 'Route']]);
    });

    it('are not listed once something is typed', () => {
      const groups = search({ query: 'peo', actions, routes, datasources, recents, entitiesLoaded: true });
      expect(groups.some(g => g.id === 'recent')).toBe(false);
    });
  });
});

import { QuickAction } from './quick-action';
import { search, flatten } from './search';

const action = (id: string, label: string, group: QuickAction['group'], extra: Partial<QuickAction> = {}): QuickAction =>
  ({ id, label, icon: 'bolt', group, run: () => undefined, ...extra });

const actions: QuickAction[] = [
  action('go:routes', 'Routes', 'navigation'),
  action('go:datasources', 'Datasources', 'navigation'),
  action('go:settings:caching', 'Settings: Caching', 'navigation', { searchOnly: true }),
  action('page:clear', "Clear this route's cache", 'page', { keywords: ['flush'] }),
  action('page:on', 'Turn route on', 'page', { visible: () => false }),
  action('page:test', 'Run test request', 'page', { disabledReason: () => 'The datasource is down' })
];
const routes = [
  { routeId: 'people-search', description: 'Find people', status: 'Started', dataSourceId: 'anzo-prod' },
  { routeId: 'people-by-org', description: '', status: 'Stopped', dataSourceId: 'anzo-test' }
];
const datasources = [{ dataSourceId: 'anzo-prod', url: 'https://anzo.example.org', status: 'UP' }];

describe('quick actions search', () => {
  it('lists page actions, then places to go, when nothing is typed; no routes or datasources', () => {
    const groups = search({ query: '', actions, routes, datasources });
    expect(groups.map(g => g.id)).toEqual(['page', 'navigation']);
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

  it('returns no groups when nothing matches', () => {
    expect(search({ query: 'zzz', actions, routes, datasources })).toEqual([]);
  });
});

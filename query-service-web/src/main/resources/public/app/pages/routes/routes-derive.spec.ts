import { Routes } from '../../core/models/routes-interfaces/routes';
import { RouteMetric } from '../../core/models/metric-data';
import { DatasourceHealth, DatasourceStatus } from '../../core/models/datasource-health';
import {
  countStates, filterRows, formatErrorRate, formatMs, formatRate, groupByDatasource, headline, NO_FILTER, recoveries,
  RouteRow, routeState, RowInputs, sortRows, startBlockedReason, toRows, trafficByRoute,
} from './routes-derive';

const route = (id: string, extra: Partial<Routes> = {}): Routes => ({
  routeId: id, routeParams: 'httpMethodRestrict=GET,POST', templateContent: '', description: `About ${id}`,
  graphMartUri: '', status: 'Started',
  datasources: { dataSourceId: 'ds1', timeOutSeconds: '30', maxQueryHeaderLength: '', username: '', url: '',
    validateCertificate: true, status: 'UP' },
  ...extra,
});

const health = (id: string, status: DatasourceStatus): DatasourceHealth => ({
  dataSourceId: id, url: '', status, lastHealthCheck: null, lastHealthError: null, consecutiveFailures: 0,
  routeCount: 0, recentChecks: [],
});

const snapshot = (routeId: string, minute: number, total: number, failed = 0, processing = total * 10): RouteMetric => ({
  route: routeId, exchangesTotal: total, exchangesCompleted: total - failed, exchangesFailed: failed, exchangesInflight: 0,
  totalProcessingTime: processing, meanProcessingTime: total ? processing / total : -1, minProcessingTime: 0,
  maxProcessingTime: 0, uptime: '', state: 'Started',
  timeStamp: `2026-11-28T10:${String(minute).padStart(2, '0')}:00`,
});

const inputs = (extra: Partial<RowInputs>): RowInputs =>
  ({ routes: [], datasources: null, metrics: null, history: null, serverDefaultAccess: 'open', ...extra });

const row = (id: string, extra: Partial<Routes> = {}, more: Partial<RowInputs> = {}): RouteRow =>
  toRows(inputs({ routes: [route(id, extra)], ...more }))[0];

describe('routes-derive', () => {
  describe('state', () => {
    it('is running when started on a datasource that can serve it, including one not checked yet', () => {
      expect(routeState(true, 'UP')).toBe('running');
      expect(routeState(true, 'CHECKING')).toBe('running');
      expect(routeState(true, 'UNKNOWN')).toBe('running');
    });

    it('is blocked when started on a datasource that is down or disabled', () => {
      expect(routeState(true, 'DOWN')).toBe('blocked');
      expect(routeState(true, 'DISABLED')).toBe('blocked');
    });

    it('is stopped when stopped, whatever the datasource', () => {
      expect(routeState(false, 'UP')).toBe('stopped');
      expect(routeState(false, 'DOWN')).toBe('stopped');
    });

    it('only refuses to start on a down or disabled datasource (as the server does)', () => {
      expect(startBlockedReason('ds1', 'DOWN')).toContain('down');
      expect(startBlockedReason('ds1', 'DISABLED')).toContain('disabled');
      expect(startBlockedReason('ds1', 'UNKNOWN')).toBeNull();
      expect(startBlockedReason('ds1', 'CHECKING')).toBeNull();
    });
  });

  describe('rows', () => {
    it('take the datasource health from the live snapshot over the route list', () => {
      const r = row('a', {}, { datasources: [health('ds1', 'DOWN')] });
      expect(r.datasourceStatus).toBe('DOWN');
      expect(r.state).toBe('blocked');
      expect(r.tone).toBe('bad');
      expect(r.note).toContain('ds1 is down');
    });

    it('take started/stopped from the live metrics over the stored status', () => {
      const live = [{ ...snapshot('a', 0, 0), state: 'Stopped' }];
      expect(row('a', { status: 'Started' }, { metrics: live }).started).toBe(false);
      const running = [{ ...snapshot('a', 0, 0), state: 'Started' }];
      const r = row('a', { status: 'Stopped', stopReason: 'MANUAL', stoppedAt: 1000 }, { metrics: running });
      expect(r.started).toBe(true);
      expect(r.stopReason).toBeNull(); // the stored reason belonged to the stored, older status
      expect(r.stoppedAt).toBeNull();
    });

    it('mark a route the health check stopped as waiting for a restart once its datasource is back up', () => {
      const stopped = { status: 'Stopped', stopReason: 'HEALTH_CHECK' as const, stoppedAt: 5000 };
      const down = row('a', stopped, { datasources: [health('ds1', 'DOWN')] });
      expect(down.awaitingRestart).toBe(false);
      expect(down.startBlocked).toContain('down');
      expect(down.note).toContain('while ds1 is down');

      const up = row('a', stopped, { datasources: [health('ds1', 'UP')] });
      expect(up.awaitingRestart).toBe(true);
      expect(up.tone).toBe('warn');
      expect(up.startBlocked).toBeNull();
      expect(up.note).toContain('back up');
      expect(up.stoppedAt).toBe(5000);
    });

    it('say a route without a template is stopped for that reason; a manual stop needs no note', () => {
      expect(row('a', { status: 'Stopped', stopReason: 'NO_TEMPLATE' }).note).toContain('no template');
      expect(row('a', { status: 'Stopped', stopReason: 'MANUAL' }).note).toBeNull();
      expect(row('a', { status: 'Stopped' }).tone).toBe('idle');
    });

    it('read methods and the access that applies', () => {
      expect(row('a').methods).toEqual(['GET', 'POST']);
      expect(row('a', { accessPolicy: null }, { serverDefaultAccess: 'authenticated' }).access).toBe('authenticated');
      expect(row('a', { accessPolicy: 'open' }, { serverDefaultAccess: 'authenticated' }).access).toBe('open');
    });

    it('parse an ISO stop time too', () => {
      expect(row('a', { status: 'Stopped', stoppedAt: '2026-11-28T10:00:00Z' }).stoppedAt)
        .toBe(Date.parse('2026-11-28T10:00:00Z'));
    });
  });

  describe('traffic', () => {
    it('turns cumulative snapshots into calls per minute, error rate and mean time', () => {
      // 10 calls a minute, 1 failure a minute, 20 ms each
      const history = [0, 1, 2, 3, 4, 5].map(m => snapshot('a', m, m * 10, m, m * 10 * 20));
      const t = trafficByRoute(history).get('a')!;
      expect(t.spark).toEqual([10, 10, 10, 10, 10]);
      expect(t.perMinute).toBe(10);
      expect(t.calls).toBe(50);
      expect(t.failures).toBe(5);
      expect(t.errorRate).toBeCloseTo(0.1);
      expect(t.meanMs).toBeCloseTo(20);
    });

    it('averages the rate over the last five minutes only', () => {
      // 0 calls for 5 minutes, then 30 a minute for 5
      const totals = [0, 0, 0, 0, 0, 0, 30, 60, 90, 120, 150];
      const t = trafficByRoute(totals.map((total, m) => snapshot('a', m, total))).get('a')!;
      expect(t.perMinute).toBe(30);
      expect(t.spark.length).toBe(10);
    });

    it('has no rate, error rate or mean without calls', () => {
      const t = trafficByRoute([snapshot('a', 0, 0), snapshot('a', 1, 0)]).get('a')!;
      expect(t.perMinute).toBe(0);
      expect(t.errorRate).toBeNull();
      expect(t.meanMs).toBeNull();
    });

    it('gives a route missing from the history no traffic', () => {
      expect(row('a', {}, { history: [] }).traffic.perMinute).toBeNull();
      expect(row('a', {}, { history: [] }).traffic.spark).toEqual([]);
    });
  });

  describe('filter, sort, group', () => {
    const rows = toRows(inputs({
      routes: [
        route('alpha', { description: 'People search' }),
        route('beta', { status: 'Stopped', stopReason: 'HEALTH_CHECK', datasources: { ...route('x').datasources, dataSourceId: 'ds2' } }),
        route('gamma', { accessPolicy: 'authenticated', datasources: { ...route('x').datasources, dataSourceId: 'ds2' } }),
        route('delta', { status: 'Stopped', stopReason: 'MANUAL' }),
      ],
      datasources: [health('ds1', 'UP'), health('ds2', 'DOWN')],
      history: [snapshot('alpha', 0, 0), snapshot('alpha', 1, 50, 0, 500), snapshot('gamma', 0, 0), snapshot('gamma', 1, 5, 5, 5000)],
    }));

    it('searches the id, the description and the datasource', () => {
      expect(filterRows(rows, { ...NO_FILTER, term: 'people' }).map(r => r.id)).toEqual(['alpha']);
      expect(filterRows(rows, { ...NO_FILTER, term: 'DS2' }).map(r => r.id)).toEqual(['beta', 'gamma']);
    });

    it('filters by state, datasource and access', () => {
      expect(filterRows(rows, { ...NO_FILTER, state: 'blocked' }).map(r => r.id)).toEqual(['gamma']);
      expect(filterRows(rows, { ...NO_FILTER, state: 'stopped' }).map(r => r.id)).toEqual(['beta', 'delta']);
      expect(filterRows(rows, { ...NO_FILTER, datasource: 'ds1' }).map(r => r.id)).toEqual(['alpha', 'delta']);
      expect(filterRows(rows, { ...NO_FILTER, access: 'authenticated' }).map(r => r.id)).toEqual(['gamma']);
      expect(filterRows(rows, { ...NO_FILTER, state: 'blocked' }, true).length).toBe(4);
    });

    it('sorts by status with attention first, and by traffic with routes without a value last', () => {
      expect(sortRows(rows, 'status').map(r => r.id)).toEqual(['gamma', 'alpha', 'beta', 'delta']);
      expect(sortRows(rows, 'busiest').map(r => r.id)).toEqual(['alpha', 'gamma', 'beta', 'delta']);
      expect(sortRows(rows, 'slowest').map(r => r.id)).toEqual(['gamma', 'alpha', 'beta', 'delta']);
      expect(sortRows(rows, 'errors').map(r => r.id)).toEqual(['gamma', 'alpha', 'beta', 'delta']);
      expect(sortRows(rows, 'name').map(r => r.id)).toEqual(['alpha', 'beta', 'delta', 'gamma']);
    });

    it('groups by datasource, troubled ones first', () => {
      const groups = groupByDatasource(rows);
      expect(groups.map(g => g.datasourceId)).toEqual(['ds2', 'ds1']);
      expect(groups[0].status).toBe('DOWN');
      expect(groups[0].counts.blocked).toBe(1);
      expect(groups[1].rows.map(r => r.id)).toEqual(['alpha', 'delta']);
    });

    it('counts states and words the headline', () => {
      const counts = countStates(rows);
      expect(counts).toEqual({ total: 4, running: 1, blocked: 1, stopped: 2, awaitingRestart: 0 });
      expect(headline(counts)).toBe('4 routes · 1 running · 1 blocked by its datasource · 2 stopped');
      expect(headline({ total: 3, running: 3, blocked: 0, stopped: 0, awaitingRestart: 0 })).toBe('3 routes, all running');
      expect(headline(countStates([]))).toBe('No routes yet');
    });
  });

  it('lists the routes waiting for a restart by datasource, with the earliest stop', () => {
    const stopped = (id: string, at: number) =>
      route(id, { status: 'Stopped', stopReason: 'HEALTH_CHECK', stoppedAt: at });
    const rows = toRows(inputs({
      routes: [stopped('a', 3000), stopped('b', 1000), route('c', { status: 'Stopped', stopReason: 'MANUAL' })],
      datasources: [health('ds1', 'UP')],
    }));
    expect(recoveries(rows)).toEqual([{ datasourceId: 'ds1', routeIds: ['a', 'b'], since: 1000 }]);
  });

  it('formats rates, error rates and times', () => {
    expect(formatRate(null)).toBe('—');
    expect(formatRate(0)).toBe('0/min');
    expect(formatRate(0.05)).toBe('<0.1/min');
    expect(formatRate(2)).toBe('2/min');
    expect(formatRate(2.34)).toBe('2.3/min');
    expect(formatRate(12.6)).toBe('13/min');
    expect(formatErrorRate(0)).toBe('0%');
    expect(formatErrorRate(0.004)).toBe('0.4%');
    expect(formatErrorRate(0.123)).toBe('12%');
    expect(formatMs(84.6)).toBe('85 ms');
    expect(formatMs(1200)).toBe('1.2 s');
    expect(formatMs(null)).toBe('—');
  });
});

import { RouteMetric } from '../../core/models/metric-data';
import { buildTrends, clockLabel, exchangeBars, inScope, latencyRanges, summarize, trendLines } from './metrics-derive';

/** A route's metrics; processing time defaults to 10 ms per exchange. */
const route = (name: string, total: number, failed = 0, extra: Partial<RouteMetric> = {}): RouteMetric => ({
  route: name, exchangesTotal: total, exchangesCompleted: total - failed, exchangesFailed: failed, exchangesInflight: 0,
  totalProcessingTime: total * 10, meanProcessingTime: total ? 10 : -1, minProcessingTime: 0, maxProcessingTime: 0,
  uptime: '1m', state: 'Started', timeStamp: '', ...extra,
});

/** A history snapshot: cumulative totals at a minute. */
const snap = (name: string, minute: string, total: number, failed = 0, processing = total * 10): RouteMetric =>
  route(name, total, failed, { totalProcessingTime: processing, timeStamp: `2026-11-28T${minute}:00.123` });

describe('metrics-derive', () => {
  describe('summarize', () => {
    it('computes the summary', () => {
      expect(summarize([route('a', 3, 0), route('b', 1, 1)])).toEqual(
        { endpoints: 2, totalExchanges: 4, avgProcessingMs: 10, busiestRoute: 'a', successRate: 75 });
    });

    // Regression: averages over zero exchanges showed NaN, then a misleading 0.
    it('leaves values that need an exchange empty', () => {
      expect(summarize([route('a', 0), route('b', 0)])).toEqual(
        { endpoints: 2, totalExchanges: 0, avgProcessingMs: null, busiestRoute: null, successRate: null });
      expect(summarize([])).toEqual(
        { endpoints: 0, totalExchanges: 0, avgProcessingMs: null, busiestRoute: null, successRate: null });
    });
  });

  it('scopes to the selected routes, or all when none are selected', () => {
    const routes = [route('a', 1), route('b', 1)];
    expect(inScope(routes, []).map(r => r.route)).toEqual(['a', 'b']);
    expect(inScope(routes, ['b']).map(r => r.route)).toEqual(['b']);
  });

  it('orders exchange bars busiest first and caps them', () => {
    const { bars, hidden } = exchangeBars([route('a', 1), route('b', 5, 2), route('c', 3)], 2);
    expect(bars).toEqual([{ route: 'b', completed: 3, failed: 2, total: 5 }, { route: 'c', completed: 3, failed: 0, total: 3 }]);
    expect(hidden).toBe(1);
  });

  it('lists latency ranges slowest first and leaves out routes without traffic', () => {
    const result = latencyRanges([
      route('fast', 2, 0, { meanProcessingTime: 5, minProcessingTime: 1, maxProcessingTime: 9 }),
      route('slow', 2, 0, { meanProcessingTime: 50, minProcessingTime: 10, maxProcessingTime: 90 }),
      route('idle', 0),
    ], 10);
    expect(result.ranges.map(r => r.route)).toEqual(['slow', 'fast']);
    expect(result.ranges[0]).toEqual({ route: 'slow', min: 10, mean: 50, max: 90 });
    expect(result.idle).toBe(1);
    expect(result.hidden).toBe(0);
  });

  describe('trends', () => {
    it('turns running totals into per-minute values', () => {
      const trends = buildTrends([snap('a', '10:02', 30, 3, 900), snap('a', '10:00', 10, 1, 100), snap('a', '10:01', 16, 1, 400)]);
      expect(trends.times).toEqual(['2026-11-28T10:01', '2026-11-28T10:02']);
      expect(trends.series).toEqual([{ route: 'a', exchanges: [6, 14], failures: [0, 2], processingMs: [300, 500] }]);
    });

    it('treats a total that went down as a restart', () => {
      const trends = buildTrends([snap('a', '10:00', 50), snap('a', '10:01', 4)]);
      expect(trends.series[0].exchanges).toEqual([4]);
    });

    it('spreads a longer gap between samples over its minutes', () => {
      const trends = buildTrends([snap('a', '10:00', 0), snap('a', '10:02', 10)]);
      expect(trends.series[0].exchanges).toEqual([5]);
    });

    it('aligns routes on the same minutes, with gaps where a route has no sample', () => {
      const trends = buildTrends([snap('a', '10:00', 0), snap('a', '10:01', 2), snap('a', '10:02', 4),
        snap('b', '10:01', 0), snap('b', '10:02', 1)]);
      expect(trends.series.find(s => s.route === 'b')!.exchanges).toEqual([null, 1]);
    });

    it('draws the busiest routes and folds the rest into one line', () => {
      const history = ['a', 'b', 'c'].flatMap((name, i) => [snap(name, '10:00', 0), snap(name, '10:01', 10 - i * 3)]);
      const lines = trendLines(buildTrends(history), 'exchanges', 1);
      expect(lines).toEqual([
        { label: 'a', values: [10], other: false },
        { label: '2 other routes', values: [7 + 4], other: true },
      ]);
    });

    it('averages processing time over exchanges, also for the folded line', () => {
      const history = [snap('a', '10:00', 0, 0, 0), snap('a', '10:01', 10, 0, 100),
        snap('b', '10:00', 0, 0, 0), snap('b', '10:01', 2, 0, 100),
        snap('c', '10:00', 0, 0, 0), snap('c', '10:01', 1, 0, 50)];
      const lines = trendLines(buildTrends(history), 'avgTime', 1);
      expect(lines[0]).toEqual({ label: 'a', values: [10], other: false });
      expect(lines[1].values).toEqual([150 / 3]);
    });

    it('has no average for a minute without exchanges', () => {
      const lines = trendLines(buildTrends([snap('a', '10:00', 5), snap('a', '10:01', 5)]), 'avgTime', 5);
      expect(lines[0].values).toEqual([null]);
    });

    it('labels minutes as HH:MM', () => {
      expect(clockLabel('2026-11-28T09:05')).toBe('09:05');
    });
  });
});

import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ChartData, ChartOptions, Plugin } from 'chart.js';
import { RouteMetric } from '../../core/models/metric-data';
import { ChartComponent } from './chart.component';
import { MetricsStatusComponent } from './metrics-status.component';
import { exchangeBars } from './metrics-derive';
import { categoryScale, formatCount, FONT_FAMILY, htmlTooltip, INK, motion, routeTick, tooltipOptions, valueScale } from './chart-theme';

const MAX_ROUTES = 10;
const ROW_PX = 34;
const AXIS_PX = 36;

/** The total at the end of each bar (the axis and tooltip carry the parts). */
const totalsPlugin: Plugin<'bar'> = {
  id: 'qttBarTotals',
  afterDatasetsDraw(chart) {
    const last = chart.getDatasetMeta(chart.data.datasets.length - 1);
    const totals = (chart.data.datasets as { data: number[] }[])
      .reduce<number[]>((sum, d) => d.data.map((v, i) => (sum[i] ?? 0) + (v ?? 0)), []);
    const ctx = chart.ctx;
    ctx.save();
    ctx.font = `500 12px ${FONT_FAMILY}`;
    ctx.fillStyle = INK.text;
    ctx.textBaseline = 'middle';
    last.data.forEach((bar, i) => {
      const { x, y } = bar.getProps(['x', 'y']) as { x: number; y: number }; // where the bar's end is now, mid-animation too
      ctx.fillText(formatCount(totals[i] ?? 0), x + 8, y);
    });
    ctx.restore();
  },
};

/** Successful and failed exchanges per route, busiest first. */
@Component({
  selector: 'qtt-exchanges-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ChartComponent, MetricsStatusComponent],
  template: `
    <section class="metrics-card" aria-labelledby="exchanges-title">
      <header class="metrics-card__header">
        <div class="metrics-card__heading">
          <h2 id="exchanges-title" class="metrics-card__title">Exchanges by route</h2>
          <p class="metrics-card__subtitle">{{ subtitle() }}</p>
        </div>
      </header>
      @if (mode() === null) {
        <ul class="metrics-legend" aria-label="Series">
          <li><span class="metrics-legend__key" [style.--key-color]="ink.primary"></span>Successful</li>
          <li><span class="metrics-legend__key" [style.--key-color]="ink.error"></span>Failed</li>
        </ul>
      }
      <div class="metrics-card__body" [style.height.px]="height()">
        @if (mode(); as mode) {
          <app-metrics-status [mode]="mode" emptyIcon="bar_chart"
            [emptyTitle]="routes()?.length ? 'No exchanges yet' : 'No routes yet'"
            [emptyDetail]="routes()?.length ? 'Bars appear once a route has handled a query.' : 'Routes appear here once they are created.'" [retryable]="false"></app-metrics-status>
        } @else {
          <qtt-chart type="bar" [data]="chartData()" [options]="chartOptions" [plugins]="plugins"
            label="Successful and failed exchanges by route"></qtt-chart>
        }
      </div>
    </section>
  `,
})
export class ExchangesCardComponent {
  /** Routes in scope; null while loading. */
  readonly routes = input.required<RouteMetric[] | null>();
  readonly failed = input(false);

  protected readonly ink = INK;
  protected readonly plugins = [totalsPlugin];

  private readonly bars = computed(() => exchangeBars(this.routes() ?? [], MAX_ROUTES));

  protected readonly mode = computed<'loading' | 'error' | 'empty' | null>(() => {
    const routes = this.routes();
    if (routes === null) {
      return this.failed() ? 'error' : 'loading';
    }
    return routes.some(r => r.exchangesTotal > 0) ? null : 'empty';
  });

  protected readonly subtitle = computed(() => {
    const { hidden } = this.bars();
    return hidden > 0
      ? `Busiest ${MAX_ROUTES} of ${MAX_ROUTES + hidden} routes since they started; all are in the table below`
      : 'Since each route started, busiest first';
  });

  protected readonly height = computed(() =>
    this.mode() === null ? Math.max(3, this.bars().bars.length) * ROW_PX + AXIS_PX : 240);

  protected readonly chartData = computed<ChartData<'bar'>>(() => {
    const { bars } = this.bars();
    const common = { maxBarThickness: 18, borderRadius: 4, borderSkipped: 'start' as const, categoryPercentage: 0.8 };
    return {
      labels: bars.map(b => b.route),
      datasets: [
        { ...common, label: 'Successful', data: bars.map(b => b.completed), backgroundColor: INK.primary,
          hoverBackgroundColor: '#9a3dff',
          // The 2px surface gap between the two parts of a bar.
          borderWidth: { right: 2 }, borderColor: INK.surface },
        { ...common, label: 'Failed', data: bars.map(b => b.failed), backgroundColor: INK.error,
          hoverBackgroundColor: '#d63a3a' },
      ],
    };
  });

  protected readonly chartOptions: ChartOptions<'bar'> = {
    ...motion(),
    indexAxis: 'y',
    interaction: { mode: 'index', axis: 'y', intersect: false },
    layout: { padding: { right: 48 } },
    scales: {
      x: { ...valueScale(formatCount), stacked: true },
      y: { ...categoryScale(), stacked: true, ticks: { color: INK.text, padding: 8, autoSkip: false, callback: routeTick } },
    },
    plugins: {
      tooltip: tooltipOptions(htmlTooltip(tooltip => {
        const points = tooltip.dataPoints;
        const completed = Number(points.find(p => p.dataset.label === 'Successful')?.raw ?? 0);
        const failed = Number(points.find(p => p.dataset.label === 'Failed')?.raw ?? 0);
        const total = completed + failed;
        return {
          title: String(points[0]?.label ?? ''),
          rows: [
            { value: formatCount(completed), label: 'successful', color: INK.primary, key: 'square' },
            { value: formatCount(failed), label: 'failed', color: INK.error, key: 'square' },
            ...(total > 0 ? [{ value: `${((completed / total) * 100).toFixed(1)}%`, label: 'success rate',
              color: 'transparent', key: 'square' as const }] : []),
          ],
        };
      })),
    },
  } as ChartOptions<'bar'>;
}
